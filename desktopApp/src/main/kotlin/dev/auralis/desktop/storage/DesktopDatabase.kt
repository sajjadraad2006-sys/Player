package dev.auralis.desktop.storage

import dev.auralis.desktop.library.DesktopTrack
import dev.auralis.desktop.logging.DesktopLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

typealias PersistentLibraryStore = DesktopDatabase
typealias JsonLibraryStore = DesktopDatabase

@Serializable
data class DesktopPlaylist(
    val id: Long,
    val name: String,
    val description: String = "",
    val trackPaths: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class DatabaseSchema(
    val schemaVersion: Int = 1,
    val version: Int = 1,
    val tracks: List<DesktopTrack> = emptyList(),
    val folders: List<String> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val playlists: List<DesktopPlaylist> = emptyList(),
    val lastPlayedTrackPath: String? = null,
    val lastPlaybackPositionMs: Long = 0L,
    val lastIndexedTimestamp: Long = 0L,
)

@Serializable
data class DesktopPlaybackSession(
    val schemaVersion: Int = 1,
    val lastPlayedTrackPath: String? = null,
    val lastPlaybackPositionMs: Long = 0L,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

/**
 * Thread-safe, crash-resilient JSON persistent library store for Lyrune Desktop.
 *
 * Requirements satisfied:
 * - Persists tracks, folders, favorites, and playlists to %LOCALAPPDATA%\Lyrune\library.json.
 * - Decouples high-frequency playback position ticks into a lightweight %LOCALAPPDATA%\Lyrune\session.json
 *   to eliminate SSD write amplification during continuous playback.
 * - Atomic replacement is used when the filesystem supports it, with a replacement fallback otherwise.
 * - Corrupt recovery: creates timestamped .bak copy and recovers safe defaults on invalid JSON without crashing.
 * - Instrumentable write counters for SSD write amplification auditing.
 */
class DesktopDatabase(
    private val dbFile: File = DesktopPaths.databaseFile,
    private val sessionFile: File = if(dbFile == DesktopPaths.databaseFile) DesktopPaths.sessionFile else File(dbFile.parentFile, "${dbFile.nameWithoutExtension}.session.json"),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private var data: DatabaseSchema = DatabaseSchema()
    private var sessionData: DesktopPlaybackSession = DesktopPlaybackSession()
    private var debounceJob: Job? = null
    private var sessionDebounceJob: Job? = null

    val libraryWriteCount = AtomicInteger(0)
    val sessionWriteCount = AtomicInteger(0)

    init {
        loadSync()
        loadSessionSync()
    }

    private fun loadSync() {
        if (!dbFile.exists() || dbFile.length() == 0L) {
            data = DatabaseSchema()
            return
        }

        try {
            val content = dbFile.readText(Charsets.UTF_8)
            data = json.decodeFromString<DatabaseSchema>(content)
        } catch (e: Exception) {
            DesktopLogger.warn("PersistentLibraryStore", "Corrupted library file detected: ${dbFile.name}", e)
            backupCorruptedFile(dbFile)

            // If primary file is corrupted, check for .tmp backup
            val tmp = File(dbFile.parentFile, "${dbFile.name}.tmp")
            if (tmp.exists() && tmp.length() > 0L) {
                try {
                    data = json.decodeFromString<DatabaseSchema>(tmp.readText(Charsets.UTF_8))
                } catch (_: Exception) {
                    data = DatabaseSchema()
                }
            } else {
                data = DatabaseSchema()
            }
        }
    }

    private fun loadSessionSync() {
        if (!sessionFile.exists() || sessionFile.length() == 0L) {
            // Fallback to legacy state embedded in data
            sessionData = DesktopPlaybackSession(
                lastPlayedTrackPath = data.lastPlayedTrackPath,
                lastPlaybackPositionMs = data.lastPlaybackPositionMs,
            )
            return
        }

        try {
            val content = sessionFile.readText(Charsets.UTF_8)
            sessionData = json.decodeFromString<DesktopPlaybackSession>(content)
        } catch (e: Exception) {
            DesktopLogger.warn("PersistentLibraryStore", "Corrupted session file detected: ${sessionFile.name}", e)
            backupCorruptedFile(sessionFile)
            sessionData = DesktopPlaybackSession(
                lastPlayedTrackPath = data.lastPlayedTrackPath,
                lastPlaybackPositionMs = data.lastPlaybackPositionMs,
            )
        }
    }

    private fun backupCorruptedFile(file: File) {
        try {
            if (file.exists() && file.length() > 0L) {
                val backup = File(file.parentFile, "${file.name}.corrupt.${System.currentTimeMillis()}.bak")
                file.copyTo(backup, overwrite = true)
                DesktopLogger.info("PersistentLibraryStore", "Preserved corrupted file as ${backup.name}")
            }
        } catch (_: Exception) {
        }
    }

    suspend fun getTracks(): List<DesktopTrack> = mutex.withLock {
        data.tracks
    }

    suspend fun getFolders(): List<String> = mutex.withLock {
        data.folders
    }

    suspend fun getFavorites(): Set<String> = mutex.withLock {
        data.favorites
    }

    suspend fun getPlaylists(): List<DesktopPlaylist> = mutex.withLock {
        data.playlists
    }

    suspend fun getLastPlaybackState(): Pair<String?, Long> = mutex.withLock {
        sessionData.lastPlayedTrackPath to sessionData.lastPlaybackPositionMs
    }

    suspend fun updateTracks(tracks: List<DesktopTrack>, debounceMs: Long = 300L) = mutex.withLock {
        data = data.copy(tracks = tracks, lastIndexedTimestamp = System.currentTimeMillis())
        scheduleSaveLibrary(debounceMs)
    }

    suspend fun setFolders(folders: List<String>) = mutex.withLock {
        data = data.copy(folders = folders)
        scheduleSaveLibrary(0L) // Immediate write for folder configuration
    }

    suspend fun toggleFavorite(trackPath: String): Boolean = mutex.withLock {
        val newFavorites = data.favorites.toMutableSet()
        val isNowFav = if (newFavorites.contains(trackPath)) {
            newFavorites.remove(trackPath)
            false
        } else {
            newFavorites.add(trackPath)
            true
        }
        val updatedTracks = data.tracks.map { track ->
            if (track.path == trackPath) track.copy(isFavorite = isNowFav) else track
        }
        data = data.copy(favorites = newFavorites, tracks = updatedTracks)
        scheduleSaveLibrary(0L)
        isNowFav
    }

    /**
     * Updates playback position and track path.
     * Persisted strictly to [sessionFile] to avoid rewriting the full library catalog on position ticks.
     */
    suspend fun updatePlaybackPosition(trackPath: String?, positionMs: Long, debounceMs: Long = 2000L) = mutex.withLock {
        sessionData = sessionData.copy(
            lastPlayedTrackPath = trackPath,
            lastPlaybackPositionMs = positionMs,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        scheduleSaveSession(debounceMs)
    }

    suspend fun savePlaylist(playlist: DesktopPlaylist) = mutex.withLock {
        val current = data.playlists.filterNot { it.id == playlist.id }
        data = data.copy(playlists = current + playlist)
        scheduleSaveLibrary(0L)
    }

    suspend fun deletePlaylist(playlistId: Long) = mutex.withLock {
        data = data.copy(playlists = data.playlists.filterNot { it.id == playlistId })
        scheduleSaveLibrary(0L)
    }

    private var isLibraryDirty = false
    private var isSessionDirty = false

    private fun scheduleSaveLibrary(debounceMs: Long) {
        isLibraryDirty = true
        debounceJob?.cancel()
        if (debounceMs <= 0L) {
            commitLibraryToDisk()
        } else {
            debounceJob = scope.launch {
                delay(debounceMs)
                mutex.withLock {
                    commitLibraryToDisk()
                }
            }
        }
    }

    private fun commitLibraryToDisk() {
        try {
            val serialized = json.encodeToString(data)
            DesktopPaths.writeAtomically(dbFile, serialized)
            libraryWriteCount.incrementAndGet()
            isLibraryDirty = false
        } catch (e: Exception) {
            DesktopLogger.error("PersistentLibraryStore", "Failed to commit library to disk", e)
        }
    }

    private fun scheduleSaveSession(debounceMs: Long) {
        isSessionDirty = true
        if (debounceMs <= 0L) {
            sessionDebounceJob?.cancel()
            commitSessionToDisk()
        } else if (sessionDebounceJob?.isActive != true) {
            // Checkpoint continuously playing audio; restarting a debounce on every
            // position tick would never save until playback stopped.
            sessionDebounceJob = scope.launch {
                delay(debounceMs)
                mutex.withLock {
                    commitSessionToDisk()
                }
            }
        }
    }

    private fun commitSessionToDisk() {
        try {
            val serialized = json.encodeToString(sessionData)
            DesktopPaths.writeAtomically(sessionFile, serialized)
            sessionWriteCount.incrementAndGet()
            isSessionDirty = false
        } catch (e: Exception) {
            DesktopLogger.error("PersistentLibraryStore", "Failed to commit session to disk", e)
        }
    }

    suspend fun flushNow() = mutex.withLock {
        debounceJob?.cancel()
        sessionDebounceJob?.cancel()
        if (isLibraryDirty) commitLibraryToDisk()
        if (isSessionDirty) commitSessionToDisk()
    }
}

