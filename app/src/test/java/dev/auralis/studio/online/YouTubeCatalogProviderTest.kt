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

    @Test
    fun extractTracksFromJsonElement_parsesCompactAndPlaylistPanelRenderers() {
        val sampleJson = """
        {
          "contents": {
            "twoColumnWatchNextResults": {
              "secondaryResults": {
                "secondaryResults": {
                  "results": [
                    {
                      "compactVideoRenderer": {
                        "videoId": "abc12345678",
                        "title": { "simpleText": "Algorithmic Recommended Song" },
                        "shortBylineText": { "runs": [{ "text": "Artist One" }] },
                        "lengthText": { "simpleText": "4:15" }
                      }
                    },
                    {
                      "playlistPanelVideoRenderer": {
                        "videoId": "def87654321",
                        "title": { "runs": [{ "text": "Playlist Radio Track" }] },
                        "longBylineText": { "runs": [{ "text": "Artist Two" }] },
                        "lengthText": { "simpleText": "3:30" }
                      }
                    },
                    {
                      "playlistVideoRenderer": {
                        "videoId": "ghi11223344",
                        "title": { "runs": [{ "text": "Browse Playlist Song" }] },
                        "shortBylineText": { "runs": [{ "text": "Artist Three" }] },
                        "lengthSeconds": "180"
                      }
                    }
                  ]
                }
              }
            }
          }
        }
        """.trimIndent()

        val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(sampleJson)
        val tracks = YouTubeCatalogProvider.extractTracksFromJsonElement(jsonElement)

        assertEquals(3, tracks.size)

        val track1 = tracks[0]
        assertEquals("abc12345678", track1.id)
        assertEquals("Algorithmic Recommended Song", track1.title)
        assertEquals("Artist One", track1.artist)
        assertEquals(255_000L, track1.durationMs)
        assertEquals("https://i.ytimg.com/vi/abc12345678/hqdefault.jpg", track1.artworkUrl)

        val track2 = tracks[1]
        assertEquals("def87654321", track2.id)
        assertEquals("Playlist Radio Track", track2.title)
        assertEquals("Artist Two", track2.artist)
        assertEquals(210_000L, track2.durationMs)

        val track3 = tracks[2]
        assertEquals("ghi11223344", track3.id)
        assertEquals("Browse Playlist Song", track3.title)
        assertEquals("Artist Three", track3.artist)
        assertEquals(180_000L, track3.durationMs)
    }

    @Test
    fun extractPlaylistsFromJsonElement_parsesPlaylistRenderer() {
        val sampleJson = """
        {
          "contents": {
            "sectionListRenderer": {
              "contents": [
                {
                  "itemSectionRenderer": {
                    "contents": [
                      {
                        "playlistRenderer": {
                          "playlistId": "PL1234567890abcdef",
                          "title": { "simpleText": "Top Hits 2026" },
                          "shortBylineText": { "runs": [{ "text": "Music Channel" }] },
                          "videoCount": "42",
                          "thumbnails": [
                            {
                              "thumbnails": [
                                { "url": "https://i.ytimg.com/vi/thumb/hqdefault.jpg" }
                              ]
                            }
                          ]
                        }
                      }
                    ]
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()

        val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(sampleJson)
        val playlists = YouTubeCatalogProvider.extractPlaylistsFromJsonElement(jsonElement)

        assertEquals(1, playlists.size)
        val pl = playlists[0]
        assertEquals("PL1234567890abcdef", pl.id)
        assertEquals("Top Hits 2026", pl.title)
        assertEquals("Music Channel", pl.author)
        assertEquals(42, pl.trackCount)
        assertEquals("https://i.ytimg.com/vi/thumb/hqdefault.jpg", pl.artworkUrl)
        assertEquals("youtube", pl.provider)
    }
}
