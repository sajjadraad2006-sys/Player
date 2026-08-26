package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.android.LrrIpcParser
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
        assertFalse(health.ejsInstalled)
        assertFalse(health.ejsCompatible)
        assertFalse(health.jsRuntimePresent)
        assertFalse(health.jsRuntimeExecutable)
        assertFalse(health.jsRuntimeDetectedByYtDlp)
        assertFalse(health.jsChallengeSelfTestReady)
        assertFalse(health.realExtractionReady)
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
            ejsInstalled = true,
            ejsCompatible = true,
            jsRuntimePresent = true,
            jsRuntimeExecutable = true,
            jsRuntimeDetectedByYtDlp = true,
            jsChallengeSelfTestReady = true,
            realExtractionReady = true,
            ffmpegReady = false,
            details = mapOf(
                "python_version" to "3.14.7",
                "yt_dlp_version" to "2026.8.19",
                "ejs_version" to "0.8.0",
                "quickjs_version" to "0.15.1",
            ),
        )

        val encoded = json.encodeToString(RuntimeHealth.serializer(), health)
        val decoded = json.decodeFromString(RuntimeHealth.serializer(), encoded)

        assertTrue(decoded.resolverServiceReady)
        assertTrue(decoded.pythonReady)
        assertTrue(decoded.ytDlpReady)
        assertTrue(decoded.ejsInstalled)
        assertTrue(decoded.ejsCompatible)
        assertTrue(decoded.jsRuntimePresent)
        assertTrue(decoded.jsRuntimeExecutable)
        assertTrue(decoded.jsRuntimeDetectedByYtDlp)
        assertTrue(decoded.jsChallengeSelfTestReady)
        assertTrue(decoded.realExtractionReady)
        assertTrue(decoded.ejsReady)
        assertTrue(decoded.jsRuntimeReady)
        assertEquals("3.14.7", decoded.details["python_version"])
        assertEquals("2026.8.19", decoded.details["yt_dlp_version"])
        assertEquals("0.8.0", decoded.details["ejs_version"])
        assertEquals("0.15.1", decoded.details["quickjs_version"])
    }

    @Test
    fun runtimeHealth_parser_handlesPhase35Payload() {
        val jsonPayload = """
            {
                "resolverServiceReady": true,
                "pythonReady": true,
                "ytDlpReady": true,
                "ejsInstalled": true,
                "ejsCompatible": true,
                "jsRuntimePresent": true,
                "jsRuntimeExecutable": true,
                "jsRuntimeDetectedByYtDlp": true,
                "jsChallengeSelfTestReady": true,
                "realExtractionReady": true,
                "ffmpegReady": false,
                "details": {
                    "python_version": "3.14.0",
                    "yt_dlp_version": "2026.08.19",
                    "ejs_version": "0.8.0",
                    "quickjs_version": "0.15.1",
                    "quickjs_path": "/data/app/lib/x86_64/libqjs.so"
                }
            }
        """.trimIndent()

        val parsed = LrrIpcParser.parseHealthJson(jsonPayload)

        assertTrue(parsed.resolverServiceReady)
        assertTrue(parsed.pythonReady)
        assertTrue(parsed.ytDlpReady)
        assertTrue(parsed.ejsInstalled)
        assertTrue(parsed.ejsCompatible)
        assertTrue(parsed.jsRuntimePresent)
        assertTrue(parsed.jsRuntimeExecutable)
        assertTrue(parsed.jsRuntimeDetectedByYtDlp)
        assertTrue(parsed.jsChallengeSelfTestReady)
        assertTrue(parsed.realExtractionReady)
        assertEquals("0.8.0", parsed.details["ejs_version"])
        assertEquals("0.15.1", parsed.details["quickjs_version"])
    }
}
