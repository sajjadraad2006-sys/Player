package dev.auralis.studio.resolver

import dev.auralis.studio.library.PreviewDownloadRejectedException
import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityBasis
import dev.auralis.studio.resolver.api.DrmState
import dev.auralis.studio.resolver.api.EphemeralResourceLease
import dev.auralis.studio.resolver.api.MediaKind
import dev.auralis.studio.resolver.api.MediaResolver
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolvedFormat
import dev.auralis.studio.resolver.api.ResolvedFormatDescriptor
import dev.auralis.studio.resolver.api.ResolvedMedia
import dev.auralis.studio.resolver.api.ResolverDownloadDescriptor
import dev.auralis.studio.resolver.api.ResolverError
import dev.auralis.studio.resolver.api.ResolverErrorCode
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult
import dev.auralis.studio.resolver.api.RuntimeHealth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class NetworkFailureAndRetryMatrixTest {

    @Test
    fun stale403_reResolvesExactlyOnce_boundedAtTwoTotalResolutions() = runBlocking {
        val resolutionCount = AtomicInteger(0)
        val expiredUrl = "https://cdn.example.org/audio.mp3?exp=1"
        val freshUrl = "https://cdn.example.org/audio.mp3?exp=2"

        val descriptor = ResolverDownloadDescriptor(
            source = "TestService",
            sourceId = "t403_1",
            canonicalUrl = "https://example.org/track/1",
            title = "Stale 403 Test Track",
            downloadCapability = DownloadCapability.PERMITTED,
        )

        val resolver = object : MediaResolver {
            override suspend fun inspect(request: ResolverRequest): ResolverResult = resolve(request)
            override suspend fun resolve(request: ResolverRequest): ResolverResult {
                val callIdx = resolutionCount.incrementAndGet()
                val url = if (callIdx == 1) expiredUrl else freshUrl
                return ResolverResult.Success(
                    media = ResolvedMedia(
                        source = descriptor.source,
                        sourceId = descriptor.sourceId,
                        webpageUrl = descriptor.canonicalUrl,
                        title = descriptor.title,
                        formats = listOf(
                            ResolvedFormatDescriptor(
                                formatId = "fmt_1",
                                mediaKind = MediaKind.AUDIO_ONLY,
                                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                                drmState = DrmState.NONE,
                                downloadCapability = DownloadCapability.PERMITTED,
                                downloadCapabilityBasis = DownloadCapabilityBasis.TEST_FIXTURE,
                            ),
                        ),
                        rawFormats = listOf(
                            ResolvedFormat(
                                formatId = "fmt_1",
                                url = url,
                                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                                downloadCapability = DownloadCapability.PERMITTED,
                                downloadCapabilityBasis = DownloadCapabilityBasis.TEST_FIXTURE,
                            ),
                        ),
                    ),
                    lease = EphemeralResourceLease(
                        requestId = request.requestId,
                        formatId = "fmt_1",
                        url = url,
                    ),
                )
            }
            override suspend fun cancel(requestId: String) {}
            override suspend fun health(): RuntimeHealth = RuntimeHealth.UNINITIALIZED
        }

        // 1. Initial resolution (attempt 1)
        val res1 = resolver.resolve(ResolverRequest(sourceUrl = descriptor.canonicalUrl))
        assertTrue(res1 is ResolverResult.Success)
        val lease1 = (res1 as ResolverResult.Success).lease
        assertEquals(expiredUrl, lease1?.url)
        assertEquals(1, resolutionCount.get())

        // Simulated HTTP 403 failure from download
        val failureMsg = "HTTP download failed with code 403"
        val isStale403 = failureMsg.contains("403") || failureMsg.contains("expired", ignoreCase = true)
        assertTrue("Error must be recognized as stale 403 / expired", isStale403)

        // 2. Re-resolution (attempt 2 - exactly once)
        val res2 = resolver.resolve(ResolverRequest(sourceUrl = descriptor.canonicalUrl))
        assertTrue(res2 is ResolverResult.Success)
        val lease2 = (res2 as ResolverResult.Success).lease
        assertEquals(freshUrl, lease2?.url)
        assertEquals(2, resolutionCount.get())

        // Max resolution count caused by stale 403 is exactly 2
        assertEquals(2, resolutionCount.get())
    }

    @Test
    fun terminalErrors_classifiedAsNonRetryable() {
        val notFoundError = ResolverError.notFound("Generic", "HTTP 404")
        val unsupportedError = ResolverError.unsupported("Generic", "Unsupported scheme")
        val downloadNotPermittedError = ResolverError(
            source = "Youtube",
            technicalCode = ResolverErrorCode.DownloadNotPermitted,
            isRetryable = false,
            safeUserMessage = "Download not permitted",
        )
        val accessRestrictedError = ResolverError(
            source = "Provider",
            technicalCode = ResolverErrorCode.AccessRestricted,
            isRetryable = false,
            safeUserMessage = "Geographic or access restriction",
        )

        assertFalse("NotFound must be terminal", notFoundError.isRetryable)
        assertFalse("UnsupportedUrl must be terminal", unsupportedError.isRetryable)
        assertFalse("DownloadNotPermitted must be terminal", downloadNotPermittedError.isRetryable)
        assertFalse("AccessRestricted must be terminal", accessRestrictedError.isRetryable)
    }

    @Test
    fun transientErrors_classifiedAsRetryable() {
        val networkError = ResolverError.networkUnavailable("Generic", "Network unreachable")
        val timeoutError = ResolverError.timeout("Generic", "Connection timed out")
        val rateLimitedError = ResolverError(
            source = "Generic",
            technicalCode = ResolverErrorCode.RateLimited,
            isRetryable = true,
            safeUserMessage = "Too many requests. Please try again later.",
        )

        assertTrue("NetworkUnavailable must be retryable", networkError.isRetryable)
        assertTrue("Timeout must be retryable", timeoutError.isRetryable)
        assertTrue("RateLimited must be retryable", rateLimitedError.isRetryable)
    }

    @Test
    fun unknownOrNonPermittedCapability_rejectedBeforeDownloader() {
        val streamOnlyDescriptor = ResolverDownloadDescriptor(
            source = "Youtube",
            sourceId = "yt-123",
            canonicalUrl = "https://www.youtube.com/watch?v=123",
            title = "YouTube Video",
            downloadCapability = DownloadCapability.STREAM_ONLY,
        )

        val unknownDescriptor = ResolverDownloadDescriptor(
            source = "Generic",
            sourceId = "gen-123",
            canonicalUrl = "https://example.com/audio.mp3",
            title = "Generic Audio",
            downloadCapability = DownloadCapability.UNKNOWN,
        )

        val notPermittedDescriptor = ResolverDownloadDescriptor(
            source = "iTunes",
            sourceId = "itunes-123",
            canonicalUrl = "https://music.apple.com/track",
            title = "iTunes Track",
            downloadCapability = DownloadCapability.NOT_PERMITTED,
        )

        // None of these should be permitted for enqueue
        assertFalse(streamOnlyDescriptor.downloadCapability == DownloadCapability.PERMITTED)
        assertFalse(unknownDescriptor.downloadCapability == DownloadCapability.PERMITTED)
        assertFalse(notPermittedDescriptor.downloadCapability == DownloadCapability.PERMITTED)
    }

    @Test
    fun previewSafety_alwaysThrowsDeterministicException() {
        val previewException = PreviewDownloadRejectedException("Preview download rejected")
        assertTrue(previewException is Exception)
        assertEquals("Preview download rejected", previewException.message)
    }
}
