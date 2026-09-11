package dev.auralis.desktop.windows.smtc

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString
import dev.auralis.desktop.storage.DesktopPaths
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * JNA binding interface for SMTCAdapter.dll (C++/WinRT bridge).
 */
interface NativeSmtcBridge : Library {

    fun init()
    fun revokeCallbacks()
    fun update()
    fun reset()

    fun setEnabled(enabled: Boolean)
    fun getEnabled(): Boolean

    fun setPlayEnabled(enabled: Boolean)
    fun setPauseEnabled(enabled: Boolean)
    fun setStopEnabled(enabled: Boolean)
    fun setNextEnabled(enabled: Boolean)
    fun setPreviousEnabled(enabled: Boolean)

    fun setPlaybackState(state: Int)
    fun getPlaybackState(): Int

    fun setTimelineProperties(start: Long, end: Long, seekStart: Long, seekEnd: Long)
    fun setPosition(position: Long)

    fun setMediaType(type: Int)
    fun getMediaType(): Int

    fun setThumbnail(thumbnailPath: WString?)
    fun thumbnailLoaded(): Boolean

    fun setMusicTitle(title: WString?)
    fun setMusicArtist(artist: WString?)
    fun setMusicAlbumTitle(albumTitle: WString?)
    fun setMusicAlbumArtist(albumArtist: WString?)
    fun setMusicTrack(track: Int)
    fun setMusicAlbumTrackCount(count: Int)

    fun setOnPlay(callback: ButtonCallback?)
    fun setOnPause(callback: ButtonCallback?)
    fun setOnStop(callback: ButtonCallback?)
    fun setOnNext(callback: ButtonCallback?)
    fun setOnPrevious(callback: ButtonCallback?)
    fun setOnSeek(callback: SeekCallback?)

    fun interface ButtonCallback : Callback {
        fun invoke()
    }

    fun interface SeekCallback : Callback {
        fun invoke(positionMs: Long)
    }

    companion object {
        private const val DLL_NAME = "SMTCAdapter.dll"
        private const val RESOURCE_PATH = "win32-x86-64/$DLL_NAME"

        const val EXPECTED_SHA256 = "c7180c3ee16b271bea997e7f77e0a80514c289d178bc24d894de40f0ac297c9e"

        /**
         * Resolves and safely loads [NativeSmtcBridge] using an absolute,
         * application-controlled path to prevent DLL hijacking with SHA-256 integrity verification.
         */
        fun loadOrNull(): NativeSmtcBridge? {
            val os = System.getProperty("os.name", "").lowercase()
            if (!os.contains("win")) {
                return null
            }

            return try {
                val targetFile = resolveOrExtractDll()
                Native.load(targetFile.absolutePath, NativeSmtcBridge::class.java)
            } catch (t: Throwable) {
                null
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

        internal fun resolveOrExtractDll(): File {
            val destinationDir = File(DesktopPaths.appDataDir, "bin/win32-x86-64").apply { mkdirs() }
            val destinationFile = File(destinationDir, DLL_NAME)

            // If the extracted DLL exists and matches trusted SHA-256, reuse it
            if (destinationFile.isFile && computeSha256(destinationFile).equals(EXPECTED_SHA256, ignoreCase = true)) {
                return destinationFile
            }

            // Check relative to current application executable directory
            val appDir = File(System.getProperty("user.dir", "."))
            val appBinDll = File(appDir, "bin/$DLL_NAME")
            if (appBinDll.isFile && computeSha256(appBinDll).equals(EXPECTED_SHA256, ignoreCase = true)) {
                return appBinDll
            }

            val appResourcesDll = File(appDir, "app/resources/$RESOURCE_PATH")
            if (appResourcesDll.isFile && computeSha256(appResourcesDll).equals(EXPECTED_SHA256, ignoreCase = true)) {
                return appResourcesDll
            }

            // Extract from trusted packaged JAR resource
            val classLoader = NativeSmtcBridge::class.java.classLoader
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

            throw SecurityException("SMTCAdapter.dll could not be verified or extracted with valid integrity hash.")
        }
    }
}
