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
private const val USER_AGENT = "Lyrune/0.2.0 (Android; https://github.com/sajjadraad2006-sys/Player)"

@Serializable
data class LrclibTrack(
    val id: Long = 0,
    @SerialName("trackName") val trackName: String = "",
    @SerialName("artistName") val artistName: String = "",
    @SerialName("albumName") val albumName: String = "",
    val duration: Double = 0.0,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
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
        val cleanTitle = LyricsMetadataNormalizer.title(title)
        val cleanArtist = LyricsMetadataNormalizer.artist(artist)
        if (cleanTitle.isBlank()) return null
        if (System.currentTimeMillis() < rateLimitedUntilMs) {
            lastLookupWasRateLimited = true
            return null
        }
        // 1. Try exact endpoint
        if (cleanArtist.isNotBlank() && durationMs > 0) try {
            throttle()
            val resp = client.get("$BASE/get") {
                parameter("track_name", cleanTitle)
                parameter("artist_name", cleanArtist)
                if (album.isNotBlank()) parameter("album_name", album)
                parameter("duration", (durationMs / 1000).toInt())
            }
            when (resp.status) {
                HttpStatusCode.OK -> {
                    val track = resp.body<LrclibTrack>()
                    if (!track.syncedLyrics.isNullOrBlank() || !track.plainLyrics.isNullOrBlank()) {
                        return track
                    }
                }
                HttpStatusCode.TooManyRequests -> {
                    val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: 5L
                    rateLimitedUntilMs = System.currentTimeMillis() + retryAfter.coerceIn(1L, 300L) * 1_000L
                    lastLookupWasRateLimited = true
                    Log.w(TAG, "Rate limited; Retry-After=${retryAfter}s")
                    return null // caller will retry after caching the 429 state
                }
                HttpStatusCode.NotFound -> { /* fall through to search */ }
                else -> Log.w(TAG, "Unexpected status from /get: ${resp.status}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastLookupError = e.localizedMessage ?: "Network request failed"
            Log.e(TAG, "LRCLIB /get error: ${e.message}")
        }

        // 2. Fall back to search
        return search(cleanTitle, cleanArtist, album, durationMs)
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
    ): LrclibTrack? {
        return try {
            if (System.currentTimeMillis() < rateLimitedUntilMs) return null
            lastLookupError = null
            throttle()
            val resp = client.get("$BASE/search") {
                parameter("track_name", title)
                if (artist.isNotBlank()) parameter("artist_name", artist)
            }
            if (resp.status == HttpStatusCode.TooManyRequests) {
                val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: 5L
                rateLimitedUntilMs = System.currentTimeMillis() + retryAfter.coerceIn(1L, 300L) * 1_000L
                lastLookupWasRateLimited = true
                Log.w(TAG, "Rate limited on /search")
                return null
            }
            if (!resp.status.isSuccess()) return null

            val candidates = resp.body<List<LrclibTrack>>()
            pickBestCandidate(candidates, title, artist, album, durationMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastLookupError = e.localizedMessage ?: "Network request failed"
            Log.e(TAG, "LRCLIB /search error: ${e.message}")
            null
        }
    }

    /**
     * Scoring: picks the candidate that best matches title, artist, and duration.
     * Rejects candidates whose synced and plain lyrics are both absent.
     * Threshold: minimum score of 0.40 required to accept a match.
     */
    internal fun pickBestCandidate(
        candidates: List<LrclibTrack>,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long,
    ): LrclibTrack? {
        if (candidates.isEmpty()) return null
        val durationSec = durationMs / 1000.0

        data class Scored(val track: LrclibTrack, val score: Double)

        val scored = candidates.mapNotNull { track ->
            if (track.syncedLyrics.isNullOrBlank() && track.plainLyrics.isNullOrBlank()) return@mapNotNull null
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
            val titleSim  = similarity(title.lowercase(), track.trackName.lowercase()) * titleWeight
            val artistSim = similarity(artist.lowercase(), track.artistName.lowercase()) * artistWeight
            val albumBonus = if (album.isBlank()) 0.0 else similarity(album.lowercase(), track.albumName.lowercase()) * 0.08
            val durDiff   = abs(durationSec - track.duration)
            val durScore  = when {
                durationMs <= 0 -> 0.0
                durDiff < 1  -> 0.20
                durDiff < 3  -> 0.15
                durDiff < 8  -> 0.08
                durDiff < 20 -> 0.03
                else         -> 0.0
            }
            val syncBonus = if (!track.syncedLyrics.isNullOrBlank()) 0.05 else 0.0
            val total = titleSim + artistSim + albumBonus + durScore + syncBonus
            Scored(track, total)
        }

        val best = scored.maxByOrNull { it.score } ?: return null
        val threshold = if (artist.isBlank() || durationMs <= 0) 0.78 else 0.58
        if (best.score < threshold) {
            return null
        }
        return best.track
    }

    /** Trigram-based string similarity in [0,1]. */
    internal fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.length < 3 || b.length < 3) return if (a == b) 1.0 else 0.0
        val trigramsA = trigrams(a)
        val trigramsB = trigrams(b)
        val intersection = (trigramsA intersect trigramsB).size
        return 2.0 * intersection / (trigramsA.size + trigramsB.size)
    }

    private fun trigrams(s: String): Set<String> =
        (0..s.length - 3).map { s.substring(it, it + 3) }.toSet()

    private suspend fun throttle() {
        val waitMs = 650L - (System.currentTimeMillis() - lastRequestAtMs)
        if (waitMs > 0) delay(waitMs)
        lastRequestAtMs = System.currentTimeMillis()
    }

    fun close() { client.close() }
}
