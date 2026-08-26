package dev.auralis.studio.resolver.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import dev.auralis.studio.resolver.api.MediaResolver
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolvedFormat
import dev.auralis.studio.resolver.api.ResolvedMedia
import dev.auralis.studio.resolver.api.ResolverError
import dev.auralis.studio.resolver.api.ResolverErrorCode
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult
import dev.auralis.studio.resolver.api.RuntimeHealth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Parser utility for LRR IPC payloads.
 */
object LrrIpcParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseIpcResponse(responseJson: String, fallbackSource: String): ResolverResult {
        return try {
            val root = json.parseToJsonElement(responseJson).jsonObject
            val isSuccess = root["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (isSuccess && root.containsKey("media")) {
                val mediaObj = root["media"]!!.jsonObject
                val media = parseResolvedMedia(mediaObj, fallbackSource)
                ResolverResult.Success(media)
            } else {
                val errorObj = root["error"]?.jsonObject
                val error = if (errorObj != null) {
                    parseResolverError(errorObj, fallbackSource)
                } else {
                    ResolverError.unknown(fallbackSource, "Invalid response from resolver")
                }
                ResolverResult.Failure(error)
            }
        } catch (t: Throwable) {
            ResolverResult.Failure(
                ResolverError.unknown(
                    source = fallbackSource,
                    message = "Failed to parse resolver response.",
                    details = "${t.javaClass.simpleName}: ${t.message}",
                ),
            )
        }
    }

    fun parseResolvedMedia(obj: kotlinx.serialization.json.JsonObject, fallbackSource: String): ResolvedMedia {
        val source = obj["source"]?.jsonPrimitive?.contentOrNull ?: fallbackSource
        val sourceId = obj["sourceId"]?.jsonPrimitive?.contentOrNull
        val webpageUrl = obj["webpageUrl"]?.jsonPrimitive?.contentOrNull
        val title = obj["title"]?.jsonPrimitive?.contentOrNull
        val artist = obj["artist"]?.jsonPrimitive?.contentOrNull
        val album = obj["album"]?.jsonPrimitive?.contentOrNull
        val durationMs = obj["durationMs"]?.jsonPrimitive?.longOrNull
        val artworkUrl = obj["artworkUrl"]?.jsonPrimitive?.contentOrNull

        val formats = obj["formats"]?.jsonArray?.mapNotNull { fmtElement ->
            val fmtObj = fmtElement.jsonObject
            val url = fmtObj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val formatId = fmtObj["formatId"]?.jsonPrimitive?.contentOrNull
            val ext = fmtObj["extension"]?.jsonPrimitive?.contentOrNull
            val mimeType = fmtObj["mimeType"]?.jsonPrimitive?.contentOrNull
            val acodec = fmtObj["audioCodec"]?.jsonPrimitive?.contentOrNull
            val vcodec = fmtObj["videoCodec"]?.jsonPrimitive?.contentOrNull
            val bitrateKbps = fmtObj["bitrateKbps"]?.jsonPrimitive?.intOrNull
            val sampleRateHz = fmtObj["sampleRateHz"]?.jsonPrimitive?.intOrNull
            val fileSizeBytes = fmtObj["fileSizeBytes"]?.jsonPrimitive?.longOrNull
            val protocol = fmtObj["protocol"]?.jsonPrimitive?.contentOrNull
            val expiresAtEpochMs = fmtObj["expiresAtEpochMs"]?.jsonPrimitive?.longOrNull
            val isAudioOnly = fmtObj["isAudioOnly"]?.jsonPrimitive?.booleanOrNull ?: true

            val modeStr = fmtObj["resolutionMode"]?.jsonPrimitive?.contentOrNull
            val resolutionMode = when (modeStr) {
                "SEGMENTED_RESOURCE" -> ResolutionMode.SEGMENTED_RESOURCE
                "ENGINE_REQUIRED" -> ResolutionMode.ENGINE_REQUIRED
                else -> ResolutionMode.DIRECT_RESOURCE
            }

            val headers = mutableMapOf<String, String>()
            fmtObj["httpHeaders"]?.jsonObject?.forEach { (k, v) ->
                v.jsonPrimitive.contentOrNull?.let { headers[k] = it }
            }

            ResolvedFormat(
                formatId = formatId,
                url = url,
                extension = ext,
                mimeType = mimeType,
                audioCodec = acodec,
                videoCodec = vcodec,
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRateHz,
                fileSizeBytes = fileSizeBytes,
                protocol = protocol,
                httpHeaders = headers,
                expiresAtEpochMs = expiresAtEpochMs,
                resolutionMode = resolutionMode,
                isAudioOnly = isAudioOnly,
            )
        } ?: emptyList()

        return ResolvedMedia(
            source = source,
            sourceId = sourceId,
            webpageUrl = webpageUrl,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            formats = formats,
        )
    }

    fun parseResolverError(obj: kotlinx.serialization.json.JsonObject, fallbackSource: String): ResolverError {
        val source = obj["source"]?.jsonPrimitive?.contentOrNull ?: fallbackSource
        val codeStr = obj["technicalCode"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
        val code = runCatching { ResolverErrorCode.valueOf(codeStr) }.getOrDefault(ResolverErrorCode.Unknown)
        val isRetryable = obj["isRetryable"]?.jsonPrimitive?.booleanOrNull ?: false
        val userMsg = obj["safeUserMessage"]?.jsonPrimitive?.contentOrNull ?: "Resolution failed."
        val debugDetails = obj["debugDetails"]?.jsonPrimitive?.contentOrNull

        return ResolverError(
            source = source,
            technicalCode = code,
            isRetryable = isRetryable,
            safeUserMessage = userMsg,
            debugDetails = debugDetails,
        )
    }

    fun parseHealthJson(jsonStr: String): RuntimeHealth {
        return try {
            val root = json.parseToJsonElement(jsonStr).jsonObject
            val svcReady = root["resolverServiceReady"]?.jsonPrimitive?.booleanOrNull ?: true
            val pyReady = root["pythonReady"]?.jsonPrimitive?.booleanOrNull ?: false
            val ytReady = root["ytDlpReady"]?.jsonPrimitive?.booleanOrNull ?: false
            val ejsInst = root["ejsInstalled"]?.jsonPrimitive?.booleanOrNull
                ?: root["ejsReady"]?.jsonPrimitive?.booleanOrNull ?: false
            val ejsComp = root["ejsCompatible"]?.jsonPrimitive?.booleanOrNull
                ?: root["ejsReady"]?.jsonPrimitive?.booleanOrNull ?: false
            val jsPres = root["jsRuntimePresent"]?.jsonPrimitive?.booleanOrNull
                ?: root["jsRuntimeReady"]?.jsonPrimitive?.booleanOrNull ?: false
            val jsExec = root["jsRuntimeExecutable"]?.jsonPrimitive?.booleanOrNull
                ?: root["jsRuntimeReady"]?.jsonPrimitive?.booleanOrNull ?: false
            val jsYt = root["jsRuntimeDetectedByYtDlp"]?.jsonPrimitive?.booleanOrNull
                ?: root["jsRuntimeReady"]?.jsonPrimitive?.booleanOrNull ?: false
            val ffReady = root["ffmpegReady"]?.jsonPrimitive?.booleanOrNull ?: false

            val details = mutableMapOf<String, String>()
            root["details"]?.jsonObject?.forEach { (k, v) ->
                v.jsonPrimitive.contentOrNull?.let { details[k] = it }
            }

            RuntimeHealth(
                resolverServiceReady = svcReady,
                pythonReady = pyReady,
                ytDlpReady = ytReady,
                ejsInstalled = ejsInst,
                ejsCompatible = ejsComp,
                jsRuntimePresent = jsPres,
                jsRuntimeExecutable = jsExec,
                jsRuntimeDetectedByYtDlp = jsYt,
                ffmpegReady = ffReady,
                details = details,
            )
        } catch (_: Throwable) {
            RuntimeHealth(resolverServiceReady = true, pythonReady = false, ytDlpReady = false)
        }
    }
}

