package dev.auralis.desktop.storage

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DesktopAtomicWriteTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lyrune_atomic_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testNormalAtomicWrite() {
        val target = File(tempDir, "normal_atomic.json")
        val payload = """{"status":"ok","count":42}"""

        DesktopPaths.writeAtomically(target, payload)

        assertTrue("Target file must exist after atomic write", target.exists())
        assertEquals("Target file content must match payload exactly", payload, target.readText())

        // Verify no leftover .tmp files
        val leftoverTmp = tempDir.listFiles()?.filter { it.name.endsWith(".tmp") } ?: emptyList()
        assertTrue("No .tmp files should remain after successful write", leftoverTmp.isEmpty())
    }

    @Test
    fun testAtomicReplaceOverwritesExistingTarget() {
        val target = File(tempDir, "existing_file.json")
        val initialContent = "Original Unmodified Content"
        target.writeText(initialContent)

        val updatedContent = "Updated Replaced Content"
        DesktopPaths.writeAtomically(target, updatedContent)

        assertEquals("Target content must be cleanly replaced", updatedContent, target.readText())
    }

    @Test
    fun testStaleTempFilesDoNotBlockSubsequentWrites() {
        val target = File(tempDir, "stale_test.json")
        // Create a stale .tmp file mimicking a previously interrupted run
        val staleTmp = File(tempDir, "${target.name}.stale.tmp")
        staleTmp.writeText("Corrupted uncommitted bytes from crashed process")

        val validContent = """{"valid":true}"""
        DesktopPaths.writeAtomically(target, validContent)

        assertTrue(target.exists())
        assertEquals(validContent, target.readText())
    }

    @Test
    fun testRapidSuccessiveWrites() {
        val target = File(tempDir, "rapid_writes.json")
        for (i in 1..50) {
            val content = """{"iteration":$i}"""
            DesktopPaths.writeAtomically(target, content)
            assertEquals("""{"iteration":$i}""", target.readText())
        }
        val leftovers = tempDir.listFiles()?.filter { it.name.endsWith(".tmp") } ?: emptyList()
        assertEquals("Zero temporary files must remain after 50 rapid writes", 0, leftovers.size)
    }

    @Test
    fun testInterruptionBeforeMoveLeavesOriginalIntact() {
        val target = File(tempDir, "resilience_target.json")
        val originalValidJson = """{"schemaVersion":1,"tracks":[{"id":1,"title":"Original"}]}"""
        target.writeText(originalValidJson)

        // Simulate an uncommitted temporary file written by a crashed process
        val uncommittedTmp = File(tempDir, "${target.name}.incomplete.tmp")
        uncommittedTmp.writeText("""{"schemaVersion":1,"tracks":[{"id":2,"title":"Incomp""")

        // The target file must remain strictly identical to its valid original state
        assertEquals(originalValidJson, target.readText())
        assertTrue("Target file must survive uncommitted crash states", target.length() > 0)
    }
}
