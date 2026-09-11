package dev.auralis.desktop.playback.flac

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import dev.auralis.desktop.storage.DesktopPaths
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * JNA binding interface for libFLAC (official Xiph reference implementation).
 */
interface FlacNativeLibrary : Library {

    fun FLAC__stream_decoder_new(): Pointer?
    fun FLAC__stream_decoder_delete(decoder: Pointer?)
    fun FLAC__stream_decoder_finish(decoder: Pointer?): Int
    fun FLAC__stream_decoder_reset(decoder: Pointer?): Int
    fun FLAC__stream_decoder_flush(decoder: Pointer?): Int

    fun FLAC__stream_decoder_init_file(
        decoder: Pointer?,
        filename: String,
        writeCallback: WriteCallback?,
        metadataCallback: MetadataCallback?,
        errorCallback: ErrorCallback?,
        clientData: Pointer?
    ): Int

    fun FLAC__stream_decoder_process_single(decoder: Pointer?): Int
    fun FLAC__stream_decoder_process_until_end_of_metadata(decoder: Pointer?): Int
    fun FLAC__stream_decoder_process_until_end_of_stream(decoder: Pointer?): Int
    fun FLAC__stream_decoder_seek_absolute(decoder: Pointer?, sample: Long): Int

    fun FLAC__stream_decoder_get_state(decoder: Pointer?): Int
    fun FLAC__stream_decoder_get_channels(decoder: Pointer?): Int
    fun FLAC__stream_decoder_get_bits_per_sample(decoder: Pointer?): Int
    fun FLAC__stream_decoder_get_sample_rate(decoder: Pointer?): Int
    fun FLAC__stream_decoder_get_blocksize(decoder: Pointer?): Int
    fun FLAC__stream_decoder_get_total_samples(decoder: Pointer?): Long

    fun interface WriteCallback : Callback {
        fun invoke(decoder: Pointer?, frame: Pointer?, buffer: Pointer?, clientData: Pointer?): Int
    }

    fun interface MetadataCallback : Callback {
        fun invoke(decoder: Pointer?, metadata: Pointer?, clientData: Pointer?)
    }

    fun interface ErrorCallback : Callback {
        fun invoke(decoder: Pointer?, status: Int, clientData: Pointer?)
    }

    companion object {
        const val FLAC__STREAM_DECODER_INIT_STATUS_OK = 0

        const val FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE = 0
        const val FLAC__STREAM_DECODER_WRITE_STATUS_ABORT = 1

        const val FLAC__STREAM_DECODER_SEARCH_FOR_METADATA = 0
        const val FLAC__STREAM_DECODER_READ_METADATA = 1
        const val FLAC__STREAM_DECODER_SEARCH_FOR_FRAME = 2
        const val FLAC__STREAM_DECODER_READ_FRAME = 3
        const val FLAC__STREAM_DECODER_END_OF_STREAM = 4
        const val FLAC__STREAM_DECODER_OGG_ERROR = 5
        const val FLAC__STREAM_DECODER_SEEK_ERROR = 6
        const val FLAC__STREAM_DECODER_ABORTED = 7
        const val FLAC__STREAM_DECODER_MEMORY_ALLOCATION_ERROR = 8
        const val FLAC__STREAM_DECODER_UNINITIALIZED = 9

        const val LIBFLAC_VERSION = "1.5.0"
        const val EXPECTED_SHA256 = "f93499172875fc2c0df80b57086f32e3f39e835283952ee2a59a3d4ffb097644"

        private const val DLL_NAME = "libFLAC.dll"
        private const val RESOURCE_PATH = "win32-x86-64/$DLL_NAME"

        @Volatile
        private var cachedInstance: FlacNativeLibrary? = null
        private val lock = Any()

        /**
         * Resolves and safely loads [FlacNativeLibrary] using an application-controlled,
         * anti-DLL-hijacking path with SHA-256 cryptographic integrity verification.
         */
        fun loadOrNull(): FlacNativeLibrary? {
            val os = System.getProperty("os.name", "").lowercase()
            if (!os.contains("win")) return null

            cachedInstance?.let { return it }

            synchronized(lock) {
                cachedInstance?.let { return it }

                return try {
                    val targetFile = resolveOrExtractDll()
                    val lib = Native.load(targetFile.absolutePath, FlacNativeLibrary::class.java)
                    cachedInstance = lib
                    lib
                } catch (_: Throwable) {
                    null
                }
            }
        }

        fun computeSha256(file: File): String {
            if (!file.isFile) return ""
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { fis ->
                val buf = ByteArray(16384)
                while (true) {
                    val read = fis.read(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        internal fun resolveOrExtractDll(customTargetDir: File? = null): File {
            val destinationDir = customTargetDir ?: File(DesktopPaths.appDataDir, "bin/win32-x86-64").apply { mkdirs() }
            val destinationFile = File(destinationDir, DLL_NAME)

            // If the extracted DLL exists and matches trusted SHA-256, reuse it
            if (destinationFile.isFile && computeSha256(destinationFile).equals(EXPECTED_SHA256, ignoreCase = true)) {
                return destinationFile
            }

            if (customTargetDir == null) {
                // Check relative to current application executable directory (e.g. packaged portable app)
                val appDir = File(System.getProperty("user.dir", "."))
                val appBinDll = File(appDir, "bin/$DLL_NAME")
                if (appBinDll.isFile && computeSha256(appBinDll).equals(EXPECTED_SHA256, ignoreCase = true)) {
                    return appBinDll
                }

                val appResourcesDll = File(appDir, "app/resources/$RESOURCE_PATH")
                if (appResourcesDll.isFile && computeSha256(appResourcesDll).equals(EXPECTED_SHA256, ignoreCase = true)) {
                    return appResourcesDll
                }
            }

            // Extract from trusted packaged JAR resource
            val classLoader = FlacNativeLibrary::class.java.classLoader
            val resourceStream: InputStream? = classLoader?.getResourceAsStream(RESOURCE_PATH)
                ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(RESOURCE_PATH)

            if (resourceStream != null) {
                val tempFile = File(destinationDir, "$DLL_NAME.${System.nanoTime()}.tmp")
                try {
                    resourceStream.use { input ->
                        Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    val extractedHash = computeSha256(tempFile)
                    if (!extractedHash.equals(EXPECTED_SHA256, ignoreCase = true)) {
                        tempFile.delete()
                        throw SecurityException("Bundled $DLL_NAME failed integrity check: expected $EXPECTED_SHA256 but got $extractedHash")
                    }
                    Files.move(tempFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } finally {
                    if (tempFile.exists()) tempFile.delete()
                }
            }

            if (destinationFile.isFile && computeSha256(destinationFile).equals(EXPECTED_SHA256, ignoreCase = true)) {
                return destinationFile
            }

            throw SecurityException("libFLAC.dll could not be verified or extracted with valid integrity hash.")
        }
    }
}
