package dev.auralis.studio.online

/** Optional link-import boundary. It is not part of catalog search and has no YouTube extractor. */
interface ExternalMediaResolver {
    val id: String
    fun supports(url: String): Boolean
    suspend fun inspect(url: String): ProviderResult<ExternalMedia>
}

data class ExternalMedia(
    val sourceUrl: String,
    val title: String,
    val artist: String?,
    val durationMs: Long?,
    val artworkUrl: String?,
    val capabilities: Set<ExternalMediaCapability>,
    val formats: List<ExternalMediaFormat> = emptyList(),
)

enum class ExternalMediaCapability { WATCH, STREAM, AUTHORIZED_EXPORT }

data class ExternalMediaFormat(
    val id: String,
    val mimeType: String?,
    val container: String?,
    val qualityLabel: String?,
    val exportAllowed: Boolean,
)

class ExternalMediaResolverRegistry(private val resolvers: List<ExternalMediaResolver>) {
    suspend fun inspect(url: String): ProviderResult<ExternalMedia> =
        resolvers.firstOrNull { it.supports(url) }?.inspect(url) ?: ProviderResult.Unavailable
}
