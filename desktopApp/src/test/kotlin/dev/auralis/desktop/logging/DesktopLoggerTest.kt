package dev.auralis.desktop.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DesktopLoggerTest {

    private lateinit var tempDir: File
    private lateinit var testLogFile: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lyrune_log_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        testLogFile = File(tempDir, "test_lyrune.log")
        DesktopLogger.logFileOverride = testLogFile
    }

    @After
    fun tearDown() {
        DesktopLogger.logFileOverride = null
        tempDir.deleteRecursively()
    }

    @Test
    fun testBasicLogging() {
        DesktopLogger.info("AppTest", "Application started successfully")

        assertTrue("Log file must exist on disk", testLogFile.exists())
        val content = testLogFile.readText()
        assertTrue("Log must contain timestamp and tag", content.contains("[INFO]"))
        assertTrue("Log must contain message", content.contains("Application started successfully"))
        assertTrue("Log must contain tag", content.contains("[AppTest]"))
    }

    @Test
    fun testPrivacyRedaction() {
        val sensitiveUrl = "https://api.music.com/v1/stream?token=secret_abc_123&session=user_456"
        val sensitiveKey = "api_key=sk_live_super_secret_key"
        val sensitivePass = "password=SuperSecretPassword123"

        DesktopLogger.info("AuthTest", "Fetching: $sensitiveUrl with $sensitiveKey and $sensitivePass")

        val content = testLogFile.readText()
        assertFalse("Must NOT contain raw secret token", content.contains("secret_abc_123"))
        assertFalse("Must NOT contain raw secret key", content.contains("sk_live_super_secret_key"))
        assertFalse("Must NOT contain raw password", content.contains("SuperSecretPassword123"))

        assertTrue("Must contain sanitized token", content.contains("[REDACTED]") || content.contains("[QUERY_REDACTED]"))
    }

    @Test
    fun testStackTraceCapping() {
        try {
            deepCallStack(30)
        } catch (e: Exception) {
            DesktopLogger.error("CrashTest", "Deep crash simulation", e)
        }

        val content = testLogFile.readText()
        assertTrue("Must log exception class", content.contains("IllegalStateException"))
        assertTrue("Must contain truncation marker", content.contains("truncated"))

        // Count how many "at " lines are present
        val atLines = content.lines().filter { it.trim().startsWith("at ") }
        assertTrue("Stack trace frames must be capped to at most 10 frames", atLines.size <= 10)
    }

    private fun deepCallStack(depth: Int) {
        if (depth <= 0) throw IllegalStateException("Simulated deeply nested exception")
        deepCallStack(depth - 1)
    }
}
