package dev.auralis.studio.core.model

import dev.auralis.studio.designsystem.buildTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeStyleTokensTest {
    @Test fun `every visual style resolves a distinct structural treatment`() {
        val tokens = ThemeStyle.entries.associateWith { buildTokens(ThemeSettings(style = it), dark = true) }
        assertEquals(ThemeStyle.entries.size, tokens.values.map { it.surfaceTreatment }.toSet().size)
        assertNotEquals(tokens.getValue(ThemeStyle.GLASS).navigationCorner, tokens.getValue(ThemeStyle.MATERIAL).navigationCorner)
        assertNotEquals(tokens.getValue(ThemeStyle.MINIMAL).artworkCorner, tokens.getValue(ThemeStyle.DYNAMIC_ART).artworkCorner)
    }

    @Test fun `appearance changes contrast strategy without changing style identity`() {
        val light = buildTokens(ThemeSettings(style = ThemeStyle.GLASS, appearance = AppearanceMode.LIGHT), dark = false)
        val dark = buildTokens(ThemeSettings(style = ThemeStyle.GLASS, appearance = AppearanceMode.DARK), dark = true)
        assertEquals(light.surfaceTreatment, dark.surfaceTreatment)
        assertNotEquals(light.surfaceAlpha, dark.surfaceAlpha)
    }

    @Test fun `dual-color support applies user-selected primary and secondary accents`() {
        val customPrimary = 0xFFE11D48L
        val customSecondary = 0xFF06B6D4L
        val settings = ThemeSettings(
            style = ThemeStyle.GLASS,
            accentArgb = customPrimary,
            secondaryArgb = customSecondary,
        )
        val palette = dev.auralis.studio.designsystem.ColorTonalPalette.buildPalette(settings, dark = true)
        assertEquals(androidx.compose.ui.graphics.Color(customSecondary.toInt()), palette.secondary)
    }

    @Test fun `amoled display mode renders true pitch black background`() {
        val settings = ThemeSettings(
            style = ThemeStyle.GLASS,
            appearance = AppearanceMode.AMOLED,
        )
        val palette = dev.auralis.studio.designsystem.ColorTonalPalette.buildPalette(settings, dark = true)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), palette.background)
        assertEquals(dev.auralis.studio.designsystem.ColorTonalPalette.SurfAmoled, palette.surface)
    }
}
