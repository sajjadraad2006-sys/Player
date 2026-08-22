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
import kotlin.math.abs

private const val TAG = "LrclibClient"
private const val BASE = "https://lrclib.net/api"
private const val USER_AGENT = "Auralis Studio/0.2.0 (Android; https://github.com/auralis-studio)"

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
        // 1. Try exact endpoint
        try {
            val resp = client.get("$BASE/get") {
                parameter("track_name", title)
                parameter("artist_name", artist)
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
                    Log.w(TAG, "Rate limited; Retry-After=${retryAfter}s")
                    return null // caller will retry after caching the 429 state
                }
                HttpStatusCode.NotFound -> { /* fall through to search */ }
                else -> Log.w(TAG, "Unexpected status from /get: ${resp.status}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "LRCLIB /get error: ${e.message}")
        }

        // 2. Fall back to search
        return search(title, artist, durationMs)
    }

    /**
     * Searches for candidate tracks and picks the best match using
     * title/artist/album similarity + duration proximity.
     * Returns null if no good match is found.
     */
    suspend fun search(
        title: String,
        artist: String,
        durationMs: Long,
    ): LrclibTrack? {
        return try {
            val resp = client.get("$BASE/search") {
                parameter("track_name", title)
                parameter("artist_name", artist)
            }
            if (resp.status == HttpStatusCode.TooManyRequests) {
                Log.w(TAG, "Rate limited on /search")
                return null
            }
            if (!resp.status.isSuccess()) return null

            val candidates = resp.body<List<LrclibTrack>>()
            pickBestCandidate(candidates, title, artist, durationMs)
        } catch (e: Exception) {
            Log.e(TAG, "LRCLIB /search error: ${e.message}")
            null
        }
    }

    /**
     * Scoring: picks the candidate that best matches title, artist, and duration.
     * Rejects candidates whose synced and plain lyrics are both absent.
     * Threshold: minimum score of 0.40 required to accept a match.
     */
    private fun pickBestCandidate(
        candidates: List<LrclibTrack>,
        title: String,
        artist: String,
        durationMs: Long,
    ): LrclibTrack? {
        if (candidates.isEmpty()) return null
        val durationSec = durationMs / 1000.0

        data class Scored(val track: LrclibTrack, val score: Double)

        val scored = candidates.mapNotNull { track ->
            if (track.syncedLyrics.isNullOrBlank() && track.plainLyrics.isNullOrBlank()) return@mapNotNull null
            val titleSim  = similarity(title.lowercase(), track.trackName.lowercase()) * 0.40
            val artistSim = similarity(artist.lowercase(), track.artistName.lowercase()) * 0.35
            val durDiff   = abs(durationSec - track.duration)
            val durScore  = when {
                durDiff < 1  -> 0.25
                durDiff < 3  -> 0.18
                durDiff < 8  -> 0.10
                durDiff < 20 -> 0.03
                else         -> 0.0
            }
            val syncBonus = if (!track.syncedLyrics.isNullOrBlank()) 0.05 else 0.0
            val total = titleSim + artistSim + durScore + syncBonus
            Scored(track, total)
        }

        val best = scored.maxByOrNull { it.score } ?: return null
        if (best.score < 0.40) {
            Log.d(TAG, "Best candidate score ${best.score} below threshold — rejecting")
            return null
        }
        return best.track
    }

    /** Trigram-based string similarity in [0,1]. */
    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.length < 2 || b.length < 2) return if (a == b) 1.0 else 0.0
        val trigramsA = trigrams(a)
        val trigramsB = trigrams(b)
        val intersection = (trigramsA intersect trigramsB).size
        return 2.0 * intersection / (trigramsA.size + trigramsB.size)
    }

    private fun trigrams(s: String): Set<String> =
        (0..s.length - 3).map { s.substring(it, it + 3) }.toSet()

    fun close() { client.close() }
}
