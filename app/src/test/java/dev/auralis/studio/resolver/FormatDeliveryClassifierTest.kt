package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.DeliveryBlockReason
import dev.auralis.studio.resolver.api.FormatDeliveryClassifier
import dev.auralis.studio.resolver.api.ResolutionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDeliveryClassifierTest {

    @Test
    fun directHttpsAudio_classifiedAsDirectResource() {
        val result = FormatDeliveryClassifier.classifyWithReason(
            protocol = "https",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
            headers = mapOf("User-Agent" to "Lyrune/1.0", "Accept" to "*/*"),
        )
        assertEquals(ResolutionMode.DIRECT_RESOURCE, result.resolutionMode)
        assertEquals(DeliveryBlockReason.NONE, result.blockReason)
    }

    @Test
    fun hlsProtocol_classifiedAsSegmentedResource() {
        val res1 = FormatDeliveryClassifier.classifyWithReason(
            protocol = "m3u8_native",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
        )
        assertEquals(ResolutionMode.SEGMENTED_RESOURCE, res1.resolutionMode)
        assertEquals(DeliveryBlockReason.SEGMENTED_STREAM, res1.blockReason)

        val res2 = FormatDeliveryClassifier.classifyWithReason(
            protocol = "https",
            manifestUrl = "https://example.com/playlist.m3u8",
            hasFragments = false,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
        )
        assertEquals(ResolutionMode.SEGMENTED_RESOURCE, res2.resolutionMode)
        assertEquals(DeliveryBlockReason.SEGMENTED_STREAM, res2.blockReason)
    }

    @Test
    fun dashProtocolOrFragments_classifiedAsSegmentedResource() {
        val res1 = FormatDeliveryClassifier.classifyWithReason(
            protocol = "http_dash_segments",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
        )
        assertEquals(ResolutionMode.SEGMENTED_RESOURCE, res1.resolutionMode)
        assertEquals(DeliveryBlockReason.SEGMENTED_STREAM, res1.blockReason)

        val res2 = FormatDeliveryClassifier.classifyWithReason(
            protocol = "https",
            manifestUrl = null,
            hasFragments = true,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
        )
        assertEquals(ResolutionMode.SEGMENTED_RESOURCE, res2.resolutionMode)
        assertEquals(DeliveryBlockReason.SEGMENTED_STREAM, res2.blockReason)
    }

    @Test
    fun compoundCombinations_precedenceEnsuresHardBlockersWin() {
        // 1. https + fragments -> SEGMENTED
        val resFragments = FormatDeliveryClassifier.classifyWithReason(
            protocol = "https",
            manifestUrl = null,
            hasFragments = true,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
        )
        assertEquals(ResolutionMode.SEGMENTED_RESOURCE, resFragments.resolutionMode)
        assertEquals(DeliveryBlockReason.SEGMENTED_STREAM, resFragments.blockReason)

        // 2. https + DRM -> ENGINE_REQUIRED (DRM_PROTECTED)
        val resDrm = FormatDeliveryClassifier.classifyWithReason(
            protocol = "https",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = false,
            hasDrm = true,
            impersonate = null,
        )
        assertEquals(ResolutionMode.ENGINE_REQUIRED, resDrm.resolutionMode)
        assertEquals(DeliveryBlockReason.DRM_PROTECTED, resDrm.blockReason)

        // 3. https + request_data -> ENGINE_REQUIRED (UNSUPPORTED_POST_DATA)
        val resPost = FormatDeliveryClassifier.classifyWithReason(
            protocol = "https",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = true,
            hasDrm = false,
            impersonate = null,
        )
        assertEquals(ResolutionMode.ENGINE_REQUIRED, resPost.resolutionMode)
        assertEquals(DeliveryBlockReason.UNSUPPORTED_POST_DATA, resPost.blockReason)

        // 4. https + Authorization header -> ENGINE_REQUIRED (SENSITIVE_AUTH_REQUIRED)
        val resAuth = FormatDeliveryClassifier.classifyWithReason(
            protocol = "https",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
            headers = mapOf("Authorization" to "Bearer token123"),
        )
        assertEquals(ResolutionMode.ENGINE_REQUIRED, resAuth.resolutionMode)
        assertEquals(DeliveryBlockReason.SENSITIVE_AUTH_REQUIRED, resAuth.blockReason)

        // 5. https + browser impersonation -> ENGINE_REQUIRED (UNSUPPORTED_BROWSER_TARGET)
        val resImpersonate = FormatDeliveryClassifier.classifyWithReason(
            protocol = "https",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = false,
            hasDrm = false,
            impersonate = "chrome-110",
        )
        assertEquals(ResolutionMode.ENGINE_REQUIRED, resImpersonate.resolutionMode)
        assertEquals(DeliveryBlockReason.UNSUPPORTED_BROWSER_TARGET, resImpersonate.blockReason)

        // 6. Precedence test: DRM + Fragments + POST -> DRM takes top precedence
        val resDrmDominates = FormatDeliveryClassifier.classifyWithReason(
            protocol = "https",
            manifestUrl = null,
            hasFragments = true,
            hasRequestData = true,
            hasDrm = true,
            impersonate = "chrome",
        )
        assertEquals(ResolutionMode.ENGINE_REQUIRED, resDrmDominates.resolutionMode)
        assertEquals(DeliveryBlockReason.DRM_PROTECTED, resDrmDominates.blockReason)
    }
}
