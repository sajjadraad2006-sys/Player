package dev.auralis.studio.library

import dev.auralis.studio.core.model.Track
import dev.auralis.studio.online.ProviderResult
import dev.auralis.studio.online.SearchNormalization
import dev.auralis.studio.online.UnifiedSearchAggregator
import dev.auralis.studio.online.UnifiedTrack
import dev.auralis.studio.online.UnifiedTrackMerger
import dev.auralis.studio.online.YouTubeCatalogProvider
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
    private val networkMonitor: dev.auralis.studio.core.NetworkMonitor? = null,
    private val youtubeCatalog: YouTubeCatalogProvider? = null,
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
        limit: Int = 35,
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
     * Recommends online tracks using YouTube's native algorithmic "Up Next" / "Radio"
     * recommendation engine (/youtubei/v1/next), falling back to multi-query search if needed.
     */
    suspend fun getOnlineRecommendations(
        currentTrack: Track?,
        limit: Int = 30,
    ): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        if (currentTrack == null) return@withContext emptyList()
        if (networkMonitor?.currentOnline == false) return@withContext emptyList()
        val cacheKey = currentTrack.uri.ifBlank { "${currentTrack.artist}#${currentTrack.title}" }

        onlineCache[cacheKey]?.let { cached ->
            if (cached.isNotEmpty()) return@withContext cached.take(limit)
        }

        // 1. Try True YouTube Algorithmic Recommendations via InnerTube /next
        if (youtubeCatalog != null) {
            val algorithmic = fetchAlgorithmicRecommendations(currentTrack, limit)
            if (algorithmic.isNotEmpty()) {
                onlineCache[cacheKey] = algorithmic
                return@withContext algorithmic
            }
        }

        // 2. Fallback to heuristic query search if InnerTube /next fails or is unavailable
        val fallback = fetchSearchBasedRecommendations(currentTrack, limit)
        if (fallback.isNotEmpty()) {
            onlineCache[cacheKey] = fallback
        }
        fallback
    }

    private suspend fun fetchAlgorithmicRecommendations(
        currentTrack: Track,
        limit: Int,
    ): List<UnifiedTrack> {
        val catalog = youtubeCatalog ?: return emptyList()

        // 1. Identify video ID from URI or metadata
        var videoId = extractYouTubeVideoId(currentTrack.uri)

        // 2. If not directly identifiable (e.g. local file), perform fast lookup for top match
        if (videoId.isNullOrBlank()) {
            val query = listOf(currentTrack.artist, currentTrack.title)
                .filter { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) }
                .joinToString(" ")
                .trim()

            if (query.isNotBlank()) {
                val searchResult = catalog.search(query)
                if (searchResult is ProviderResult.Success && searchResult.data.isNotEmpty()) {
                    videoId = searchResult.data.first().id
                }
            }
        }

        val resolvedVideoId = videoId?.takeIf { it.isNotBlank() } ?: return emptyList()

        // 3. Query native YouTube "Watch Next" algorithm
        val nextResult = catalog.fetchWatchNext(resolvedVideoId)
        if (nextResult is ProviderResult.Success && nextResult.data.isNotEmpty()) {
            val filtered = nextResult.data.filter { it.id != resolvedVideoId }
            val merged = UnifiedTrackMerger.merge(filtered)
            if (merged.isNotEmpty()) {
                return merged.take(limit)
            }
        }

        return emptyList()
    }

    private fun extractYouTubeVideoId(uri: String): String? {
        val trimmed = uri.trim()
        if (trimmed.isBlank()) return null

        val watchMatch = Regex("""[?&]v=([a-zA-Z0-9_-]{11})""").find(trimmed)
        if (watchMatch != null) return watchMatch.groupValues[1]

        val shortMatch = Regex("""youtu\.be/([a-zA-Z0-9_-]{11})""").find(trimmed)
        if (shortMatch != null) return shortMatch.groupValues[1]

        if (trimmed.length == 11 && trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}\$"))) {
            return trimmed
        }

        return null
    }

    private suspend fun fetchSearchBasedRecommendations(
        currentTrack: Track,
        limit: Int,
    ): List<UnifiedTrack> {
        val searchAggregator = unifiedSearch ?: return emptyList()

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

        val queries = mutableListOf<String>()
        val hasArabic = (detectedArtist ?: "").any { it in '\u0600'..'\u06FF' } || coreTitle.any { it in '\u0600'..'\u06FF' }

        if (!detectedArtist.isNullOrBlank()) {
            queries.add("$detectedArtist songs")
            if (hasArabic) {
                queries.add("$detectedArtist اغاني")
                queries.add("$detectedArtist اجمل ما غنى")
            } else {
                queries.add("$detectedArtist top tracks")
            }
        }
        if (coreTitle.isNotBlank()) {
            queries.add(if (hasArabic) "$coreTitle مكس" else "$coreTitle mix")
            if (!detectedArtist.isNullOrBlank()) {
                queries.add("$detectedArtist $coreTitle")
            }
        }

        if (queries.isEmpty()) return emptyList()

        return try {
            val aggregated = mutableListOf<UnifiedTrack>()
            val seenKeys = mutableSetOf<String>()
            val normCurrentTitle = SearchNormalization.normalize(rawTitle)
            val normCoreTitle = SearchNormalization.normalize(coreTitle)

            for (query in queries) {
                if (aggregated.size >= limit) break
                val snapshot = withTimeoutOrNull(2_500L) {
                    searchAggregator.search(query).firstOrNull { it.isComplete || it.recordings.size >= (limit - aggregated.size) }
                }

                val items = snapshot?.recordings.orEmpty()
                for (onlineTrack in items) {
                    val normOnlineTitle = SearchNormalization.normalize(onlineTrack.title)
                    if (normOnlineTitle.isNotBlank() &&
                        normOnlineTitle != normCurrentTitle &&
                        (normCoreTitle.isBlank() || normOnlineTitle != normCoreTitle) &&
                        seenKeys.add(onlineTrack.recordingIdentity)
                    ) {
                        aggregated.add(onlineTrack)
                        if (aggregated.size >= limit) break
                    }
                }
            }

            aggregated.take(limit)
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
            "ريمكس", "مباشر", "حفلة", "جلسة", "عود", "طرب", "مكس", "روقان", "هادئ", "مسرع", "مبطأ",
            "عراقي", "خليجي", "مصري", "شامي", "مغربي", "دبكة", "راب", "راب عربي", "شعبي", "حزين"
        )
        for (tag in vibeTags) {
            if (normalized.contains(tag)) keywords.add(tag)
        }
        return keywords
    }
}
