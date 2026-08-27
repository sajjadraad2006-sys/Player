package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.FormatDeliveryClassifier
import dev.auralis.studio.resolver.api.ResolutionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDeliveryClassifierTest {

    @Test
    fun directHttpsAudio_classifiedAsDirectResource() {
        val mode = FormatDeliveryClassifier.classify(
            protocol = "https",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
            headers = mapOf("User-Agent" to "Lyrune/1.0", "Accept" to "*/*"),
        )
        assertEquals(ResolutionMode.DIRECT_RESOURCE, mode)
    }

    @Test
    fun hlsProtocol_classifiedAsSegmentedResource() {
        val mode1 = FormatDeliveryClassifier.classify(
            protocol = "m3u8_native",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
        )
        assertEquals(ResolutionMode.SEGMENTED_RESOURCE, mode1)

        val mode2 = FormatDeliveryClassifier.classify(
            protocol = "https",
            manifestUrl = "https://example.com/playlist.m3u8",
            hasFragments = false,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
        )
        assertEquals(ResolutionMode.SEGMENTED_RESOURCE, mode2)
    }

    @Test
    fun dashProtocolOrFragments_classifiedAsSegmentedResource() {
        val mode1 = FormatDeliveryClassifier.classify(
            protocol = "http_dash_segments",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
        )
        assertEquals(ResolutionMode.SEGMENTED_RESOURCE, mode1)

        val mode2 = FormatDeliveryClassifier.classify(
            protocol = "https",
            manifestUrl = null,
            hasFragments = true,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
        )
        assertEquals(ResolutionMode.SEGMENTED_RESOURCE, mode2)
    }

    @Test
    fun requestDataOrPost_classifiedAsEngineRequired() {
        val mode = FormatDeliveryClassifier.classify(
            protocol = "https",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = true,
            hasDrm = false,
            impersonate = null,
        )
        assertEquals(ResolutionMode.ENGINE_REQUIRED, mode)
    }

    @Test
    fun browserImpersonation_classifiedAsEngineRequired() {
        val mode = FormatDeliveryClassifier.classify(
            protocol = "https",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = false,
            hasDrm = false,
            impersonate = "chrome-110",
        )
        assertEquals(ResolutionMode.ENGINE_REQUIRED, mode)
    }

    @Test
    fun drmProtectedFormat_classifiedAsEngineRequired() {
        val mode = FormatDeliveryClassifier.classify(
            protocol = "https",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = false,
            hasDrm = true,
            impersonate = null,
        )
        assertEquals(ResolutionMode.ENGINE_REQUIRED, mode)
    }

    @Test
    fun sensitiveHeadersRequired_classifiedAsEngineRequired() {
        val modeCookie = FormatDeliveryClassifier.classify(
            protocol = "https",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
            headers = mapOf("Cookie" to "session=secret_token"),
        )
        assertEquals(ResolutionMode.ENGINE_REQUIRED, modeCookie)

        val modeAuth = FormatDeliveryClassifier.classify(
            protocol = "https",
            manifestUrl = null,
            hasFragments = false,
            hasRequestData = false,
            hasDrm = false,
            impersonate = null,
            headers = mapOf("Authorization" to "Bearer secret_jwt"),
        )
        assertEquals(ResolutionMode.ENGINE_REQUIRED, modeAuth)
    }
}
