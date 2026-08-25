package dev.auralis.studio.online

import java.text.Normalizer
import kotlin.math.abs

data class UnifiedTrack(
    val recordingIdentity: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val durationMs: Long,
    val artworkUrl: String,
    val isrc: String?,
    val sources: List<OnlineTrack>,
) {
    val previewSource: OnlineTrack? get() = sources.firstOrNull { !it.previewUrl.isNullOrBlank() }
    val streamSource: OnlineTrack? get() = sources.firstOrNull { it.streamAllowed && !it.streamUrl.isNullOrBlank() }
    val playableSource: OnlineTrack? get() = streamSource ?: previewSource

    fun preferredDownloadSource(quality: DownloadQuality): OnlineTrack? = sources
        .filter(OnlineTrack::isDownloadableFullTrack)
        .maxWithOrNull(
            compareBy<OnlineTrack> { downloadScore(it, quality) }
                .thenBy { metadataScore(it) }
                .thenBy { providerReliability(it.provider) },
        )

    companion object {
        private fun downloadScore(track: OnlineTrack, preference: DownloadQuality): Int {
            val format = track.audioFormat.orEmpty().lowercase()
            val formats = track.availableDownloadFormats.map(String::lowercase)
            val lossless = format == "flac" || format == "wav" || "flac" in formats || "wav" in formats ||
                track.qualityLabel?.contains("original", true) == true
            return when (preference) {
                DownloadQuality.BEST_AVAILABLE -> if (lossless) 300 else if (format == "ogg" || format == "mp3") 180 else 100
                DownloadQuality.HIGH -> if ("mp32" in formats) 260 else if (lossless) 220 else if (track.qualityLabel?.contains("high", true) == true) 240 else 160
                DownloadQuality.DATA_SAVER -> if ("mp31" in formats) 280 else if (track.qualityLabel?.contains("96", true) == true) 260 else if (lossless) 80 else 160
            }
        }

        private fun metadataScore(track: OnlineTrack): Int =
            (if (!track.isrc.isNullOrBlank()) 80 else 0) + (if (track.durationMs > 0) 30 else 0) +
                (if (track.album.isNotBlank()) 20 else 0) + (if (track.artist.isTrustedValue()) 30 else 0)

        private fun providerReliability(provider: String): Int = when (provider) {
            "audius" -> 40
            "jamendo" -> 35
            "itunes" -> 30
            "deezer" -> 25
            else -> 10
        }
    }
}

private fun String.isTrustedValue(): Boolean = isNotBlank() &&
    !equals("Music", true) && !equals("Lyrune", true) && !startsWith("Unknown", true)

object RecordingIdentity {
    private val qualifiers = listOf(
        "radio edit", "sped up", "live", "concert", "remix", "mix", "edit", "acoustic",
        "unplugged", "remaster", "remastered", "cover", "karaoke", "instrumental", "slowed",
        "nightcore", "demo",
    )

    fun normalizedIsrc(value: String?): String? = value?.uppercase()?.filter(Char::isLetterOrDigit)
        ?.takeIf { it.length in 10..15 }

    fun forTrack(track: OnlineTrack): String {
        normalizedIsrc(track.isrc)?.let { return "isrc:$it" }
        val durationBucket = if (track.durationMs > 0) track.durationMs / 2_000L else -1L
        return "meta:${normalizedText(track.title)}|${normalizedArtist(track.artist)}|" +
            "${versionSignature(track.title)}|$durationBucket"
    }

    fun versionSignature(title: String): String {
        val normalized = normalizedText(title)
        return qualifiers.filter { qualifier ->
            normalized == qualifier || normalized.contains(" $qualifier ") ||
                normalized.startsWith("$qualifier ") || normalized.endsWith(" $qualifier")
        }.sorted().joinToString("+").ifBlank { "studio" }
    }

