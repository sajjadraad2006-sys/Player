package dev.auralis.studio.online

import java.util.UUID
import kotlin.math.abs

/**
 * Structured recording identity evidence.
 *
 * Invariant: Preserves full structured evidence (ISRC, normalized title/artist, version qualifier, duration bucket)
 * instead of discarding evidence into a lossy flat string.
 */
data class StructuredIdentity(
    val isrc: String? = null,
    val normalizedTitle: String,
    val normalizedArtist: String,
    val versionQualifier: VersionQualifier = VersionQualifier.ORIGINAL,
    val durationBucket: Long = -1L,
) {
    /**
     * Stable key string representation. Does NOT replace structured evidence.
     */
    val stableKey: String
        get() = isrc?.let { "isrc:$it" }
            ?: "meta:$normalizedTitle|$normalizedArtist|${versionQualifier.name.lowercase()}|$durationBucket"
}

/**
 * Canonical recording domain model unifying candidates across multiple providers and local storage.
 *
 * Key Invariants:
 * 1. [sources] is the authoritative, lossless list of all discovered candidate sources.
 * 2. [bestPlaybackSource] and [bestDownloadSource] are purely DERIVED from [sources], never stored mutable state.
 * 3. [stableSessionId] is assigned when the canonical group is created and remains completely stable across progressive provider updates.
 */
data class CanonicalRecording(
    val identity: StructuredIdentity,
    val stableSessionId: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String,
    val album: String = "",
    val albumArtist: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String = "",
    val isrc: String? = null,
    val sources: List<RecordingSource> = emptyList(),
) {
    /** Backward-compatible recording identity string */
    val recordingIdentity: String get() = identity.stableKey

    /** Stable UI key for Compose keying */
    val uiKey: String get() = stableSessionId

    /**
     * Purely derived best playback source. Re-evaluates as progressive sources arrive.
     * Previews will NEVER beat a full-length playable stream.
     */
    val bestPlaybackSource: RecordingSource?
        get() = PlaybackSourceSelector.select(sources)

    /**
     * Purely derived best download source enforcing Phase 4 permissions.
     */
    val bestDownloadSource: RecordingSource?
        get() = DownloadSourceSelector.select(sources, DownloadQuality.BEST_AVAILABLE)

    fun bestDownloadSource(quality: DownloadQuality): RecordingSource? =
        DownloadSourceSelector.select(sources, quality)

    val isLocal: Boolean get() = sources.any { it is LocalRecordingSource }
    val isPlayable: Boolean get() = bestPlaybackSource != null
    val isDownloadable: Boolean get() = bestDownloadSource != null
    val hasPreview: Boolean get() = sources.any { it.playbackCapability == PlaybackCapability.PREVIEW }
    val sourceCount: Int get() = sources.size

    // ── Backward-compatible legacy accessors ───────────────────────────────────

    val onlineSources: List<OnlineTrack>
        get() = sources.mapNotNull { (it as? OnlineRecordingSource)?.originalTrack }

    val previewSource: OnlineTrack?
        get() = sources.firstNotNullOfOrNull { (it as? OnlineRecordingSource)?.originalTrack?.takeIf { t -> !t.previewUrl.isNullOrBlank() } }

    val streamSource: OnlineTrack?
        get() = sources.firstNotNullOfOrNull { (it as? OnlineRecordingSource)?.originalTrack?.takeIf { t -> t.streamAllowed && !t.streamUrl.isNullOrBlank() } }

    val playableSource: OnlineTrack?
        get() = (bestPlaybackSource as? OnlineRecordingSource)?.originalTrack
            ?: onlineSources.firstOrNull { it.streamAllowed && !it.streamUrl.isNullOrBlank() }
            ?: previewSource

    fun preferredDownloadSource(quality: DownloadQuality): OnlineTrack? =
        (bestDownloadSource(quality) as? OnlineRecordingSource)?.originalTrack
}

/**
 * Backward-compatible alias for existing codebase references.
 */
typealias UnifiedTrack = CanonicalRecording

private fun String.isTrustedValue(): Boolean = isNotBlank() &&
    !equals("Music", true) && !equals("Lyrune", true) && !startsWith("Unknown", true)

