package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.AudioQualityPreference
import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolvedFormat
import dev.auralis.studio.resolver.api.ResolverFormatSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ResolverFormatSelectorTest {

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

    @Test
    fun selectBestAudioFormat_prefersAudioOnlyAndMatchesQuality() {
        val formats = listOf(
            ResolvedFormat(
                formatId = "fmt_muxed_video",
                url = "https://example.com/video.mp4",
                extension = "mp4",
                bitrateKbps = 320,
                isAudioOnly = false,
                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                downloadCapability = DownloadCapability.PERMITTED,
            ),
            ResolvedFormat(
                formatId = "fmt_audio_128",
                url = "https://example.com/audio_128.m4a",
                extension = "m4a",
                bitrateKbps = 128,
                isAudioOnly = true,
                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                downloadCapability = DownloadCapability.PERMITTED,
            ),
            ResolvedFormat(
                formatId = "fmt_audio_320",
                url = "https://example.com/audio_320.m4a",
                extension = "m4a",
                bitrateKbps = 320,
                isAudioOnly = true,
                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                downloadCapability = DownloadCapability.PERMITTED,
            ),
        )

        val best = ResolverFormatSelector.selectBestAudioFormat(formats, AudioQualityPreference.BEST)
        assertNotNull(best)
        assertEquals("fmt_audio_320", best?.formatId)

        val low = ResolverFormatSelector.selectBestAudioFormat(formats, AudioQualityPreference.LOW)
        assertNotNull(low)
        assertEquals("fmt_audio_128", low?.formatId)
    }

    @Test
    fun selectBestAudioFormat_fallbackWhenFormatHintNotFound() {
        val formats = listOf(
            ResolvedFormat(
                formatId = "fmt_audio_256",
                url = "https://example.com/audio_256.opus",
                extension = "opus",
                bitrateKbps = 256,
                isAudioOnly = true,
                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                downloadCapability = DownloadCapability.PERMITTED,
            ),
        )

        // Request a formatHint that no longer exists on re-resolution
        val selected = ResolverFormatSelector.selectBestAudioFormat(
            formats = formats,
            preference = AudioQualityPreference.HIGH,
            formatHint = "fmt_obsolete_id",
        )

        assertNotNull("Selector should smoothly fallback to best available eligible format", selected)
        assertEquals("fmt_audio_256", selected?.formatId)
    }
}