    fun normalizedText(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
        return buildString(decomposed.length) {
            var space = true
            decomposed.lowercase().forEach { char ->
                when {
                    Character.getType(char) == Character.NON_SPACING_MARK.toInt() -> Unit
                    char.isLetterOrDigit() -> { append(char); space = false }
                    !space -> { append(' '); space = true }
                }
            }
        }.trim()
    }

    fun normalizedArtist(value: String): String = normalizedText(value)
        .replace(Regex("\\b(and|feat|featuring|ft)\\b"), " ")
        .replace(Regex("\\s+"), " ").trim()
}

object UnifiedTrackMerger {
    fun merge(input: List<OnlineTrack>): List<UnifiedTrack> {
        val groups = mutableListOf<MutableList<OnlineTrack>>()
        input.distinctBy { it.downloadKey }.forEach { candidate ->
            val group = groups.firstOrNull { canMerge(it.first(), candidate) }
            if (group == null) groups += mutableListOf(candidate) else group += candidate
        }
        return groups.map(::canonicalize)
    }

    fun canMerge(left: OnlineTrack, right: OnlineTrack): Boolean {
        val leftIsrc = RecordingIdentity.normalizedIsrc(left.isrc)
        val rightIsrc = RecordingIdentity.normalizedIsrc(right.isrc)
        if (leftIsrc != null && rightIsrc != null) return leftIsrc == rightIsrc
        if (RecordingIdentity.versionSignature(left.title) != RecordingIdentity.versionSignature(right.title)) return false
        if (RecordingIdentity.normalizedText(left.title) != RecordingIdentity.normalizedText(right.title)) return false
        if (RecordingIdentity.normalizedArtist(left.artist) != RecordingIdentity.normalizedArtist(right.artist)) return false
        if (left.durationMs > 0 && right.durationMs > 0) {
            val tolerance = maxOf(3_000L, minOf(left.durationMs, right.durationMs) / 50L)
            if (abs(left.durationMs - right.durationMs) > tolerance) return false
        }
        val leftAlbum = RecordingIdentity.normalizedText(left.album)
        val rightAlbum = RecordingIdentity.normalizedText(right.album)
        return leftAlbum.isBlank() || rightAlbum.isBlank() || leftAlbum == rightAlbum ||
            (left.durationMs > 0 && right.durationMs > 0)
    }

    private fun canonicalize(sources: List<OnlineTrack>): UnifiedTrack {
        val ranked = sources.sortedByDescending(::metadataScore)
        val best = ranked.first()
        val isrc = sources.mapNotNull { RecordingIdentity.normalizedIsrc(it.isrc) }.firstOrNull()
        return UnifiedTrack(
            recordingIdentity = isrc?.let { "isrc:$it" } ?: RecordingIdentity.forTrack(best),
            title = best.title,
            artist = ranked.firstOrNull { it.artist.isTrustedValue() }?.artist ?: best.artist,
            album = ranked.firstOrNull { it.album.isNotBlank() }?.album.orEmpty(),
            albumArtist = ranked.firstNotNullOfOrNull(OnlineTrack::albumArtist),
            durationMs = ranked.firstOrNull { it.durationMs > 0 }?.durationMs ?: 0L,
            artworkUrl = ranked.map(OnlineTrack::artworkUrl).filter(String::isNotBlank).maxByOrNull(String::length).orEmpty(),
            isrc = isrc,
            sources = sources.sortedBy { providerOrder(it.provider) },
        )
    }

    private fun metadataScore(track: OnlineTrack): Int =
        (if (RecordingIdentity.normalizedIsrc(track.isrc) != null) 1_000 else 0) +
            (if (track.artist.isTrustedValue()) 100 else 0) + (if (track.album.isNotBlank()) 40 else 0) +
            (if (track.durationMs > 0) 80 else 0) +
            (if (track.provider in setOf("itunes", "deezer", "audius", "jamendo")) 40 else 0)

    private fun providerOrder(provider: String): Int = when (provider) {
        "audius" -> 0
        "jamendo" -> 1
        "itunes" -> 2
        "deezer" -> 3
        else -> 4
    }
}
