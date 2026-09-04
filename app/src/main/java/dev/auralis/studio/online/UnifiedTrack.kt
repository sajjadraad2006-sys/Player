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
    val versionProfile: VersionProfile = VersionProfile.ORIGINAL,
    val durationBucket: Long = -1L,
) {
    val versionQualifier: VersionQualifier get() = versionProfile.primary

    /**
     * Stable key string representation. Does NOT replace structured evidence.
     */
    val stableKey: String
        get() = isrc?.let { "isrc:$it" }
            ?: "meta:$normalizedTitle|$normalizedArtist|${versionProfile.qualifiers.map { it.name.lowercase() }.sorted().joinToString("+")}|$durationBucket"
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
        val profile = VersionProfile.parse(title)
        val durationBucket = if (durationMs > 0) durationMs / 2_000L else -1L
        return StructuredIdentity(
            isrc = normIsrc,
            normalizedTitle = normTitle,
            normalizedArtist = normArtist,
            versionProfile = profile,
            durationBucket = durationBucket,
        )
    }

    fun versionSignature(title: String): String = VersionQualifier.parse(title).name.lowercase()

    fun normalizedText(value: String): String = IdentityNormalization.normalize(value)

    fun normalizedArtist(value: String): String = IdentityNormalization.normalizeArtist(value)
}

object CanonicalMetadataSelector {

    fun selectDuration(sources: List<RecordingSource>): Long {
        val fullSources = sources.filter { it.playbackCapability != PlaybackCapability.PREVIEW && it.durationMs > 0 }
        val candidates = if (fullSources.isNotEmpty()) fullSources else sources.filter { it.durationMs > 0 }
        if (candidates.isEmpty()) return 0L

        val durations = candidates.map { it.durationMs }.sorted()
        if (durations.size == 1) return durations.first()

        // Cluster durations within 5000ms or 5% tolerance
        val clusters = mutableListOf<MutableList<Long>>()
        for (d in durations) {
            val cluster = clusters.firstOrNull { c ->
                val avg = c.average()
                val tol = maxOf(4_000.0, avg * 0.05)
                abs(d - avg) <= tol
            }
            if (cluster != null) {
                cluster.add(d)
            } else {
                clusters.add(mutableListOf(d))
            }
        }

        val bestCluster = clusters.maxByOrNull { it.size } ?: durations
        return bestCluster[bestCluster.size / 2]
    }

    fun selectMetadata(sources: List<RecordingSource>, stableSessionId: String): CanonicalRecording {
        val sortedSources = sources.sortedWith(
            compareByDescending<RecordingSource> { sourceMetadataScore(it) }
                .thenBy { it.provider }
                .thenBy { it.providerTrackId }
        )
        val best = sortedSources.first()
        val canonicalDuration = selectDuration(sources)
        val trustedIsrc = sortedSources.mapNotNull { it.trustedIsrc }.firstOrNull()

        val identity = RecordingIdentity.structuredIdentity(
            title = best.title,
            artist = sortedSources.firstOrNull { it.artist.isTrustedValue() }?.artist ?: best.artist,
            durationMs = canonicalDuration,
            isrc = trustedIsrc,
        )

        return CanonicalRecording(
            identity = identity,
            stableSessionId = stableSessionId,
            title = best.title,
            artist = sortedSources.firstOrNull { it.artist.isTrustedValue() }?.artist ?: best.artist,
            album = sortedSources.firstOrNull { it.album.isNotBlank() }?.album.orEmpty(),
            albumArtist = sortedSources.firstNotNullOfOrNull { it.albumArtist },
            durationMs = canonicalDuration,
            artworkUrl = sortedSources.map { it.artworkUrl }.filter(String::isNotBlank).maxByOrNull(String::length).orEmpty(),
            isrc = trustedIsrc,
            sources = sortedSources.sortedWith(
                compareByDescending<RecordingSource> { if (it is LocalRecordingSource) 100 else 0 }
                    .thenBy { it.provider }
                    .thenBy { it.providerTrackId }
            ),
        )
    }

