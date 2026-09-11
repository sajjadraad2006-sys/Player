package dev.auralis.desktop.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Centralized resolution of Windows application data paths and atomic file operations.
 *
 * Requirements satisfied:
 * - All mutable user data stored in %LOCALAPPDATA%\Lyrune (or user home fallback).
 * - Complete isolation from program files and repository build directories.
 * - Crash-safe atomic writes via temporary files and atomic replace.
 */
object DesktopPaths {

    val appDataDir: File by lazy {
        val localAppData = System.getenv("LOCALAPPDATA")
        val baseDir = if (!localAppData.isNullOrBlank()) {
            File(localAppData)
        } else {
            val appData = System.getenv("APPDATA")
            if (!appData.isNullOrBlank()) {
                File(appData)
            } else {
                File(System.getProperty("user.home"), ".lyrune")
            }
        }
        File(baseDir, "Lyrune").apply { mkdirs() }
    }

    val databaseFile: File by lazy {
        File(appDataDir, "library.json")
    }

    val settingsFile: File by lazy {
        File(appDataDir, "settings.json")
    }

    val artworkCacheDir: File by lazy {
        File(appDataDir, "artwork_cache").apply { mkdirs() }
    }

    val playlistsFile: File by lazy {
        File(appDataDir, "playlists.json")
    }

    val sessionFile: File by lazy {
        File(appDataDir, "session.json")
    }

    val logsDir: File by lazy {
        File(appDataDir, "logs").apply { mkdirs() }
    }

    val logFile: File by lazy {
        File(logsDir, "lyrune.log")
    }

    val resolverDir: File by lazy {
        File(appDataDir, "resolver").apply { mkdirs() }
    }

    /**
     * Atomically writes string content to a target file.
     * Atomic replacement is used when the filesystem supports it, with a replacement fallback otherwise.
     * Writes to a temporary `.tmp` file first, flushes OS buffers, and performs an atomic replace of the target.
     */
    fun writeAtomically(targetFile: File, content: String) {
        val parent = targetFile.parentFile ?: appDataDir
        parent.mkdirs()
        val tempFile = File(parent, "${targetFile.name}.${System.nanoTime()}.tmp")

        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
                try {
                    fos.fd.sync() // Flush OS buffers to physical disk
                } catch (_: Exception) {
                }
            }

            // Atomic file replace
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                // If filesystem does not support ATOMIC_MOVE across boundaries, fallback to REPLACE_EXISTING
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
