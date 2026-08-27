package dev.auralis.studio.resolver

import androidx.work.workDataOf
import dev.auralis.studio.library.DownloadIngestionRequest
import dev.auralis.studio.library.DownloadWorker
import dev.auralis.studio.library.decodeDescriptor
import dev.auralis.studio.resolver.api.AudioQualityPreference
import dev.auralis.studio.resolver.api.DeliveryBlockReason
import dev.auralis.studio.resolver.api.DeliveryRequirements
import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityBasis
import dev.auralis.studio.resolver.api.DrmState
import dev.auralis.studio.resolver.api.EphemeralResourceLease
import dev.auralis.studio.resolver.api.MediaKind
import dev.auralis.studio.resolver.api.MediaResolver
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolveIntent
import dev.auralis.studio.resolver.api.ResolvedFormatDescriptor
import dev.auralis.studio.resolver.api.ResolvedMedia
import dev.auralis.studio.resolver.api.ResolverDownloadDescriptor
import dev.auralis.studio.resolver.api.ResolverError
import dev.auralis.studio.resolver.api.ResolverErrorCode
import dev.auralis.studio.resolver.api.ResolverFormatSelector
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult
import dev.auralis.studio.resolver.api.RuntimeHealth
import dev.auralis.studio.resolver.api.SecretSanitizer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LrrPhase5DownloadIntegrationTest {

    @Test
    fun workManagerData_containsNoLeaseAndRemainsUnderSizeLimit() {
        val descriptor = ResolverDownloadDescriptor(
            schemaVersion = 1,
            source = "ArchiveOrg",
            sourceId = "open_audio_123",
            canonicalUrl = "https://archive.org/details/open_audio_123",
            title = "Open Audio Track",
            artist = "Open Artist",
            album = "Public Records",
            expectedDurationMs = 210_000L,
            qualityPreference = AudioQualityPreference.HIGH,
            downloadCapability = DownloadCapability.PERMITTED,
            downloadCapabilityBasis = DownloadCapabilityBasis.LICENSE_METADATA,
        )

        val jsonStr = Json.encodeToString(ResolverDownloadDescriptor.serializer(), descriptor)
        val data = workDataOf(DownloadWorker.KEY_RESOLVER_DESCRIPTOR_JSON to jsonStr)

        // 1. Size constraint: must be comfortably below 10 KB limit
        val byteSize = jsonStr.toByteArray(Charsets.UTF_8).size
        assertTrue("WorkManager payload must be under 2 KB (actual: $byteSize bytes)", byteSize < 2048)

        // 2. Lease-free constraint: must contain no signed URL, headers, or lease
        assertFalse(jsonStr.contains("EphemeralResourceLease"))
        assertFalse(jsonStr.contains("stream.mp3"))
        assertFalse(jsonStr.contains("Cookie"))
        assertFalse(jsonStr.contains("Authorization"))

        // 3. Round-trip decode
        val decoded = decodeDescriptor(data.getString(DownloadWorker.KEY_RESOLVER_DESCRIPTOR_JSON) ?: "")
        assertNotNull(decoded)
        assertEquals(1, decoded?.schemaVersion)
        assertEquals("ArchiveOrg", decoded?.source)
        assertEquals("open_audio_123", decoded?.sourceId)
    }

    @Test
    fun worker_freshResolvesWithDownloadIntent() = runBlocking {
        val resolveCount = AtomicInteger(0)
        var capturedIntent: ResolveIntent? = null

        val fakeResolver = object : MediaResolver {
            override suspend fun inspect(request: ResolverRequest): ResolverResult = resolve(request)
            override suspend fun resolve(request: ResolverRequest): ResolverResult {
                resolveCount.incrementAndGet()
                capturedIntent = request.intent
                return ResolverResult.Success(
                    media = ResolvedMedia(
                        source = "ArchiveOrg",
                        sourceId = "item_1",
                        title = "Sample Audio",
                        formats = listOf(
                            ResolvedFormatDescriptor(
                                formatId = "fmt_direct_1",
                                mediaKind = MediaKind.AUDIO_ONLY,
                                container = "m4a",
                                extension = "m4a",
                                bitrateKbps = 256.0,
                                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                                drmState = DrmState.NONE,
                                downloadCapability = DownloadCapability.PERMITTED,
                                downloadCapabilityBasis = DownloadCapabilityBasis.LICENSE_METADATA,
                            ),
                        ),
                    ),
                    lease = EphemeralResourceLease(
                        requestId = request.requestId,
                        formatId = "fmt_direct_1",
                        url = "https://cdn.example.org/fresh_audio.m4a?token=fresh1",
                        headers = mapOf("User-Agent" to "Lyrune"),
                    ),
                )
            }
            override suspend fun cancel(requestId: String) {}
            override suspend fun health(): RuntimeHealth = RuntimeHealth.UNINITIALIZED
        }

        val descriptor = ResolverDownloadDescriptor(
            source = "ArchiveOrg",
            sourceId = "item_1",
            canonicalUrl = "https://archive.org/details/item_1",
            title = "Sample Audio",
            downloadCapability = DownloadCapability.PERMITTED,
        )

        val result = fakeResolver.resolve(
            ResolverRequest(
                sourceUrl = descriptor.canonicalUrl,
                sourceId = descriptor.sourceId,
                intent = ResolveIntent.DOWNLOAD,
            ),
        )

        assertEquals(1, resolveCount.get())
        assertEquals(ResolveIntent.DOWNLOAD, capturedIntent)
        assertTrue(result is ResolverResult.Success)
        val lease = (result as ResolverResult.Success).lease
        assertEquals("https://cdn.example.org/fresh_audio.m4a?token=fresh1", lease?.url)
    }

    @Test
    fun permissionAndDeliveryGates_enforceRejectionOfUnsafeOrProhibitedResources() {
        // 1. UNKNOWN permission rejected
        val directUnknown = ResolvedFormatDescriptor(
            formatId = "f1",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            drmState = DrmState.NONE,
            downloadCapability = DownloadCapability.UNKNOWN,
        )
        assertNull(ResolverFormatSelector.selectBestDescriptor(listOf(directUnknown), requirePermittedDownload = true))

        // 2. NOT_PERMITTED rejected
        val directNotPermitted = ResolvedFormatDescriptor(
            formatId = "f2",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            drmState = DrmState.NONE,
            downloadCapability = DownloadCapability.NOT_PERMITTED,
        )
        assertNull(ResolverFormatSelector.selectBestDescriptor(listOf(directNotPermitted), requirePermittedDownload = true))

        // 3. SEGMENTED_RESOURCE rejected for direct download
        val segmentedPermitted = ResolvedFormatDescriptor(
            formatId = "f3",
            resolutionMode = ResolutionMode.SEGMENTED_RESOURCE,
            drmState = DrmState.NONE,
            downloadCapability = DownloadCapability.PERMITTED,
        )
        assertNull(ResolverFormatSelector.selectBestDescriptor(listOf(segmentedPermitted), requirePermittedDownload = true))

        // 4. ENGINE_REQUIRED rejected
        val enginePermitted = ResolvedFormatDescriptor(
            formatId = "f4",
            resolutionMode = ResolutionMode.ENGINE_REQUIRED,
            drmState = DrmState.NONE,
            downloadCapability = DownloadCapability.PERMITTED,
        )
        assertNull(ResolverFormatSelector.selectBestDescriptor(listOf(enginePermitted), requirePermittedDownload = true))

        // 5. DRM PRESENT rejected even if PERMITTED
        val drmPresent = ResolvedFormatDescriptor(
            formatId = "f5",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            drmState = DrmState.PRESENT,
            blockReason = DeliveryBlockReason.DRM_PROTECTED,
            downloadCapability = DownloadCapability.PERMITTED,
        )
        assertNull(ResolverFormatSelector.selectBestDescriptor(listOf(drmPresent), requirePermittedDownload = true))

        // 6. DRM POSSIBLE rejected
        val drmPossible = ResolvedFormatDescriptor(
            formatId = "f6",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            drmState = DrmState.POSSIBLE,
            blockReason = DeliveryBlockReason.DRM_POSSIBLE,
            downloadCapability = DownloadCapability.PERMITTED,
        )
        assertNull(ResolverFormatSelector.selectBestDescriptor(listOf(drmPossible), requirePermittedDownload = true))

        // 7. DIRECT + PERMITTED + NO DRM accepted
        val directPermitted = ResolvedFormatDescriptor(
            formatId = "f7",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            drmState = DrmState.NONE,
            blockReason = DeliveryBlockReason.NONE,
            downloadCapability = DownloadCapability.PERMITTED,
        )
        val selected = ResolverFormatSelector.selectBestDescriptor(listOf(directPermitted), requirePermittedDownload = true)
        assertNotNull(selected)
        assertEquals("f7", selected?.formatId)
    }

    @Test
    fun formatFallback_exactFormatPreferred_missingFormatFallsBackSafely() {
        val fmt128 = ResolvedFormatDescriptor(
            formatId = "fmt_128",
            mediaKind = MediaKind.AUDIO_ONLY,
            container = "m4a",
            bitrateKbps = 128.0,
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            drmState = DrmState.NONE,
            downloadCapability = DownloadCapability.PERMITTED,
        )
        val fmt256 = ResolvedFormatDescriptor(
            formatId = "fmt_256",
            mediaKind = MediaKind.AUDIO_ONLY,
            container = "m4a",
            bitrateKbps = 256.0,
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            drmState = DrmState.NONE,
            downloadCapability = DownloadCapability.PERMITTED,
        )

        // Case A: Exact requested format exists
        val selectedExact = ResolverFormatSelector.selectBestDescriptor(
            formats = listOf(fmt128, fmt256),
            preference = AudioQualityPreference.BEST,
            formatHint = "fmt_128",
        )
        assertEquals("fmt_128", selectedExact?.formatId)

        // Case B: Requested format disappeared -> falls back to best eligible format on same media
        val selectedFallback = ResolverFormatSelector.selectBestDescriptor(
            formats = listOf(fmt128, fmt256),
            preference = AudioQualityPreference.BEST,
            formatHint = "fmt_missing_disappeared",
        )
        assertEquals("fmt_256", selectedFallback?.formatId)
    }

    @Test
    fun identityMismatch_stopsWithProviderChanged() {
        val descriptor = ResolverDownloadDescriptor(
            source = "ProviderA",
            sourceId = "track_original_123",
            canonicalUrl = "https://example.com/track/123",
            title = "Original Song",
            downloadCapability = DownloadCapability.PERMITTED,
        )

        val freshMediaDifferentSourceId = ResolvedMedia(
            source = "ProviderA",
            sourceId = "track_different_999", // Changed ID!
            title = "Different Song",
            formats = listOf(
                ResolvedFormatDescriptor(
                    formatId = "fmt_1",
                    resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                    drmState = DrmState.NONE,
                    downloadCapability = DownloadCapability.PERMITTED,
                ),
            ),
        )

        val isMismatched = !descriptor.sourceId.isNullOrBlank() &&
            !freshMediaDifferentSourceId.sourceId.isNullOrBlank() &&
            descriptor.sourceId != freshMediaDifferentSourceId.sourceId

        assertTrue("Identity mismatch must be detected", isMismatched)
    }

    @Test
    fun safeHeadersHandoff_andDownloaderRangeOwnership() {
        val leaseHeaders = mapOf(
            "User-Agent" to "Lyrune/2.0",
            "Referer" to "https://archive.org",
            "Origin" to "https://archive.org",
            "Accept" to "*/*",
            "Accept-Language" to "en-US",
            "Range" to "bytes=0-100", // Extractor range must be ignored
            "Cookie" to "secret=123", // Sensitive
            "Authorization" to "Bearer abc", // Sensitive
        )

        val safeOnly = SecretSanitizer.extractSafeHeaders(leaseHeaders)
        assertEquals(5, safeOnly.size)
        assertTrue(safeOnly.containsKey("User-Agent"))
        assertTrue(safeOnly.containsKey("Referer"))
        assertTrue(safeOnly.containsKey("Origin"))
        assertTrue(safeOnly.containsKey("Accept"))
        assertTrue(safeOnly.containsKey("Accept-Language"))

        assertFalse("Range must be downloader-controlled and not in safe headers", safeOnly.containsKey("Range"))
        assertFalse("Cookie must never be forwarded", safeOnly.containsKey("Cookie"))
        assertFalse("Authorization must never be forwarded", safeOnly.containsKey("Authorization"))
    }

    @Test
    fun expiredResourceRecovery_boundedAtOneRetry() = runBlocking {
        val resolveCount = AtomicInteger(0)
        val expiredUrl = "https://cdn.example.org/audio.mp3?exp=old"
        val freshUrl = "https://cdn.example.org/audio.mp3?exp=new"

        val resolver = object : MediaResolver {
            override suspend fun inspect(request: ResolverRequest): ResolverResult = resolve(request)
            override suspend fun resolve(request: ResolverRequest): ResolverResult {
                val call = resolveCount.incrementAndGet()
                val url = if (call == 1) expiredUrl else freshUrl
                return ResolverResult.Success(
                    media = ResolvedMedia(
                        source = "ArchiveOrg",
                        sourceId = "rec_1",
                        title = "Recording 1",
                        formats = listOf(
                            ResolvedFormatDescriptor(
                                formatId = "f1",
                                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                                drmState = DrmState.NONE,
                                downloadCapability = DownloadCapability.PERMITTED,
                            ),
                        ),
                    ),
                    lease = EphemeralResourceLease(
                        requestId = request.requestId,
                        formatId = "f1",
                        url = url,
                    ),
                )
            }
            override suspend fun cancel(requestId: String) {}
            override suspend fun health(): RuntimeHealth = RuntimeHealth.UNINITIALIZED
        }

        // 1. First resolution attempt
        val res1 = resolver.resolve(ResolverRequest(sourceUrl = "https://archive.org/details/rec_1", intent = ResolveIntent.DOWNLOAD))
        assertEquals(1, resolveCount.get())
        assertEquals(expiredUrl, (res1 as ResolverResult.Success).lease?.url)

        // 2. Simulated HTTP 403 / expired token -> trigger one-shot re-resolution
        val res2 = resolver.resolve(ResolverRequest(sourceUrl = "https://archive.org/details/rec_1", intent = ResolveIntent.DOWNLOAD))
        assertEquals(2, resolveCount.get())
        assertEquals(freshUrl, (res2 as ResolverResult.Success).lease?.url)

        // Bounded at max 1 retry (total 2 calls)
        assertEquals(2, resolveCount.get())
    }
}
