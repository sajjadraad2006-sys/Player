package dev.auralis.studio.audio.stems

import java.io.File

enum class StemKind { VOCALS, INSTRUMENTAL, BASS, DRUMS, OTHER }

data class StemEngineDescriptor(
    val engineId: String,
    val modelId: String,
    val modelVersion: String,
    val supportedStems: Set<StemKind>,
    val modelBytes: Long,
    val modelSha256: String,
    val license: String,
)

sealed interface StemModelAvailability {
    data object NotOffered : StemModelAvailability
    data class DownloadRequired(val bytes: Long) : StemModelAvailability
    data class Installed(val file: File) : StemModelAvailability
    data class Invalid(val reason: String) : StemModelAvailability
}

data class StemSeparationRequest(
    val sourceIdentity: String,
    val sourceFile: File,
    val outputDirectory: File,
)

data class StemSeparationResult(
    val cacheKey: String,
    val files: Map<StemKind, File>,
    val sampleRateHz: Int,
    val durationFrames: Long,
    val metadata: Map<String, String> = emptyMap(),
)

/** Neural inference belongs in a worker implementation, never a Media3 AudioProcessor callback. */
interface StemSeparationEngine {
    val descriptor: StemEngineDescriptor
    suspend fun availability(): StemModelAvailability
    suspend fun separate(request: StemSeparationRequest, onProgress: (Int) -> Unit): StemSeparationResult
    fun cancel()
}

object StemCacheIdentity {
    fun create(sourceIdentity: String, descriptor: StemEngineDescriptor, parameters: String = "default"): String =
        listOf(sourceIdentity, descriptor.engineId, descriptor.modelId, descriptor.modelVersion, parameters)
            .joinToString("|")
            .encodeToByteArray()
            .let(::sha256)

    private fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

object StemMixerMath {
    /** Equal-gain controls with deterministic headroom; final soft limiting prevents overs. */
    fun mix(samples: FloatArray, gains: FloatArray): Float {
        require(samples.size == gains.size)
        if (samples.isEmpty()) return 0f
        var sum = 0f
        var gainSum = 0f
        for (index in samples.indices) {
            val gain = gains[index].coerceIn(0f, 1f)
            sum += samples[index] * gain
            gainSum += gain
        }
        val headroom = maxOf(1f, gainSum * 0.82f)
        val normalized = sum / headroom
        return (normalized / (1f + kotlin.math.abs(normalized))).coerceIn(-1f, 1f)
    }
}
