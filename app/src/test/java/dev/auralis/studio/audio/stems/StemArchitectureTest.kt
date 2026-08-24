package dev.auralis.studio.audio.stems

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StemArchitectureTest {
    private val bytes = "verified-test-model".encodeToByteArray()
    private val descriptor = StemEngineDescriptor(
        engineId = "test", modelId = "two-stem", modelVersion = "1",
        supportedStems = setOf(StemKind.VOCALS, StemKind.INSTRUMENTAL),
        modelBytes = bytes.size.toLong(), modelSha256 = sha(bytes), license = "test",
    )

    @Test fun `cache identity includes model version and parameters`() {
        val first = StemCacheIdentity.create("track", descriptor)
        assertEquals(first, StemCacheIdentity.create("track", descriptor))
        assertNotEquals(first, StemCacheIdentity.create("track", descriptor.copy(modelVersion = "2")))
    }

    @Test fun `manager verifies install and rejects checksum mismatch`() = runTest {
        val directory = Files.createTempDirectory("lyrune-stems").toFile()
        val manager = StemModelManager(directory)
        assertTrue(manager.availability(descriptor) is StemModelAvailability.DownloadRequired)
        assertTrue(manager.install(descriptor, ByteArrayInputStream(bytes)) {} is StemModelAvailability.Installed)
        assertTrue(manager.availability(descriptor) is StemModelAvailability.Installed)
        val bad = descriptor.copy(modelVersion = "bad", modelSha256 = "00".repeat(32))
        assertTrue(manager.install(bad, ByteArrayInputStream(bytes)) {} is StemModelAvailability.Invalid)
        directory.deleteRecursively()
    }

    @Test fun `mixer preserves silence and bounds stacked stems`() {
        assertEquals(0f, StemMixerMath.mix(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 0f)
        val output = StemMixerMath.mix(floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 1f))
        assertTrue(output in -1f..1f)
    }

    private fun sha(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}
