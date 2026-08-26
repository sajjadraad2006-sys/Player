package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.android.LrrIpcParser
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolverResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrrNormalizationTest {

    @Test
    fun normalizeYtDlpOutput_extractsAudioFormatsAndOmitsVideoOnly() {
        val fixtureJson = """
            {
                "success": true,
                "media": {
                    "source": "Bandcamp",
                    "sourceId": "bc-102938",
                    "webpageUrl": "https://artist.bandcamp.com/track/song",
                    "title": "Solar Wind",
                    "artist": "Acoustic Horizon",
                    "album": "Atmospheres",
                    "durationMs": 245000,
                    "artworkUrl": "https://f4.bcbits.com/img/a123_10.jpg",
                    "formats": [
                        {
                            "formatId": "mp3-128",
                            "url": "https://t4.bcbits.com/stream/sample.mp3?token=signed&expire=1780000000",
                            "extension": "mp3",
                            "mimeType": "audio/mpeg",
                            "audioCodec": "mp3",
                            "videoCodec": null,
                            "bitrateKbps": 128,
                            "sampleRateHz": 44100,
                            "fileSizeBytes": 3920000,
                            "protocol": "https",
                            "httpHeaders": {
                                "User-Agent": "Lyrune",
                                "Accept": "*/*"
                            },
                            "expiresAtEpochMs": 1780000000000,
                            "resolutionMode": "DIRECT_RESOURCE",
                            "isAudioOnly": true
                        },
                        {
                            "formatId": "hls-audio",
                            "url": "https://t4.bcbits.com/stream/master.m3u8",
                            "extension": "m3u8",
                            "mimeType": null,
                            "audioCodec": "aac",
                            "videoCodec": null,
                            "bitrateKbps": 256,
                            "sampleRateHz": 48000,
                            "fileSizeBytes": null,
                            "protocol": "m3u8_native",
                            "httpHeaders": {},
                            "expiresAtEpochMs": null,
                            "resolutionMode": "SEGMENTED_RESOURCE",
                            "isAudioOnly": true
                        }
                    ]
                }
            }
        """.trimIndent()

        val result = LrrIpcParser.parseIpcResponse(fixtureJson, "https://artist.bandcamp.com/track/song")
        assertTrue(result is ResolverResult.Success)

        val media = (result as ResolverResult.Success).media
        assertEquals("Bandcamp", media.source)
        assertEquals("Solar Wind", media.title)
        assertEquals("Acoustic Horizon", media.artist)
        assertEquals("Atmospheres", media.album)
        assertEquals(245_000L, media.durationMs)
        assertEquals(2, media.formats.size)

        val directFormat = media.formats[0]
        assertEquals("mp3-128", directFormat.formatId)
        assertEquals(ResolutionMode.DIRECT_RESOURCE, directFormat.resolutionMode)
        assertEquals(1780000000000L, directFormat.expiresAtEpochMs)
        assertFalse(directFormat.httpHeaders.containsKey("Cookie"))
        assertFalse(directFormat.httpHeaders.containsKey("Authorization"))

        val hlsFormat = media.formats[1]
        assertEquals("hls-audio", hlsFormat.formatId)
        assertEquals(ResolutionMode.SEGMENTED_RESOURCE, hlsFormat.resolutionMode)
        assertNull(hlsFormat.expiresAtEpochMs)
    }

    @Test
    fun normalizeYtDlpOutput_doesNotInventMissingArtistOrAlbum() {
        val fixtureJson = """
            {
                "success": true,
                "media": {
                    "source": "Generic",
                    "sourceId": "audio_sample",
                    "webpageUrl": "https://example.com/audio_sample.ogg",
                    "title": "audio_sample",
                    "artist": null,
                    "album": null,
                    "durationMs": 12000,
                    "artworkUrl": null,
                    "formats": [
                        {
                            "formatId": "direct",
                            "url": "https://example.com/audio_sample.ogg",
                            "extension": "ogg",
                            "mimeType": "audio/ogg",
                            "audioCodec": "vorbis",
                            "videoCodec": null,
                            "bitrateKbps": 160,
                            "sampleRateHz": 44100,
                            "fileSizeBytes": 240000,
                            "protocol": "https",
                            "httpHeaders": {},
                            "expiresAtEpochMs": null,
                            "resolutionMode": "DIRECT_RESOURCE",
                            "isAudioOnly": true
                        }
                    ]
                }
            }
        """.trimIndent()

        val result = LrrIpcParser.parseIpcResponse(fixtureJson, "https://example.com/audio_sample.ogg")
        assertTrue(result is ResolverResult.Success)

        val media = (result as ResolverResult.Success).media
        assertNull(media.artist)
        assertNull(media.album)
        assertNull(media.artworkUrl)
        assertEquals("audio_sample", media.title)
    }
}
