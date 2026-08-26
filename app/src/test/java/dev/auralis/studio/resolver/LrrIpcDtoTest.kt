package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.android.LrrIpcParser
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolverErrorCode
import dev.auralis.studio.resolver.api.ResolverResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrrIpcDtoTest {

    @Test
    fun parseIpcResponse_validSuccessJson_producesResolvedMedia() {
        val successJson = """
            {
                "success": true,
                "media": {
                    "source": "SoundCloud",
                    "sourceId": "sc-123456",
                    "webpageUrl": "https://soundcloud.com/artist/track",
                    "title": "Nightfall Drive",
                    "artist": "Synth Artist",
                    "album": null,
                    "durationMs": 210000,
                    "artworkUrl": "https://i1.sndcdn.com/art.jpg",
                    "formats": [
                        {
                            "formatId": "http_mp3",
                            "url": "https://cf-media.sndcdn.com/stream.mp3?token=signed",
                            "extension": "mp3",
                            "mimeType": "audio/mpeg",
                            "audioCodec": "mp3",
                            "videoCodec": null,
                            "bitrateKbps": 128,
                            "sampleRateHz": 44100,
                            "fileSizeBytes": 3360000,
                            "protocol": "https",
                            "httpHeaders": {"User-Agent": "Lyrune"},
                            "expiresAtEpochMs": 1780000000000,
                            "resolutionMode": "DIRECT_RESOURCE",
                            "isAudioOnly": true
                        }
                    ]
                }
            }
        """.trimIndent()

        val result = LrrIpcParser.parseIpcResponse(successJson, "https://soundcloud.com/artist/track")
        assertTrue(result is ResolverResult.Success)

        val media = (result as ResolverResult.Success).media
        assertEquals("SoundCloud", media.source)
        assertEquals("sc-123456", media.sourceId)
        assertEquals("Nightfall Drive", media.title)
        assertEquals("Synth Artist", media.artist)
        assertEquals(210_000L, media.durationMs)
        assertEquals(1, media.formats.size)

        val fmt = media.formats.first()
        assertEquals("http_mp3", fmt.formatId)
        assertEquals("https://cf-media.sndcdn.com/stream.mp3?token=signed", fmt.url)
        assertEquals("audio/mpeg", fmt.mimeType)
        assertEquals(ResolutionMode.DIRECT_RESOURCE, fmt.resolutionMode)
        assertTrue(fmt.isAudioOnly)
    }

    @Test
    fun parseIpcResponse_errorJson_producesStructuredFailure() {
        val errorJson = """
            {
                "success": false,
                "error": {
                    "source": "https://example.com/blocked",
                    "technicalCode": "AccessRestricted",
                    "isRetryable": false,
                    "safeUserMessage": "Access is restricted in this region.",
                    "debugDetails": "GeoRestrictedError: Not available in US"
                }
            }
        """.trimIndent()

        val result = LrrIpcParser.parseIpcResponse(errorJson, "https://example.com/blocked")
        assertTrue(result is ResolverResult.Failure)

        val error = (result as ResolverResult.Failure).error
        assertEquals("https://example.com/blocked", error.source)
        assertEquals(ResolverErrorCode.AccessRestricted, error.technicalCode)
        assertEquals("Access is restricted in this region.", error.safeUserMessage)
        assertEquals("GeoRestrictedError: Not available in US", error.debugDetails)
    }

    @Test
    fun parseIpcResponse_malformedJson_returnsFailureGracefully() {
        val malformedJson = "{ not-valid-json "
        val result = LrrIpcParser.parseIpcResponse(malformedJson, "https://example.com/broken")

        assertTrue(result is ResolverResult.Failure)
        val error = (result as ResolverResult.Failure).error
        assertEquals(ResolverErrorCode.Unknown, error.technicalCode)
        assertNotNull(error.debugDetails)
    }
}
