package dev.auralis.studio.resolver.android

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.util.Log
import dev.auralis.studio.resolver.engine.ChaquopyYtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Service running in the isolated :resolver process to handle inspection and resolution
 * through asynchronous Binder IPC.
 */
class LrrResolverService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private lateinit var engine: ChaquopyYtDlpEngine

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "LrrResolverService created in process ':resolver' (PID: ${Process.myPid()})")
        engine = ChaquopyYtDlpEngine(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "LrrResolverService destroying (PID: ${Process.myPid()})")
        serviceScope.cancel()
        activeJobs.clear()
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Client bound to LrrResolverService (PID: ${Process.myPid()})")
        return binder
    }

    private val binder = object : ILrrResolverService.Stub() {
        override fun inspect(requestJson: String?, callback: ILrrResolverCallback?) {
            if (callback == null) return
            val jsonStr = requestJson.orEmpty()
            val requestId = extractRequestId(jsonStr)

            val job = serviceScope.launch {
                try {
                    val rawResult = engine.inspect(jsonStr)
                    val finalJson = if (rawResult.length > MAX_PAYLOAD_SIZE_BYTES) {
                        buildErrorJson(
                            source = extractSourceUrl(jsonStr),
                            code = "PayloadTooLarge",
                            message = "The media resolution response exceeded safe communication limits.",
                            details = "Payload size ${rawResult.length} bytes exceeded $MAX_PAYLOAD_SIZE_BYTES limit",
                        )
                    } else {
                        rawResult
                    }
                    val isSuccess = isJsonSuccess(finalJson)
                    if (isSuccess) {
                        callback.onSuccess(requestId, finalJson)
                    } else {
                        callback.onFailure(requestId, finalJson)
                    }
                } catch (e: RemoteException) {
                    Log.w(TAG, "Remote client disconnected during inspect for $requestId")
                } catch (t: Throwable) {
                    val errJson = buildErrorJson(
                        source = extractSourceUrl(jsonStr),
                        code = "Unknown",
                        message = "Inspection error: ${t.message}",
                        details = t.stackTraceToString(),
                    )
                    try {
                        callback.onFailure(requestId, errJson)
                    } catch (_: RemoteException) {}
                } finally {
                    activeJobs.remove(requestId)
                }
            }

            activeJobs[requestId] = job
        }

        override fun resolve(requestJson: String?, callback: ILrrResolverCallback?) {
            if (callback == null) return
            val jsonStr = requestJson.orEmpty()
            val requestId = extractRequestId(jsonStr)

            val job = serviceScope.launch {
                try {
                    val rawResult = engine.resolve(jsonStr)
                    val finalJson = if (rawResult.length > MAX_PAYLOAD_SIZE_BYTES) {
                        buildErrorJson(
                            source = extractSourceUrl(jsonStr),
                            code = "PayloadTooLarge",
                            message = "The media resolution response exceeded safe communication limits.",
                            details = "Payload size ${rawResult.length} bytes exceeded $MAX_PAYLOAD_SIZE_BYTES limit",
                        )
                    } else {
                        rawResult
                    }
                    val isSuccess = isJsonSuccess(finalJson)
                    if (isSuccess) {
                        callback.onSuccess(requestId, finalJson)
                    } else {
                        callback.onFailure(requestId, finalJson)
                    }
                } catch (e: RemoteException) {
                    Log.w(TAG, "Remote client disconnected during resolve for $requestId")
                } catch (t: Throwable) {
                    val errJson = buildErrorJson(
                        source = extractSourceUrl(jsonStr),
                        code = "Unknown",
                        message = "Resolution error: ${t.message}",
                        details = t.stackTraceToString(),
                    )
                    try {
                        callback.onFailure(requestId, errJson)
                    } catch (_: RemoteException) {}
                } finally {
                    activeJobs.remove(requestId)
                }
            }

            activeJobs[requestId] = job
        }

        override fun cancelRequest(requestId: String?) {
            if (requestId == null) return
            val job = activeJobs.remove(requestId)
            if (job != null && job.isActive) {
                job.cancel()
                Log.d(TAG, "Cancelled active request $requestId")
            }
        }

        override fun getHealthJson(): String {
            val rawJson = engine.getHealthJson()
            return try {
                val element = Json.parseToJsonElement(rawJson)
                val obj = element as? kotlinx.serialization.json.JsonObject ?: return rawJson
                val existingDetails = obj["details"]?.let { (it as? kotlinx.serialization.json.JsonObject)?.toMutableMap() } ?: mutableMapOf()

                // Compute live process diagnostics INSIDE :resolver process
                val processName = dev.auralis.studio.core.ProcessUtils.currentProcessName(this@LrrResolverService)
                val pid = Process.myPid().toString()
                val processRole = dev.auralis.studio.core.ProcessUtils.detectProcessRole(this@LrrResolverService).name
                val mainContainerInit = ((application as? dev.auralis.studio.AuralisApplication)?.isMainContainerInitialized == true).toString()

                existingDetails["process_name"] = kotlinx.serialization.json.JsonPrimitive(processName)
                existingDetails["pid"] = kotlinx.serialization.json.JsonPrimitive(pid)
                existingDetails["process_role"] = kotlinx.serialization.json.JsonPrimitive(processRole)
                existingDetails["main_container_initialized"] = kotlinx.serialization.json.JsonPrimitive(mainContainerInit)

                val updatedRoot = obj.toMutableMap()
                updatedRoot["details"] = kotlinx.serialization.json.JsonObject(existingDetails)
                kotlinx.serialization.json.JsonObject(updatedRoot).toString()
            } catch (_: Throwable) {
                rawJson
            }
        }
    }

    private fun extractRequestId(json: String): String {
        return try {
            val element = Json.parseToJsonElement(json)
            (element as? kotlinx.serialization.json.JsonObject)?.get("requestId")?.toString()?.trim('"')
                ?: java.util.UUID.randomUUID().toString()
        } catch (_: Throwable) {
            java.util.UUID.randomUUID().toString()
        }
    }

    private fun extractSourceUrl(json: String): String {
        return try {
            val element = Json.parseToJsonElement(json)
            (element as? kotlinx.serialization.json.JsonObject)?.get("sourceUrl")?.toString()?.trim('"').orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun isJsonSuccess(json: String): Boolean {
        return try {
            val element = Json.parseToJsonElement(json)
            val obj = element as? kotlinx.serialization.json.JsonObject
            obj?.get("success")?.toString() == "true"
        } catch (_: Throwable) {
            false
        }
    }

    private fun buildErrorJson(source: String, code: String, message: String, details: String?): String {
        return buildJsonObject {
            put("success", false)
            putJsonObject("error") {
                put("source", source)
                put("technicalCode", code)
                put("isRetryable", false)
                put("safeUserMessage", message)
                if (details != null) {
                    put("debugDetails", details)
                }
            }
        }.toString()
    }

    companion object {
        private const val TAG = "LRR:Service"
        private const val MAX_PAYLOAD_SIZE_BYTES = 512 * 1024
    }
}
