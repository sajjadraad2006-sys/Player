package dev.auralis.studio.lyrics

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val TAG = "LrclibClient"
private const val BASE = "https://lrclib.net/api"
private const val USER_AGENT = "Lyrune/1.0.0 (Android; https://github.com/sajjadraad2006-sys/Player)"

@Serializable
data class LrclibTrack(
    val id: Long = 0,
    @SerialName("trackName") val trackName: String = "",
    @SerialName("artistName") val artistName: String = "",
    @SerialName("albumName") val albumName: String = "",
    val duration: Double = 0.0,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    /**
     * Raw Lyricsfile YAML document (https://lrclib.net/lyricsfile).
     * LRCLIB docs state every lyrics record contains one. May carry genuine
     * word-level timing that legacy syncedLyrics cannot express.
     */
    val lyricsfile: String? = null,
)


/**
 * Client for the LRCLIB open lyrics API.
 *
 * Responsibilities:
 * - Uses User-Agent per LRCLIB requirements.
 * - Respects HTTP 429 / Retry-After.
 * - Does NOT burst requests; callers should cache and debounce.
 * - Tries exact endpoint first, falls back to search + scoring.
 */
class LrclibClient {
    private var lastRequestAtMs = 0L
    private var rateLimitedUntilMs = 0L
    @Volatile var lastLookupWasRateLimited: Boolean = false
        private set
    @Volatile var lastLookupError: String? = null
        private set
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        defaultRequest {
            headers.append("User-Agent", USER_AGENT)
        }
    }

    /**
     * Resolve lyrics for a track. Returns null if not found.
     *
     * Priority:
     * 1. Exact /get endpoint (needs all four fields for best match).
     * 2. Search endpoint with candidate scoring.
     */
    suspend fun resolve(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ): LrclibTrack? {
        lastLookupWasRateLimited = false
        lastLookupError = null
        val norm = LyricsMetadataNormalizer.normalize(title, artist)
        val cleanTitle = norm.title
        val cleanArtist = norm.artist
        val searchQuery = norm.query

        if (cleanTitle.isBlank()) return null
        if (System.currentTimeMillis() < rateLimitedUntilMs) {
            lastLookupWasRateLimited = true
            return null
        }

        val variations = LyricsMetadataNormalizer.searchVariations(cleanTitle, cleanArtist)

        // 1. Try exact endpoint with variations (e.g. Arabic title + Latin artist alias)
        if (durationMs > 0) {
            for ((vTitle, vArtist) in variations) {
                if (vArtist.isBlank() || vTitle.isBlank()) continue
                try {
                    throttle()
                    val resp = client.get("$BASE/get") {
                        parameter("track_name", vTitle)
                        parameter("artist_name", vArtist)
                        if (album.isNotBlank()) parameter("album_name", album)
                        parameter("duration", (durationMs / 1000).toInt())
                    }
                    when (resp.status) {
                        HttpStatusCode.OK -> {
                            val track = resp.body<LrclibTrack>()
                            if (!track.syncedLyrics.isNullOrBlank() || !track.plainLyrics.isNullOrBlank() || !track.lyricsfile.isNullOrBlank()) {
                                return track
                            }
                        }
                        HttpStatusCode.TooManyRequests -> {
                            val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: 5L
                            rateLimitedUntilMs = System.currentTimeMillis() + retryAfter.coerceIn(1L, 300L) * 1_000L
                            lastLookupWasRateLimited = true
                            Log.w(TAG, "Rate limited; Retry-After=${retryAfter}s")
                            return null
                        }
                        HttpStatusCode.NotFound -> { /* try next variation */ }
                        else -> Log.w(TAG, "Unexpected status from /get: ${resp.status}")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastLookupError = e.localizedMessage ?: "Network request failed"
                    Log.e(TAG, "LRCLIB /get error: ${e.message}")
                }
            }
        }

        // 2. Fall back to search
        return search(cleanTitle, cleanArtist, album, durationMs, searchQuery)
    }

    /**
     * Searches for candidate tracks and picks the best match using
     * title/artist/album similarity + duration proximity.
     * Returns null if no good match is found.
     */
    suspend fun search(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long,
        searchQuery: String = "",
    ): LrclibTrack? {
        return try {
            if (System.currentTimeMillis() < rateLimitedUntilMs) return null
            lastLookupError = null

            val variations = LyricsMetadataNormalizer.searchVariations(title, artist)
            for ((vTitle, vArtist) in variations) {
                throttle()
                val resp = client.get("$BASE/search") {
                    parameter("track_name", vTitle)
                    if (vArtist.isNotBlank()) parameter("artist_name", vArtist)
                }
                if (resp.status == HttpStatusCode.TooManyRequests) {
                    val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: 5L
                    rateLimitedUntilMs = System.currentTimeMillis() + retryAfter.coerceIn(1L, 300L) * 1_000L
                    lastLookupWasRateLimited = true
                    Log.w(TAG, "Rate limited on /search")
                    return null
                }
                if (resp.status.isSuccess()) {
                    val candidates = resp.body<List<LrclibTrack>>()
                    val best = pickBestCandidate(candidates, title, artist, album, durationMs)
                    if (best != null) return best
                }
            }

            // Fallback: try search with general query parameter `q` and variations
            val queries = mutableListOf<String>()
            if (searchQuery.isNotBlank()) queries.add(searchQuery)
            if (artist.isNotBlank() && !title.contains(artist, ignoreCase = true)) {
                queries.add("$artist $title")
            }
            queries.add(title)
            for ((vTitle, vArtist) in variations) {
                if (vArtist.isNotBlank() && vTitle.isNotBlank()) {
                    queries.add("$vArtist $vTitle")
                }
            }

            for (q in queries.distinct()) {
                throttle()
                val qResp = client.get("$BASE/search") {
                    parameter("q", q)
                }
                if (qResp.status.isSuccess()) {
                    val qCandidates = qResp.body<List<LrclibTrack>>()
                    val best = pickBestCandidate(qCandidates, title, artist, album, durationMs)
                    if (best != null) return best
                }
            }

            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastLookupError = e.localizedMessage ?: "Network request failed"
            Log.e(TAG, "LRCLIB /search error: ${e.message}")
            null
        }
    }

    /**
     * Searches for candidates and returns them sorted by match confidence.
     */
    suspend fun searchCandidates(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long,
    ): List<LrclibTrack> {
        return try {
            if (System.currentTimeMillis() < rateLimitedUntilMs) return emptyList()
            lastLookupError = null
            val norm = LyricsMetadataNormalizer.normalize(title, artist)
            val cleanTitle = norm.title
            val cleanArtist = norm.artist
            val searchQuery = norm.query

            val variations = LyricsMetadataNormalizer.searchVariations(title, artist)
            val allCandidates = mutableListOf<LrclibTrack>()

            for ((vTitle, vArtist) in variations) {
                throttle()
                val resp = client.get("$BASE/search") {
                    parameter("track_name", vTitle)
                    if (vArtist.isNotBlank()) parameter("artist_name", vArtist)
                }
                if (resp.status == HttpStatusCode.TooManyRequests) {
                    val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: 5L
                    rateLimitedUntilMs = System.currentTimeMillis() + retryAfter.coerceIn(1L, 300L) * 1_000L
                    lastLookupWasRateLimited = true
                    return emptyList()
                }
                if (resp.status.isSuccess()) {
                    val list = resp.body<List<LrclibTrack>>()
                    for (c in list) {
                        if (allCandidates.none { it.id == c.id }) allCandidates.add(c)
                    }
                }
            }

            // Gather candidates from q parameter as well
            val q = searchQuery.ifBlank { if (cleanArtist.isNotBlank()) "$cleanArtist $cleanTitle" else cleanTitle }.trim()
            if (q.isNotBlank()) {
                throttle()
                val qResp = client.get("$BASE/search") {
                    parameter("q", q)
                }
                if (qResp.status.isSuccess()) {
                    val qList = qResp.body<List<LrclibTrack>>()
                    for (c in qList) {
                        if (allCandidates.none { it.id == c.id }) {
                            allCandidates.add(c)
                        }
                    }
                }
            }

            scoreCandidates(allCandidates, cleanTitle, cleanArtist, album, durationMs)
                .filter { it.score >= 0.10 }
                .map { it.track }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastLookupError = e.localizedMessage ?: "Network request failed"
            Log.e(TAG, "LRCLIB /searchCandidates error: ${e.message}")
            emptyList()
        }
    }

    internal data class ScoredTrack(val track: LrclibTrack, val score: Double)

    internal fun scoreCandidates(
        candidates: List<LrclibTrack>,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long,
    ): List<ScoredTrack> {
        if (candidates.isEmpty()) return emptyList()
        val durationSec = durationMs / 1000.0

        return candidates.mapNotNull { track ->
            if (track.syncedLyrics.isNullOrBlank() && track.plainLyrics.isNullOrBlank() && track.lyricsfile.isNullOrBlank()) return@mapNotNull null
            val titleWeight = when {
                artist.isBlank() && durationMs <= 0 -> 0.95
                artist.isBlank() -> 0.75
                durationMs <= 0 -> 0.55
                else -> 0.50
            }
            val artistWeight = when {
                artist.isBlank() -> 0.0
                durationMs <= 0 -> 0.40
                else -> 0.30
            }
            val queryTitle = title.lowercase()
            val candidateTitle = track.trackName.lowercase()
            val titleSim  = similarity(queryTitle, candidateTitle) * titleWeight
            val isArtistEquiv = LyricsMetadataNormalizer.areArtistsEquivalent(artist, track.artistName)
            val artistSim = if (isArtistEquiv) {
                1.0 * artistWeight
            } else {
                similarity(artist.lowercase(), track.artistName.lowercase()) * artistWeight
            }
            val albumBonus = if (album.isBlank()) 0.0 else similarity(album.lowercase(), track.albumName.lowercase()) * 0.08
            val durDiff   = abs(durationSec - track.duration)
            val textMatchConfidence = titleSim + artistSim
            val durScore  = when {
                durationMs <= 0 -> 0.0
                durDiff < 1.5   -> 0.20 // Excellent match — audio lengths match precisely
                durDiff < 4.0   -> 0.12 // Strong match
                durDiff < 15.0  -> if (textMatchConfidence >= 0.50) 0.04 else -0.20 // Acceptable video intro/outro variance
                durDiff < 30.0  -> if (textMatchConfidence >= 0.60) -0.06 else -0.40 // Longer video intro/outro or credits
                durDiff < 50.0  -> if (textMatchConfidence >= 0.70) -0.18 else -0.55 // Extended music video cut
                else            -> -0.60 // Severe mismatch
            }
            val syncBonus = if (!track.syncedLyrics.isNullOrBlank()) 0.08 else 0.0
            val versionPenalty = versionMismatchPenalty(queryTitle, candidateTitle)
            val total = titleSim + artistSim + albumBonus + durScore + syncBonus - versionPenalty
            ScoredTrack(track, total)
        }.sortedByDescending { it.score }
    }

    /**
     * Scoring: picks the candidate that best matches title, artist, and duration.
     * Rejects candidates whose synced and plain lyrics are both absent.
     * Threshold: minimum score of 0.35 required to accept an automatic match.
     */
    internal fun pickBestCandidate(
        candidates: List<LrclibTrack>,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long,
    ): LrclibTrack? {
        val scored = scoreCandidates(candidates, title, artist, album, durationMs)
        val best = scored.firstOrNull() ?: return null
        val threshold = 0.35
        if (best.score < threshold) {
            return null
        }
        return best.track
    }

    /** Trigram and token-based string similarity in [0,1], normalized for Arabic and Latin. */
    internal fun similarity(a: String, b: String): Double {
        val normA = dev.auralis.studio.online.SearchNormalization.normalize(a)
        val normB = dev.auralis.studio.online.SearchNormalization.normalize(b)
        if (normA == normB) return 1.0
        if (normA.isBlank() || normB.isBlank()) return 0.0
        if (normA.contains(normB) || normB.contains(normA)) return 0.90

        if (LyricsMetadataNormalizer.areArtistsEquivalent(a, b)) return 0.95

        val aIsAr = LyricsMetadataNormalizer.isArabic(normA)
        val bIsAr = LyricsMetadataNormalizer.isArabic(normB)
        if (aIsAr != bIsAr) {
            val transA = if (aIsAr) LyricsMetadataNormalizer.transliterateArabicToLatin(normA) else normA
            val transB = if (bIsAr) LyricsMetadataNormalizer.transliterateArabicToLatin(normB) else normB
            if (transA == transB) return 0.92
            if (transA.contains(transB) || transB.contains(transA)) return 0.88
        }

        val wordsA = normA.split(" ").filter { it.isNotBlank() }.toSet()
        val wordsB = normB.split(" ").filter { it.isNotBlank() }.toSet()
        val wordOverlap = if (wordsA.isNotEmpty() && wordsB.isNotEmpty()) {
            val intersection = (wordsA intersect wordsB).size
            val union = (wordsA union wordsB).size
            if (union > 0) intersection.toDouble() / union.toDouble() else 0.0
        } else 0.0

        val trigramsA = trigrams(normA)
        val trigramsB = trigrams(normB)
        val trigramSim = if (trigramsA.isNotEmpty() && trigramsB.isNotEmpty()) {
            val intersection = (trigramsA intersect trigramsB).size
            2.0 * intersection / (trigramsA.size + trigramsB.size)
        } else 0.0

        return maxOf(wordOverlap, trigramSim)
    }

    internal fun versionMismatchPenalty(queryTitle: String, candidateTitle: String): Double {
        val query = versionTags(queryTitle)
        val candidate = versionTags(candidateTitle)
        if (query == candidate) return 0.0
        // Version identity is stronger than duration.
        // A live or remix version must never beat a studio query simply because of duration.
        return when {
            query.isNotEmpty() && candidate.isNotEmpty() -> 0.45
            query.isEmpty() && candidate.isNotEmpty() -> 0.40
            query.isNotEmpty() && candidate.isEmpty() -> 0.35
            else -> 0.0
        }
    }

    private fun versionTags(value: String): Set<String> {
        val normalized = value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
        val words = normalized.split(Regex("\\s+")).filter(String::isNotBlank).toSet()
        val tags = linkedSetOf<String>()
        if ("live" in words || "concert" in words) tags += "live"
        if ("acoustic" in words || "unplugged" in words) tags += "acoustic"
        if ("remix" in words || "mix" in words) tags += "remix"
        if ("instrumental" in words) tags += "instrumental"
        if ("karaoke" in words) tags += "karaoke"
        if ("cover" in words) tags += "cover"
        if ("demo" in words) tags += "demo"
        if ("remaster" in words || "remastered" in words) tags += "remaster"
        if ("slowed" in words) tags += "slowed"
        if ("sped" in words || "nightcore" in words || ("speed" in words && "up" in words)) tags += "sped_up"
        if ("radio" in words && "edit" in words) tags += "radio_edit"
        return tags
    }


    private fun trigrams(s: String): Set<String> =
        (0..s.length - 3).map { s.substring(it, it + 3) }.toSet()

    private suspend fun throttle() {
        val waitMs = 250L - (System.currentTimeMillis() - lastRequestAtMs)
        if (waitMs > 0) delay(waitMs)
        lastRequestAtMs = System.currentTimeMillis()
    }

    fun close() { client.close() }
}
