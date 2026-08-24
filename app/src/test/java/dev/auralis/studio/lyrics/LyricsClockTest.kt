package dev.auralis.studio.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsClockTest {

    @Test
    fun initialStateReturnsZero() {
        val clock = LyricsClock()
        assertEquals(0L, clock.tick())
        assertEquals(0L, clock.currentPositionMs())
    }

    @Test
    fun pausedStateClampsToBasePosition() {
        val clock = LyricsClock()
        clock.update(positionMs = 15_000L, isPlaying = false, speed = 1.0f)
        assertEquals(15_000L, clock.tick())
        assertEquals(15_000L, clock.currentPositionMs())
    }

    @Test
    fun playingStateAdvancesMonotonically() {
        var mockNanos = 1_000_000_000L // 1.0s in nanos
        val clock = LyricsClock(nanoTimeProvider = { mockNanos })

        clock.update(positionMs = 10_000L, isPlaying = true, speed = 1.0f)
        assertEquals(10_000L, clock.currentPositionMs())

        // Advance mock time by 500ms
        mockNanos += 500_000_000L
        assertEquals(10_500L, clock.currentPositionMs())

        // Advance mock time by another 1000ms
        mockNanos += 1_000_000_000L
        assertEquals(11_500L, clock.currentPositionMs())
    }

    @Test
    fun playbackSpeedAffectsClockScaling() {
        var mockNanos = 10_000_000_000L
        val clock = LyricsClock(nanoTimeProvider = { mockNanos })

        // 2.0x speed
        clock.update(positionMs = 20_000L, isPlaying = true, speed = 2.0f)
        mockNanos += 1_000_000_000L // 1s wall time -> 2s audio time
        assertEquals(22_000L, clock.currentPositionMs())

        // 0.5x speed
        clock.update(positionMs = 30_000L, isPlaying = true, speed = 0.5f)
        mockNanos += 1_000_000_000L // 1s wall time -> 0.5s audio time
        assertEquals(30_500L, clock.currentPositionMs())
    }

    @Test
    fun seekInstantlyUpdatesPosition() {
        var mockNanos = 5_000_000_000L
        val clock = LyricsClock(nanoTimeProvider = { mockNanos })

        clock.update(positionMs = 10_000L, isPlaying = true, speed = 1.0f)
        mockNanos += 1_000_000_000L
        assertEquals(11_000L, clock.currentPositionMs())

        // Seek backwards to 5000ms
        clock.onSeek(5_000L)
        assertEquals(5_000L, clock.currentPositionMs())

        // Advance 200ms
        mockNanos += 200_000_000L
        assertEquals(5_200L, clock.currentPositionMs())
    }

    @Test
    fun onSpeedChangedSmoothlyPreservesPosition() {
        var mockNanos = 2_000_000_000L
        val clock = LyricsClock(nanoTimeProvider = { mockNanos })

        clock.update(positionMs = 10_000L, isPlaying = true, speed = 1.0f)
        mockNanos += 1_000_000_000L // now at 11_000ms
        assertEquals(11_000L, clock.currentPositionMs())

        // Change speed to 1.5x at current position
        clock.onSpeedChanged(speed = 1.5f, currentPositionMs = 11_000L)
        mockNanos += 1_000_000_000L // +1.5s
        assertEquals(12_500L, clock.currentPositionMs())
    }
}
