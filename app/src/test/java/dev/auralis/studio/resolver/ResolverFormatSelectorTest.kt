package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.AudioQualityPreference
import dev.auralis.studio.resolver.api.DeliveryRequirements
import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DrmState
import dev.auralis.studio.resolver.api.MediaKind
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolvedFormat
import dev.auralis.studio.resolver.api.ResolvedFormatDescriptor
import dev.auralis.studio.resolver.api.ResolverFormatSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ResolverFormatSelectorTest {

    @Test
    fun selectBestDescriptor_rejectsDrmAndNonDirectOrNonPermitted() {
        val descriptors = listOf(
            ResolvedFormatDescriptor(
                formatId = "fmt_drm",
                mediaKind = MediaKind.AUDIO_ONLY,
                bitrateKbps = 320.0,
                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                drmState = DrmState.PRESENT,
                downloadCapability = DownloadCapability.PERMITTED,
            ),
            ResolvedFormatDescriptor(
                formatId = "fmt_segmented",
                mediaKind = MediaKind.AUDIO_ONLY,
                bitrateKbps = 320.0,
                resolutionMode = ResolutionMode.SEGMENTED_RESOURCE,
                drmState = DrmState.NONE,
                downloadCapability = DownloadCapability.PERMITTED,
            ),
            ResolvedFormatDescriptor(
                formatId = "fmt_stream_only",
                mediaKind = MediaKind.AUDIO_ONLY,
                bitrateKbps = 320.0,
                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                drmState = DrmState.NONE,
                downloadCapability = DownloadCapability.STREAM_ONLY,
            ),
            ResolvedFormatDescriptor(
                formatId = "fmt_valid_direct",
                mediaKind = MediaKind.AUDIO_ONLY,
                extension = "m4a",
                bitrateKbps = 256.0,
                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                drmState = DrmState.NONE,
                downloadCapability = DownloadCapability.PERMITTED,
            ),
        )

        val selected = ResolverFormatSelector.selectBestDescriptor(descriptors, AudioQualityPreference.BEST)
        assertNotNull(selected)
        assertEquals("fmt_valid_direct", selected?.formatId)
    }

    @Test
    fun selectBestDescriptor_ranksByAudioOnlyAndQuality() {
        val descriptors = listOf(
            ResolvedFormatDescriptor(
                formatId = "fmt_video_muxed_320",
                mediaKind = MediaKind.MUXED_AUDIO_VIDEO,
                extension = "mp4",
                bitrateKbps = 320.0,
                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                drmState = DrmState.NONE,
                downloadCapability = DownloadCapability.PERMITTED,
            ),
            ResolvedFormatDescriptor(
                formatId = "fmt_audio_128",
                mediaKind = MediaKind.AUDIO_ONLY,
                extension = "m4a",
                bitrateKbps = 128.0,
                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                drmState = DrmState.NONE,
                downloadCapability = DownloadCapability.PERMITTED,
            ),
            ResolvedFormatDescriptor(
                formatId = "fmt_audio_256",
                mediaKind = MediaKind.AUDIO_ONLY,
                extension = "m4a",
                bitrateKbps = 256.0,
                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                drmState = DrmState.NONE,
                downloadCapability = DownloadCapability.PERMITTED,
            ),
        )

        val best = ResolverFormatSelector.selectBestDescriptor(descriptors, AudioQualityPreference.BEST)
        assertEquals("fmt_audio_256", best?.formatId)

        val low = ResolverFormatSelector.selectBestDescriptor(descriptors, AudioQualityPreference.LOW)
        assertEquals("fmt_audio_128", low?.formatId)
    }

    @Test
    fun selectBestAudioFormat_rejectsNonPermittedOrSegmentedFormats() {
        val formats = listOf(
            ResolvedFormat(
                formatId = "fmt_segmented",
                url = "https://example.com/stream.m3u8",
                resolutionMode = ResolutionMode.SEGMENTED_RESOURCE,
                downloadCapability = DownloadCapability.PERMITTED,
            ),
            ResolvedFormat(
                formatId = "fmt_stream_only",
                url = "https://example.com/audio.mp3",
                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                downloadCapability = DownloadCapability.STREAM_ONLY,
            ),
            ResolvedFormat(
                formatId = "fmt_unknown",
                url = "https://example.com/audio.mp3",
                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                downloadCapability = DownloadCapability.UNKNOWN,
            ),
        )

        val selected = ResolverFormatSelector.selectBestAudioFormat(formats)
        assertNull("Non-permitted or non-direct formats must be rejected", selected)
    }
}
