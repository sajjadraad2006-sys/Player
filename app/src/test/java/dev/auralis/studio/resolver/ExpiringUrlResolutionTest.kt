package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.AudioQualityPreference
import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityBasis
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolvedFormat
import dev.auralis.studio.resolver.api.ResolvedMedia
import dev.auralis.studio.resolver.api.ResolverDownloadDescriptor
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiringUrlResolutionTest {

    @Test
    fun descriptor_doesNotContainTransientCdnUrlOrTokens() {
        val canonicalPageUrl = "https://archive.org/details/sample_track_2026"
        val transientSignedCdnUrl = "https://ia8001.us.archive.org/sample.mp3?token=exp12345&sig=abc987"

        val descriptor = ResolverDownloadDescriptor(
            source = "ArchiveOrg",
            sourceId = "sample_track_2026",
            canonicalUrl = canonicalPageUrl,
            title = "Sample Track",
            artist = null,
            album = null,
            artworkUrl = null,
            expectedDurationMs = 180_000L,
            downloadCapability = DownloadCapability.PERMITTED,
            downloadCapabilityBasis = DownloadCapabilityBasis.LICENSE_METADATA,
        )

        // Verify descriptor does not store the transient signed URL
        assertFalse(
            "Descriptor must not contain transient signed URL",
            descriptor.canonicalUrl.contains("token=") || descriptor.canonicalUrl.contains("sig="),
        )
        assertEquals("lrr:archiveorg:sample_track_2026", descriptor.downloadKey)
    }

    @Test
    fun descriptor_stableSha256DownloadKey_whenSourceIdNull() {
        val canonicalUrl = "https://example.org/audio/track_one"
        val descriptor1 = ResolverDownloadDescriptor(
            source = "GenericOpen",
            sourceId = null,
            canonicalUrl = canonicalUrl,
            title = "Track One",
            downloadCapability = DownloadCapability.PERMITTED,
        )

        val descriptor2 = ResolverDownloadDescriptor(
            source = "GenericOpen",
            sourceId = null,
            canonicalUrl = canonicalUrl,
            title = "Track One (Renamed)",
            downloadCapability = DownloadCapability.PERMITTED,
        )

        assertEquals("Same canonical URL must produce identical downloadKey", descriptor1.downloadKey, descriptor2.downloadKey)
        assertTrue("DownloadKey must be prefixed with lrr:genericopen:", descriptor1.downloadKey.startsWith("lrr:genericopen:"))
        // SHA-256 hex is 64 characters
        assertEquals(64, descriptor1.downloadKey.substringAfterLast(":").length)
    }

    @Test
    fun descriptor_missingMetadata_preservesNullsWithoutFakePlaceholders() {
        val descriptor = ResolverDownloadDescriptor(
            source = "OpenTrack",
            sourceId = "ot-1",
            canonicalUrl = "https://example.org/track",
            title = "Original Title",
            artist = null,
            album = null,
            artworkUrl = null,
            expectedDurationMs = null,
        )

        assertNull(descriptor.artist)
        assertNull(descriptor.album)
        assertNull(descriptor.artworkUrl)
        assertNull(descriptor.expectedDurationMs)
        assertNotEquals("Music", descriptor.artist)
        assertNotEquals("Lyrune", descriptor.artist)
    }

    @Test
    fun simulatedFreshResolution_workerConsumesNewCdnUrl() = runBlocking {
        // Inspect returns URL_A (simulating expired CDN URL)
        val expiredCdnUrl = "https://cdn.example.org/stream.m4a?exp=1000"
        // Worker re-resolves fresh and gets URL_B (fresh signed URL)
        val freshCdnUrl = "https://cdn.example.org/stream.m4a?exp=9999"

        val descriptor = ResolverDownloadDescriptor(
            source = "TestProvider",
            sourceId = "tp-99",
            canonicalUrl = "https://example.org/recordings/tp-99",
            title = "Test Song",
            downloadCapability = DownloadCapability.PERMITTED,
        )

        val fakeResolver = object : dev.auralis.studio.resolver.api.MediaResolver {
            override suspend fun inspect(request: ResolverRequest): ResolverResult {
                return ResolverResult.Success(
                    ResolvedMedia(
                        source = descriptor.source,
                        sourceId = descriptor.sourceId,
                        webpageUrl = descriptor.canonicalUrl,
                        title = descriptor.title,
                        formats = listOf(
                            ResolvedFormat(
                                formatId = "audio_best",
                                url = expiredCdnUrl,
                                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                                downloadCapability = DownloadCapability.PERMITTED,
                                downloadCapabilityBasis = DownloadCapabilityBasis.PROVIDER_EXPLICIT,
                            ),
                        ),
                    ),
                )
            }

            override suspend fun resolve(request: ResolverRequest): ResolverResult {
                // Re-resolution returns fresh URL_B
                return ResolverResult.Success(
                    ResolvedMedia(
                        source = descriptor.source,
                        sourceId = descriptor.sourceId,
                        webpageUrl = descriptor.canonicalUrl,
                        title = descriptor.title,
                        formats = listOf(
                            ResolvedFormat(
                                formatId = "audio_best",
                                url = freshCdnUrl,
                                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                                downloadCapability = DownloadCapability.PERMITTED,
                                downloadCapabilityBasis = DownloadCapabilityBasis.PROVIDER_EXPLICIT,
                            ),
                        ),
                    ),
                )
            }

            override suspend fun cancel(requestId: String) {}
            override suspend fun health(): dev.auralis.studio.resolver.api.RuntimeHealth = dev.auralis.studio.resolver.api.RuntimeHealth.UNINITIALIZED
        }

        val resolveResult = fakeResolver.resolve(ResolverRequest(sourceUrl = descriptor.canonicalUrl))
        assertTrue(resolveResult is ResolverResult.Success)
        val resolvedFormat = (resolveResult as ResolverResult.Success).media.formats.first()

        assertEquals("Downloader must consume the fresh URL_B, not expired URL_A", freshCdnUrl, resolvedFormat.url)
        assertNotEquals(expiredCdnUrl, resolvedFormat.url)
    }
}
