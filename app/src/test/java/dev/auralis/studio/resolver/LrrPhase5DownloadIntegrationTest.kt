package dev.auralis.studio.resolver

import androidx.work.workDataOf
import dev.auralis.studio.library.DownloadWorker
import dev.auralis.studio.library.ExpiredResourceClassifier
import dev.auralis.studio.library.RecordingIdentityRevalidator
import dev.auralis.studio.library.SafeDownloadUrlValidator
import dev.auralis.studio.library.decodeDescriptor
import dev.auralis.studio.resolver.api.AudioQualityPreference
import dev.auralis.studio.resolver.api.DeliveryBlockReason
import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityBasis
import dev.auralis.studio.resolver.api.DownloadCapabilityEvaluator
import dev.auralis.studio.resolver.api.DrmState
import dev.auralis.studio.resolver.api.EphemeralResourceLease
import dev.auralis.studio.resolver.api.MediaKind
import dev.auralis.studio.resolver.api.MediaResolver
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolveIntent
import dev.auralis.studio.resolver.api.ResolvedFormatDescriptor
import dev.auralis.studio.resolver.api.ResolvedMedia
import dev.auralis.studio.resolver.api.ResolverDownloadDescriptor
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
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

class LrrPhase5DownloadIntegrationTest {

    @Test
    fun descriptorDefaults_toUnknownCapabilityAndDoesNotManufacturePermission() {
        val defaultDescriptor = ResolverDownloadDescriptor(
            source = "TestArchive",
            canonicalUrl = "https://archive.org/details/test_track",
            title = "Test Track",
        )

        // 1. Default capability must be UNKNOWN
        assertEquals(DownloadCapability.UNKNOWN, defaultDescriptor.downloadCapability)
        assertEquals(DownloadCapabilityBasis.NONE, defaultDescriptor.downloadCapabilityBasis)

        // 2. UNKNOWN capability alone must not be authorized to download
        val directFormat = ResolvedFormatDescriptor(
            formatId = "fmt_1",
            resolutionMode = ResolutionMode.DIRECT_RESOURCE,
            drmState = DrmState.NONE,
            downloadCapability = defaultDescriptor.downloadCapability,
        )
        assertNull(
            "Default descriptor capability must not grant download authorization",
            ResolverFormatSelector.selectBestDescriptor(listOf(directFormat), requirePermittedDownload = true),
        )
    }

    @Test
    fun freshAuthorization_isAuthoritativeOverPersistedDescriptor() {
        // Case A: Persisted PERMITTED, but fresh resolution evaluates to UNKNOWN -> REJECT
        val freshMediaUnknown = ResolvedMedia(
            source = "CustomProvider",
            sourceId = "track_1",
            title = "Track 1",
            formats = listOf(
                ResolvedFormatDescriptor(
                    formatId = "fmt_1",
                    resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                    drmState = DrmState.NONE,
                    downloadCapability = DownloadCapability.UNKNOWN,
                ),
            ),
        )
        val selectedA = ResolverFormatSelector.selectBestDescriptor(
            DownloadCapabilityEvaluator.annotateMedia(freshMediaUnknown).formats,
            requirePermittedDownload = true,
        )
        assertNull("Persisted PERMITTED + Fresh UNKNOWN must be rejected", selectedA)

        // Case B: Persisted PERMITTED, but fresh resolution evaluates to NOT_PERMITTED -> REJECT
        val freshMediaNotPermitted = ResolvedMedia(
            source = "CustomProvider",
            sourceId = "track_1",
            title = "Track 1",
            formats = listOf(
                ResolvedFormatDescriptor(
                    formatId = "fmt_1",
                    resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                    drmState = DrmState.NONE,
                    downloadCapability = DownloadCapability.NOT_PERMITTED,
                ),
            ),
        )
        val selectedB = ResolverFormatSelector.selectBestDescriptor(
            DownloadCapabilityEvaluator.annotateMedia(freshMediaNotPermitted).formats,
            requirePermittedDownload = true,
        )
        assertNull("Persisted PERMITTED + Fresh NOT_PERMITTED must be rejected", selectedB)

        // Case C: Persisted UNKNOWN, but fresh resolution evaluates to PERMITTED -> AUTHORIZED
        val freshMediaPermitted = ResolvedMedia(
            source = "Jamendo",
            sourceId = "jam_1",
            title = "Creative Commons Song",
            formats = listOf(
                ResolvedFormatDescriptor(
                    formatId = "fmt_1",
                    resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                    drmState = DrmState.NONE,
                    downloadCapability = DownloadCapability.PERMITTED,
                ),
            ),
        )
        val annotated = DownloadCapabilityEvaluator.annotateMedia(freshMediaPermitted)
        val selectedC = ResolverFormatSelector.selectBestDescriptor(annotated.formats, requirePermittedDownload = true)
        assertNotNull("Persisted UNKNOWN + Fresh PERMITTED must be authorized", selectedC)
        assertEquals("fmt_1", selectedC?.formatId)
    }

