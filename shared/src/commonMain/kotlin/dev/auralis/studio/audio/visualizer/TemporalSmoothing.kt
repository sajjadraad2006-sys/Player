package dev.auralis.studio.audio.visualizer

import kotlin.math.exp

/** Refresh-rate-independent exponential smoothing used by analysis and rendering. */
object TemporalSmoothing {
    fun coefficient(deltaSeconds: Float, timeConstantSeconds: Float): Float {
        if (deltaSeconds <= 0f) return 0f
        if (timeConstantSeconds <= 0f) return 1f
        return (1f - exp(-deltaSeconds / timeConstantSeconds)).coerceIn(0f, 1f)
    }

    fun next(current: Float, target: Float, deltaSeconds: Float, attackSeconds: Float, releaseSeconds: Float): Float {
        val coefficient = coefficient(deltaSeconds, if (target > current) attackSeconds else releaseSeconds)
        return current + (target - current) * coefficient
    }
}