object RecordingIdentity {

    private val ISRC_REGEX = Regex("^[A-Z]{2}[A-Z0-9]{3}[0-9]{7}$")

    fun normalizedIsrc(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val cleaned = value.uppercase().filter(Char::isLetterOrDigit)
        return cleaned.takeIf { ISRC_REGEX.matches(it) }
    }

    fun forTrack(track: OnlineTrack): StructuredIdentity = structuredIdentity(
        title = track.title,
        artist = track.artist,
        durationMs = track.durationMs,
        isrc = track.isrc,
    )

    fun forTrackKey(track: OnlineTrack): String = forTrack(track).stableKey

    fun structuredIdentity(
        title: String,
        artist: String,
        durationMs: Long,
        isrc: String?,
    ): StructuredIdentity {
        val normIsrc = normalizedIsrc(isrc)
        val normTitle = IdentityNormalization.cleanTitleForIdentity(title)
        val normArtist = IdentityNormalization.normalizeArtist(artist)
        val qualifier = VersionQualifier.parse(title)
        val durationBucket = if (durationMs > 0) durationMs / 2_000L else -1L
        return StructuredIdentity(
            isrc = normIsrc,
            normalizedTitle = normTitle,
            normalizedArtist = normArtist,
            versionQualifier = qualifier,
            durationBucket = durationBucket,
        )
    }

    fun versionSignature(title: String): String = VersionQualifier.parse(title).name.lowercase()

    fun normalizedText(value: String): String = IdentityNormalization.normalize(value)

    fun normalizedArtist(value: String): String = IdentityNormalization.normalizeArtist(value)
}

object UnifiedTrackMerger {

    fun merge(input: List<OnlineTrack>): List<CanonicalRecording> {
        val sources = input.map(OnlineTrack::toRecordingSource)
        return mergeSources(sources)
    }

    /**
     * Merges candidate sources into canonical recordings while strictly preventing false merges.
     *
     * Invariants:
     * 1. Version qualifier conflicts block merging (ORIGINAL != LIVE != REMIX != COVER).
     * 2. Conflicting valid trusted ISRCs block merging.
     * 3. Preview duration (30s) does not block merging with a full-length track if strong evidence matches.
     * 4. Transitive traps are prevented: every incoming source must remain compatible with the group's strong constraints.
     */
    fun mergeSources(
        sources: List<RecordingSource>,
        existingGroups: List<CanonicalRecording> = emptyList(),
    ): List<CanonicalRecording> {
        val groups = existingGroups.map { GroupBuilder.from(it) }.toMutableList()

        for (candidate in sources) {
            val matchingGroup = groups.firstOrNull { it.canAdmit(candidate) }
            if (matchingGroup != null) {
                matchingGroup.addSource(candidate)
            } else {
                val newBuilder = GroupBuilder()
                newBuilder.addSource(candidate)
                groups.add(newBuilder)
            }
        }

        return groups.map { it.build() }
    }

    fun canMerge(left: OnlineTrack, right: OnlineTrack): Boolean {
        val leftSource = left.toRecordingSource()
        val rightSource = right.toRecordingSource()
        return canMergeSources(leftSource, rightSource)
    }

