package dev.auralis.studio.online

import dev.auralis.studio.library.MediaStoreMusicRepository
import dev.auralis.studio.library.SearchEngine

/**
 * Local library discovery adapter.
 *
 * Integrates on-device tracks into canonical search without creating fake network/OnlineTrack objects.
 */
class LocalLibraryDiscovery(
    private val repository: MediaStoreMusicRepository,
) {
    /**
     * Searches local library tracks against [query] and returns normalized [LocalRecordingSource] instances.
     */
    fun search(query: String): List<LocalRecordingSource> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val allTracks = repository.tracks.value
        val matched = SearchEngine.search(allTracks, trimmed)
        return matched.map { it.toLocalRecordingSource() }
    }
}
