package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolvedFormat
import dev.auralis.studio.resolver.api.ResolvedMedia
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedMediaTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun resolvedMedia_serializationRoundTrip_matchesContract() {
        val format = ResolvedFormat(
            formatId = "audio-1",
            url = "https://cdn.example.com/stream.m4a?token=abc",
            extension = "m4a",
            mimeType = "audio/mp4",
            audioCodec = "mp4a.40.2",
            videoCodec = null,
            bitrateKbps = 256,
            sampleRateHz = 44100,
            fileSizeBytes = 5_000_000L,
            protocol = "https",
            httpHeaders = mapOf("User-Agent" to "Lyrune/1.0"),
            expiresAtEpochMs = 1780000000000L,
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            isAudioOnly = true,
        )

        val media = ResolvedMedia(
            source = "Jamendo",
            sourceId = "track-999",
            webpageUrl = "https://jamendo.com/track/999",
            title = "Ambient Echoes",
            artist = "Solar Drift",
            album = "Cosmic Waves",
            durationMs = 185_000L,
            artworkUrl = "https://cdn.example.com/art.jpg",
            formats = listOf(format),
        )

        val encoded = json.encodeToString(ResolvedMedia.serializer(), media)
        val decoded = json.decodeFromString(ResolvedMedia.serializer(), encoded)

        assertEquals("Jamendo", decoded.source)
        assertEquals("track-999", decoded.sourceId)
        assertEquals("Ambient Echoes", decoded.title)
        assertEquals("Solar Drift", decoded.artist)
        assertEquals("Cosmic Waves", decoded.album)
        assertEquals(185_000L, decoded.durationMs)
        assertEquals(1, decoded.formats.size)

        val decodedFormat = decoded.formats.first()
        assertEquals("audio-1", decodedFormat.formatId)
        assertEquals("https://cdn.example.com/stream.m4a?token=abc", decodedFormat.url)
        assertEquals(ResolutionMode.DIRECT_RESOURCE, decodedFormat.resolutionMode)
        assertTrue(decodedFormat.isAudioOnly)
        assertEquals(1780000000000L, decodedFormat.expiresAtEpochMs)
    }

    @Test
    fun resolvedMedia_doesNotForceNonexistentArtistOrAlbum() {
        val media = ResolvedMedia(
            source = "Direct",
            webpageUrl = "https://example.com/raw.mp3",
            title = "raw",
            artist = null,
            album = null,
            durationMs = 60_000L,
        )

        assertNull(media.artist)
        assertNull(media.album)
        assertEquals("raw", media.title)
    }

    @Test
    fun resolutionMode_identifiesSegmentedAndEngineRequiredResources() {
        val hlsFormat = ResolvedFormat(
            url = "https://example.com/master.m3u8",
            resolutionMode = ResolutionMode.SEGMENTED_RESOURCE,
        )
        val engineFormat = ResolvedFormat(
            url = "https://example.com/complex.dash",
            resolutionMode = ResolutionMode.ENGINE_REQUIRED,
        )

        assertEquals(ResolutionMode.SEGMENTED_RESOURCE, hlsFormat.resolutionMode)
        assertEquals(ResolutionMode.ENGINE_REQUIRED, engineFormat.resolutionMode)
    }
}
