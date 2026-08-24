package dev.auralis.studio.designsystem.hct

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * HCT (Hue, Chroma, Tone) color space from Material Design Color Utilities.
 * - Hue: 0.0 .. 360.0
 * - Chroma: 0.0 .. ~120.0 (colorfulness)
 * - Tone: 0.0 .. 100.0 (L* perceptual lightness)
 */
class Hct private constructor(
    val hue: Double,
    val chroma: Double,
    val tone: Double,
    val argb: Int,
) {
    companion object {
        fun fromInt(argb: Int): Hct {
            val cam = Cam16.fromInt(argb)
            val tone = toneFromInt(argb)
            return Hct(cam.hue, cam.chroma, tone, argb)
        }

        fun from(hue: Double, chroma: Double, tone: Double): Hct {
            val argb = solveToInt(hue, chroma, tone)
            val cam = Cam16.fromInt(argb)
            return Hct(cam.hue, cam.chroma, tone, argb)
        }

        fun toneFromInt(argb: Int): Double {
            val r = (argb shr 16 and 0xff) / 255.0
            val g = (argb shr 8 and 0xff) / 255.0
            val b = (argb and 0xff) / 255.0
            val rL = if (r <= 0.04045) r / 12.92 else ((r + 0.055) / 1.055).pow(2.4)
            val gL = if (g <= 0.04045) g / 12.92 else ((g + 0.055) / 1.055).pow(2.4)
            val bL = if (b <= 0.04045) b / 12.92 else ((b + 0.055) / 1.055).pow(2.4)
            val y = (0.2126 * rL + 0.7152 * gL + 0.0722 * bL) * 100.0
            return lstarFromY(y)
        }

        fun yFromLstar(lstar: Double): Double {
            return if (lstar > 8.0) {
                ((lstar + 16.0) / 116.0).pow(3.0) * 100.0
            } else {
                lstar / (24389.0 / 27.0) * 100.0
            }
        }

        fun lstarFromY(y: Double): Double {
            val yNormalized = y / 100.0
            return if (yNormalized <= 216.0 / 24389.0) {
                (24389.0 / 27.0) * yNormalized
            } else {
                116.0 * yNormalized.pow(1.0 / 3.0) - 16.0
            }
        }

        private fun solveToInt(hue: Double, chroma: Double, tone: Double): Int {
            if (tone < 0.0001) return (0xff shl 24)
            if (tone > 99.9999) return (0xff shl 24) or 0xffffff

            val sanitizedHue = if (hue < 0.0) 0.0 else if (hue >= 360.0) 359.9 else hue
            val sanitizedChroma = max(0.0, chroma)
            val sanitizedTone = tone.coerceIn(0.0, 100.0)

            // Fast path for achromatic / grayscale tones (Chroma < 3.0)
            if (sanitizedChroma < 3.0) {
                val y = yFromLstar(sanitizedTone)
                val c = Cam16.delinearized(y)
                return (0xff shl 24) or (c shl 16) or (c shl 8) or c
            }

            var jEst = sanitizedTone
            var bestArgb = (0xff shl 24)
            for (iter in 0 until 4) {
                var low = 0.0
                var high = sanitizedChroma
                var cand = Cam16.fromJchInViewingConditions(
                    j = jEst,
                    c = 0.0,
                    h = sanitizedHue,
                    vc = ViewingConditions.DEFAULT,
                ).toInt()

                while (abs(low - high) > 0.2) {
                    val mid = low + (high - low) / 2.0
                    val cam = Cam16.fromJchInViewingConditions(
                        j = jEst,
                        c = mid,
                        h = sanitizedHue,
                        vc = ViewingConditions.DEFAULT,
                    )

                    if (cam.isInGamut(ViewingConditions.DEFAULT)) {
                        cand = cam.toInt()
                        low = mid
                    } else {
                        high = mid
                    }
                }

                bestArgb = cand
                val actualTone = toneFromInt(cand)
                val delta = sanitizedTone - actualTone
                if (abs(delta) < 0.4) break
                jEst = (jEst + delta).coerceIn(0.0, 100.0)
            }

            return bestArgb
        }
    }
}


