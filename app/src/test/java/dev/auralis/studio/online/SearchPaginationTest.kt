package dev.auralis.studio.online

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPaginationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun extractContinuationToken_findsTokenInsideContinuationItemRenderer() {
        val sampleJson = """
            {
              "contents": {
                "twoColumnSearchResultsRenderer": {
                  "primaryContents": {
                    "sectionListRenderer": {
                      "contents": [
                        {
                          "itemSectionRenderer": {
                            "contents": [
                              {
                                "continuationItemRenderer": {
                                  "continuationEndpoint": {
                                    "continuationCommand": {
                                      "token": "4qmF3gMIEg14eXpfdG9rZW5fYWJj"
                                    }
                                  }
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val jsonElement = json.parseToJsonElement(sampleJson)
        val token = YouTubeCatalogProvider.extractContinuationToken(jsonElement)

        assertNotNull("Continuation token should be extracted", token)
        assertEquals("4qmF3gMIEg14eXpfdG9rZW5fYWJj", token)
    }

    @Test
    fun extractContinuationToken_findsTokenInDirectContinuationCommand() {
        val sampleJson = """
            {
              "onResponseReceivedCommands": [
                {
                  "appendContinuationItemsAction": {
                    "continuationItems": [
                      {
                        "continuationCommand": {
                          "token": "next_page_token_xyz_789"
                        }
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val jsonElement = json.parseToJsonElement(sampleJson)
        val token = YouTubeCatalogProvider.extractContinuationToken(jsonElement)

        assertNotNull("Direct continuation command token should be extracted", token)
        assertEquals("next_page_token_xyz_789", token)
    }

    @Test
    fun extractContinuationToken_returnsNullWhenNoTokenPresent() {
        val sampleJson = """
            {
              "contents": {
                "itemSectionRenderer": {
                  "contents": [
                    { "videoRenderer": { "videoId": "abc12345678" } }
                  ]
                }
              }
            }
        """.trimIndent()

        val jsonElement = json.parseToJsonElement(sampleJson)
        val token = YouTubeCatalogProvider.extractContinuationToken(jsonElement)

        assertNull("Should return null when continuation token is absent", token)
    }

    private fun makeTestRecording(title: String, artist: String): CanonicalRecording {
        val track = OnlineTrack(
            provider = "youtube",
            id = "$artist-$title".hashCode().toString(),
            title = title,
            artist = artist,
        )
        val identity = RecordingIdentity.forTrack(track)
        return CanonicalRecording(
            identity = identity,
            title = title,
            artist = artist,
        )
    }

    @Test
    fun paginationDeduplication_mergesUniqueItemsAndRejectsDuplicates() {
        val page0Items = listOf(
            makeTestRecording("Song A", "Artist A"),
            makeTestRecording("Song B", "Artist B"),
        )

        val page1Items = listOf(
            makeTestRecording("Song B", "Artist B"), // Duplicate from page 0
            makeTestRecording("Song C", "Artist C"), // New item
        )

        val existingIdentities = page0Items.map { it.recordingIdentity }.toSet()
        val newRecordings = page1Items.filterNot { it.recordingIdentity in existingIdentities }
        val combined = page0Items + newRecordings

        assertEquals(3, combined.size)
        assertEquals(listOf("Song A", "Song B", "Song C"), combined.map { it.title })
    }

    @Test
    fun youtubeDataApi_parsesNextPageTokenCorrectly() {
        val sampleSearchResponse = """
            {
              "nextPageToken": "CAUQAA",
              "items": [
                {
                  "id": { "kind": "youtube#video", "videoId": "sample_vid_1" },
                  "snippet": { "title": "Test Title", "channelTitle": "Test Channel" }
                }
              ]
            }
        """.trimIndent()

        val element = json.parseToJsonElement(sampleSearchResponse)
        val nextPageToken = element.let {
            it as? kotlinx.serialization.json.JsonObject
        }?.get("nextPageToken")?.let {
            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
        }

        assertEquals("CAUQAA", nextPageToken)
    }
}
