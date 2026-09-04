package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityBasis
import dev.auralis.studio.resolver.api.DownloadCapabilityEvaluator
import dev.auralis.studio.resolver.api.DrmState
import dev.auralis.studio.resolver.api.RecognizedDownloadLicense
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolvedFormat
import dev.auralis.studio.resolver.api.MediaKind
import dev.auralis.studio.resolver.api.ResolvedMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun youtube_evaluatesToPermitted_forDownload() {
        val eval = DownloadCapabilityEvaluator.evaluate(
            source = "Youtube",
            webpageUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        )

        assertEquals(DownloadCapability.PERMITTED, eval.capability)
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
    fun audiusAndJamendo_evaluateToProviderExplicit() {
        val audiusEval = DownloadCapabilityEvaluator.evaluate(
            source = "Audius",
            webpageUrl = "https://audius.co/artist/track-123",
        )
        assertEquals(DownloadCapability.PERMITTED, audiusEval.capability)
        assertEquals(DownloadCapabilityBasis.PROVIDER_EXPLICIT, audiusEval.basis)

        val jamendoEval = DownloadCapabilityEvaluator.evaluate(
            source = "Jamendo",
            webpageUrl = "https://www.jamendo.com/track/456",
        )
        assertEquals(DownloadCapability.PERMITTED, jamendoEval.capability)
        assertEquals(DownloadCapabilityBasis.PROVIDER_EXPLICIT, jamendoEval.basis)
    }

    @Test
    fun strictLicenseModel_knownTrustedMediaLicenses_evaluateToPermitted() {
        val cc0Eval = DownloadCapabilityEvaluator.evaluate(
            source = "ArchiveOrg",
            webpageUrl = "https://archive.org/details/test_cc0",
            license = "CC0 1.0",
        )
        assertEquals(DownloadCapability.PERMITTED, cc0Eval.capability)
        assertEquals(DownloadCapabilityBasis.LICENSE_METADATA, cc0Eval.basis)
        assertEquals(RecognizedDownloadLicense.CC0, cc0Eval.recognizedLicense)

        val cc0UrlEval = DownloadCapabilityEvaluator.evaluate(
            source = "ArchiveOrg",
            webpageUrl = "https://archive.org/details/test_cc0",
            licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
        )
        assertEquals(DownloadCapability.PERMITTED, cc0UrlEval.capability)
        assertEquals(DownloadCapabilityBasis.LICENSE_METADATA, cc0UrlEval.basis)
        assertEquals(RecognizedDownloadLicense.CC0, cc0UrlEval.recognizedLicense)

        val pdEval = DownloadCapabilityEvaluator.evaluate(
            source = "PublicDomainTracks",
            webpageUrl = "https://example.org/pd.mp3",
            license = "Public Domain",
        )
        assertEquals(DownloadCapability.PERMITTED, pdEval.capability)
        assertEquals(DownloadCapabilityBasis.LICENSE_METADATA, pdEval.basis)
        assertEquals(RecognizedDownloadLicense.PUBLIC_DOMAIN, pdEval.recognizedLicense)

        val ccByEval = DownloadCapabilityEvaluator.evaluate(
            source = "OpenAudio",
            webpageUrl = "https://example.org/audio",
            license = "CC BY 4.0",
        )
        assertEquals(DownloadCapability.PERMITTED, ccByEval.capability)
        assertEquals(DownloadCapabilityBasis.LICENSE_METADATA, ccByEval.basis)
        assertEquals(RecognizedDownloadLicense.CC_BY, ccByEval.recognizedLicense)

        val ccBySaUrlEval = DownloadCapabilityEvaluator.evaluate(
            source = "OpenAudio",
            webpageUrl = "https://example.org/audio",
            licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
        )
        assertEquals(DownloadCapability.PERMITTED, ccBySaUrlEval.capability)
        assertEquals(DownloadCapabilityBasis.LICENSE_METADATA, ccBySaUrlEval.basis)
        assertEquals(RecognizedDownloadLicense.CC_BY_SA, ccBySaUrlEval.recognizedLicense)
    }

    @Test
    fun ccVariantsWithRestrictions_evaluateToUnknown() {
        val ncEval = DownloadCapabilityEvaluator.evaluate(
            source = "OpenAudio",
            webpageUrl = "https://example.org/audio",
            license = "CC BY-NC 4.0",
        )
        assertEquals("CC BY-NC must evaluate to UNKNOWN until restrictions are modeled", DownloadCapability.UNKNOWN, ncEval.capability)
        assertEquals(DownloadCapabilityBasis.NONE, ncEval.basis)
        assertEquals(RecognizedDownloadLicense.CC_BY_NC, ncEval.recognizedLicense)

        val ncSaEval = DownloadCapabilityEvaluator.evaluate(
            source = "OpenAudio",
            webpageUrl = "https://example.org/audio",
            license = "CC BY-NC-SA 4.0",
        )
        assertEquals("CC BY-NC-SA must evaluate to UNKNOWN until restrictions are modeled", DownloadCapability.UNKNOWN, ncSaEval.capability)
        assertEquals(DownloadCapabilityBasis.NONE, ncSaEval.basis)
        assertEquals(RecognizedDownloadLicense.CC_BY_NC_SA, ncSaEval.recognizedLicense)

        val ndEval = DownloadCapabilityEvaluator.evaluate(
            source = "OpenAudio",
            webpageUrl = "https://example.org/audio",
            license = "CC BY-ND 4.0",
        )
        assertEquals("CC BY-ND must evaluate to UNKNOWN until restrictions are modeled", DownloadCapability.UNKNOWN, ndEval.capability)
        assertEquals(DownloadCapabilityBasis.NONE, ndEval.basis)
        assertEquals(RecognizedDownloadLicense.CC_BY_ND, ndEval.recognizedLicense)

        val ncNdEval = DownloadCapabilityEvaluator.evaluate(
            source = "OpenAudio",
            webpageUrl = "https://example.org/audio",
            license = "CC BY-NC-ND 4.0",
        )
        assertEquals("CC BY-NC-ND must evaluate to UNKNOWN until restrictions are modeled", DownloadCapability.UNKNOWN, ncNdEval.capability)
        assertEquals(DownloadCapabilityBasis.NONE, ncNdEval.basis)
        assertEquals(RecognizedDownloadLicense.CC_BY_NC_ND, ncNdEval.recognizedLicense)
    }

    @Test
    fun softwareLicenses_attachedToGenericMedia_evaluateToUnknown() {
        val mitEval = DownloadCapabilityEvaluator.evaluate(
            source = "Generic",
            webpageUrl = "https://example.com/audio.mp3",
            license = "MIT",
            licenseUrl = "https://opensource.org/licenses/MIT",
        )
        assertEquals("Software licenses like MIT must NOT be sufficient for audio download", DownloadCapability.UNKNOWN, mitEval.capability)
        assertEquals(DownloadCapabilityBasis.NONE, mitEval.basis)
        assertEquals(RecognizedDownloadLicense.UNRECOGNIZED, mitEval.recognizedLicense)

        val apacheEval = DownloadCapabilityEvaluator.evaluate(
            source = "Generic",
            webpageUrl = "https://example.com/audio.mp3",
            license = "Apache 2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        )
        assertEquals("Software licenses like Apache-2.0 must NOT be sufficient for audio download", DownloadCapability.UNKNOWN, apacheEval.capability)
        assertEquals(DownloadCapabilityBasis.NONE, apacheEval.basis)
        assertEquals(RecognizedDownloadLicense.UNRECOGNIZED, apacheEval.recognizedLicense)
    }

    @Test
    fun strictLicenseModel_vagueOrUnverifiedClaims_evaluateToUnknown() {
        // "Creative Commons" without specific license specifier
        val vagueCc = DownloadCapabilityEvaluator.evaluate(
            source = "Generic",
            webpageUrl = "https://example.com/audio.mp3",
            license = "Creative Commons",
        )
        assertEquals(DownloadCapability.UNKNOWN, vagueCc.capability)
        assertEquals(DownloadCapabilityBasis.NONE, vagueCc.basis)
        assertEquals(RecognizedDownloadLicense.UNRECOGNIZED, vagueCc.recognizedLicense)

        // "No Copyright"
        val noCopyright = DownloadCapabilityEvaluator.evaluate(
            source = "Generic",
            webpageUrl = "https://example.com/audio.mp3",
            license = "No Copyright",
        )
        assertEquals(DownloadCapability.UNKNOWN, noCopyright.capability)
        assertEquals(DownloadCapabilityBasis.NONE, noCopyright.basis)

        // "Free download"
        val freeDownload = DownloadCapabilityEvaluator.evaluate(
            source = "Generic",
            webpageUrl = "https://example.com/audio.mp3",
            license = "Free download",
        )
        assertEquals(DownloadCapability.UNKNOWN, freeDownload.capability)
        assertEquals(DownloadCapabilityBasis.NONE, freeDownload.basis)

        // "Royalty Free"
        val royaltyFree = DownloadCapabilityEvaluator.evaluate(
            source = "Generic",
            webpageUrl = "https://example.com/audio.mp3",
            license = "Royalty Free",
        )
        assertEquals(DownloadCapability.UNKNOWN, royaltyFree.capability)
        assertEquals(DownloadCapabilityBasis.NONE, royaltyFree.basis)

        // Missing / empty license
        val missing = DownloadCapabilityEvaluator.evaluate(
            source = "Generic",
            webpageUrl = "https://example.com/audio.mp3",
            license = null,
        )
        assertEquals(DownloadCapability.UNKNOWN, missing.capability)
        assertEquals(DownloadCapabilityBasis.NONE, missing.basis)
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
        val permittedFormat = dev.auralis.studio.resolver.api.ResolvedFormatDescriptor(
            formatId = "fmt_direct_permitted",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            downloadCapability = DownloadCapability.PERMITTED,
            downloadCapabilityBasis = DownloadCapabilityBasis.LICENSE_METADATA,
        )

        val streamOnlyFormat = dev.auralis.studio.resolver.api.ResolvedFormatDescriptor(
            formatId = "fmt_stream_only",
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

    @Test
    fun permissionSeparationTruthTable_enforcesAllCombinations() {
        // 1. DIRECT_RESOURCE + UNKNOWN permission -> NOT downloadable
        val directUnknown = dev.auralis.studio.resolver.api.ResolvedFormatDescriptor(
            formatId = "f1",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            downloadCapability = DownloadCapability.UNKNOWN,
            drmState = DrmState.NONE,
        )
        val mediaUnknown = ResolvedMedia(source = "Test", formats = listOf(directUnknown))
        assertFalse("DIRECT_RESOURCE + UNKNOWN must NOT be downloadable", mediaUnknown.hasPermittedDirectDownload)

        // 2. DIRECT_RESOURCE + NOT_PERMITTED -> NOT downloadable
        val directNotPermitted = dev.auralis.studio.resolver.api.ResolvedFormatDescriptor(
            formatId = "f2",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            downloadCapability = DownloadCapability.NOT_PERMITTED,
            drmState = DrmState.NONE,
        )
        val mediaNotPermitted = ResolvedMedia(source = "Test", formats = listOf(directNotPermitted))
        assertFalse("DIRECT_RESOURCE + NOT_PERMITTED must NOT be downloadable", mediaNotPermitted.hasPermittedDirectDownload)

        // 3. DIRECT_RESOURCE + PERMITTED -> downloadable
        val directPermitted = dev.auralis.studio.resolver.api.ResolvedFormatDescriptor(
            formatId = "f3",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            downloadCapability = DownloadCapability.PERMITTED,
            drmState = DrmState.NONE,
        )
        val mediaPermitted = ResolvedMedia(source = "Test", formats = listOf(directPermitted))
        assertTrue("DIRECT_RESOURCE + PERMITTED must be downloadable", mediaPermitted.hasPermittedDirectDownload)

        // 4. SEGMENTED_RESOURCE + PERMITTED -> NOT direct downloadable
        val segmentedPermitted = dev.auralis.studio.resolver.api.ResolvedFormatDescriptor(
            formatId = "f4",
            resolutionMode = ResolutionMode.SEGMENTED_RESOURCE,
            downloadCapability = DownloadCapability.PERMITTED,
            drmState = DrmState.NONE,
        )
        val mediaSegmented = ResolvedMedia(source = "Test", formats = listOf(segmentedPermitted))
        assertFalse("SEGMENTED_RESOURCE + PERMITTED must NOT be direct downloadable", mediaSegmented.hasPermittedDirectDownload)

        // 5. DRM PRESENT + PERMITTED -> NOT downloadable
        val drmPermitted = dev.auralis.studio.resolver.api.ResolvedFormatDescriptor(
            formatId = "f5",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            downloadCapability = DownloadCapability.PERMITTED,
            drmState = DrmState.PRESENT,
        )
        val mediaDrm = ResolvedMedia(source = "Test", formats = listOf(drmPermitted))
        assertFalse("DRM + PERMITTED must NOT be downloadable", mediaDrm.hasPermittedDirectDownload)
    }

    @Test
    fun youtube_annotatedMedia_marksDirectFormatsPermittedForDownload() {
        val rawFormat = dev.auralis.studio.resolver.api.ResolvedFormatDescriptor(
            formatId = "140",
            mediaKind = MediaKind.AUDIO_ONLY,
            container = "m4a",
            extension = "m4a",
            bitrateKbps = 128.0,
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            drmState = DrmState.NONE,
        )
        val media = ResolvedMedia(
            source = "youtube",
            sourceId = "dQw4w9WgXcQ",
            webpageUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            title = "Test Music",
            formats = listOf(rawFormat),
        )

        val annotated = DownloadCapabilityEvaluator.annotateMedia(media)

        assertTrue("YouTube media must report hasPermittedDirectDownload == true", annotated.hasPermittedDirectDownload)
        assertEquals(1, annotated.permittedDirectFormats.size)
        assertEquals(DownloadCapability.PERMITTED, annotated.formats.first().downloadCapability)

        val selected = dev.auralis.studio.resolver.api.ResolverFormatSelector.selectBestDescriptor(
            formats = annotated.formats,
            preference = dev.auralis.studio.resolver.api.AudioQualityPreference.BEST,
            requirePermittedDownload = true,
        )
        assertNotNull("Selector must select permitted direct format for YouTube", selected)
        assertEquals("140", selected?.formatId)
    }
}