    fun canMergeSources(left: RecordingSource, right: RecordingSource): Boolean {
        // 1. Version qualifier conflict is a hard merge blocker (ORIGINAL != LIVE != REMIX != COVER etc.)
        val leftQualifier = VersionQualifier.parse(left.title)
        val rightQualifier = VersionQualifier.parse(right.title)
        if (leftQualifier != rightQualifier) return false

        // 2. ISRC is authoritative recording identity when present
        val leftIsrc = RecordingIdentity.normalizedIsrc(left.isrc)
        val rightIsrc = RecordingIdentity.normalizedIsrc(right.isrc)
        if (leftIsrc != null && rightIsrc != null) {
            // Same valid ISRC + compatible qualifiers = same recording
            // Conflicting valid ISRCs = strong blocker
            return leftIsrc == rightIsrc
        }

        // 3. Preprocessed normalized title & artist check (stripping display noise like '(Official Video)')
        val leftTitle = IdentityNormalization.cleanTitleForIdentity(left.title)
        val rightTitle = IdentityNormalization.cleanTitleForIdentity(right.title)
        if (leftTitle != rightTitle) return false

        val leftArtist = IdentityNormalization.normalizeArtist(left.artist)
        val rightArtist = IdentityNormalization.normalizeArtist(right.artist)
        if (leftArtist.isNotBlank() && rightArtist.isNotBlank() && leftArtist != rightArtist) return false

        // 4. Duration compatibility (Previews don't block full tracks)
        val leftIsPreview = left.playbackCapability == PlaybackCapability.PREVIEW
        val rightIsPreview = right.playbackCapability == PlaybackCapability.PREVIEW
        if (!leftIsPreview && !rightIsPreview && left.durationMs > 0 && right.durationMs > 0) {
            val tolerance = maxOf(4_000L, minOf(left.durationMs, right.durationMs) / 25L)
            if (abs(left.durationMs - right.durationMs) > tolerance) return false
        }

        return true
    }

    private class GroupBuilder(
        var stableSessionId: String = UUID.randomUUID().toString(),
        val sources: MutableList<RecordingSource> = mutableListOf(),
    ) {
        companion object {
            fun from(group: CanonicalRecording): GroupBuilder = GroupBuilder(
                stableSessionId = group.stableSessionId,
                sources = group.sources.toMutableList(),
            )
        }

        fun canAdmit(candidate: RecordingSource): Boolean {
            if (sources.isEmpty()) return true
            // Transitive protection: candidate must be compatible with ALL existing sources in the group
            return sources.all { canMergeSources(it, candidate) }
        }

        fun addSource(source: RecordingSource) {
            // Avoid duplicate source entries from the same provider + id
            val exists = sources.any { it.provider == source.provider && it.providerTrackId == source.providerTrackId }
            if (!exists) {
                sources.add(source)
            }
        }

        fun build(): CanonicalRecording {
            val ranked = sources.sortedByDescending(::sourceMetadataScore)
            val best = ranked.first()

            // Canonical duration prefers full-length trusted tracks over 30s previews
            val canonicalDuration = sources
                .filter { it.playbackCapability != PlaybackCapability.PREVIEW && it.durationMs > 0 }
                .maxOfOrNull { it.durationMs }
                ?: sources.firstOrNull { it.durationMs > 0 }?.durationMs ?: 0L

            val isrc = sources.mapNotNull { RecordingIdentity.normalizedIsrc(it.isrc) }.firstOrNull()

            val identity = RecordingIdentity.structuredIdentity(
                title = best.title,
                artist = ranked.firstOrNull { it.artist.isTrustedValue() }?.artist ?: best.artist,
                durationMs = canonicalDuration,
                isrc = isrc,
            )

            return CanonicalRecording(
                identity = identity,
                stableSessionId = stableSessionId,
                title = best.title,
                artist = ranked.firstOrNull { it.artist.isTrustedValue() }?.artist ?: best.artist,
                album = ranked.firstOrNull { it.album.isNotBlank() }?.album.orEmpty(),
                albumArtist = ranked.firstNotNullOfOrNull { it.albumArtist },
                durationMs = canonicalDuration,
                artworkUrl = ranked.map { it.artworkUrl }.filter(String::isNotBlank).maxByOrNull(String::length).orEmpty(),
                isrc = isrc,
                sources = sources.sortedWith(
                    compareByDescending<RecordingSource> { if (it is LocalRecordingSource) 100 else 0 }
                        .thenBy { it.provider }
                ),
            )
        }

        private fun sourceMetadataScore(source: RecordingSource): Int =
            (if (source is LocalRecordingSource) 500 else 0) +
                (if (RecordingIdentity.normalizedIsrc(source.isrc) != null) 1_000 else 0) +
                (if (source.playbackCapability == PlaybackCapability.FULL_STREAM) 200 else 0) +
                (if (source.artist.isTrustedValue()) 100 else 0) +
                (if (source.album.isNotBlank()) 40 else 0) +
                (if (source.durationMs > 60_000L) 80 else 0)
    }
}
