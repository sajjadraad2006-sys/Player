package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.AudioQualityPreference
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverSourceType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverRequestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun resolverRequest_roundTripSerialization_preservesFields() {
        val request = ResolverRequest(
            requestId = "req-12345",
            sourceUrl = "https://example.com/audio/sample.mp3",
            sourceId = "sample-123",
            sourceType = ResolverSourceType.MUSIC_TRACK,
            preferredAudioQuality = AudioQualityPreference.HIGH,
            extraOptions = mapOf("customKey" to "customVal"),
        )

        val encoded = json.encodeToString(ResolverRequest.serializer(), request)
        val decoded = json.decodeFromString(ResolverRequest.serializer(), encoded)

        assertEquals("req-12345", decoded.requestId)
        assertEquals("https://example.com/audio/sample.mp3", decoded.sourceUrl)
        assertEquals("sample-123", decoded.sourceId)
        assertEquals(ResolverSourceType.MUSIC_TRACK, decoded.sourceType)
        assertEquals(AudioQualityPreference.HIGH, decoded.preferredAudioQuality)
        assertEquals("customVal", decoded.extraOptions["customKey"])
    }

    @Test
    fun resolverRequest_defaultGeneratesUuidAndBestQuality() {
        val request = ResolverRequest(sourceUrl = "https://example.com/song")
        assertNotNull(request.requestId)
        assertTrue(request.requestId.isNotBlank())
        assertEquals(AudioQualityPreference.BEST, request.preferredAudioQuality)
        assertEquals(ResolverSourceType.GENERIC_URL, request.sourceType)
    }
}
