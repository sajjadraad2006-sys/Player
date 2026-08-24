package dev.auralis.studio.core.model

import dev.auralis.studio.designsystem.buildTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeStyleTokensTest {
    @Test fun `every visual style resolves a distinct structural treatment`() {
        val tokens = ThemeStyle.entries.associateWith { buildTokens(ThemeSettings(style = it), dark = true) }
        assertEquals(ThemeStyle.entries.size, tokens.values.map { it.surfaceTreatment }.toSet().size)
        assertNotEquals(tokens.getValue(ThemeStyle.GLASS).navigationCorner, tokens.getValue(ThemeStyle.CLASSIC).navigationCorner)
        assertNotEquals(tokens.getValue(ThemeStyle.MINIMAL).artworkCorner, tokens.getValue(ThemeStyle.DYNAMIC_ART).artworkCorner)
    }

    @Test fun `appearance changes contrast strategy without changing style identity`() {
        val light = buildTokens(ThemeSettings(style = ThemeStyle.GLASS, appearance = AppearanceMode.LIGHT), dark = false)
        val dark = buildTokens(ThemeSettings(style = ThemeStyle.GLASS, appearance = AppearanceMode.DARK), dark = true)
        assertEquals(light.surfaceTreatment, dark.surfaceTreatment)
        assertNotEquals(light.surfaceAlpha, dark.surfaceAlpha)
    }
}