    @Test
    fun workManagerData_containsNoLeaseAndRemainsUnderSizeLimit() {
        val descriptor = ResolverDownloadDescriptor(
            schemaVersion = 1,
            source = "ArchiveOrg",
            sourceId = "open_audio_123",
            canonicalUrl = "https://archive.org/details/open_audio_123",
            title = "Open Audio Track with a very long descriptive title that tests bounding of data payloads",
            artist = "Open Artist Collective",
            album = "Public Records Worldwide Edition",
            artworkUrl = "https://archive.org/services/img/open_audio_123",
            expectedDurationMs = 210_000L,
            qualityPreference = AudioQualityPreference.HIGH,
            formatHint = "mp3_high",
            downloadCapability = DownloadCapability.UNKNOWN,
            downloadCapabilityBasis = DownloadCapabilityBasis.NONE,
        )

        val jsonStr = Json.encodeToString(ResolverDownloadDescriptor.serializer(), descriptor)
        val data = workDataOf(DownloadWorker.KEY_RESOLVER_DESCRIPTOR_JSON to jsonStr)

        val byteSize = jsonStr.toByteArray(Charsets.UTF_8).size
        assertTrue("WorkManager payload must be under 2 KB (actual: $byteSize bytes)", byteSize < 2048)

        assertFalse(jsonStr.contains("EphemeralResourceLease"))
        assertFalse(jsonStr.contains("stream.mp3"))
        assertFalse(jsonStr.contains("Cookie"))
        assertFalse(jsonStr.contains("Authorization"))

        val decoded = decodeDescriptor(data.getString(DownloadWorker.KEY_RESOLVER_DESCRIPTOR_JSON) ?: "")
        assertNotNull(decoded)
        assertEquals(1, decoded?.schemaVersion)
        assertEquals("ArchiveOrg", decoded?.source)
        assertEquals("open_audio_123", decoded?.sourceId)
    }

    @Test
    fun expiredResourceClassifier_distinguishesExpirationFromGenericAuthFailures() {
        val activeLease = EphemeralResourceLease(
            requestId = "req_1",
            formatId = "fmt_1",
            url = "https://cdn.example.org/track.mp3",
            knownExpiryEpochMs = System.currentTimeMillis() + 3_600_000L, // 1 hour in future
        )

        val expiredLease = EphemeralResourceLease(
            requestId = "req_2",
            formatId = "fmt_2",
            url = "https://cdn.example.org/track.mp3",
            knownExpiryEpochMs = System.currentTimeMillis() - 10_000L, // Expired 10s ago
        )

        // 1. Generic 403 without expiry evidence -> NO refresh
        assertFalse(
            "Generic 403 must not trigger lease refresh",
            ExpiredResourceClassifier.shouldRefreshExpiredLease(activeLease, null, "HTTP download failed with code 403"),
        )

        // 2. Generic 401 without expiry evidence -> NO refresh
        assertFalse(
            "Generic 401 must not trigger lease refresh",
            ExpiredResourceClassifier.shouldRefreshExpiredLease(activeLease, null, "HTTP download failed with code 401"),
        )

        // 3. Expired lease with 403 -> REFRESH
        assertTrue(
            "Expired lease with 403 must trigger refresh",
            ExpiredResourceClassifier.shouldRefreshExpiredLease(expiredLease, null, "HTTP download failed with code 403"),
        )

        // 4. Active lease with explicit CDN expiration message -> REFRESH
        assertTrue(
            "Explicit URL expired text must trigger refresh",
            ExpiredResourceClassifier.shouldRefreshExpiredLease(activeLease, null, "HTTP 403: Request has expired"),
        )
    }

