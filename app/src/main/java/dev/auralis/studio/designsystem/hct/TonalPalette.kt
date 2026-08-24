package dev.auralis.studio.designsystem.hct

import java.util.concurrent.ConcurrentHashMap

/**
 * A TonalPalette provides a range of tones (lightness values) for a given Hue and Chroma.
 * Tones range from 0 (pure black) to 100 (pure white).
 */
class TonalPalette private constructor(
    val hue: Double,
    val chroma: Double,
) {
    private val cache = ConcurrentHashMap<Int, Int>()

    fun tone(tone: Int): Int {
        val clampedTone = tone.coerceIn(0, 100)
        return cache.computeIfAbsent(clampedTone) { t ->
            Hct.from(hue, chroma, t.toDouble()).argb
        }
    }

    companion object {
        fun fromInt(argb: Int): TonalPalette {
            val hct = Hct.fromInt(argb)
            return TonalPalette(hct.hue, hct.chroma)
        }

        fun fromHueAndChroma(hue: Double, chroma: Double): TonalPalette {
            return TonalPalette(hue, chroma)
        }
    }
}
