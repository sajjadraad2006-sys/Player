package dev.auralis.studio.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Per-theme design tokens that drive structural visual differences beyond colour.
 * One instance is derived per (ThemeStyle × AppearanceMode) combination and
 * provided via LocalAuralisTokens.
 */
@Immutable
data class AuralisTokens(
    // Surface strategy
    val surfaceAlpha: Float,           // glass/translucency of card surfaces
    val backgroundUsesArtwork: Boolean,// should blurred album art fill the background

    // Glass strategy
    val glassBlur: Boolean,            // expensive backdrop blur available?
    val glassContrastScrimAlpha: Float,// scrim over artwork for readability

    // Border treatment
    val cardBorderWidth: Dp,
    val cardBorderAlpha: Float,

    // Elevation / shadow
    val cardElevation: Dp,

    // Corner radius (dp)
    val cardCorner: Float,
    val buttonCorner: Float,
    val chipCorner: Float,

    // Navigation bar treatment
    val navBarContainerAlpha: Float,

    // Mini player treatment
    val miniPlayerElevation: Dp,
    val miniPlayerAlpha: Float,

    // Animation personality
    val springStiffness: Float,        // higher = snappier
    val springDamping: Float,

    // Neon glow
    val accentGlowAlpha: Float,        // 0 = no glow; >0 = neon accent glow

    // Gradient overlay on glass cards for depth
    val gradientOverlay: Boolean = false,
)

/** Motion timing constants shared across all themes. */
object AuralisMotion {
    const val MICRO_MS    = 150
    const val CONTENT_MS  = 250
    const val CONTAINER_MS = 380
}

/** Accent palette — premium curated colours. */
object AccentPalette {
    val MINT    = Color(0xFF5EE6B8)    // Vibrant mint
    val PURPLE  = Color(0xFFA78BFA)    // Rich violet
    val PINK    = Color(0xFFFF6FAE)    // Vivid pink
    val BLUE    = Color(0xFF60A5FA)    // Sky blue
    val ORANGE  = Color(0xFFFBBF24)    // Warm amber
    val TEAL    = Color(0xFF2DD4BF)    // Modern teal
    val GOLD    = Color(0xFFF59E0B)    // Rich gold
    val CORAL   = Color(0xFFFB7185)    // Soft coral
    val LIME    = Color(0xFF84CC16)    // Fresh lime
}
