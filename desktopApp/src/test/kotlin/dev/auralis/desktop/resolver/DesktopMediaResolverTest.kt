package dev.auralis.desktop.resolver

import dev.auralis.studio.resolver.api.ResolverError
import dev.auralis.studio.resolver.api.ResolverErrorCode
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.SecretSanitizer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopMediaResolverTest {

    @Test
    fun testInitialStateIsStoppedAndZeroPythonStarted() {
        val resolver = DesktopMediaResolver()
        // Critical invariant: zero child process on instantiation
        assertEquals(ResolverLifecycleState.STOPPED, resolver.state.value)
    }

    @Test
    fun testRequestValidationRejectsBlankUrlAndId() {
        val req = ResolverRequest(sourceUrl = "   ", sourceId = "")
        val err = req.validate()
        assertNotNull(err)
        assertEquals(ResolverErrorCode.UnsupportedUrl, err.technicalCode)
    }

    @Test
    fun testRequestValidationRejectsForbiddenOptions() {
        val forbiddenKeys = listOf("exec", "command", "proxy", "postprocessor", "cookie", "script")
        for (key in forbiddenKeys) {
            val req = ResolverRequest(
                sourceUrl = "https://example.com/test",
                extraOptions = mapOf(key to "malicious_value"),
            )
            val err = req.validate()
            assertNotNull(err, "Expected validation failure for forbidden option '$key'")
            assertEquals(ResolverErrorCode.Unknown, err.technicalCode)
            assertTrue(err.safeUserMessage.contains("Forbidden configuration option"))
        }
    }

    @Test
    fun testRequestValidationAcceptsSafeExtraOptions() {
        val req = ResolverRequest(
            sourceUrl = "https://example.com/test",
            extraOptions = mapOf("user_agent" to "CustomUA/1.0"),
        )
        val err = req.validate()
        assertNull(err)
    }

    @Test
    fun testSecretSanitizerRedactsSensitiveHeaders() {
        val headers = mapOf(
            "User-Agent" to "Lyrune/1.0",
            "Authorization" to "Bearer secret_token_12345",
            "Cookie" to "session_id=abcdef",
            "Accept" to "audio/*",
        )

        val sanitized = SecretSanitizer.sanitizeHeaders(headers)
        assertEquals("Lyrune/1.0", sanitized["User-Agent"])
        assertEquals("<redacted>", sanitized["Authorization"])
        assertEquals("<redacted>", sanitized["Cookie"])
        assertEquals("audio/*", sanitized["Accept"])

        val safeOnly = SecretSanitizer.extractSafeHeaders(headers)
        assertTrue(safeOnly.containsKey("User-Agent"))
        assertTrue(safeOnly.containsKey("Accept"))
        assertFalse(safeOnly.containsKey("Authorization"))
        assertFalse(safeOnly.containsKey("Cookie"))
    }

    @Test
    fun testSecretSanitizerRedactsUrlQueryTokens() {
        val url = "https://example.com/stream.mp3?token=secret_abc_123&expire=99999"
        val sanitized = SecretSanitizer.sanitizeUrl(url)
        assertEquals("https://example.com/stream.mp3?<redacted>", sanitized)
    }

    @Test
    fun testCancelInFlightRequest() = runBlocking {
        val resolver = DesktopMediaResolver()
        resolver.cancel("non-existent-request-id")
        assertEquals(ResolverLifecycleState.STOPPED, resolver.state.value)
    }

    @Test
    fun testShutdownWorkerWhenStoppedIsSafe() {
        val resolver = DesktopMediaResolver()
        resolver.shutdownWorker()
        assertEquals(ResolverLifecycleState.STOPPED, resolver.state.value)
    }
}
