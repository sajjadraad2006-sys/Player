package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.RuntimeHealth
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeHealthTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun runtimeHealth_uninitialized_reportsAccurateNegativeState() {
        val health = RuntimeHealth.UNINITIALIZED

        assertFalse(health.resolverServiceReady)
        assertFalse(health.pythonReady)
        assertFalse(health.ytDlpReady)
        assertFalse(health.ejsReady)
        assertFalse(health.jsRuntimeReady)
        assertFalse(health.ffmpegReady)
        assertEquals("NOT_INITIALIZED", health.details["status"])
    }

    @Test
    fun runtimeHealth_serialization_preservesComponents() {
        val health = RuntimeHealth(
            resolverServiceReady = true,
            pythonReady = true,
            ytDlpReady = true,
            ejsReady = false,
            jsRuntimeReady = false,
            ffmpegReady = false,
            details = mapOf(
                "python_version" to "3.14.7",
                "yt_dlp_version" to "2026.8.19",
                "ejs_version" to "NOT_INSTALLED",
                "quickjs_version" to "NOT_INSTALLED",
            ),
        )

        val encoded = json.encodeToString(RuntimeHealth.serializer(), health)
        val decoded = json.decodeFromString(RuntimeHealth.serializer(), encoded)

        assertTrue(decoded.resolverServiceReady)
        assertTrue(decoded.pythonReady)
        assertTrue(decoded.ytDlpReady)
        assertFalse(decoded.ejsReady)
        assertFalse(decoded.jsRuntimeReady)
        assertEquals("3.14.7", decoded.details["python_version"])
        assertEquals("2026.8.19", decoded.details["yt_dlp_version"])
        assertEquals("NOT_INSTALLED", decoded.details["ejs_version"])
    }
}
