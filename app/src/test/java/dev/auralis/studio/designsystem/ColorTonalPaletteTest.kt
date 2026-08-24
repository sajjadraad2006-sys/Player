package dev.auralis.studio.designsystem

import androidx.compose.ui.graphics.Color
import dev.auralis.studio.core.model.AppearanceMode
import dev.auralis.studio.core.model.ThemeSettings
import dev.auralis.studio.core.model.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTonalPaletteTest {

    private val testSeeds = listOf(
        "#000000" to 0xFF000000L,
        "#FFFFFF" to 0xFFFFFFFFL,
        "#FFFF00" to 0xFFFFFF00L,
        "#FF0000" to 0xFFFF0000L,
        "#00FF00" to 0xFF00FF00L,
        "#0000FF" to 0xFF0000FFL,
        "#00C2A8" to 0xFF00C2A8L,
        "#9C7CFF" to 0xFF9C7CFFL,
    )

    @Test
    fun amoledMode_guaranteesTrueBlackBackground() {
        for ((name, seed) in testSeeds) {
            val settings = ThemeSettings(
                appearance = AppearanceMode.AMOLED,
                accentArgb = seed,
                style = ThemeStyle.GLASS,
            )
            val palette = ColorTonalPalette.buildPalette(settings, dark = true)
            assertEquals(
                "AMOLED background must be pure true black (0xFF000000) for seed $name",
                Color(0xFF000000),
                palette.background,
            )
        }
    }

    @Test
    fun contrastRatios_underDark_meetStandards() {
        for ((name, seed) in testSeeds) {
            val settings = ThemeSettings(
                appearance = AppearanceMode.DARK,
                accentArgb = seed,
                style = ThemeStyle.GLASS,
            )
            val palette = ColorTonalPalette.buildPalette(settings, dark = true)

            val primaryBgContrast = ColorTonalPalette.contrastRatio(palette.primary, palette.background)
            assertTrue(
                "Dark primary vs bg contrast for $name should be >= 4.0:1, was $primaryBgContrast",
                primaryBgContrast >= 4.0f,
            )

            val onPrimaryContrast = ColorTonalPalette.contrastRatio(palette.onPrimary, palette.primary)
            assertTrue(
                "Dark onPrimary vs primary contrast for $name should be >= 4.5:1, was $onPrimaryContrast",
                onPrimaryContrast >= 4.5f,
            )

            val onBgContrast = ColorTonalPalette.contrastRatio(palette.onBackground, palette.background)
            assertTrue(
                "Dark onBackground vs bg contrast for $name should be >= 7.0:1, was $onBgContrast",
                onBgContrast >= 7.0f,
            )
        }
    }

    @Test
    fun contrastRatios_underLight_meetStandards() {
        for ((name, seed) in testSeeds) {
            val settings = ThemeSettings(
                appearance = AppearanceMode.LIGHT,
                accentArgb = seed,
                style = ThemeStyle.GLASS,
            )
            val palette = ColorTonalPalette.buildPalette(settings, dark = false)

            val primaryBgContrast = ColorTonalPalette.contrastRatio(palette.primary, palette.background)
            assertTrue(
                "Light primary vs bg contrast for $name should be >= 4.0:1, was $primaryBgContrast",
                primaryBgContrast >= 4.0f,
            )

            val onPrimaryContrast = ColorTonalPalette.contrastRatio(palette.onPrimary, palette.primary)
            assertTrue(
                "Light onPrimary vs primary contrast for $name should be >= 4.0:1, was $onPrimaryContrast",
                onPrimaryContrast >= 4.0f,
            )

            val onBgContrast = ColorTonalPalette.contrastRatio(palette.onBackground, palette.background)
            assertTrue(
                "Light onBackground vs bg contrast for $name should be >= 7.0:1, was $onBgContrast",
                onBgContrast >= 7.0f,
            )
        }
    }

    @Test
    fun contrastRatios_underAmoled_meetStandards() {
        for ((name, seed) in testSeeds) {
            val settings = ThemeSettings(
                appearance = AppearanceMode.AMOLED,
                accentArgb = seed,
                style = ThemeStyle.GLASS,
            )

            val palette = ColorTonalPalette.buildPalette(settings, dark = true)

            val primaryBgContrast = ColorTonalPalette.contrastRatio(palette.primary, palette.background)
            assertTrue(
                "AMOLED primary vs bg contrast for $name should be >= 4.5:1, was $primaryBgContrast",
                primaryBgContrast >= 4.5f,
            )

            val onPrimaryContrast = ColorTonalPalette.contrastRatio(palette.onPrimary, palette.primary)
            assertTrue(
                "AMOLED onPrimary vs primary contrast for $name should be >= 4.5:1, was $onPrimaryContrast",
                onPrimaryContrast >= 4.5f,
            )
        }
    }
}
