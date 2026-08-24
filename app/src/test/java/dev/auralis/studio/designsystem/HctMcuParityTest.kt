package dev.auralis.studio.designsystem

import dev.auralis.studio.designsystem.hct.Hct
import dev.auralis.studio.designsystem.hct.TonalPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HctMcuParityTest {

    private val testSeeds = listOf(
        0xFF000000L to "Black",
        0xFFFFFFFFL to "White",
        0xFFFF0000L to "Red",
        0xFF00FF00L to "Green",
        0xFF0000FFL to "Blue",
        0xFF9C7CFFL to "Purple",
        0xFF00C2A8L to "Teal",
        0xFFFFFF00L to "Yellow",
    )

    private val targetTones = listOf(10, 20, 30, 40, 70, 80, 90, 95)

    @Test
    fun hctTonalPalette_boundaryTones_produceExactBlackAndWhite() {
        for ((seed, name) in testSeeds) {
            val palette = TonalPalette.fromInt(seed.toInt())
            val tone0 = palette.tone(0).toLong() and 0xFFFFFFFFL
            val tone100 = palette.tone(100).toLong() and 0xFFFFFFFFL

            assertEquals("Tone 0 of $name must be pure black 0xFF000000", 0xFF000000L, tone0)
            assertEquals("Tone 100 of $name must be pure white 0xFFFFFFFF", 0xFFFFFFFFL, tone100)
        }
    }

    @Test
    fun hctTonalPalette_tonesFollowTargetPerceptualLuminance() {
        for ((seed, name) in testSeeds) {
            val palette = TonalPalette.fromInt(seed.toInt())
            println("DEBUG: name=$name seedHex=${java.lang.Long.toHexString(seed)} hue=${palette.hue} chroma=${palette.chroma}")
            for (tone in targetTones) {
                val argb = palette.tone(tone)
                val hct = Hct.fromInt(argb)
                val delta = kotlin.math.abs(hct.tone - tone)
                if (delta >= 1.5) {
                    println("MISMATCH: name=$name targetTone=$tone actualTone=${hct.tone} argb=${Integer.toHexString(argb)}")
                }
                assertTrue(
                    "Tone $tone for $name was ${hct.tone} (delta=$delta)",
                    delta < 2.5,
                )
            }
        }
    }



    @Test
    fun liveHexChanges_produceDistinctColorSchemesWithoutStaleCaches() {
        val p9c = TonalPalette.fromInt(0xFF9C7CFF.toInt())
        val p00 = TonalPalette.fromInt(0xFF00C2A8.toInt())
        println("TEST: p9c.tone(40)=0x${Integer.toHexString(p9c.tone(40))} p00.tone(40)=0x${Integer.toHexString(p00.tone(40))}")

        val hexSequence = listOf(

            "#9C7CFF", // Purple
            "#00C2A8", // Teal
            "#FFB020", // Amber
            "#E91E63", // Pink
            "#2196F3", // Blue
            "#9C7CFF", // Reset
        )

        var previousScheme: Any? = null
        for (hex in hexSequence) {
            val parsedArgb = HexColorParser.parse(hex) ?: 0xFF9C7CFFL
            val hctSeed = Hct.fromInt(parsedArgb.toInt())
            println("HEX=$hex parsedArgb=${java.lang.Long.toHexString(parsedArgb)} hctSeed.hue=${hctSeed.hue} hctSeed.chroma=${hctSeed.chroma} hctSeed.tone=${hctSeed.tone}")

            val lightSettings = dev.auralis.studio.core.model.ThemeSettings(accentArgb = parsedArgb, appearance = dev.auralis.studio.core.model.AppearanceMode.LIGHT)
            val darkSettings = dev.auralis.studio.core.model.ThemeSettings(accentArgb = parsedArgb, appearance = dev.auralis.studio.core.model.AppearanceMode.DARK)
            val amoledSettings = dev.auralis.studio.core.model.ThemeSettings(accentArgb = parsedArgb, appearance = dev.auralis.studio.core.model.AppearanceMode.AMOLED)

            val lightPalette = ColorTonalPalette.buildPalette(lightSettings, dark = false)
            val darkPalette = ColorTonalPalette.buildPalette(darkSettings, dark = true)
            val amoledPalette = ColorTonalPalette.buildPalette(amoledSettings, dark = true)

            println("  -> lightPalette.primary=${lightPalette.primary}")


            // AMOLED background must be strictly true black
            assertEquals("AMOLED background must be exact #000000", 0xFF000000L, HexColorParser.fromComposeColor(amoledPalette.background))

            // Text contrast must be >= 4.5:1
            assertTrue(
                "Light onBackground contrast for $hex",
                ColorTonalPalette.contrastRatio(lightPalette.onBackground, lightPalette.background) >= 4.5f,
            )
            assertTrue(
                "Dark onBackground contrast for $hex",
                ColorTonalPalette.contrastRatio(darkPalette.onBackground, darkPalette.background) >= 4.5f,
            )
            assertTrue(
                "AMOLED onBackground contrast for $hex",
                ColorTonalPalette.contrastRatio(amoledPalette.onBackground, amoledPalette.background) >= 4.5f,
            )

            // Verify new palette instance is created
            if (previousScheme != null && hex != "#9C7CFF") {
                if (previousScheme == lightPalette) {
                    println("EQUALITY FAILURE for hex=$hex: primary=${lightPalette.primary}")
                }
                assertNotEquals("Palette for $hex should differ from previous", previousScheme, lightPalette)
            }
            previousScheme = lightPalette
        }
    }
}



