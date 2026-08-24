package dev.auralis.studio.designsystem.hct

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CAM16, a color appearance model.
 * Translates between sRGB (device-dependent color space) and perceptual attributes:
 * - Hue (0..360)
 * - Chroma (colorfulness relative to white)
 * - J (lightness)
 */
internal class Cam16(
    val hue: Double,
    val chroma: Double,
    val j: Double,
    val q: Double,
    val m: Double,
    val s: Double,
    val jstar: Double,
    val astar: Double,
    val bstar: Double,
) {
    companion object {
        val XYZ_TO_CAM16RGB = arrayOf(
            doubleArrayOf(0.401288, 0.650173, -0.051461),
            doubleArrayOf(-0.250268, 1.204414, 0.045854),
            doubleArrayOf(-0.002079, 0.048952, 0.953127),
        )

        val CAM16RGB_TO_XYZ = arrayOf(
            doubleArrayOf(1.8620678, -1.0112546, 0.1491867),
            doubleArrayOf(0.3875265, 0.6214474, -0.0089739),
            doubleArrayOf(-0.0158412, -0.0341229, 1.0500150),
        )

        fun fromInt(argb: Int): Cam16 = fromIntInViewingConditions(argb, ViewingConditions.DEFAULT)

        fun fromIntInViewingConditions(argb: Int, vc: ViewingConditions): Cam16 {
            val red = (argb shr 16 and 0xff)
            val green = (argb shr 8 and 0xff)
            val blue = (argb and 0xff)

            val rL = linearized(red)
            val gL = linearized(green)
            val bL = linearized(blue)

            val x = 0.41233895 * rL + 0.35762064 * gL + 0.18051042 * bL
            val y = 0.2126 * rL + 0.7152 * gL + 0.0722 * bL
            val z = 0.01932141 * rL + 0.11916382 * gL + 0.95034478 * bL

            val rC = (x * XYZ_TO_CAM16RGB[0][0]) + (y * XYZ_TO_CAM16RGB[0][1]) + (z * XYZ_TO_CAM16RGB[0][2])
            val gC = (x * XYZ_TO_CAM16RGB[1][0]) + (y * XYZ_TO_CAM16RGB[1][1]) + (z * XYZ_TO_CAM16RGB[1][2])
            val bC = (x * XYZ_TO_CAM16RGB[2][0]) + (y * XYZ_TO_CAM16RGB[2][1]) + (z * XYZ_TO_CAM16RGB[2][2])

            val rD = vc.rgbD[0] * rC
            val gD = vc.rgbD[1] * gC
            val bD = vc.rgbD[2] * bC

            val rAF = (vc.fl * abs(rD) / 100.0).pow(0.42)
            val gAF = (vc.fl * abs(gD) / 100.0).pow(0.42)
            val bAF = (vc.fl * abs(bD) / 100.0).pow(0.42)

            val rA = (if (rD < 0) -1.0 else 1.0) * 400.0 * rAF / (rAF + 27.13)
            val gA = (if (gD < 0) -1.0 else 1.0) * 400.0 * gAF / (gAF + 27.13)
            val bA = (if (bD < 0) -1.0 else 1.0) * 400.0 * bAF / (bAF + 27.13)

            val a = (11.0 * rA + -12.0 * gA + bA) / 11.0
            val b = (rA + gA - 2.0 * bA) / 9.0
            val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
            val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0

            val atanDegrees = Math.toDegrees(atan2(b, a))
            val hue = if (atanDegrees < 0) atanDegrees + 360.0 else if (atanDegrees >= 360) atanDegrees - 360.0 else atanDegrees
            val hueRadians = Math.toRadians(hue)

            val ac = p2 * vc.nbb
            val j = 100.0 * (ac / vc.aw).pow(vc.c * vc.z)
            val q = (4.0 / vc.c) * sqrt(j / 100.0) * (vc.aw + 4.0) * vc.fLRoot

            val huePrime = if (hue < 20.14) hue + 360 else hue
            val eHue = 0.25 * (cos(Math.toRadians(huePrime) + 2.0) + 3.8)
            val p1 = 50000.0 / 13.0 * eHue * vc.nc * vc.ncb
            val t = p1 * hypot(a, b) / (u + 0.305)
            val alpha = t.pow(0.9) * (1.64 - 0.29.pow(vc.n)).pow(0.73)
            val c = alpha * sqrt(j / 100.0)
            val m = c * vc.fLRoot
            val s = 50.0 * sqrt((alpha * vc.c) / (vc.aw + 4.0))

            val jstar = (1.0 + 100.0 * 0.007) * j / (1.0 + 0.007 * j)
            val mstar = 1.0 / 0.0228 * ln(1.0 + 0.0228 * m)
            val astar = mstar * cos(hueRadians)
            val bstar = mstar * sin(hueRadians)

            return Cam16(hue, c, j, q, m, s, jstar, astar, bstar)
        }

        fun fromJchInViewingConditions(j: Double, c: Double, h: Double, vc: ViewingConditions): Cam16 {
            val q = (4.0 / vc.c) * sqrt(j / 100.0) * (vc.aw + 4.0) * vc.fLRoot
            val m = c * vc.fLRoot
            val alpha = c / sqrt(j / 100.0)
            val s = 50.0 * sqrt((alpha * vc.c) / (vc.aw + 4.0))

            val hueRadians = Math.toRadians(h)
            val jstar = (1.0 + 100.0 * 0.007) * j / (1.0 + 0.007 * j)
            val mstar = 1.0 / 0.0228 * ln(1.0 + 0.0228 * m)
            val astar = mstar * cos(hueRadians)
            val bstar = mstar * sin(hueRadians)

            return Cam16(h, c, j, q, m, s, jstar, astar, bstar)
        }

        private fun linearized(rgb: Int): Double {
            val normalized = rgb / 255.0
            return if (normalized <= 0.04045) {
                normalized / 12.92 * 100.0
            } else {
                ((normalized + 0.055) / 1.055).pow(2.4) * 100.0
            }
        }

        fun delinearized(rgb: Double): Int {
            val normalized = rgb / 100.0
            val delinearized = if (normalized <= 0.0031308) {
                normalized * 12.92
            } else {
                1.055 * normalized.pow(1.0 / 2.4) - 0.055
            }
            return (delinearized * 255.0).roundToIntSafe().coerceIn(0, 255)
        }

        private fun Double.roundToIntSafe(): Int = kotlin.math.round(this).toInt()
    }

    fun toInt(): Int = viewed(ViewingConditions.DEFAULT)

    fun viewed(vc: ViewingConditions): Int {
        if (chroma < 0.0001 || j < 0.0001) {
            val y = Hct.yFromLstar(j)
            val comp = delinearized(y)
            return (0xff shl 24) or (comp shl 16) or (comp shl 8) or comp
        }
        if (j > 99.9999) {
            return (0xff shl 24) or 0xffffff
        }


        val alpha = chroma / sqrt(j / 100.0)
        val t = (alpha / (1.64 - 0.29.pow(vc.n)).pow(0.73)).pow(1.0 / 0.9)
        val hRad = Math.toRadians(hue)

        val eHue = 0.25 * (cos(hRad + 2.0) + 3.8)
        val ac = vc.aw * (j / 100.0).pow(1.0 / (vc.c * vc.z))
        val p1 = 50000.0 / 13.0 * eHue * vc.nc * vc.ncb
        val p2 = ac / vc.nbb

        val hSin = sin(hRad)
        val hCos = cos(hRad)

        val gamma = (1403.0 * (p2 + 0.305) * t) / (1403.0 * p1 + t * (671.0 * hCos + 6588.0 * hSin))
        val a = gamma * hCos
        val b = gamma * hSin
        val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
        val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
        val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0

        val rCBase = max(0.0, (27.13 * abs(rA)) / (400.0 - abs(rA)))
        val rC = (if (rA < 0) -1.0 else 1.0) * (100.0 / vc.fl) * rCBase.pow(1.0 / 0.42)
        val gCBase = max(0.0, (27.13 * abs(gA)) / (400.0 - abs(gA)))
        val gC = (if (gA < 0) -1.0 else 1.0) * (100.0 / vc.fl) * gCBase.pow(1.0 / 0.42)
        val bCBase = max(0.0, (27.13 * abs(bA)) / (400.0 - abs(bA)))
        val bC = (if (bA < 0) -1.0 else 1.0) * (100.0 / vc.fl) * bCBase.pow(1.0 / 0.42)

        val rF = rC / vc.rgbD[0]
        val gF = gC / vc.rgbD[1]
        val bF = bC / vc.rgbD[2]

        val x = (rF * CAM16RGB_TO_XYZ[0][0]) + (gF * CAM16RGB_TO_XYZ[0][1]) + (bF * CAM16RGB_TO_XYZ[0][2])
        val y = (rF * CAM16RGB_TO_XYZ[1][0]) + (gF * CAM16RGB_TO_XYZ[1][1]) + (bF * CAM16RGB_TO_XYZ[1][2])
        val z = (rF * CAM16RGB_TO_XYZ[2][0]) + (gF * CAM16RGB_TO_XYZ[2][1]) + (bF * CAM16RGB_TO_XYZ[2][2])

        val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z

        val intR = delinearized(r)
        val intG = delinearized(g)
        val intB = delinearized(bl)

        return (0xff shl 24) or (intR shl 16) or (intG shl 8) or intB
    }

    fun isInGamut(vc: ViewingConditions = ViewingConditions.DEFAULT): Boolean {
        if (chroma < 0.0001 || j < 0.0001 || j > 99.9999) return true

        val alpha = chroma / sqrt(j / 100.0)

        val t = (alpha / (1.64 - 0.29.pow(vc.n)).pow(0.73)).pow(1.0 / 0.9)
        val hRad = Math.toRadians(hue)

        val eHue = 0.25 * (cos(hRad + 2.0) + 3.8)
        val ac = vc.aw * (j / 100.0).pow(1.0 / (vc.c * vc.z))
        val p1 = 50000.0 / 13.0 * eHue * vc.nc * vc.ncb
        val p2 = ac / vc.nbb

        val hSin = sin(hRad)
        val hCos = cos(hRad)

        val gamma = (1403.0 * (p2 + 0.305) * t) / (1403.0 * p1 + t * (671.0 * hCos + 6588.0 * hSin))
        val a = gamma * hCos
        val b = gamma * hSin
        val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
        val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
        val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0

        if (abs(rA) >= 400.0 || abs(gA) >= 400.0 || abs(bA) >= 400.0) return false

        val rCBase = (27.13 * abs(rA)) / (400.0 - abs(rA))
        val rC = (if (rA < 0) -1.0 else 1.0) * (100.0 / vc.fl) * rCBase.pow(1.0 / 0.42)
        val gCBase = (27.13 * abs(gA)) / (400.0 - abs(gA))
        val gC = (if (gA < 0) -1.0 else 1.0) * (100.0 / vc.fl) * gCBase.pow(1.0 / 0.42)
        val bCBase = (27.13 * abs(bA)) / (400.0 - abs(bA))
        val bC = (if (bA < 0) -1.0 else 1.0) * (100.0 / vc.fl) * bCBase.pow(1.0 / 0.42)

        val rF = rC / vc.rgbD[0]
        val gF = gC / vc.rgbD[1]
        val bF = bC / vc.rgbD[2]

        val x = (rF * CAM16RGB_TO_XYZ[0][0]) + (gF * CAM16RGB_TO_XYZ[0][1]) + (bF * CAM16RGB_TO_XYZ[0][2])
        val y = (rF * CAM16RGB_TO_XYZ[1][0]) + (gF * CAM16RGB_TO_XYZ[1][1]) + (bF * CAM16RGB_TO_XYZ[1][2])
        val z = (rF * CAM16RGB_TO_XYZ[2][0]) + (gF * CAM16RGB_TO_XYZ[2][1]) + (bF * CAM16RGB_TO_XYZ[2][2])

        val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z

        return r in -0.5..100.5 && g in -0.5..100.5 && bl in -0.5..100.5
    }
}



