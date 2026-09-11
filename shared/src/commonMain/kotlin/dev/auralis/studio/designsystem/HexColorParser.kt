package dev.auralis.studio.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Safe parser and formatter for hex color strings.
 * Accepts #RRGGBB, RRGGBB, #AARRGGBB, AARRGGBB.
 * Normalizes accent colors to fully opaque ARGB (alpha = 0xFF).
 * Never throws exceptions on invalid or malformed input.
 */
object HexColorParser {

    /**
     * Parses a hex color string into a standard opaque 32-bit ARGB Long (0xFFxxxxxxL).
     * Returns null if the string is not a valid hex color.
     */
    fun parse(input: String?): Long? {
        if (input.isNullOrBlank()) return null
        val clean = input.trim().removePrefix("#").trim()

        val hex = when (clean.length) {
            6 -> "FF$clean"
            8 -> "FF" + clean.substring(2) // Normalize pasted AARRGGBB to opaque
            3 -> {
                // Support short #RGB -> #RRGGBB
                val r = clean[0]
                val g = clean[1]
                val b = clean[2]
                "FF$r$r$g$g$b$b"
            }
            else -> return null
        }

        return try {
            val parsed = hex.toLong(16)
            // Ensure alpha is 0xFF and mask to 32-bit range
            (parsed and 0x00FFFFFFL) or 0xFF000000L
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Formats an ARGB Long to #RRGGBB hex string.
     */
    fun formatToHex(argb: Long, includeHash: Boolean = true): String {
        val rgb = (argb and 0x00FFFFFFL).toString(16).uppercase().padStart(6, '0')
        return if (includeHash) "#$rgb" else rgb
    }

    /**
     * Converts a standard ARGB Long to Compose Color.
     */
    fun toComposeColor(argb: Long): Color = Color(argb.toInt())

    /**
     * Converts a Compose Color to standard ARGB Long (0xFFxxxxxxL).
     */
    fun fromComposeColor(color: Color): Long {
        val r = (color.red * 255f).toInt().coerceIn(0, 255)
        val g = (color.green * 255f).toInt().coerceIn(0, 255)
        val b = (color.blue * 255f).toInt().coerceIn(0, 255)
        val a = 0xFF
        val argbInt = (a shl 24) or (r shl 16) or (g shl 8) or b
        return argbInt.toLong() and 0xFFFFFFFFL
    }
}
