package dev.auralis.studio.library

import dev.auralis.studio.core.model.Track
import dev.auralis.studio.online.SearchNormalization
import dev.auralis.studio.online.UnifiedSearchAggregator
import dev.auralis.studio.online.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Intelligent recommendation engine providing offline library discovery
 * and online radio matching based on artist affinity, mood/vibe keywords,
 * acoustic profile similarity, and listening history co-occurrence.
 */
class SmartRecommendationEngine(
    private val unifiedSearch: UnifiedSearchAggregator? = null,
) {
    private val onlineCache = ConcurrentHashMap<String, List<UnifiedTrack>>()

    /**
     * Recommends tracks from the local on-device library matching the current track.
     */
    suspend fun getOfflineRecommendations(
        currentTrack: Track?,
        allTracks: List<Track>,
        historyUris: List<String> = emptyList(),
        favorites: Set<String> = emptySet(),
        limit: Int = 15,
    ): List<Track> = withContext(Dispatchers.Default) {
        if (allTracks.isEmpty()) return@withContext emptyList()
        if (currentTrack == null) {
            // Cold start: prioritize favorites, recently played, then shuffled sample
            val favList = allTracks.filter { it.uri in favorites }
            if (favList.isNotEmpty()) return@withContext favList.shuffled().take(limit)
            val historyList = historyUris.mapNotNull { uri -> allTracks.firstOrNull { it.uri == uri } }
            if (historyList.isNotEmpty()) return@withContext historyList.take(limit)
            return@withContext allTracks.shuffled().take(limit)
        }

        val currentUri = currentTrack.uri
        val normCurrentArtist = SearchNormalization.normalize(currentTrack.artist)
        val normCurrentAlbum = SearchNormalization.normalize(currentTrack.album)
        val currentKeywords = extractVibeKeywords(currentTrack.title)
        val historyIndexMap = historyUris.mapIndexed { index, uri -> uri to index }.toMap()

        val otherTracks = allTracks.filter { it.uri != currentUri }
        if (otherTracks.isEmpty()) return@withContext emptyList()

        val scoredTracks = otherTracks.map { track ->
            var score = 0f
            val normTrackArtist = SearchNormalization.normalize(track.artist)
            val normTrackAlbum = SearchNormalization.normalize(track.album)

            // 1. Artist Affinity (Highest weight, normalized for Arabic & Latin)
            if (normCurrentArtist.isNotBlank() && normTrackArtist.isNotBlank() &&
                normCurrentArtist != "unknown" && !normCurrentArtist.contains("unknown")
            ) {
                if (normTrackArtist == normCurrentArtist) {
                    score += 50f
                } else if (normTrackArtist.contains(normCurrentArtist) || normCurrentArtist.contains(normTrackArtist)) {
                    score += 30f
                }
            }

            // 2. Album / Project Cohesion
            if (normCurrentAlbum.isNotBlank() && normTrackAlbum == normCurrentAlbum &&
                normCurrentAlbum != "unknown" && !normCurrentAlbum.contains("unknown")
            ) {
                score += 20f
            }

            // 3. Vibe / Keyword Acoustic Match ("remix", "acoustic", "live", "ريمكس", etc.)
            val trackKeywords = extractVibeKeywords(track.title)
            val sharedKeywords = currentKeywords.intersect(trackKeywords)
            score += sharedKeywords.size * 15f

            // 4. Duration similarity (+/- 35 seconds matches similar genre/track structure)
            if (currentTrack.durationMs > 0 && track.durationMs > 0) {
                val durationDiffSec = abs(currentTrack.durationMs - track.durationMs) / 1000
                if (durationDiffSec < 35) {
                    score += 8f
                }
            }

            // 5. Listening History Affinity (tracks listened to in close temporal sessions)
            val historyIdx = historyIndexMap[track.uri]
            val currentHistoryIdx = historyIndexMap[currentUri]
            if (historyIdx != null && currentHistoryIdx != null) {
                val distance = abs(historyIdx - currentHistoryIdx)
                if (distance in 1..4) {
                    score += 16f
                } else if (distance in 5..10) {
                    score += 8f
                }
            }

            // 6. User Favorite bonus
            if (track.uri in favorites) {
                score += 6f
            }

            track to score
        }

        val matching = scoredTracks.filter { it.second > 0f }
            .sortedByDescending { it.second }
            .map { it.first }

        if (matching.size >= limit) {
            matching.take(limit)
        } else {
            // Intelligent fallback blending: matching tracks first, then fill remainder with
            // listening history / favorites / diverse randomized tracks instead of static array head
            val matchedSet = matching.map { it.uri }.toSet()
            val remainingNeeded = limit - matching.size

            val pool = otherTracks.filter { it.uri !in matchedSet }
            val prioritizedPool = pool.sortedWith(
                compareByDescending<Track> { it.uri in favorites }
                    .thenByDescending { historyIndexMap.containsKey(it.uri) }
            )

            val filler = prioritizedPool.take(remainingNeeded * 3).shuffled().take(remainingNeeded)
            matching + filler
        }
    }

    /**
     * Recommends online tracks from YouTube and connected providers matching
     * the current artist or vibe.
     */
    suspend fun getOnlineRecommendations(
        currentTrack: Track?,
        limit: Int = 12,
    ): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        if (currentTrack == null || unifiedSearch == null) return@withContext emptyList()
        val cacheKey = currentTrack.uri.ifBlank { "${currentTrack.artist}#${currentTrack.title}" }

        onlineCache[cacheKey]?.let { cached ->
            if (cached.isNotEmpty()) return@withContext cached.take(limit)
        }

        val rawArtist = currentTrack.artist.trim()
        val rawTitle = currentTrack.title.trim()

        val cleanTitle = SearchEngine.normalize(rawTitle)
        var detectedArtist = rawArtist.takeUnless {
            it.equals("unknown", ignoreCase = true) ||
            it.equals("<unknown>", ignoreCase = true) ||
            it.equals("unknown artist", ignoreCase = true)
        }

        // If artist is blank or unknown but title contains "Artist - Title", extract them
        val coreTitle = if (detectedArtist.isNullOrBlank() && rawTitle.contains(" - ")) {
            val parts = rawTitle.split(" - ", limit = 2)
            detectedArtist = parts[0].trim()
            SearchEngine.normalize(parts[1])
        } else {
            cleanTitle
        }

        // Generate recommendations query: search for artist's top tracks or related music
        val searchQuery = when {
            !detectedArtist.isNullOrBlank() -> "$detectedArtist songs"
            coreTitle.isNotBlank() -> "$coreTitle mix"
            else -> return@withContext emptyList()
        }

        return@withContext try {
            val snapshot = withTimeoutOrNull(4_500L) {
                unifiedSearch.search(searchQuery).firstOrNull { it.isComplete || it.recordings.size >= limit }
            } ?: unifiedSearch.search(searchQuery).firstOrNull()

            val normCurrentTitle = SearchNormalization.normalize(rawTitle)
            val normCoreTitle = SearchNormalization.normalize(coreTitle)

            val filtered = snapshot?.recordings.orEmpty()
                .filter { onlineTrack ->
                    val normOnlineTitle = SearchNormalization.normalize(onlineTrack.title)
                    normOnlineTitle.isNotBlank() &&
                        normOnlineTitle != normCurrentTitle &&
                        (normCoreTitle.isBlank() || normOnlineTitle != normCoreTitle)
                }
                .distinctBy { it.uiKey }
                .take(limit)

            if (filtered.isNotEmpty()) {
                onlineCache[cacheKey] = filtered
            }
            filtered
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractVibeKeywords(title: String): Set<String> {
        val normalized = SearchNormalization.normalize(title)
        val keywords = mutableSetOf<String>()
        val vibeTags = listOf(
            "remix", "acoustic", "live", "slowed", "reverb", "sped up", "instrumental",
            "lo-fi", "lofi", "chill", "ambient", "club", "mix", "extended", "cover", "edit",
            "ريمكس", "مباشر", "حفلة", "جلسة", "عود", "طرب", "مكس", "روقان", "هادئ", "مسرع", "مبطأ"
        )
        for (tag in vibeTags) {
            if (normalized.contains(tag)) keywords.add(tag)
        }
        return keywords
    }
}