    @Test
    fun redirectSsrfSafety_rejectsPrivateAndLoopbackTargets() {
        // 1. Loopback IPv4
        assertFalse("127.0.0.1 must be rejected", SafeDownloadUrlValidator.isSafeUrl("http://127.0.0.1/audio.mp3"))
        assertFalse("localhost must be rejected", SafeDownloadUrlValidator.isSafeUrl("http://localhost/audio.mp3"))

        // 2. Private IPv4 ranges (RFC 1918)
        assertFalse("10.0.0.1 must be rejected", SafeDownloadUrlValidator.isSafeUrl("http://10.0.0.1/audio.mp3"))
        assertFalse("172.16.0.1 must be rejected", SafeDownloadUrlValidator.isSafeUrl("http://172.16.0.1/audio.mp3"))
        assertFalse("192.168.1.1 must be rejected", SafeDownloadUrlValidator.isSafeUrl("http://192.168.1.1/audio.mp3"))

        // 3. Link-local IPv4
        assertFalse("169.254.1.1 must be rejected", SafeDownloadUrlValidator.isSafeUrl("http://169.254.1.1/audio.mp3"))

        // 4. IPv6 Loopback and ULA / Link-local
        assertFalse("::1 must be rejected", SafeDownloadUrlValidator.isSafeAddress(InetAddress.getByName("::1")))
        assertFalse("fe80::1 must be rejected", SafeDownloadUrlValidator.isSafeAddress(InetAddress.getByName("fe80::1")))
        assertFalse("fc00::1 must be rejected", SafeDownloadUrlValidator.isSafeAddress(InetAddress.getByName("fc00::1")))

        // 5. Unsafe schemes
        assertFalse("file:// scheme must be rejected", SafeDownloadUrlValidator.isSafeUrl("file:///data/audio.mp3"))
        assertFalse("content:// scheme must be rejected", SafeDownloadUrlValidator.isSafeUrl("content://media/audio/1"))
        assertFalse("ftp:// scheme must be rejected", SafeDownloadUrlValidator.isSafeUrl("ftp://example.com/audio.mp3"))
    }

    @Test
    fun recordingIdentityRevalidation_coversAllPrecedenceRules() {
        // Case 1: Same sourceId -> PASS
        assertTrue(
            "Same sourceId must establish identity",
            RecordingIdentityRevalidator.isSameRecording(
                persistedSourceId = "song_123",
                persistedArtist = "Artist A",
                persistedTitle = "Title A",
                freshSourceId = "song_123",
                freshArtist = "Artist A",
                freshTitle = "Title A",
            ),
        )

        // Case 2: Different sourceId -> FAIL (ProviderChanged)
        assertFalse(
            "Different sourceId must fail identity",
            RecordingIdentityRevalidator.isSameRecording(
                persistedSourceId = "song_123",
                persistedArtist = "Artist A",
                persistedTitle = "Title A",
                freshSourceId = "song_999",
                freshArtist = "Artist A",
                freshTitle = "Title A",
            ),
        )

        // Case 3: Both sourceIds null + same normalized artist and title -> PASS
        assertTrue(
            "Null sourceIds with matching normalized identity must pass",
            RecordingIdentityRevalidator.isSameRecording(
                persistedSourceId = null,
                persistedArtist = "Daft Punk",
                persistedTitle = "Get Lucky",
                freshSourceId = null,
                freshArtist = "Daft Punk",
                freshTitle = "Get Lucky",
            ),
        )

        // Case 4: Both sourceIds null + different artist -> FAIL
        assertFalse(
            "Null sourceIds with different artist must fail",
            RecordingIdentityRevalidator.isSameRecording(
                persistedSourceId = null,
                persistedArtist = "Daft Punk",
                persistedTitle = "Get Lucky",
                freshSourceId = null,
                freshArtist = "Pharrell Williams",
                freshTitle = "Get Lucky",
            ),
        )

        // Case 5: Original vs Live / Remix version qualifier -> FAIL
        assertFalse(
            "Original vs Live qualifier must fail identity revalidation",
            RecordingIdentityRevalidator.isSameRecording(
                persistedSourceId = null,
                persistedArtist = "Queen",
                persistedTitle = "Bohemian Rhapsody",
                freshSourceId = null,
                freshArtist = "Queen",
                freshTitle = "Bohemian Rhapsody (Live at Wembley)",
            ),
        )
    }

    @Test
    fun safeHeadersHandoff_andDownloaderRangeOwnership() {
        val leaseHeaders = mapOf(
            "User-Agent" to "Lyrune/2.0",
            "Referer" to "https://archive.org",
            "Origin" to "https://archive.org",
            "Accept" to "*/*",
            "Accept-Language" to "en-US",
            "Range" to "bytes=0-100",
            "Cookie" to "secret=123",
            "Authorization" to "Bearer abc",
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
}
