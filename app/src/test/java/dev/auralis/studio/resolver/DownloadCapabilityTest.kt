package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityBasis
import dev.auralis.studio.resolver.api.DownloadCapabilityEvaluator
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolvedFormat
import dev.auralis.studio.resolver.api.ResolvedMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCapabilityTest {

    @Test
    fun genericAudioUrl_defaultsToUnknown_neverPermitted() {
        val eval = DownloadCapabilityEvaluator.evaluate(
            source = "Generic",
            webpageUrl = "https://example.com/song.mp3",
            license = null,
        )

        assertEquals("Generic URL must default to UNKNOWN capability", DownloadCapability.UNKNOWN, eval.capability)
        assertEquals("Generic URL basis must be NONE", DownloadCapabilityBasis.NONE, eval.basis)
    }

    @Test
    fun youtube_evaluatesToStreamOnly_neverPermittedForDownload() {
        val eval = DownloadCapabilityEvaluator.evaluate(
            source = "Youtube",
            webpageUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        )

        assertEquals(DownloadCapability.STREAM_ONLY, eval.capability)
        assertEquals(DownloadCapabilityBasis.PROVIDER_EXPLICIT, eval.basis)
    }

    @Test
    fun itunesAndDeezer_evaluateToNotPermitted() {
        val itunesEval = DownloadCapabilityEvaluator.evaluate(
            source = "iTunes",
            webpageUrl = "https://music.apple.com/us/album/test/123",
        )
        assertEquals(DownloadCapability.NOT_PERMITTED, itunesEval.capability)
        assertEquals(DownloadCapabilityBasis.PROVIDER_EXPLICIT, itunesEval.basis)

        val deezerEval = DownloadCapabilityEvaluator.evaluate(
            source = "Deezer",
            webpageUrl = "https://www.deezer.com/track/456",
        )
        assertEquals(DownloadCapability.NOT_PERMITTED, deezerEval.capability)
        assertEquals(DownloadCapabilityBasis.PROVIDER_EXPLICIT, deezerEval.basis)
    }

    @Test
    fun openLicenseMetadata_evaluatesToPermitted() {
        val ccEval = DownloadCapabilityEvaluator.evaluate(
            source = "ArchiveOrg",
            webpageUrl = "https://archive.org/details/test_track",
            license = "Creative Commons Attribution 4.0 (CC-BY)",
        )
        assertEquals(DownloadCapability.PERMITTED, ccEval.capability)
        assertEquals(DownloadCapabilityBasis.LICENSE_METADATA, ccEval.basis)

        val pdEval = DownloadCapabilityEvaluator.evaluate(
            source = "PublicDomainTracks",
            webpageUrl = "https://example.org/pd.mp3",
            license = "Public Domain / CC0",
        )
        assertEquals(DownloadCapability.PERMITTED, pdEval.capability)
        assertEquals(DownloadCapabilityBasis.LICENSE_METADATA, pdEval.basis)
    }

    @Test
    fun testFixture_evaluatesToPermittedWithTestFixtureBasis() {
        val testEval = DownloadCapabilityEvaluator.evaluate(
            source = "TestHarness",
            webpageUrl = "http://127.0.0.1:8080/test.wav",
            isTestFixture = true,
        )
        assertEquals(DownloadCapability.PERMITTED, testEval.capability)
        assertEquals(DownloadCapabilityBasis.TEST_FIXTURE, testEval.basis)
    }

    @Test
    fun resolvedMedia_derivedDownloadCapability_reflectsFormatPermissions() {
        val permittedFormat = ResolvedFormat(
            formatId = "fmt_direct_permitted",
            url = "https://example.org/cc_audio.m4a",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            downloadCapability = DownloadCapability.PERMITTED,
            downloadCapabilityBasis = DownloadCapabilityBasis.LICENSE_METADATA,
        )

        val streamOnlyFormat = ResolvedFormat(
            formatId = "fmt_stream_only",
            url = "https://rr---sn.googlevideo.com/videoplayback?...",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            downloadCapability = DownloadCapability.STREAM_ONLY,
            downloadCapabilityBasis = DownloadCapabilityBasis.PROVIDER_EXPLICIT,
        )

        val mediaWithPermitted = ResolvedMedia(
            source = "ArchiveOrg",
            title = "Open Audio Track",
            formats = listOf(permittedFormat),
        )
        assertTrue("Media with permitted direct format must report hasPermittedDirectDownload == true", mediaWithPermitted.hasPermittedDirectDownload)
        assertEquals(1, mediaWithPermitted.permittedDirectFormats.size)

        val mediaStreamOnly = ResolvedMedia(
            source = "Youtube",
            title = "YouTube Video",
            formats = listOf(streamOnlyFormat),
        )
        assertFalse("Media with only STREAM_ONLY formats must report hasPermittedDirectDownload == false", mediaStreamOnly.hasPermittedDirectDownload)
        assertTrue(mediaStreamOnly.permittedDirectFormats.isEmpty())
    }
}
