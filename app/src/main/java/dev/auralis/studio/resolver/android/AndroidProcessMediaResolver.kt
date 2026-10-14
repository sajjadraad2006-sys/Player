package dev.auralis.studio.resolver.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import dev.auralis.studio.resolver.api.MediaResolver
import dev.auralis.studio.resolver.api.ResolvedFormat
import dev.auralis.studio.resolver.api.ResolvedMedia
import dev.auralis.studio.resolver.api.ResolverError
import dev.auralis.studio.resolver.api.ResolverErrorCode
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult
import dev.auralis.studio.resolver.api.RuntimeHealth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * Parser utilities for LRR IPC payloads.
 */
object LrrIpcParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseIpcResponse(resultJson: String, sourceUrl: String): ResolverResult {
        if (resultJson.length > 512 * 1024) {
            return ResolverResult.Failure(ResolverError.payloadTooLarge(sourceUrl))
        }

        return try {
            val root = json.parseToJsonElement(resultJson).jsonObject
            val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: false

            if (success) {
                val mediaObj = root["media"]?.jsonObject
                    ?: return ResolverResult.Failure(ResolverError.unknown(sourceUrl, details = "Missing media payload"))

                val schemaVer = mediaObj["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
                if (schemaVer > 1) {
                    return ResolverResult.Failure(ResolverError.incompatibleSchemaVersion(sourceUrl, schemaVer))
                }

                val leaseObj = root["lease"]?.jsonObject
                val lease = leaseObj?.let { l ->
                    val headersMap = mutableMapOf<String, String>()
                    l["headers"]?.jsonObject?.forEach { (k, v) ->
                        v.jsonPrimitive.contentOrNull?.let { headersMap[k] = it }
                    }
                    val modeStr = l["resolutionMode"]?.jsonPrimitive?.contentOrNull ?: "DIRECT_RESOURCE"
                    val mode = try { dev.auralis.studio.resolver.api.ResolutionMode.valueOf(modeStr) } catch (_: Exception) { dev.auralis.studio.resolver.api.ResolutionMode.DIRECT_RESOURCE }
                    val kindStr = l["mediaKind"]?.jsonPrimitive?.contentOrNull ?: "AUDIO_ONLY"
                    val kind = try { dev.auralis.studio.resolver.api.MediaKind.valueOf(kindStr) } catch (_: Exception) { dev.auralis.studio.resolver.api.MediaKind.AUDIO_ONLY }

                    dev.auralis.studio.resolver.api.EphemeralResourceLease(
                        requestId = l["requestId"]?.jsonPrimitive?.contentOrNull ?: "",
                        formatId = l["formatId"]?.jsonPrimitive?.contentOrNull ?: "",
                        url = l["url"]?.jsonPrimitive?.contentOrNull ?: "",
                        headers = headersMap,
                        resolvedAtEpochMs = l["resolvedAtEpochMs"]?.jsonPrimitive?.longOrNull
                            ?: l["resolvedAtElapsedRealtimeMs"]?.jsonPrimitive?.longOrNull
                            ?: System.currentTimeMillis(),
                        knownExpiryEpochMs = l["knownExpiryEpochMs"]?.jsonPrimitive?.longOrNull,
                        resolutionMode = mode,
                        mediaKind = kind,
                    )
                }

                val media = parseMediaJson(mediaObj, sourceUrl)
                ResolverResult.Success(media = media, lease = lease)
            } else {
                val errorObj = root["error"]?.jsonObject
                    ?: return ResolverResult.Failure(ResolverError.unknown(sourceUrl, details = "Missing error payload"))
                ResolverResult.Failure(parseErrorJson(errorObj, sourceUrl))
            }
        } catch (e: Exception) {
            ResolverResult.Failure(
                ResolverError.unknown(
                    source = sourceUrl,
                    message = "Malformed resolver response payload.",
                    details = "${e.javaClass.simpleName}: ${e.message}",
                ),
            )
        }
    }