/**
 * Main-process client for Lyrune Resolver Runtime (LRR).
 *
 * Communicates with [LrrResolverService] running in the isolated :resolver process
 * via asynchronous Binder IPC. Translates raw Binder responses into stable Kotlin contracts.
 */
class AndroidProcessMediaResolver(
    private val context: Context,
    private val defaultTimeoutMs: Long = 25_000L,
) : MediaResolver {

    private val json = Json { ignoreUnknownKeys = true }
    private var service: ILrrResolverService? = null
    private val isBinding = AtomicBoolean(false)
    private var bindDeferred = CompletableDeferred<ILrrResolverService?>()
    private val pendingRequests = ConcurrentHashMap<String, (Result<String>) -> Unit>()

    private val deathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, "LrrResolverService (:resolver process) died!")
        synchronized(this) {
            service = null
            bindDeferred = CompletableDeferred()
        }
        // Fail any pending requests with RuntimeCrashed
        val errorJson = buildCrashedJson()
        pendingRequests.values.forEach { it(Result.failure(RemoteException(errorJson))) }
        pendingRequests.clear()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "Connected to LrrResolverService in process :resolver")
            val boundService = ILrrResolverService.Stub.asInterface(binder)
            try {
                binder?.linkToDeath(deathRecipient, 0)
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to link death recipient to resolver service", e)
            }
            synchronized(this@AndroidProcessMediaResolver) {
                service = boundService
                isBinding.set(false)
                bindDeferred.complete(boundService)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Disconnected from LrrResolverService")
            synchronized(this@AndroidProcessMediaResolver) {
                service = null
                isBinding.set(false)
                bindDeferred = CompletableDeferred()
            }
        }
    }

    private suspend fun getOrBindService(): ILrrResolverService? {
        synchronized(this) {
            service?.let { return it }
        }

        if (isBinding.compareAndSet(false, true)) {
            val intent = Intent(context, LrrResolverService::class.java)
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                Log.e(TAG, "Failed to bind to LrrResolverService")
                isBinding.set(false)
                return null
            }
        }

        return withTimeoutOrNull(8_000L) {
            bindDeferred.await()
        }
    }

    override suspend fun inspect(request: ResolverRequest): ResolverResult = withContext(Dispatchers.IO) {
        val requestJson = json.encodeToString(ResolverRequest.serializer(), request)
        executeIpc(request.requestId, request.sourceUrl.orEmpty()) { svc, cb ->
            svc.inspect(requestJson, cb)
        }
    }

    override suspend fun resolve(request: ResolverRequest): ResolverResult = withContext(Dispatchers.IO) {
        val requestJson = json.encodeToString(ResolverRequest.serializer(), request)
        executeIpc(request.requestId, request.sourceUrl.orEmpty()) { svc, cb ->
            svc.resolve(requestJson, cb)
        }
    }

    override suspend fun cancel(requestId: String) {
        withContext(Dispatchers.IO) {
            service?.let { svc ->
                try {
                    svc.cancelRequest(requestId)
                } catch (e: RemoteException) {
                    Log.w(TAG, "Remote exception cancelling request $requestId", e)
                }
            }
            pendingRequests.remove(requestId)
        }
    }

    override suspend fun health(): RuntimeHealth = withContext(Dispatchers.IO) {
        val svc = getOrBindService()
        if (svc == null) {
            return@withContext RuntimeHealth(
                resolverServiceReady = false,
                pythonReady = false,
                ytDlpReady = false,
                ejsInstalled = false,
                ejsCompatible = false,
                jsRuntimePresent = false,
                jsRuntimeExecutable = false,
                jsRuntimeDetectedByYtDlp = false,
                ffmpegReady = false,
                details = mapOf("error" to "SERVICE_UNAVAILABLE"),
            )
        }

        try {
            val healthJson = svc.healthJson
            if (healthJson.isNullOrBlank()) {
                RuntimeHealth(
                    resolverServiceReady = true,
                    pythonReady = false,
                    ytDlpReady = false,
                )
            } else {
                LrrIpcParser.parseHealthJson(healthJson)
            }
        } catch (e: Exception) {
            RuntimeHealth(
                resolverServiceReady = false,
                pythonReady = false,
                ytDlpReady = false,
                details = mapOf("error" to (e.message ?: "Health check failed")),
            )
        }
    }

    private suspend fun executeIpc(
        requestId: String,
        sourceUrl: String,
        action: (ILrrResolverService, ILrrResolverCallback) -> Unit,
    ): ResolverResult {
        val svc = getOrBindService()
            ?: return ResolverResult.Failure(
                ResolverError(
                    source = sourceUrl,
                    technicalCode = ResolverErrorCode.RuntimeUnavailable,
                    isRetryable = true,
                    safeUserMessage = "Resolver service is currently unavailable.",
                    debugDetails = "Could not bind to LrrResolverService in :resolver process",
                ),
            )

        val resultJson = withTimeoutOrNull(defaultTimeoutMs) {
            suspendCancellableCoroutine<String> { continuation ->
                val callback = object : ILrrResolverCallback.Stub() {
                    override fun onSuccess(reqId: String?, resultJson: String?) {
                        if (reqId == requestId && continuation.isActive) {
                            pendingRequests.remove(requestId)
                            continuation.resume(resultJson.orEmpty())
                        }
                    }

                    override fun onFailure(reqId: String?, errorJson: String?) {
                        if (reqId == requestId && continuation.isActive) {
                            pendingRequests.remove(requestId)
                            continuation.resume(errorJson.orEmpty())
                        }
                    }
                }

                pendingRequests[requestId] = { res ->
                    if (continuation.isActive) {
                        res.fold(
                            onSuccess = { continuation.resume(it) },
                            onFailure = { continuation.resume(buildCrashedJson()) },
                        )
                    }
                }

                continuation.invokeOnCancellation {
                    try {
                        svc.cancelRequest(requestId)
                    } catch (_: RemoteException) {}
                    pendingRequests.remove(requestId)
                }

                try {
                    action(svc, callback)
                } catch (e: RemoteException) {
                    if (continuation.isActive) {
                        pendingRequests.remove(requestId)
                        continuation.resume(buildCrashedJson())
                    }
                }
            }
        }

        if (resultJson == null) {
            // Timed out
            try {
                svc.cancelRequest(requestId)
            } catch (_: RemoteException) {}
            return ResolverResult.Failure(ResolverError.timeout(sourceUrl, "IPC call timed out after ${defaultTimeoutMs}ms"))
        }

        if (resultJson.length > MAX_PAYLOAD_SIZE_BYTES) {
            return ResolverResult.Failure(
                ResolverError(
                    source = sourceUrl,
                    technicalCode = ResolverErrorCode.Unknown,
                    isRetryable = false,
                    safeUserMessage = "Resolver returned an oversized payload.",
                    debugDetails = "Payload size ${resultJson.length} exceeded limit $MAX_PAYLOAD_SIZE_BYTES",
                ),
            )
        }

        return LrrIpcParser.parseIpcResponse(resultJson, sourceUrl)
    }

    private fun buildCrashedJson(): String {
        return """{"success":false,"error":{"source":"","technicalCode":"RuntimeCrashed","isRetryable":true,"safeUserMessage":"The resolver process terminated unexpectedly.","debugDetails":"Binder death detected"}}"""
    }

    companion object {
        private const val TAG = "LRR:AndroidResolver"
        private const val MAX_PAYLOAD_SIZE_BYTES = 512 * 1024 // 512 KB limit
    }
}
