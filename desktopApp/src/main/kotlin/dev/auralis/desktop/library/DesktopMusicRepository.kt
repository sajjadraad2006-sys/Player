package dev.auralis.desktop.library

import dev.auralis.desktop.storage.DesktopDatabase
import dev.auralis.desktop.storage.DesktopPaths
import dev.auralis.studio.online.SearchNormalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class DesktopTrack(
    val id: Long,
    val path: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val mimeType: String = "audio/mpeg",
    val artworkKey: String? = null,
    val isFavorite: Boolean = false,
    val folderPath: String = "",
    val metadataVersion: Int = 0,
) {
    val displayName: String get() = if (artist.isNotBlank()) "$artist - $title" else title
}

enum class TrackSortOption {
    TITLE,
    ARTIST,
    ALBUM,
    DURATION,
    RECENTLY_ADDED,
}

/**
 * High-performance streaming music library repository for Windows Desktop.
 *
 * Production Hardening (Milestone 6):
 * - Backed by crash-safe [DesktopDatabase] in %LOCALAPPDATA%\Lyrune\library.json.
 * - Incremental change reconciliation: deletes missing tracks, rescans modified tracks, reuses unchanged tracks.
 * - Dynamic music folder management (add, remove, refresh) without deleting user files.
 * - Favorites persistence and fast multi-criteria sorting.
 */