    private fun sourceMetadataScore(source: RecordingSource): Int =
        (if (source is LocalRecordingSource) 500 else 0) +
            (if (source.trustedIsrc != null) 1_000 else 0) +
            (if (source.playbackCapability == PlaybackCapability.FULL_STREAM) 200 else 0) +
            (if (source.artist.isTrustedValue()) 100 else 0) +
            (if (source.album.isNotBlank()) 40 else 0) +
            (if (source.durationMs > 60_000L) 80 else 0)
}

object CanonicalSearchRanker {

    fun rank(recordings: List<CanonicalRecording>, rawQuery: String): List<CanonicalRecording> {
        val normQuery = SearchNormalization.normalize(rawQuery)
        if (normQuery.isBlank()) {
            return recordings.sortedWith(
                compareByDescending<CanonicalRecording> { it.sources.size }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.artist.lowercase() }
            )
        }

        return recordings
            .map { it to score(it, normQuery) }
            .sortedWith(
                compareByDescending<Pair<CanonicalRecording, Int>> { it.second }
                    .thenByDescending { it.first.sources.size }
                    .thenBy { it.first.title.lowercase() }
                    .thenBy { it.first.artist.lowercase() }
                    .thenBy { it.first.recordingIdentity }
            )
            .map { it.first }
    }

    private fun score(recording: CanonicalRecording, normQuery: String): Int {
        val normTitle = SearchNormalization.normalize(recording.title)
        val normArtist = SearchNormalization.normalize(recording.artist)
        val normAlbum = SearchNormalization.normalize(recording.album)

        var score = 0

        // 1. Title matching
        val titleRatio = SearchNormalization.matchRatio(normTitle, normQuery)
        if (normTitle == normQuery) {
            score += 10_000
        } else if (normTitle.startsWith(normQuery)) {
            score += 5_000
        } else if (normTitle.contains(normQuery)) {
            score += 3_500
        } else if (titleRatio >= 1.0f) {
            score += 3_000
        }

        // 2. Artist matching
        if (normArtist.isNotEmpty()) {
            val artistRatio = SearchNormalization.matchRatio(normArtist, normQuery)
            if (normArtist == normQuery) {
                score += 1_200
            } else if (normArtist.startsWith(normQuery)) {
                score += 800
            } else if (normArtist.contains(normQuery)) {
                score += 400
            } else if (artistRatio >= 1.0f) {
                score += 350
            }
        }

        // 3. Album matching
        if (normAlbum.isNotEmpty()) {
            if (normAlbum == normQuery) {
                score += 300
            } else if (normAlbum.contains(normQuery)) {
                score += 150
            }
        }

        // 4. Source capability bonus
        if (recording.isLocal) score += 50
        if (recording.isPlayable) score += 30
        if (recording.isDownloadable) score += 20

        return score
    }

    private fun isTokenMatch(target: String, query: String): Boolean {
        return SearchNormalization.matchRatio(target, query) >= 0.75f
    }
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
     * 1. Version profile conflicts block merging (ORIGINAL != LIVE != REMIX != COVER).
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
        // Distinct tracks from the same provider must never be merged
        if (left.provider.equals(right.provider, true) && left.providerTrackId != right.providerTrackId) {
            return false
        }

        // 1. Version profile conflict is a hard merge blocker (ORIGINAL != LIVE != REMIX != COVER etc.)
        val leftProfile = VersionProfile.parse(left.title)
        val rightProfile = VersionProfile.parse(right.title)
        if (!leftProfile.isCompatibleWith(rightProfile)) return false

        // 2. Trusted ISRC is authoritative recording identity when present
        val leftIsrc = left.trustedIsrc
        val rightIsrc = right.trustedIsrc
        if (leftIsrc != null && rightIsrc != null) {
            return leftIsrc == rightIsrc
        }

        // 3. Preprocessed normalized core title & artist check
        val leftTitle = IdentityNormalization.coreTitle(left.title)
        val rightTitle = IdentityNormalization.coreTitle(right.title)
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

    class GroupBuilder(
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
            val exists = sources.any { it.provider == source.provider && it.providerTrackId == source.providerTrackId }
            if (!exists) {
                sources.add(source)
            }
        }

        fun build(): CanonicalRecording = CanonicalMetadataSelector.selectMetadata(sources, stableSessionId)
    }
}
