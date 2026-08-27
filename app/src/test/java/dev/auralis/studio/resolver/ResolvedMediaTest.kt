package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.DrmState
import dev.auralis.studio.resolver.api.MediaKind
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolvedFormat
import dev.auralis.studio.resolver.api.ResolvedFormatDescriptor
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
        val format = ResolvedFormatDescriptor(
            formatId = "audio-1",
            mediaKind = MediaKind.AUDIO_ONLY,
            container = "m4a",
            extension = "m4a",
            audioCodec = "mp4a.40.2",
            videoCodec = null,
            bitrateKbps = 256.0,
            sampleRateHz = 44100,
            fileSizeBytes = 5_000_000L,
            protocol = "https",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            drmState = DrmState.NONE,
        )

        val rawFormat = ResolvedFormat(
            formatId = "audio-1",
            url = "https://cdn.example.com/stream.m4a?token=abc",
            extension = "m4a",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            isAudioOnly = true,
        )

        val media = ResolvedMedia(
            schemaVersion = 1,
            source = "Jamendo",
            sourceId = "track-999",
            webpageUrl = "https://jamendo.com/track/999",
            title = "Ambient Echoes",
            artist = "Solar Drift",
            album = "Cosmic Waves",
            durationMs = 185_000L,
            artworkUrl = "https://cdn.example.com/art.jpg",
            formats = listOf(format),
            rawFormats = listOf(rawFormat),
        )

        val encoded = json.encodeToString(ResolvedMedia.serializer(), media)
        val decoded = json.decodeFromString(ResolvedMedia.serializer(), encoded)

        assertEquals(1, decoded.schemaVersion)
        assertEquals("Jamendo", decoded.source)
        assertEquals("track-999", decoded.sourceId)
        assertEquals("Ambient Echoes", decoded.title)
        assertEquals("Solar Drift", decoded.artist)
        assertEquals("Cosmic Waves", decoded.album)
        assertEquals(185_000L, decoded.durationMs)
        assertEquals(1, decoded.formats.size)

        val decodedFormat = decoded.formats.first()
        assertEquals("audio-1", decodedFormat.formatId)
        assertEquals(MediaKind.AUDIO_ONLY, decodedFormat.mediaKind)
        assertEquals(ResolutionMode.DIRECT_RESOURCE, decodedFormat.resolutionMode)
        assertEquals(DrmState.NONE, decodedFormat.drmState)
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
        val hlsFormat = ResolvedFormatDescriptor(
            formatId = "hls-1",
            protocol = "m3u8",
            resolutionMode = ResolutionMode.SEGMENTED_RESOURCE,
        )
        val engineFormat = ResolvedFormatDescriptor(
            formatId = "dash-1",
            protocol = "dash",
            resolutionMode = ResolutionMode.ENGINE_REQUIRED,
        )

        assertEquals(ResolutionMode.SEGMENTED_RESOURCE, hlsFormat.resolutionMode)
        assertEquals(ResolutionMode.ENGINE_REQUIRED, engineFormat.resolutionMode)
    }
}
