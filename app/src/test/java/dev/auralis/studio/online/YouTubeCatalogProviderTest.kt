package dev.auralis.studio.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeCatalogProviderTest {

    @Test
    fun unconfiguredProvider_returnsNotConfiguredWithoutCrashing() = runBlocking {
        val provider = YouTubeCatalogProvider(apiKey = "", enableWebFallback = false)
        assertFalse("Provider without API key and web fallback disabled must not be configured", provider.isConfigured)

        val result = provider.search("Bohemian Rhapsody")
        assertTrue("Unconfigured search must return ProviderResult.NotConfigured", result is ProviderResult.NotConfigured)
    }

    @Test
    fun providerWithWebFallback_isAlwaysConfigured() {
        val provider = YouTubeCatalogProvider(apiKey = "", enableWebFallback = true)
        assertTrue("Provider with web fallback must be configured", provider.isConfigured)
    }

    @Test
    fun parseClockDurationMs_handlesVariousFormats() {
        assertEquals(225_000L, YouTubeCatalogProvider.parseClockDurationMs("3:45"))
        assertEquals(3_730_000L, YouTubeCatalogProvider.parseClockDurationMs("1:02:10"))
        assertEquals(45_000L, YouTubeCatalogProvider.parseClockDurationMs("45"))
        assertEquals(0L, YouTubeCatalogProvider.parseClockDurationMs(""))
        assertEquals(0L, YouTubeCatalogProvider.parseClockDurationMs("invalid"))
    }

    @Test
    fun parseIsoDurationMs_handlesVariousStandardFormats() {
        assertEquals(225_000L, YouTubeCatalogProvider.parseIsoDurationMs("PT3M45S"))
        assertEquals(3_730_000L, YouTubeCatalogProvider.parseIsoDurationMs("PT1H2M10S"))
        assertEquals(45_000L, YouTubeCatalogProvider.parseIsoDurationMs("PT45S"))
        assertEquals(60_000L, YouTubeCatalogProvider.parseIsoDurationMs("PT1M"))
        assertEquals(7_200_000L, YouTubeCatalogProvider.parseIsoDurationMs("PT2H"))
        assertEquals(0L, YouTubeCatalogProvider.parseIsoDurationMs(""))
        assertEquals(0L, YouTubeCatalogProvider.parseIsoDurationMs("invalid"))
    }

    @Test
    fun unescapeHtml_decodesCommonHtmlEntities() {
        val input = "Queen &amp; David Bowie - Under Pressure &#39;Official Video&#39; &quot;HD&quot; &lt;Rock&gt;"
        val expected = "Queen & David Bowie - Under Pressure 'Official Video' \"HD\" <Rock>"
        assertEquals(expected, YouTubeCatalogProvider.unescapeHtml(input))
    }

    @Test
    fun canonicalWatchUrl_isAlwaysConstructedAndNeverGooglevideo() {
        val videoId = "fJ9rUzIMcZQ"
        val track = OnlineTrack(
            provider = "youtube",
            id = videoId,
            title = "Bohemian Rhapsody",
            artist = "Queen Official",
            sourceUrl = "https://www.youtube.com/watch?v=$videoId",
            streamAllowed = false,
            downloadAllowed = false,
        )

        assertEquals("https://www.youtube.com/watch?v=fJ9rUzIMcZQ", track.sourceUrl)
        assertFalse("Source URL must never be a temporary googlevideo URL", track.sourceUrl.contains("googlevideo.com"))
        assertFalse("Search must not grant download permission automatically", track.downloadAllowed)
        assertFalse("Search must not grant stream permission automatically", track.streamAllowed)
    }

    @Test
    fun canonicalRecordingMerge_keepsLiveAndRemixVariantsSeparate() {
        val original = OnlineTrack(
            provider = "youtube",
            id = "vid_orig",
            title = "Hotel California",
            artist = "Eagles",
            sourceUrl = "https://www.youtube.com/watch?v=vid_orig",
        )

        val live = OnlineTrack(
            provider = "youtube",
            id = "vid_live",
            title = "Hotel California (Live at the Forum)",
            artist = "Eagles",
            sourceUrl = "https://www.youtube.com/watch?v=vid_live",
        )

        val remix = OnlineTrack(
            provider = "youtube",
            id = "vid_remix",
            title = "Hotel California (Club Remix)",
            artist = "Eagles",
            sourceUrl = "https://www.youtube.com/watch?v=vid_remix",
        )

        val origProfile = VersionProfile.parse(original.title)
        val liveProfile = VersionProfile.parse(live.title)
        val remixProfile = VersionProfile.parse(remix.title)

        assertTrue(origProfile.isOriginal)
        assertFalse(origProfile.isCompatibleWith(liveProfile))
        assertFalse(origProfile.isCompatibleWith(remixProfile))
        assertFalse(liveProfile.isCompatibleWith(remixProfile))
    }
}
