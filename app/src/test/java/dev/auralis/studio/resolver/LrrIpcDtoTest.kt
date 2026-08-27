package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.android.LrrIpcParser
import dev.auralis.studio.resolver.api.MediaKind
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
                    "schemaVersion": 1,
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
                            "mediaKind": "AUDIO_ONLY",
                            "container": "mp3",
                            "extension": "mp3",
                            "audioCodec": "mp3",
                            "videoCodec": null,
                            "bitrateKbps": 128.0,
                            "sampleRateHz": 44100,
                            "fileSizeBytes": 3360000,
                            "protocol": "https",
                            "resolutionMode": "DIRECT_RESOURCE",
                            "drmState": "NONE"
                        }
                    ],
                    "rawFormats": [
                        {
                            "formatId": "http_mp3",
                            "url": "https://cf-media.sndcdn.com/stream.mp3?token=signed",
                            "extension": "mp3",
                            "resolutionMode": "DIRECT_RESOURCE",
                            "isAudioOnly": true
                        }
                    ]
                },
                "lease": {
                    "requestId": "req-1",
                    "formatId": "http_mp3",
                    "url": "https://cf-media.sndcdn.com/stream.mp3?token=signed",
                    "headers": {"User-Agent": "Lyrune"},
                    "resolvedAtElapsedRealtimeMs": 1000,
                    "resolutionMode": "DIRECT_RESOURCE",
                    "mediaKind": "AUDIO_ONLY"
                }
            }
        """.trimIndent()

        val result = LrrIpcParser.parseIpcResponse(successJson, "https://soundcloud.com/artist/track")
        assertTrue(result is ResolverResult.Success)

        val success = result as ResolverResult.Success
        val media = success.media
        assertEquals(1, media.schemaVersion)
        assertEquals("SoundCloud", media.source)
        assertEquals("sc-123456", media.sourceId)
        assertEquals("Nightfall Drive", media.title)
        assertEquals("Synth Artist", media.artist)
        assertEquals(210_000L, media.durationMs)
        assertEquals(1, media.formats.size)

        val fmt = media.formats.first()
        assertEquals("http_mp3", fmt.formatId)
        assertEquals(MediaKind.AUDIO_ONLY, fmt.mediaKind)
        assertEquals(ResolutionMode.DIRECT_RESOURCE, fmt.resolutionMode)

        val lease = success.lease
        assertNotNull(lease)
        assertEquals("https://cf-media.sndcdn.com/stream.mp3?token=signed", lease?.url)
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
