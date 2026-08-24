package dev.auralis.studio.audio.stems

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Installs an explicitly requested model into private storage and verifies its immutable contract. */
class StemModelManager(private val modelDirectory: File) {
    fun modelFile(descriptor: StemEngineDescriptor): File =
        File(modelDirectory, "${descriptor.engineId}-${descriptor.modelId}-${descriptor.modelVersion}.tflite")

    suspend fun availability(descriptor: StemEngineDescriptor): StemModelAvailability = withContext(Dispatchers.IO) {
        val file = modelFile(descriptor)
        when {
            !file.exists() -> StemModelAvailability.DownloadRequired(descriptor.modelBytes)
            file.length() != descriptor.modelBytes -> StemModelAvailability.Invalid("size_mismatch")
            sha256(file) != descriptor.modelSha256.lowercase() -> StemModelAvailability.Invalid("checksum_mismatch")
            else -> StemModelAvailability.Installed(file)
        }
    }

    suspend fun install(
        descriptor: StemEngineDescriptor,
        input: InputStream,
        onProgress: (Int) -> Unit,
    ): StemModelAvailability = withContext(Dispatchers.IO) {
        modelDirectory.mkdirs()
        val destination = modelFile(descriptor)
        val partial = File(destination.parentFile, "${destination.name}.partial")
        runCatching {
            input.use { source ->
                partial.outputStream().buffered().use { sink ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                        copied += count
                        onProgress(((copied * 100L) / descriptor.modelBytes.coerceAtLeast(1L)).toInt().coerceIn(0, 99))
                    }
                }
            }
            if (partial.length() != descriptor.modelBytes || sha256(partial) != descriptor.modelSha256.lowercase()) {
                partial.delete()
                return@withContext StemModelAvailability.Invalid("checksum_or_size_mismatch")
            }
            if (destination.exists()) destination.delete()
            if (!partial.renameTo(destination)) {
                partial.delete()
                return@withContext StemModelAvailability.Invalid("atomic_install_failed")
            }
            onProgress(100)
            StemModelAvailability.Installed(destination)
        }.getOrElse {
            partial.delete()
            if (it is kotlinx.coroutines.CancellationException) throw it
            StemModelAvailability.Invalid(it.message ?: "install_failed")
        }
    }

    fun delete(descriptor: StemEngineDescriptor): Boolean = modelFile(descriptor).let { !it.exists() || it.delete() }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
