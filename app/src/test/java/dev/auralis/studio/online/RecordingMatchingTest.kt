package dev.auralis.studio.online

import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityBasis
import dev.auralis.studio.resolver.api.ResolutionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMatchingTest {

    private fun createSource(
        provider: String = "audius",
        id: String = "track1",
        title: String = "Rolling in the Deep",
        artist: String = "Adele",
        album: String = "21",
        durationMs: Long = 228_000L,
        isrc: String? = "GBBKS1000300",
        playbackCapability: PlaybackCapability = PlaybackCapability.FULL_STREAM,
        downloadCapability: DownloadCapability = DownloadCapability.PERMITTED,
    ): RecordingSource = OnlineRecordingSource(
        provider = provider,
        providerTrackId = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        isrc = isrc,
        playbackCapability = playbackCapability,
        downloadCapability = downloadCapability,
        downloadCapabilityBasis = DownloadCapabilityBasis.PROVIDER_EXPLICIT,
        resolutionMode = ResolutionMode.DIRECT_RESOURCE,
    )

    @Test
    fun testA_sameIsrcAcrossMultipleProviders_mergesIntoSingleCanonicalRecording() {
        val s1 = createSource(provider = "audius", id = "a1", isrc = "USUM71703861", durationMs = 210_000L)
        val s2 = createSource(provider = "jamendo", id = "j1", isrc = "USUM71703861", durationMs = 211_000L)
        val s3 = createSource(provider = "itunes", id = "it1", isrc = "USUM71703861", durationMs = 210_000L, playbackCapability = PlaybackCapability.PREVIEW)

        val merged = UnifiedTrackMerger.mergeSources(listOf(s1, s2, s3))

        assertEquals("Should merge into exactly 1 canonical recording", 1, merged.size)
        val canonical = merged.first()
        assertEquals("Should preserve all 3 candidate sources", 3, canonical.sources.size)
        assertEquals("USUM71703861", canonical.isrc)
        assertEquals(3, canonical.sourceCount)
    }

    @Test
    fun testB_sameTitleArtistCloseDuration_mergesWithoutIsrc() {
        val s1 = createSource(provider = "audius", id = "a1", isrc = null, title = "Shape of You", artist = "Ed Sheeran", durationMs = 233_000L)
        val s2 = createSource(provider = "jamendo", id = "j1", isrc = null, title = "shape of you", artist = "ed sheeran", durationMs = 234_000L)

        val merged = UnifiedTrackMerger.mergeSources(listOf(s1, s2))

        assertEquals("Should merge into 1 recording", 1, merged.size)
        assertEquals(2, merged.first().sources.size)
    }

    @Test
    fun testC_sameTitleArtist_originalVsLive_blocksMerge() {
        val s1 = createSource(provider = "audius", id = "a1", title = "Hotel California", artist = "Eagles", durationMs = 390_000L, isrc = null)
        val s2 = createSource(provider = "jamendo", id = "j1", title = "Hotel California (Live at The Forum)", artist = "Eagles", durationMs = 410_000L, isrc = null)

        val merged = UnifiedTrackMerger.mergeSources(listOf(s1, s2))

        assertEquals("Live qualifier conflict MUST produce 2 distinct recordings", 2, merged.size)
    }

    @Test
    fun testD_sameTitleArtist_originalVsRemix_blocksMerge() {
        val s1 = createSource(provider = "audius", id = "a1", title = "Blinding Lights", artist = "The Weeknd", durationMs = 200_000L, isrc = null)
        val s2 = createSource(provider = "jamendo", id = "j1", title = "Blinding Lights (Major Lazer Remix)", artist = "The Weeknd", durationMs = 210_000L, isrc = null)

        val merged = UnifiedTrackMerger.mergeSources(listOf(s1, s2))

        assertEquals("Remix qualifier conflict MUST produce 2 distinct recordings", 2, merged.size)
    }

    @Test
    fun testE_sameTitleArtist_conflictingTrustedIsrc_blocksMerge() {
        val s1 = createSource(provider = "audius", id = "a1", title = "Hello", artist = "Adele", isrc = "GBBKS1500214")
        val s2 = createSource(provider = "itunes", id = "it1", title = "Hello", artist = "Adele", isrc = "USRC17607839") // Different valid ISRC

        val merged = UnifiedTrackMerger.mergeSources(listOf(s1, s2))

        assertEquals("Conflicting valid ISRCs MUST block merge into 2 recordings", 2, merged.size)
    }

    @Test
    fun testF_sameTitle_differentArtists_blocksMerge() {
        val s1 = createSource(provider = "audius", id = "a1", title = "Hello", artist = "Adele", isrc = null)
        val s2 = createSource(provider = "jamendo", id = "j1", title = "Hello", artist = "Lionel Richie", isrc = null)

        val merged = UnifiedTrackMerger.mergeSources(listOf(s1, s2))

        assertEquals("Different artists MUST block merge", 2, merged.size)
    }

    @Test
    fun testG_preview30sAndFull220s_strongEvidence_mergesWithCanonicalFullDuration() {
        val full = createSource(provider = "audius", id = "a1", title = "Someone Like You", artist = "Adele", durationMs = 285_000L, playbackCapability = PlaybackCapability.FULL_STREAM)
        val preview = createSource(provider = "itunes", id = "it1", title = "Someone Like You", artist = "Adele", durationMs = 30_000L, playbackCapability = PlaybackCapability.PREVIEW)

        val merged = UnifiedTrackMerger.mergeSources(listOf(preview, full))

        assertEquals("Should merge into 1 recording", 1, merged.size)
        val canonical = merged.first()
        assertEquals("Canonical duration MUST prefer full track 285s over preview 30s", 285_000L, canonical.durationMs)
        assertEquals("Both sources preserved", 2, canonical.sources.size)
        assertEquals("Audius full stream must be selected for playback", "audius", canonical.bestPlaybackSource?.provider)
    }

    @Test
    fun testH_preview30sAndUnrelated220s_weakTitleOnly_differentDuration_blocksMerge() {
        // Different version / unrelated song with drastically different duration and no ISRC
        val s1 = createSource(provider = "audius", id = "a1", title = "Track Name", artist = "Artist A", durationMs = 180_000L, isrc = null, playbackCapability = PlaybackCapability.FULL_STREAM)
        val s2 = createSource(provider = "itunes", id = "it1", title = "Track Name", artist = "Artist B", durationMs = 30_000L, isrc = null, playbackCapability = PlaybackCapability.PREVIEW)

        val merged = UnifiedTrackMerger.mergeSources(listOf(s1, s2))

        assertEquals("Different artists MUST NOT merge", 2, merged.size)
    }

    @Test
    fun testI_transitiveTrap_preventFalseTransitiveMerge() {
        // A matches B, but C conflicts with A
        val sA = createSource(provider = "audius", id = "a1", title = "Stay", artist = "Kid Laroi", isrc = "USSM12104976")
        val sB = createSource(provider = "jamendo", id = "j1", title = "Stay", artist = "Kid Laroi", isrc = "USSM12104976")
        val sC = createSource(provider = "itunes", id = "it1", title = "Stay", artist = "Rihanna", isrc = "DEF012345678") // Conflicting artist + ISRC

        val merged = UnifiedTrackMerger.mergeSources(listOf(sA, sB, sC))

        assertEquals("Must produce 2 distinct groups", 2, merged.size)
        val kidLaroiGroup = merged.first { it.artist.contains("Kid Laroi", ignoreCase = true) }
        assertEquals(2, kidLaroiGroup.sources.size)
        assertFalse("Rihanna source must NOT enter Kid Laroi group", kidLaroiGroup.sources.any { it.artist.contains("Rihanna") })
    }

    @Test
    fun testJ_groupGetsIsrcLater_preservesStableSessionId() {
        val initialSource = createSource(provider = "jamendo", id = "j1", isrc = null, title = "Bad Guy", artist = "Billie Eilish")
        val initialMerged = UnifiedTrackMerger.mergeSources(listOf(initialSource))
        assertEquals(1, initialMerged.size)
        val initialGroup = initialMerged.first()
        val stableId = initialGroup.stableSessionId

        // Later, stronger ISRC evidence arrives
        val laterSourceWithIsrc = createSource(provider = "itunes", id = "it1", isrc = "USUM71900764", title = "Bad Guy", artist = "Billie Eilish")
        val updatedMerged = UnifiedTrackMerger.mergeSources(listOf(laterSourceWithIsrc), initialMerged)

        assertEquals(1, updatedMerged.size)
        val updatedGroup = updatedMerged.first()
        assertEquals("Stable UI session ID MUST NOT change when stronger evidence arrives!", stableId, updatedGroup.stableSessionId)
        assertEquals("USUM71900764", updatedGroup.isrc)
        assertEquals(2, updatedGroup.sources.size)
    }

    @Test
    fun testK_sameIsrc_originalVsLive_blocksMerge() {
        val original = createSource(provider = "audius", id = "a1", title = "Hotel California", artist = "Eagles", isrc = "USEE17600001")
        val live = createSource(provider = "jamendo", id = "j1", title = "Hotel California (Live)", artist = "Eagles", isrc = "USEE17600001")

        val merged = UnifiedTrackMerger.mergeSources(listOf(original, live))

        assertEquals("Same ISRC with conflicting ORIGINAL vs LIVE MUST produce 2 distinct recordings", 2, merged.size)
    }

    @Test
    fun testL_sameIsrc_originalVsRemix_blocksMerge() {
        val original = createSource(provider = "audius", id = "a1", title = "Blinding Lights", artist = "The Weeknd", isrc = "USUM71900001")
        val remix = createSource(provider = "jamendo", id = "j1", title = "Blinding Lights (Remix)", artist = "The Weeknd", isrc = "USUM71900001")

        val merged = UnifiedTrackMerger.mergeSources(listOf(original, remix))

        assertEquals("Same ISRC with conflicting ORIGINAL vs REMIX MUST produce 2 distinct recordings", 2, merged.size)
    }

    @Test
    fun testM_sameIsrc_liveVsLive_merges() {
        val live1 = createSource(provider = "audius", id = "a1", title = "Layla (Live)", artist = "Eric Clapton", isrc = "USPR19200001")
        val live2 = createSource(provider = "itunes", id = "i1", title = "Layla (Live at Wembley)", artist = "Eric Clapton", isrc = "USPR19200001", playbackCapability = PlaybackCapability.PREVIEW)

        val merged = UnifiedTrackMerger.mergeSources(listOf(live1, live2))

        assertEquals("Same ISRC with compatible LIVE qualifiers MUST merge into 1 recording", 1, merged.size)
        assertEquals(2, merged.first().sources.size)
    }

    @Test
    fun testN_isrcValidation_strictIso3901() {
        // Valid ISRCs
        assertEquals("USUM71703861", RecordingIdentity.normalizedIsrc("US-UM7-17-03861"))
        assertEquals("GBBKS1000300", RecordingIdentity.normalizedIsrc("GBBKS1000300"))
        assertEquals("USEE17600001", RecordingIdentity.normalizedIsrc("USEE17600001"))

        // Invalid ISRCs: must return null
        org.junit.Assert.assertNull("All numeric without country code rejected", RecordingIdentity.normalizedIsrc("123456789012"))
        org.junit.Assert.assertNull("Arbitrary text rejected", RecordingIdentity.normalizedIsrc("AUDIUS12345"))
        org.junit.Assert.assertNull("Too short rejected", RecordingIdentity.normalizedIsrc("USUM717"))
        org.junit.Assert.assertNull("Too long rejected", RecordingIdentity.normalizedIsrc("USUM717038619999"))
        org.junit.Assert.assertNull("Empty rejected", RecordingIdentity.normalizedIsrc(""))
        org.junit.Assert.assertNull("Null rejected", RecordingIdentity.normalizedIsrc(null))
    }

    @Test
    fun testO_releaseDisplayNoise_mergesIntoSingleRecording() {
        val s1 = createSource(provider = "audius", id = "a1", title = "Hello", artist = "Adele", isrc = null)
        val s2 = createSource(provider = "jamendo", id = "j1", title = "Hello (Official Video)", artist = "Adele", isrc = null)

        val merged = UnifiedTrackMerger.mergeSources(listOf(s1, s2))

        assertEquals("Display noise '(Official Video)' should not prevent merging into 1 recording", 1, merged.size)
        assertEquals(2, merged.first().sources.size)
    }
}
