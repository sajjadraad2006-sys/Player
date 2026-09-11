package dev.auralis.studio.lyrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * High-resolution monotonic playback clock for synchronized lyrics display.
 *
 * Guarantees:
 * 1. Monotonic elapsed time using [nanoTimeProvider] — unaffected by wall-clock/NTP shifts.
 * 2. Accurate position estimation scaled by effective [playbackSpeed] (0.75x, 1.0x, 1.25x, etc.).
 * 3. Instant reset on user seek, pause, resume, buffering, track change, or speed change.
 * 4. Zero polling overhead on non-lyrics screens; smooth interpolation during lyrics view.
 */
class LyricsClock(
    private val nanoTimeProvider: () -> Long = { System.nanoTime() }
) {

    private var basePositionMs: Long = 0L
    private var baseNanos: Long = nanoTimeProvider()
    private var isPlaying: Boolean = false
    private var playbackSpeed: Float = 1.0f

    private val _interpolatedPositionMs = MutableStateFlow(0L)
    val interpolatedPositionMs: StateFlow<Long> = _interpolatedPositionMs.asStateFlow()

    fun update(positionMs: Long, isPlaying: Boolean, speed: Float = 1.0f) {
        this.basePositionMs = max(0L, positionMs)
        this.baseNanos = nanoTimeProvider()
        this.isPlaying = isPlaying
        this.playbackSpeed = if (speed > 0.1f) speed else 1.0f
        _interpolatedPositionMs.value = basePositionMs
    }

    fun onSeek(newPositionMs: Long) {
        this.basePositionMs = max(0L, newPositionMs)
        this.baseNanos = nanoTimeProvider()
        _interpolatedPositionMs.value = basePositionMs
    }

    fun onSpeedChanged(speed: Float, currentPositionMs: Long) {
        this.basePositionMs = max(0L, currentPositionMs)
        this.baseNanos = nanoTimeProvider()
        this.playbackSpeed = if (speed > 0.1f) speed else 1.0f
        _interpolatedPositionMs.value = basePositionMs
    }

    /**
     * Computes the current estimated position in milliseconds based on monotonic elapsed time and speed.
     */
    fun currentPositionMs(nowNanos: Long = nanoTimeProvider()): Long {
        if (!isPlaying) return basePositionMs
        val elapsedNanos = nowNanos - baseNanos
        if (elapsedNanos <= 0L) return basePositionMs

        val elapsedMs = (elapsedNanos / 1_000_000.0) * playbackSpeed
        val estimated = (basePositionMs + elapsedMs).toLong().coerceAtLeast(0L)
        _interpolatedPositionMs.value = estimated
        return estimated
    }

    /**
     * Ticks the clock and emits the updated position. Call from Compose frame or lightweight ticker.
     */
    fun tick(): Long = currentPositionMs()
}
