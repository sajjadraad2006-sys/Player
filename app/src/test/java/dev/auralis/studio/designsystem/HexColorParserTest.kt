package dev.auralis.studio.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HexColorParserTest {

    @Test
    fun parse_validHexWithHash_returnsOpaqueArgb() {
        val result = HexColorParser.parse("#00C2A8")
        assertNotNull(result)
        assertEquals(0xFF00C2A8L, result)
    }

    @Test
    fun parse_validHexWithoutHash_returnsOpaqueArgb() {
        val result = HexColorParser.parse("9C7CFF")
        assertNotNull(result)
        assertEquals(0xFF9C7CFFL, result)
    }

    @Test
    fun parse_validHexWithAlphaPrefix_normalizesToOpaque() {
        val result = HexColorParser.parse("#805EE6B8")
        assertNotNull(result)
        assertEquals(0xFF5EE6B8L, result)
    }

    @Test
    fun parse_shortHex_expandsCorrectly() {
        val result = HexColorParser.parse("#F0A")
        assertNotNull(result)
        assertEquals(0xFFFF00AAL, result)
    }

    @Test
    fun parse_caseInsensitive() {
        val lower = HexColorParser.parse("#a78bfa")
        val upper = HexColorParser.parse("#A78BFA")
        assertEquals(lower, upper)
        assertEquals(0xFFA78BFAL, lower)
    }

    @Test
    fun parse_invalidStrings_returnsNullNeverThrows() {
        assertNull(HexColorParser.parse(null))
        assertNull(HexColorParser.parse(""))
        assertNull(HexColorParser.parse("   "))
        assertNull(HexColorParser.parse("XYZ123"))
        assertNull(HexColorParser.parse("#12"))
        assertNull(HexColorParser.parse("#12345"))
        assertNull(HexColorParser.parse("#1234567890"))
        assertNull(HexColorParser.parse("not a color"))
    }

    @Test
    fun formatToHex_producesCorrectFormat() {
        assertEquals("#00C2A8", HexColorParser.formatToHex(0xFF00C2A8L, includeHash = true))
        assertEquals("00C2A8", HexColorParser.formatToHex(0xFF00C2A8L, includeHash = false))
        assertEquals("#A78BFA", HexColorParser.formatToHex(0xFFA78BFAL, includeHash = true))
    }

    @Test
    fun fromComposeColor_producesCorrectStandardArgb() {
        val color = Color(0xFF5EE6B8)
        val argb = HexColorParser.fromComposeColor(color)
        assertEquals(0xFF5EE6B8L, argb)
    }
}
