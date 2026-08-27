package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.DeliveryRequirements
import dev.auralis.studio.resolver.api.DrmState
import dev.auralis.studio.resolver.api.EphemeralResourceLease
import dev.auralis.studio.resolver.api.MediaKind
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolvedFormatDescriptor
import dev.auralis.studio.resolver.api.ResolvedMedia
import dev.auralis.studio.resolver.api.SecretSanitizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EphemeralResourceSafetyTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun secretSanitizer_redactsQueryParametersFromUrls() {
        val signedUrl = "https://cdn.example.com/audio/stream.mp3?token=SECRET123&expire=1780000000&sig=abc987"
        val sanitized = SecretSanitizer.sanitizeUrl(signedUrl)
        assertEquals("https://cdn.example.com/audio/stream.mp3?<redacted>", sanitized)
        assertFalse(sanitized.contains("SECRET123"))
        assertFalse(sanitized.contains("abc987"))
    }

    @Test
    fun secretSanitizer_detectsAndRedactsSensitiveHeaders() {
        assertTrue(SecretSanitizer.isSensitiveHeader("Cookie"))
        assertTrue(SecretSanitizer.isSensitiveHeader("authorization"))
        assertTrue(SecretSanitizer.isSensitiveHeader("Proxy-Authorization"))
        assertTrue(SecretSanitizer.isSensitiveHeader("X-Api-Key"))
        assertTrue(SecretSanitizer.isSensitiveHeader("X-Auth-Token"))
        assertTrue(SecretSanitizer.isSensitiveHeader("custom-secret-token"))

        assertTrue(SecretSanitizer.isSafeHeader("User-Agent"))
        assertTrue(SecretSanitizer.isSafeHeader("Referer"))
        assertTrue(SecretSanitizer.isSafeHeader("Origin"))
        assertTrue(SecretSanitizer.isSafeHeader("Accept"))
        assertTrue(SecretSanitizer.isSafeHeader("Accept-Language"))
        assertTrue(SecretSanitizer.isSafeHeader("Range"))

        val headers = mapOf(
            "User-Agent" to "Lyrune/1.0",
            "Cookie" to "session=SECRET789",
            "Authorization" to "Bearer SECRET456",
        )
        val sanitized = SecretSanitizer.sanitizeHeaders(headers)
        assertEquals("Lyrune/1.0", sanitized["User-Agent"])
        assertEquals("<redacted>", sanitized["Cookie"])
        assertEquals("<redacted>", sanitized["Authorization"])

        val safeOnly = SecretSanitizer.extractSafeHeaders(headers)
        assertEquals(1, safeOnly.size)
        assertTrue(safeOnly.containsKey("User-Agent"))
        assertFalse(safeOnly.containsKey("Cookie"))
        assertFalse(safeOnly.containsKey("Authorization"))
    }

    @Test
    fun secretSanitizer_sanitizesLogMessages() {
        val logMessage = "Connecting to https://audio.cdn.com/play?auth=SECRET123 with Bearer SECRET456 and token SECRET789"
        val cleanLog = SecretSanitizer.sanitizeLog(logMessage)
        assertFalse(cleanLog.contains("SECRET123"))
        assertFalse(cleanLog.contains("SECRET456"))
        assertFalse(cleanLog.contains("SECRET789"))
        assertTrue(cleanLog.contains("<redacted>"))
    }

    @Test
    fun resolvedMedia_serializationContainsNoSignedUrlsOrSecrets() {
        val media = ResolvedMedia(
            schemaVersion = 1,
            source = "TestProvider",
            sourceId = "track-123",
            webpageUrl = "https://example.com/track/123",
            title = "Test Song",
            artist = "Artist",
            formats = listOf(
                ResolvedFormatDescriptor(
                    formatId = "fmt-1",
                    mediaKind = MediaKind.AUDIO_ONLY,
                    container = "m4a",
                    extension = "m4a",
                    bitrateKbps = 256.0,
                    resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                    drmState = DrmState.NONE,
                    deliveryRequirements = DeliveryRequirements(
                        requiresCustomHeaders = true,
                        requiredSafeHeaderKeys = listOf("User-Agent"),
                    ),
                ),
            ),
        )

        val serialized = json.encodeToString(media)
        assertFalse(serialized.contains("SECRET123"))
        assertFalse(serialized.contains("SECRET456"))
        assertFalse(serialized.contains("SECRET789"))
        assertFalse(serialized.contains("Cookie"))
        assertFalse(serialized.contains("Authorization"))
        assertFalse(serialized.contains("token="))
    }

    @Test
    fun ephemeralResourceLease_expiryCalculation() {
        val pastLease = EphemeralResourceLease(
            requestId = "req-1",
            formatId = "f1",
            url = "https://cdn.example.com/audio.mp3",
            knownExpiryEpochMs = System.currentTimeMillis() - 10_000L,
        )
        assertTrue(pastLease.isExpired)

        val futureLease = EphemeralResourceLease(
            requestId = "req-2",
            formatId = "f2",
            url = "https://cdn.example.com/audio.mp3",
            knownExpiryEpochMs = System.currentTimeMillis() + 60_000L,
        )
        assertFalse(futureLease.isExpired)
    }
}
