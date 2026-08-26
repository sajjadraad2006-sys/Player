package dev.auralis.studio.online

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiusClientTest {
    private val client = AudiusClient("")

    @Test
    fun `maps stable identity metadata and separate media capabilities`() {
        val payload = requireNotNull(javaClass.classLoader?.getResource("audius/search_tracks.json")).readText()
        val tracks = client.parseSearchFixture(payload)
        val downloadable = tracks.first()

        assertEquals("audius", downloadable.provider)
        assertEquals("D7KyD", downloadable.id)
        assertEquals("Fixture Song", downloadable.title)
        assertEquals("Fixture Artist", downloadable.artist)
        assertEquals("Fixture Album", downloadable.album)
        assertEquals(188_000L, downloadable.durationMs)
        assertEquals("USA1B2600001", RecordingIdentity.normalizedIsrc(downloadable.isrc))
        assertNull(downloadable.previewUrl)
        assertTrue(downloadable.streamAllowed)
        assertTrue(downloadable.streamUrl!!.contains("/tracks/D7KyD/stream?app_name=Lyrune"))
        assertTrue(downloadable.isDownloadableFullTrack)
        assertTrue(downloadable.downloadUrl!!.contains("/tracks/D7KyD/download?original=true"))
        assertEquals("flac", downloadable.audioFormat)
    }

    @Test
    fun `downloadable false never produces a download URL`() {
        val payload = requireNotNull(javaClass.classLoader?.getResource("audius/search_tracks.json")).readText()
        val track = client.parseSearchFixture(payload)[1]
        assertFalse(track.downloadAllowed)
        assertNull(track.downloadUrl)
        assertFalse(track.isDownloadableFullTrack)
        assertTrue(track.streamAllowed)
    }

    @Test
    fun `current is_downloadable contract enables download`() {
        val payload = requireNotNull(javaClass.classLoader?.getResource("audius/search_tracks.json")).readText()
        val current = client.parseSearchFixture(payload)[0]
        assertTrue(current.downloadAllowed)
        assertTrue(current.isDownloadableFullTrack)
        val legacy = client.parseSearchFixture(payload)[2]
        assertTrue(legacy.downloadAllowed)
    }

    @Test
    fun `download gated tracks are not freely downloadable`() {
        val payload = """
            {"data":[{"id":"gd","title":"Gated Download",
            "is_downloadable":true,"is_download_gated":true,
            "is_streamable":true,
            "download_conditions":{"usdc_purchase":{"price":"1"}},
            "user":{"name":"Artist"}}]}
        """.trimIndent()
        val track = client.parseSearchFixture(payload).single()
        assertFalse(track.downloadAllowed)
        assertNull(track.downloadUrl)
    }

    @Test
    fun `gated Audius media is not exposed without user authorization`() {
        val payload = """
            {"data":[{"id":"gated","title":"Gated","downloadable":true,
            "is_streamable":true,"is_stream_gated":true,
            "download_conditions":{"usdc_purchase":{"price":"1"}},
            "user":{"name":"Artist"}}]}
        """.trimIndent()
        val track = client.parseSearchFixture(payload).single()
        assertFalse(track.streamAllowed)
        assertNull(track.streamUrl)
        assertFalse(track.downloadAllowed)
        assertNull(track.downloadUrl)
    }

    @Test
    fun `rate limit is mapped without exposing transport exception`() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.TooManyRequests) }
        val mockClient = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val result = AudiusClient("test-public-key", mockClient).search("fixture")
        assertTrue(result is ProviderResult.RateLimited)
    }

    @Test
    fun `client safe API key uses official query parameter and never bearer auth`() = runTest {
        var apiKey: String? = null
        var authorization: String? = null
        val engine = MockEngine { request ->
            apiKey = request.url.parameters["api_key"]
            authorization = request.headers["Authorization"]
            respond("{\"data\":[]}", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val mockClient = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        AudiusClient("public-client-key", mockClient).search("fixture")
        assertEquals("public-client-key", apiKey)
        assertNull(authorization)
    }

    @Test
    fun `AudiusDns parses standard DNS-over-HTTPS JSON response`() {
        val jsonPayload = """
            {
                "Status": 0,
                "Answer": [
                    {"name": "api.audius.co", "type": 1, "TTL": 300, "data": "104.18.24.162"},
                    {"name": "api.audius.co", "type": 1, "TTL": 300, "data": "104.18.25.162"},
                    {"name": "api.audius.co", "type": 28, "TTL": 300, "data": "2606:4700::6812:18a2"}
                ]
            }
        """.trimIndent()

        val parsedIps = AudiusDns.parseDohAnswer(jsonPayload)
        assertEquals(listOf("104.18.24.162", "104.18.25.162"), parsedIps)
    }

    @Test
    fun `AudiusDns resolveDoh constructs InetAddresses with original hostname for TLS SNI`() {
        val mockPayload = """
            {"Status": 0, "Answer": [{"name": "api.audius.co", "type": 1, "TTL": 60, "data": "192.0.2.1"}]}
        """.trimIndent()

        val addresses = AudiusDns.resolveDoh("api.audius.co", fetcher = { mockPayload })
        assertEquals(1, addresses.size)
        assertEquals("api.audius.co", addresses.first().hostName)
        assertEquals("192.0.2.1", addresses.first().hostAddress)
    }

    @Test(expected = java.net.UnknownHostException::class)
    fun `AudiusDns lookup throws UnknownHostException for nonexistent non-audius host`() {
        AudiusDns.lookup("nonexistent.invalid.domain.12345")
    }
}

