package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.android.LrrIpcParser
import dev.auralis.studio.resolver.api.DrmState
import dev.auralis.studio.resolver.api.MediaKind
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolverErrorCode
import dev.auralis.studio.resolver.api.ResolverResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
                    "schemaVersion": 1,
                    "source": "Bandcamp",
                    "sourceId": "bc-102938",
                    "webpageUrl": "https://artist.bandcamp.com/track/song",
                    "title": "Solar Wind",
                    "artist": "Acoustic Horizon",
                    "album": "Atmospheres",
                    "durationMs": 245000,
                    "artworkUrl": "https://f4.bcbits.com/img/a123_10.jpg",
                    "availability": "AVAILABLE",
                    "formats": [
                        {
                            "formatId": "mp3-128",
                            "mediaKind": "AUDIO_ONLY",
                            "container": "mp3",
                            "extension": "mp3",
                            "audioCodec": "mp3",
                            "videoCodec": null,
                            "bitrateKbps": 128.0,
                            "sampleRateHz": 44100,
                            "channels": 2,
                            "fileSizeBytes": 3920000,
                            "approximateFileSizeBytes": 3920000,
                            "protocol": "https",
                            "resolutionMode": "DIRECT_RESOURCE",
                            "drmState": "NONE",
                            "deliveryRequirements": {
                                "requiresCustomHeaders": true,
                                "requiredSafeHeaderKeys": ["User-Agent", "Accept"],
                                "requiresBrowserImpersonation": false,
                                "requiresPostRequestData": false,
                                "isSegmented": false,
                                "hasDrm": false
                            },
                            "downloadCapability": "UNKNOWN",
                            "downloadCapabilityBasis": "NONE"
                        },
                        {
                            "formatId": "hls-audio",
                            "mediaKind": "AUDIO_ONLY",
                            "container": "m3u8",
                            "extension": "m3u8",
                            "audioCodec": "aac",
                            "videoCodec": null,
                            "bitrateKbps": 256.0,
                            "sampleRateHz": 48000,
                            "channels": 2,
                            "fileSizeBytes": null,
                            "approximateFileSizeBytes": null,
                            "protocol": "m3u8_native",
                            "resolutionMode": "SEGMENTED_RESOURCE",
                            "drmState": "NONE",
                            "deliveryRequirements": {
                                "requiresCustomHeaders": false,
                                "requiredSafeHeaderKeys": [],
                                "requiresBrowserImpersonation": false,
                                "requiresPostRequestData": false,
                                "isSegmented": true,
                                "hasDrm": false
                            },
                            "downloadCapability": "UNKNOWN",
                            "downloadCapabilityBasis": "NONE"
                        }
                    ]
                },
                "lease": {
                    "requestId": "req-123",
                    "formatId": "mp3-128",
                    "url": "https://t4.bcbits.com/stream/sample.mp3?token=signed&expire=1780000000",
                    "headers": {
                        "User-Agent": "Lyrune",
                        "Accept": "*/*"
                    },
                    "resolvedAtEpochMs": 1780000000000,
                    "knownExpiryEpochMs": 1780000000000,
                    "resolutionMode": "DIRECT_RESOURCE",
                    "mediaKind": "AUDIO_ONLY"
                }
            }
        """.trimIndent()

        val result = LrrIpcParser.parseIpcResponse(fixtureJson, "https://artist.bandcamp.com/track/song")
        assertTrue(result is ResolverResult.Success)

        val success = result as ResolverResult.Success
        val media = success.media
        assertEquals(1, media.schemaVersion)
        assertEquals("Bandcamp", media.source)
        assertEquals("Solar Wind", media.title)
        assertEquals("Acoustic Horizon", media.artist)
        assertEquals("Atmospheres", media.album)
        assertEquals(245_000L, media.durationMs)
        assertEquals(2, media.formats.size)

        val directFormat = media.formats[0]
        assertEquals("mp3-128", directFormat.formatId)
        assertEquals(MediaKind.AUDIO_ONLY, directFormat.mediaKind)
        assertEquals(ResolutionMode.DIRECT_RESOURCE, directFormat.resolutionMode)
        assertEquals(DrmState.NONE, directFormat.drmState)
        assertFalse(directFormat.deliveryRequirements.isSegmented)

        val hlsFormat = media.formats[1]
        assertEquals("hls-audio", hlsFormat.formatId)
        assertEquals(ResolutionMode.SEGMENTED_RESOURCE, hlsFormat.resolutionMode)
        assertTrue(hlsFormat.deliveryRequirements.isSegmented)

        // Verify Ephemeral Lease
        val lease = success.lease
        assertNotNull(lease)
        assertEquals("req-123", lease!!.requestId)
        assertEquals("mp3-128", lease.formatId)
        assertEquals("https://t4.bcbits.com/stream/sample.mp3?token=signed&expire=1780000000", lease.url)
        assertEquals(1780000000000L, lease.knownExpiryEpochMs)
        assertEquals("Lyrune", lease.headers["User-Agent"])
        assertFalse(lease.headers.containsKey("Cookie"))
        assertFalse(lease.headers.containsKey("Authorization"))
    }

    @Test
    fun normalizeYtDlpOutput_doesNotInventMissingArtistOrAlbum() {
        val fixtureJson = """
            {
                "success": true,
                "media": {
                    "schemaVersion": 1,
                    "source": "Generic",
                    "sourceId": "audio_sample",
                    "webpageUrl": "https://example.com/audio_sample.ogg",
                    "title": "audio_sample",
                    "artist": null,
                    "album": null,
                    "durationMs": 12000,
                    "artworkUrl": null,
                    "availability": "AVAILABLE",
                    "formats": [
                        {
                            "formatId": "direct",
                            "mediaKind": "AUDIO_ONLY",
                            "container": "ogg",
                            "extension": "ogg",
                            "audioCodec": "vorbis",
                            "videoCodec": null,
                            "bitrateKbps": 160.0,
                            "sampleRateHz": 44100,
                            "channels": 2,
                            "fileSizeBytes": 240000,
                            "approximateFileSizeBytes": 240000,
                            "protocol": "https",
                            "resolutionMode": "DIRECT_RESOURCE",
                            "drmState": "NONE",
                            "deliveryRequirements": {
                                "requiresCustomHeaders": false,
                                "requiredSafeHeaderKeys": [],
                                "requiresBrowserImpersonation": false,
                                "requiresPostRequestData": false,
                                "isSegmented": false,
                                "hasDrm": false
                            },
                            "downloadCapability": "UNKNOWN",
                            "downloadCapabilityBasis": "NONE"
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

    @Test
    fun unsupportedSchemaVersion_returnsIncompatibleSchemaVersionError() {
        val futureJson = """
            {
                "success": true,
                "media": {
                    "schemaVersion": 2,
                    "source": "FutureProvider",
                    "title": "Song from 2030",
                    "formats": []
                }
            }
        """.trimIndent()

        val result = LrrIpcParser.parseIpcResponse(futureJson, "https://example.com/song")
        assertTrue(result is ResolverResult.Failure)

        val error = (result as ResolverResult.Failure).error
        assertEquals(ResolverErrorCode.IncompatibleSchemaVersion, error.technicalCode)
        assertFalse(error.isRetryable)
        assertTrue(error.safeUserMessage.contains("schema version"))
    }

    @Test
    fun oversizedPayload_returnsPayloadTooLargeError() {
        val giantPadding = "a".repeat(600 * 1024)
        val giantJson = """{"success":true,"media":{"schemaVersion":1,"source":"Test","title":"$giantPadding","formats":[]}}"""

        val result = LrrIpcParser.parseIpcResponse(giantJson, "https://example.com/huge")
        assertTrue(result is ResolverResult.Failure)

        val error = (result as ResolverResult.Failure).error
        assertEquals(ResolverErrorCode.PayloadTooLarge, error.technicalCode)
        assertFalse(error.isRetryable)
    }
}
