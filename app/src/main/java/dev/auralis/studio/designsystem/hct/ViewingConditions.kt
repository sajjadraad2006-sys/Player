package dev.auralis.studio.designsystem.hct

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * In CAM16, [ViewingConditions] characterizes the surrounding environment.
 * Standard default parameters match the sRGB standard observer.
 */
internal class ViewingConditions private constructor(
    val n: Double,
    val aw: Double,
    val nbb: Double,
    val ncb: Double,
    val c: Double,
    val nc: Double,
    val rgbD: DoubleArray,
    val fl: Double,
    val fLRoot: Double,
    val z: Double,
) {
    companion object {
        val DEFAULT: ViewingConditions = make(
            whitePoint = doubleArrayOf(95.047, 100.0, 108.883),
            adaptingLuminance = (200.0 / PI * 0.2), // 200 cd/m2 with 20% gray surround
            backgroundLstar = 50.0,
            surround = 2.0,
            discountingIlluminant = false,
        )

        fun make(
            whitePoint: DoubleArray,
            adaptingLuminance: Double,
            backgroundLstar: Double,
            surround: Double,
            discountingIlluminant: Boolean,
        ): ViewingConditions {
            val matrix = Cam16.XYZ_TO_CAM16RGB
            val rW = (whitePoint[0] * matrix[0][0]) + (whitePoint[1] * matrix[0][1]) + (whitePoint[2] * matrix[0][2])
            val gW = (whitePoint[0] * matrix[1][0]) + (whitePoint[1] * matrix[1][1]) + (whitePoint[2] * matrix[1][2])
            val bW = (whitePoint[0] * matrix[2][0]) + (whitePoint[1] * matrix[2][1]) + (whitePoint[2] * matrix[2][2])

            val f = 0.8 + (surround / 10.0)
            val c = if (f >= 0.9) 0.69 else 0.59 + (0.05 * (f - 0.8) / 0.1)
            var d = if (discountingIlluminant) 1.0 else f * (1.0 - (1.0 / 3.6) * exp((-adaptingLuminance - 42.0) / 92.0))
            d = d.coerceIn(0.0, 1.0)
            val nc = f
            val rgbD = doubleArrayOf(
                d * (100.0 / rW) + 1.0 - d,
                d * (100.0 / gW) + 1.0 - d,
                d * (100.0 / bW) + 1.0 - d,
            )

            val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
            val k4 = k * k * k * k
            val k4F = 1.0 - k4
            val fl = (k4 * adaptingLuminance) + (0.1 * k4F * k4F * (5.0 * adaptingLuminance).pow(1.0 / 3.0))
            val n = yFromLstar(backgroundLstar) / whitePoint[1]
            val z = 1.48 + sqrt(n)
            val nbb = 0.725 / n.pow(0.2)
            val ncb = nbb
            val rgbA = doubleArrayOf(
                (rgbD[0] * rW * fl / 100.0).pow(0.42),
                (rgbD[1] * gW * fl / 100.0).pow(0.42),
                (rgbD[2] * bW * fl / 100.0).pow(0.42),
            )
            val rgbC = doubleArrayOf(
                (400.0 * rgbA[0]) / (rgbA[0] + 27.13),
                (400.0 * rgbA[1]) / (rgbA[1] + 27.13),
                (400.0 * rgbA[2]) / (rgbA[2] + 27.13),
            )
            val aw = ((2.0 * rgbC[0]) + rgbC[1] + (0.05 * rgbC[2])) * nbb

            return ViewingConditions(n, aw, nbb, ncb, c, nc, rgbD, fl, fl.pow(0.25), z)
        }

        private fun yFromLstar(lstar: Double): Double {
            return if (lstar > 8.0) {
                ((lstar + 16.0) / 116.0).pow(3.0) * 100.0
            } else {
                lstar / (24389.0 / 27.0) * 100.0
            }
        }
    }
}
