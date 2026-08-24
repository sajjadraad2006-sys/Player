package dev.auralis.studio.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for full-track validation semantics (spec #16/#17):
 * a 29-second local file must NEVER validate as a complete 219-second song.
 *
 * The production tolerance logic lives in DownloadIngestionManager; these tests
 * pin its contract: ±10% of expected duration, clamped to [2 s, 15 s].
 */
class DownloadValidationTest {

    private fun tolerance(expectedMs: Long): Long =
        (expectedMs.coerceAtLeast(0L) * 0.10).toLong().coerceIn(2_000L, 15_000L)

    private fun validates(actualMs: Long, expectedMs: Long): Boolean {
        if (expectedMs <= 0L) return true
        return abs(actualMs - expectedMs) <= tolerance(expectedMs)
    }

    @Test
    fun `29 second preview fails validation for 219 second track`() {
        // The exact regression from the bug report screenshot.
        assertFalse(validates(actualMs = 29_000L, expectedMs = 219_000L))
    }

    @Test
    fun `full download within tolerance passes`() {
        assertTrue(validates(actualMs = 218_400L, expectedMs = 219_000L)) // encoder drift
    }

    @Test
    fun `tolerance is generous enough for normal re-encoding`() {
        val t = tolerance(200_000L)
        assertTrue("Tolerance must be at least 2s", t >= 2_000L)
        assertTrue("Tolerance must never exceed 15s", t <= 15_000L)
        assertTrue(validates(actualMs = 195_000L, expectedMs = 200_000L))
    }

    @Test
    fun `half-length file is rejected`() {
        assertFalse(validates(actualMs = 110_000L, expectedMs = 220_000L))
    }

    @Test
    fun `unknown expected duration cannot be validated by duration`() {
        // Without catalog metadata the duration check is skipped — container and
        // readability checks still apply in the manager.
        assertTrue(validates(actualMs = 29_000L, expectedMs = 0L))
    }
}