class DesktopMusicRepository(
    val database: DesktopDatabase = DesktopDatabase(),
) {
    private val _tracks = MutableStateFlow<List<DesktopTrack>>(emptyList())
    val tracks: StateFlow<List<DesktopTrack>> = _tracks.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val supportedExtensions = dev.auralis.desktop.playback.DesktopAudioCapabilities.extensions

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val cachedTracks = database.getTracks()
        val configuredFolders = database.getFolders()
        val favorites = database.getFavorites()

        val resolvedFolders = if (configuredFolders.isEmpty()) {
            val defaults = getDefaultMusicFolders().map { it.absolutePath }
            database.setFolders(defaults)
            defaults
        } else {
            configuredFolders
        }

        _folders.value = resolvedFolders

        // Populate cached tracks immediately with favorite state mapped
        if (cachedTracks.isNotEmpty()) {
            _tracks.value = cachedTracks.map { track ->
                track.copy(isFavorite = favorites.contains(track.path))
            }
        }
    }

    /**
     * Incrementally scans all configured folders.
     * Reconciles deleted files, modified files, and new files.
     */
    suspend fun scanFolders(targetFolders: List<File>? = null) = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext
        _isScanning.value = true

        try {
            val foldersToScan = targetFolders ?: _folders.value.map { File(it) }
            val existingByPath = _tracks.value.associateBy { it.path }.toMutableMap()
            val favorites = database.getFavorites()
            val discovered = mutableListOf<DesktopTrack>()
            val verifiedPaths = mutableSetOf<String>()
            var nextId = (existingByPath.values.maxOfOrNull { it.id } ?: 0L) + 1L

            for (folder in foldersToScan) {
                if (!folder.exists() || !folder.isDirectory) continue

                folder.walkTopDown()
                    .maxDepth(6)
                    .filter { file ->
                        file.isFile && file.length() > 1024 && file.extension.lowercase() in supportedExtensions
                    }
                    .forEach { file ->
                        val path = file.absolutePath
                        verifiedPaths.add(path)
                        val cached = existingByPath[path]

                        if (cached != null && cached.metadataVersion >= 1 && cached.lastModified == file.lastModified() && cached.sizeBytes == file.length()) {
                            // Re-use cached track with latest favorite state
                            val currentFav = favorites.contains(path)
                            val track = if (cached.isFavorite != currentFav) cached.copy(isFavorite = currentFav) else cached
                            discovered.add(track)
                        } else {
                            // Parse metadata only for new or modified file
                            val track = DesktopTrackMetadata.read(file, nextId++, folder.absolutePath).copy(
                                isFavorite = favorites.contains(path),
                            )
                            discovered.add(track)
                            existingByPath[path] = track
                        }

                        // Incremental UI batch emission every 50 items
                        if (discovered.size % 50 == 0) {
                            _tracks.value = discovered.toList()
                        }
                    }
            }

            // Reconcile deleted files: if a file was previously in a scanned folder but no longer exists on disk
            val unscanned = if(targetFolders != null) existingByPath.values.filter { old -> foldersToScan.none { folder -> File(old.path).toPath().startsWith(folder.toPath()) } } else emptyList()
            val finalTracks = (discovered + unscanned).filter { File(it.path).exists() }.sortedBy { it.title.lowercase() }
            _tracks.value = finalTracks
            database.updateTracks(finalTracks, debounceMs = 0L)
        } catch (_: Throwable) {
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun addFolder(folder: File) = withContext(Dispatchers.IO) {
        if (!folder.exists() || !folder.isDirectory) return@withContext
        val path = folder.absolutePath
        if (_folders.value.contains(path)) return@withContext

        val updated = _folders.value + path
        _folders.value = updated
        database.setFolders(updated)
        scanFolders(listOf(folder))
    }

    suspend fun removeFolder(folderPath: String) = withContext(Dispatchers.IO) {
        val updated = _folders.value.filterNot { it.equals(folderPath, ignoreCase = true) }
        _folders.value = updated
        database.setFolders(updated)

        // Remove tracks belonging to that folder from library index (never deletes physical files)
        val remainingTracks = _tracks.value.filterNot {
            it.folderPath.equals(folderPath, ignoreCase = true) || it.path.startsWith(folderPath, ignoreCase = true)
        }
        _tracks.value = remainingTracks
        database.updateTracks(remainingTracks, debounceMs = 0L)
    }

    suspend fun refreshFolder(folderPath: String) = withContext(Dispatchers.IO) {
        val folder = File(folderPath)
        if (folder.exists() && folder.isDirectory) {
            scanFolders(listOf(folder))
        }
    }

    suspend fun toggleFavorite(track: DesktopTrack): Boolean = withContext(Dispatchers.IO) {
        val isFav = database.toggleFavorite(track.path)
        _tracks.value = _tracks.value.map {
            if (it.path == track.path) it.copy(isFavorite = isFav) else it
        }
        isFav
    }

    fun search(query: String): List<DesktopTrack> {
        val q = query.trim()
        if (q.isBlank()) return _tracks.value
        val normQuery = SearchNormalization.normalize(q)
        return _tracks.value.filter { track ->
            val matchTitle = SearchNormalization.matchRatio(track.title, normQuery) > 0.4f ||
                track.title.contains(q, ignoreCase = true)
            val matchArtist = SearchNormalization.matchRatio(track.artist, normQuery) > 0.4f ||
                track.artist.contains(q, ignoreCase = true)
            val matchAlbum = track.album.contains(q, ignoreCase = true)
            matchTitle || matchArtist || matchAlbum
        }
    }

    fun getSortedTracks(sortOption: TrackSortOption, ascending: Boolean = true): List<DesktopTrack> {
        val current = _tracks.value
        val comparator = when (sortOption) {
            TrackSortOption.TITLE -> compareBy<DesktopTrack> { it.title.lowercase() }
            TrackSortOption.ARTIST -> compareBy<DesktopTrack> { it.artist.lowercase() }
            TrackSortOption.ALBUM -> compareBy<DesktopTrack> { it.album.lowercase() }
            TrackSortOption.DURATION -> compareBy<DesktopTrack> { it.durationMs }
            TrackSortOption.RECENTLY_ADDED -> compareByDescending<DesktopTrack> { it.lastModified }
        }
        return if (ascending) current.sortedWith(comparator) else current.sortedWith(comparator.reversed())
    }

    fun getDefaultMusicFolders(): List<File> {
        val list = mutableListOf<File>()
        val userHome = System.getProperty("user.home")
        val winMusic = File(userHome, "Music")
        if (winMusic.exists() && winMusic.isDirectory) list.add(winMusic)

        val downloads = File(userHome, "Downloads")
        if (downloads.exists() && downloads.isDirectory) list.add(downloads)
        return list
    }

}
