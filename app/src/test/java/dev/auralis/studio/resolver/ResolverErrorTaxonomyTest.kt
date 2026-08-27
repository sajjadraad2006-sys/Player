package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.ResolverError
import dev.auralis.studio.resolver.api.ResolverErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverErrorTaxonomyTest {

    @Test
    fun resolverError_neverUsesGenericSourceErrorString() {
        val error = ResolverError.unknown("https://example.com")
        assertNotEquals("Source Error", error.safeUserMessage)
        assertNotEquals("Source Error", error.technicalCode.name)
    }

    @Test
    fun retryableErrors_markedCorrectly() {
        val networkErr = ResolverError.networkUnavailable("https://example.com")
        val timeoutErr = ResolverError.timeout("https://example.com")
        val crashErr = ResolverError.runtimeCrashed("https://example.com")
        val challengeErr = ResolverError.challengeFailed("https://example.com")

        assertTrue(networkErr.isRetryable)
        assertTrue(timeoutErr.isRetryable)
        assertTrue(crashErr.isRetryable)
        assertTrue(challengeErr.isRetryable)
    }

    @Test
    fun nonRetryableErrors_markedCorrectly() {
        val unsupported = ResolverError.unsupported("https://example.com")
        val notFound = ResolverError.notFound("https://example.com")
        val restricted = ResolverError.accessRestricted("https://example.com")
        val downloadDenied = ResolverError.downloadNotPermitted("https://example.com")
        val cancelled = ResolverError.cancelled("https://example.com")
        val jsUnavailable = ResolverError.jsRuntimeUnavailable("https://example.com")
        val ejsIncompatible = ResolverError.ejsIncompatible("https://example.com")

        assertFalse(unsupported.isRetryable)
        assertFalse(notFound.isRetryable)
        assertFalse(restricted.isRetryable)
        assertFalse(downloadDenied.isRetryable)
        assertFalse(cancelled.isRetryable)
        assertFalse(jsUnavailable.isRetryable)
        assertFalse(ejsIncompatible.isRetryable)
    }

    @Test
    fun safeUserMessage_doesNotLeakSecretsOrCookies() {
        val secretUrl = "https://example.com/audio.mp3?token=SECRET_KEY_12345&cookie=session=abc"
        val error = ResolverError.accessRestricted(secretUrl, details = "HTTP 403 Forbidden with token=SECRET_KEY_12345")

        assertEquals("Access to this content is restricted by the provider.", error.safeUserMessage)
        assertFalse(error.safeUserMessage.contains("SECRET_KEY"))
        assertFalse(error.safeUserMessage.contains("cookie"))
    }

    @Test
    fun allErrorCodes_areDefinedInTaxonomy() {
        val codes = ResolverErrorCode.entries.map { it.name }
        assertTrue(codes.contains("UnsupportedUrl"))
        assertTrue(codes.contains("NotFound"))
        assertTrue(codes.contains("NetworkUnavailable"))
        assertTrue(codes.contains("DnsFailure"))
        assertTrue(codes.contains("Timeout"))
        assertTrue(codes.contains("RateLimited"))
        assertTrue(codes.contains("AuthenticationRequired"))
        assertTrue(codes.contains("AccessRestricted"))
        assertTrue(codes.contains("DownloadNotPermitted"))
        assertTrue(codes.contains("RuntimeUnavailable"))
        assertTrue(codes.contains("PythonUnavailable"))
        assertTrue(codes.contains("JsRuntimeUnavailable"))
        assertTrue(codes.contains("EjsIncompatible"))
        assertTrue(codes.contains("ChallengeFailed"))
        assertTrue(codes.contains("ExpiredResource"))
        assertTrue(codes.contains("FormatUnavailable"))
        assertTrue(codes.contains("ProviderChanged"))
        assertTrue(codes.contains("RuntimeCrashed"))
        assertTrue(codes.contains("Cancelled"))
        assertTrue(codes.contains("PayloadTooLarge"))
        assertTrue(codes.contains("IncompatibleSchemaVersion"))
        assertTrue(codes.contains("CollectionNotSupported"))
        assertTrue(codes.contains("Unknown"))
    }
}