    fun parseMediaJson(obj: kotlinx.serialization.json.JsonObject, sourceUrl: String): ResolvedMedia {
        val schemaVer = obj["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
        val source = obj["source"]?.jsonPrimitive?.contentOrNull ?: "Generic"
        val webpageUrl = obj["webpageUrl"]?.jsonPrimitive?.contentOrNull ?: sourceUrl
        val license = obj["license"]?.jsonPrimitive?.contentOrNull

        val formatsArray = obj["formats"]?.jsonArray ?: emptyList()
        val formats = formatsArray.mapNotNull { it as? kotlinx.serialization.json.JsonObject }.map { f ->
            val modeStr = f["resolutionMode"]?.jsonPrimitive?.contentOrNull ?: "DIRECT_RESOURCE"
            val mode = try {
                dev.auralis.studio.resolver.api.ResolutionMode.valueOf(modeStr)
            } catch (_: Exception) {
                dev.auralis.studio.resolver.api.ResolutionMode.DIRECT_RESOURCE
            }

            val drmStr = f["drmState"]?.jsonPrimitive?.contentOrNull ?: "NONE"
            val drm = try {
                dev.auralis.studio.resolver.api.DrmState.valueOf(drmStr)
            } catch (_: Exception) {
                dev.auralis.studio.resolver.api.DrmState.NONE
            }

            val blockReasonStr = f["blockReason"]?.jsonPrimitive?.contentOrNull ?: "NONE"
            val blockReason = try {
                dev.auralis.studio.resolver.api.DeliveryBlockReason.valueOf(blockReasonStr)
            } catch (_: Exception) {
                dev.auralis.studio.resolver.api.DeliveryBlockReason.NONE
            }

            val kindStr = f["mediaKind"]?.jsonPrimitive?.contentOrNull ?: "AUDIO_ONLY"
            val kind = try {
                dev.auralis.studio.resolver.api.MediaKind.valueOf(kindStr)
            } catch (_: Exception) {
                dev.auralis.studio.resolver.api.MediaKind.AUDIO_ONLY
            }

            val explicitCapStr = f["downloadCapability"]?.jsonPrimitive?.contentOrNull
            val explicitBasisStr = f["downloadCapabilityBasis"]?.jsonPrimitive?.contentOrNull
            val eval = if (explicitCapStr != null && explicitCapStr != "UNKNOWN") {
                dev.auralis.studio.resolver.api.DownloadCapabilityEvaluator.EvaluationResult(
                    capability = dev.auralis.studio.resolver.api.DownloadCapability.valueOf(explicitCapStr),
                    basis = explicitBasisStr?.let { dev.auralis.studio.resolver.api.DownloadCapabilityBasis.valueOf(it) }
                        ?: dev.auralis.studio.resolver.api.DownloadCapabilityBasis.NONE,
                )
            } else {
                dev.auralis.studio.resolver.api.DownloadCapabilityEvaluator.evaluate(
                    source = source,
                    webpageUrl = webpageUrl,
                    license = license,
                )
            }

            val reqsObj = f["deliveryRequirements"]?.jsonObject
            val reqs = dev.auralis.studio.resolver.api.DeliveryRequirements(
                requiresCustomHeaders = reqsObj?.get("requiresCustomHeaders")?.jsonPrimitive?.booleanOrNull ?: false,
                requiresBrowserImpersonation = reqsObj?.get("requiresBrowserImpersonation")?.jsonPrimitive?.booleanOrNull ?: false,
                requiresPostRequestData = reqsObj?.get("requiresPostRequestData")?.jsonPrimitive?.booleanOrNull ?: false,
                isSegmented = reqsObj?.get("isSegmented")?.jsonPrimitive?.booleanOrNull ?: false,
                hasDrm = reqsObj?.get("hasDrm")?.jsonPrimitive?.booleanOrNull ?: false,
            )

            val bitrateVal = f["bitrateKbps"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

            dev.auralis.studio.resolver.api.ResolvedFormatDescriptor(
                formatId = f["formatId"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                mediaKind = kind,
                container = f["container"]?.jsonPrimitive?.contentOrNull,
                extension = f["extension"]?.jsonPrimitive?.contentOrNull,
                audioCodec = f["audioCodec"]?.jsonPrimitive?.contentOrNull,
                videoCodec = f["videoCodec"]?.jsonPrimitive?.contentOrNull,
                bitrateKbps = bitrateVal,
                sampleRateHz = f["sampleRateHz"]?.jsonPrimitive?.intOrNull,
                channels = f["channels"]?.jsonPrimitive?.intOrNull,
                fileSizeBytes = f["fileSizeBytes"]?.jsonPrimitive?.longOrNull,
                approximateFileSizeBytes = f["approximateFileSizeBytes"]?.jsonPrimitive?.longOrNull,
                protocol = f["protocol"]?.jsonPrimitive?.contentOrNull,
                resolutionMode = mode,
                drmState = drm,
                blockReason = blockReason,
                deliveryRequirements = reqs,
                downloadCapability = eval.capability,
                downloadCapabilityBasis = eval.basis,
            )
        }

        val rawFormatsArray = obj["rawFormats"]?.jsonArray ?: emptyList()
        val rawFormats = rawFormatsArray.mapNotNull { it as? kotlinx.serialization.json.JsonObject }.map { f ->
            val headersMap = mutableMapOf<String, String>()
            f["httpHeaders"]?.jsonObject?.forEach { (k, v) ->
                v.jsonPrimitive.contentOrNull?.let { headersMap[k] = it }
            }

            val modeStr = f["resolutionMode"]?.jsonPrimitive?.contentOrNull ?: "DIRECT_RESOURCE"
            val mode = try {
                dev.auralis.studio.resolver.api.ResolutionMode.valueOf(modeStr)
            } catch (_: Exception) {
                dev.auralis.studio.resolver.api.ResolutionMode.DIRECT_RESOURCE
            }

            ResolvedFormat(
                formatId = f["formatId"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                url = f["url"]?.jsonPrimitive?.contentOrNull ?: "",
                extension = f["extension"]?.jsonPrimitive?.contentOrNull,
                mimeType = f["mimeType"]?.jsonPrimitive?.contentOrNull,
                audioCodec = f["audioCodec"]?.jsonPrimitive?.contentOrNull,
                videoCodec = f["videoCodec"]?.jsonPrimitive?.contentOrNull,
                bitrateKbps = f["bitrateKbps"]?.jsonPrimitive?.intOrNull,
                sampleRateHz = f["sampleRateHz"]?.jsonPrimitive?.intOrNull,
                fileSizeBytes = f["fileSizeBytes"]?.jsonPrimitive?.longOrNull,
                protocol = f["protocol"]?.jsonPrimitive?.contentOrNull,
                httpHeaders = headersMap,
                expiresAtEpochMs = f["expiresAtEpochMs"]?.jsonPrimitive?.longOrNull,
                resolutionMode = mode,
                isAudioOnly = f["isAudioOnly"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }

        val availStr = obj["availability"]?.jsonPrimitive?.contentOrNull ?: "AVAILABLE"
        val avail = try {
            dev.auralis.studio.resolver.api.AvailabilityState.valueOf(availStr)
        } catch (_: Exception) {
            dev.auralis.studio.resolver.api.AvailabilityState.AVAILABLE
        }

        return ResolvedMedia(
            schemaVersion = schemaVer,
            source = source,
            sourceId = obj["sourceId"]?.jsonPrimitive?.contentOrNull,
            webpageUrl = webpageUrl,
            title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown Title",
            artist = obj["artist"]?.jsonPrimitive?.contentOrNull,
            album = obj["album"]?.jsonPrimitive?.contentOrNull,
            durationMs = obj["durationMs"]?.jsonPrimitive?.longOrNull,
            artworkUrl = obj["artworkUrl"]?.jsonPrimitive?.contentOrNull,
            availability = avail,
            formats = formats,
            rawFormats = rawFormats,
        )
    }

    fun parseErrorJson(obj: kotlinx.serialization.json.JsonObject, fallbackSource: String): ResolverError {
        val src = obj["source"]?.jsonPrimitive?.contentOrNull ?: fallbackSource
        val codeStr = obj["technicalCode"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
        val code = try {
            ResolverErrorCode.valueOf(codeStr)
        } catch (_: Exception) {
            ResolverErrorCode.Unknown
        }
        val retryable = obj["isRetryable"]?.jsonPrimitive?.booleanOrNull ?: false
        val userMsg = obj["safeUserMessage"]?.jsonPrimitive?.contentOrNull ?: "Unable to resolve media from this source."
        val debugDetails = obj["debugDetails"]?.jsonPrimitive?.contentOrNull

        return ResolverError(
            source = src,
            technicalCode = code,
            isRetryable = retryable,
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
            val jsChallengeSelfTest = root["jsChallengeSelfTestReady"]?.jsonPrimitive?.booleanOrNull ?: false
            val realExtraction = root["realExtractionReady"]?.jsonPrimitive?.booleanOrNull ?: false
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
                jsChallengeSelfTestReady = jsChallengeSelfTest,
                realExtractionReady = realExtraction,
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
 *
 * Implements a conservative idle policy: unbinds after [idleTimeoutMs] of inactivity,
 * allowing the Android OS to reclaim the :resolver process when idle.
 */
class AndroidProcessMediaResolver(
    private val context: Context,
    private val defaultTimeoutMs: Long = 25_000L,
    private val idleTimeoutMs: Long = 60_000L,
) : MediaResolver {

    init {
        val role = dev.auralis.studio.core.ProcessUtils.detectProcessRole(context)
        check(role == dev.auralis.studio.core.ProcessRole.MAIN) {
            "AndroidProcessMediaResolver client can only be instantiated inside the main application process (current: $role)"
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var service: ILrrResolverService? = null
    private val isBinding = AtomicBoolean(false)
    private var isBound = false
    private var bindDeferred = CompletableDeferred<ILrrResolverService?>()
    private val pendingRequests = ConcurrentHashMap<String, (Result<String>) -> Unit>()
    private val resolutionCache = ConcurrentHashMap<String, Pair<Long, ResolverResult.Success>>()

    private val resolverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var idleJob: Job? = null

    private val deathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, "LrrResolverService (:resolver process) died!")
        synchronized(this) {
            service = null
            isBound = false
            bindDeferred = CompletableDeferred()
        }
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
                isBound = true
                isBinding.set(false)
                bindDeferred.complete(boundService)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Disconnected from LrrResolverService")
            synchronized(this@AndroidProcessMediaResolver) {
                service = null
                isBound = false
                isBinding.set(false)
                bindDeferred = CompletableDeferred()
            }
        }
    }

    private suspend fun getOrBindService(): ILrrResolverService? {
        synchronized(this) {
            idleJob?.cancel()
            idleJob = null
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

    private fun scheduleIdleUnbind() {
        if (idleTimeoutMs <= 0) return
        synchronized(this) {
            idleJob?.cancel()
            if (pendingRequests.isEmpty() && isBound) {
                idleJob = resolverScope.launch {
                    delay(idleTimeoutMs)
                    synchronized(this@AndroidProcessMediaResolver) {
                        if (pendingRequests.isEmpty() && isBound) {
                            try {
                                context.unbindService(connection)
                                Log.i(TAG, "Resolver idle timeout reached (${idleTimeoutMs}ms), unbound service to allow :resolver process termination")
                            } catch (e: Exception) {
                                Log.w(TAG, "Error unbinding idle resolver service", e)
                            }
                            service = null
                            isBound = false
                            bindDeferred = CompletableDeferred()
                        }
                    }
                }
            }
        }
    }

    fun unbind() {
        synchronized(this) {
            idleJob?.cancel()
            idleJob = null
            if (isBound) {
                try {
                    context.unbindService(connection)
                } catch (_: Exception) {}
                service = null
                isBound = false
                bindDeferred = CompletableDeferred()
            }
        }
    }

    override suspend fun inspect(request: ResolverRequest): ResolverResult = withContext(Dispatchers.IO) {
        val validationErr = request.validate()
        if (validationErr != null) {
            return@withContext ResolverResult.Failure(validationErr)
        }
        val requestJson = json.encodeToString(ResolverRequest.serializer(), request)
        try {
            executeIpc(request.requestId, request.sourceUrl.orEmpty()) { svc, cb ->
                svc.inspect(requestJson, cb)
            }
        } finally {
            scheduleIdleUnbind()
        }
    }

    override suspend fun resolve(request: ResolverRequest): ResolverResult = withContext(Dispatchers.IO) {
        val validationErr = request.validate()
        if (validationErr != null) {
            return@withContext ResolverResult.Failure(validationErr)
        }
        val cacheKey = "${request.sourceUrl?.trim()}|${request.sourceId?.trim()}|${request.preferredAudioQuality.name}|${request.formatHint}"
        val now = System.currentTimeMillis()
        val cached = resolutionCache[cacheKey]
        if (cached != null && cached.first > now + 30_000L) {
            return@withContext cached.second
        }

        val requestJson = json.encodeToString(ResolverRequest.serializer(), request)
        try {
            val result = executeIpc(request.requestId, request.sourceUrl.orEmpty()) { svc, cb ->
                svc.resolve(requestJson, cb)
            }
            if (result is ResolverResult.Success) {
                val expiry = result.lease?.knownExpiryEpochMs ?: (now + 4 * 3600 * 1000L)
                resolutionCache[cacheKey] = (expiry to result)
            }
            result
        } finally {
            scheduleIdleUnbind()
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
            scheduleIdleUnbind()
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
                jsChallengeSelfTestReady = false,
                realExtractionReady = false,
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
        } finally {
            scheduleIdleUnbind()
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
            try {
                svc.cancelRequest(requestId)
            } catch (_: RemoteException) {}
            return ResolverResult.Failure(ResolverError.timeout(sourceUrl, "IPC call timed out after ${defaultTimeoutMs}ms"))
        }

        if (resultJson.length > MAX_PAYLOAD_SIZE_BYTES) {
            return ResolverResult.Failure(
                ResolverError.payloadTooLarge(
                    source = sourceUrl,
                    details = "Payload size ${resultJson.length} exceeded limit $MAX_PAYLOAD_SIZE_BYTES",
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
