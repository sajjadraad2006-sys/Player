package dev.auralis.studio.online

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

private const val TAG = "ArchiveOrgProvider"
private const val SEARCH_BASE = "https://archive.org/advancedsearch.php"
private const val USER_AGENT = "Lyrune/0.2.0 (Android)"

@Serializable
private data class ArchiveSearchResponse(
    val response: ArchiveResponseBody = ArchiveResponseBody(),
)

@Serializable
private data class ArchiveResponseBody(
    val numFound: Int = 0,
    val start: Int = 0,
    val docs: List<ArchiveDocDto> = emptyList(),
)

@Serializable
private data class ArchiveDocDto(
    val identifier: String = "",
    val title: String = "",
    val creator: String? = null,
    val album: String? = null,
    val year: String? = null,
    val description: String? = null,
    val downloads: Int = 0,
)

@Serializable
private data class ArchiveMetadataResponse(
    val files: List<ArchiveFileDto> = emptyList(),
    val server: String = "",
    val dir: String = "",
)

@Serializable
private data class ArchiveFileDto(
    val name: String = "",
    val source: String = "",
    val format: String = "",
    val title: String? = null,
    val creator: String? = null,
    val album: String? = null,
    val length: String? = null,
    val size: String? = null,
)

/**
 * Free online music discovery and download provider powered by the Internet Archive.
 * Requires NO API key, completely open and legal, and offers free streaming and direct downloads.
 */
class ArchiveOrgMusicProvider : OnlineMusicProvider {

    override val name: String = "Free Music Archive"

    override val isConfigured: Boolean = true

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            defaultRequest {
                headers.append("User-Agent", USER_AGENT)
            }
        }
    }

    override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ProviderResult.Success(emptyList())

        return try {
            val rows = 25
            val start = page * rows
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val searchParam = "mediatype:audio AND ($trimmed)"

            val resp = client.get(SEARCH_BASE) {
                parameter("q", searchParam)
                parameter("fl[]", "identifier,title,creator,album,year,description,downloads")
                parameter("sort[]", "downloads desc")
                parameter("rows", rows)
                parameter("page", page + 1)
                parameter("output", "json")
            }

            if (resp.status != HttpStatusCode.OK) {
                return ProviderResult.Error("HTTP ${resp.status.value}")
            }

            val body = resp.body<ArchiveSearchResponse>()
            val tracks = body.response.docs.map { doc ->
                val id = doc.identifier
                val title = doc.title.ifBlank { id }
                val artist = doc.creator?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                val album = doc.album?.takeIf { it.isNotBlank() } ?: (doc.year ?: "Internet Archive")
                val artwork = "https://archive.org/services/img/$id"
                val streamUrl = "https://archive.org/download/$id"

                OnlineTrack(
                    provider        = "archive_org",
                    id              = id,
                    title           = title,
                    artist          = artist,
                    album           = album,
                    durationMs      = 0L, // Will resolve on playback
                    artworkUrl      = artwork,
                    streamUrl       = streamUrl,
                    downloadUrl     = streamUrl,
                    downloadAllowed = true,
                    license         = "Public Domain / Creative Commons",
                    licenseUrl      = "https://creativecommons.org/licenses/publicdomain/",
                    sourceUrl       = "https://archive.org/details/$id",
                )
            }

            ProviderResult.Success(tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Archive.org search error", e)
            ProviderResult.Error(e.localizedMessage ?: "Search failed")
        }
    }

    override suspend fun getTrack(id: String): ProviderResult<OnlineTrack> {
        return try {
            val metaResp = client.get("https://archive.org/metadata/$id")
            if (metaResp.status != HttpStatusCode.OK) {
                return ProviderResult.Error("HTTP ${metaResp.status.value}")
            }
            val meta = metaResp.body<ArchiveMetadataResponse>()
            val audioFile = meta.files.firstOrNull { 
                it.name.endsWith(".mp3", ignoreCase = true) || 
                it.name.endsWith(".ogg", ignoreCase = true) ||
                it.name.endsWith(".m4a", ignoreCase = true) ||
                it.name.endsWith(".flac", ignoreCase = true)
            } ?: meta.files.firstOrNull { it.format.contains("Audio", ignoreCase = true) }

            val directUrl = if (audioFile != null) {
                "https://archive.org/download/$id/${audioFile.name}"
            } else {
                "https://archive.org/download/$id"
            }

            val durSec = audioFile?.length?.toDoubleOrNull() ?: 0.0

            ProviderResult.Success(
                OnlineTrack(
                    provider        = "archive_org",
                    id              = id,
                    title           = audioFile?.title?.ifBlank { null } ?: id,
                    artist          = audioFile?.creator?.ifBlank { null } ?: "Archive Artist",
                    album           = audioFile?.album?.ifBlank { null } ?: "Internet Archive",
                    durationMs      = (durSec * 1000).toLong(),
                    artworkUrl      = "https://archive.org/services/img/$id",
                    streamUrl       = directUrl,
                    downloadUrl     = directUrl,
                    downloadAllowed = true,
                    license         = "Public Domain / Creative Commons",
                    licenseUrl      = "https://creativecommons.org/licenses/publicdomain/",
                    sourceUrl       = "https://archive.org/details/$id",
                )
            )
        } catch (e: Exception) {
            ProviderResult.Error(e.localizedMessage ?: "Failed to resolve track")
        }
    }
}
