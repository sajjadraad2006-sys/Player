package dev.auralis.desktop.resolver

import dev.auralis.studio.resolver.api.ResolverRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopResolverRuntimeTest {

    @Test
    fun testStaticInstallationCheckSpawnsZeroProcesses() = runBlocking {
        val resolver = DesktopMediaResolver()

        // 1. Static installation check must not spawn Python
        val status = resolver.getInstallationStatus()
        assertEquals(ResolverLifecycleState.STOPPED, resolver.state.value)
        assertNotNull(status.runtimeType)

        // 2. Public health check with default forceSpawn=false must return without starting worker
        val health = resolver.health(forceSpawn = false)
        assertNotNull(health)
        assertEquals(ResolverLifecycleState.STOPPED, resolver.state.value)
    }

    @Test
    fun testMaxFrameBytesCeiling() {
        assertEquals(1024 * 1024, DesktopMediaResolver.MAX_FRAME_BYTES)
    }

    @Test
    fun testManifestIntegrity() {
        val manifestStream = javaClass.getResourceAsStream("/resolver/manifest.json")
        assertNotNull(manifestStream, "manifest.json should be present in resources")

        val content = manifestStream.bufferedReader().use { it.readText() }
        val json = Json.parseToJsonElement(content).jsonObject

        assertEquals("3.12.10", json["runtimeVersion"]?.jsonPrimitive?.content)
        assertEquals("2026.7.4", json["ytDlpVersion"]?.jsonPrimitive?.content)
        assertEquals(1, json["protocolVersion"]?.jsonPrimitive?.content?.toIntOrNull())

        val files = json["files"]?.jsonObject
        assertNotNull(files)
        assertTrue(files.isNotEmpty(), "Manifest must list files and SHA-256 hashes")
    }

    @Test
    fun testCancellationAndShutdownLifecycle() = runBlocking {
        val resolver = DesktopMediaResolver()
        resolver.cancel("req-test-lifecycle")
        assertEquals(ResolverLifecycleState.STOPPED, resolver.state.value)

        resolver.shutdownWorker()
        assertEquals(ResolverLifecycleState.STOPPED, resolver.state.value)
    }
}
