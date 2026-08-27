package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.DeliveryRequirements
import dev.auralis.studio.resolver.api.DrmState
import dev.auralis.studio.resolver.api.EphemeralResourceLease
import dev.auralis.studio.resolver.api.MediaKind
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolvedFormatDescriptor
import dev.auralis.studio.resolver.api.ResolvedMedia
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverSourceType
import dev.auralis.studio.resolver.api.SecretSanitizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun secretSanitizer_stripsUserinfoCredentials() {
        val userinfoUrl = "https://user:password123@cdn.example.com/audio/stream.mp3?token=SECRET"
        val sanitized = SecretSanitizer.sanitizeUrl(userinfoUrl)
        assertFalse("Userinfo password must not leak", sanitized.contains("password123"))
        assertFalse("Userinfo user must not leak", sanitized.contains("user:"))
        assertTrue(sanitized.contains("https://cdn.example.com/audio/stream.mp3?<redacted>"))
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
        // Range header is excluded because download engine owns range/resume semantics
        assertFalse(SecretSanitizer.isSafeHeader("Range"))

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
    fun resolverRequest_validatesInvariantsAndExtraOptions() {
        // 1. Both empty -> invalid
        val emptyReq = ResolverRequest(sourceUrl = "", sourceId = "")
        assertNotNull(emptyReq.validate())

        // 2. Generic URL missing sourceUrl -> invalid
        val invalidGeneric = ResolverRequest(sourceUrl = null, sourceId = "123", sourceType = ResolverSourceType.GENERIC_URL)
        assertNotNull(invalidGeneric.validate())

        // 3. Valid URL -> passes
        val validReq = ResolverRequest(sourceUrl = "https://example.com/track/1")
        assertNull(validReq.validate())

        // 4. Forbidden extraOption -> rejected
        val forbiddenReq = ResolverRequest(
            sourceUrl = "https://example.com/track/1",
            extraOptions = mapOf("proxy" to "http://malicious-proxy.com"),
        )
        assertNotNull(forbiddenReq.validate())

        val forbiddenExecReq = ResolverRequest(
            sourceUrl = "https://example.com/track/1",
            extraOptions = mapOf("downloader_args" to "ffmpeg:-i"),
        )
        assertNotNull(forbiddenExecReq.validate())

        // 5. Allowed extraOption -> passes
        val allowedReq = ResolverRequest(
            sourceUrl = "https://example.com/track/1",
            extraOptions = mapOf("user_agent" to "Custom/1.0", "preferred_quality" to "high"),
        )
        assertNull(allowedReq.validate())
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
    fun ephemeralResourceLease_epochTimestampAndExpiryCalculation() {
        val now = System.currentTimeMillis()
        val pastLease = EphemeralResourceLease(
            requestId = "req-1",
            formatId = "f1",
            url = "https://cdn.example.com/audio.mp3",
            resolvedAtEpochMs = now - 30_000L,
            knownExpiryEpochMs = now - 10_000L,
        )
        assertTrue(pastLease.isExpired)
        assertEquals(now - 30_000L, pastLease.resolvedAtEpochMs)

        val futureLease = EphemeralResourceLease(
            requestId = "req-2",
            formatId = "f2",
            url = "https://cdn.example.com/audio.mp3",
            resolvedAtEpochMs = now,
            knownExpiryEpochMs = now + 60_000L,
        )
        assertFalse(futureLease.isExpired)
    }
}
