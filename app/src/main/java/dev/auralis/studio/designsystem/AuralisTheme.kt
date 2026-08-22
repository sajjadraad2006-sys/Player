package dev.auralis.studio.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.studio.core.model.AppearanceMode
import dev.auralis.studio.core.model.ThemeSettings
import dev.auralis.studio.core.model.ThemeStyle

val LocalThemeSettings = staticCompositionLocalOf { ThemeSettings() }
val LocalAuralisTokens = staticCompositionLocalOf { defaultGlassTokens() }

@Immutable
private object AuralisPalette {
    // Dark backgrounds
    val BgDeepDark   = Color(0xFF090A12)
    val BgAmoled     = Color(0xFF000000)

    // Dark surfaces
    val SurfDark     = Color(0xFF131522)
    val SurfAmoled   = Color(0xFF050505)
    val SurfVarDark  = Color(0xFF202334)

    // Light backgrounds
    val BgLight      = Color(0xFFF5F3FA)
    val SurfLight    = Color(0xFFFFFFFF)
    val SurfVarLight = Color(0xFFEAE7F2)

    // Content
    val OnDark       = Color(0xFFF4F3FA)
    val OnDarkMuted  = Color(0xFFBBBACC)
    val OnLight      = Color(0xFF17151E)
    val OnLightMuted = Color(0xFF5A5769)

    // Outline
    val OutlineDark  = Color(0xFF3C3B4E)
    val OutlineLight = Color(0xFFCAC7D8)

    // Error
    val Error        = Color(0xFFFF5E5E)
    val OnError      = Color(0xFFFFFFFF)
}

/**
 * Derives a complete M3 color scheme for the given theme+appearance combination.
 * Every text color is set via semantically correct onBackground/onSurface tokens —
 * never hard-coded Color.Black or Color.White.
 */
private fun buildColorScheme(settings: ThemeSettings, dark: Boolean) = if (dark) {
    val accent = Color(settings.accentArgb)
    val amoled = settings.style == ThemeStyle.AMOLED || settings.appearance == AppearanceMode.AMOLED
    darkColorScheme(
        primary            = accent,
        onPrimary          = if (isColorDark(accent)) AuralisPalette.OnDark else AuralisPalette.OnLight,
        primaryContainer   = lerp(AuralisPalette.SurfDark, accent, 0.22f),
        onPrimaryContainer = AuralisPalette.OnDark,
        secondary          = when (settings.style) {
            ThemeStyle.NEON    -> Color(0xFFFF62D6)
            ThemeStyle.CLASSIC -> AuralisPalette.OnDark.copy(alpha = 0.75f)
            ThemeStyle.AMOLED  -> accent.copy(alpha = 0.85f)
            else               -> Color(0xFF9F91FF)
        },
        onSecondary        = AuralisPalette.OnDark,
        secondaryContainer = Color(0xFF2E2B42),
        onSecondaryContainer = AuralisPalette.OnDark,
        tertiary           = Color(0xFF70E1C1),
        onTertiary         = AuralisPalette.OnLight,
        background         = if (amoled) AuralisPalette.BgAmoled else AuralisPalette.BgDeepDark,
        onBackground       = AuralisPalette.OnDark,          // ← guaranteed contrast on dark bg
        surface            = if (amoled) AuralisPalette.SurfAmoled else AuralisPalette.SurfDark,
        onSurface          = AuralisPalette.OnDark,          // ← no Color.Black here
        surfaceVariant     = if (amoled) Color(0xFF101010) else AuralisPalette.SurfVarDark,
        onSurfaceVariant   = AuralisPalette.OnDarkMuted,
        outline            = AuralisPalette.OutlineDark,
        outlineVariant     = AuralisPalette.OutlineDark.copy(alpha = 0.5f),
        error              = AuralisPalette.Error,
        onError            = AuralisPalette.OnError,
        scrim              = Color.Black,
        inverseSurface     = AuralisPalette.BgLight,
        inverseOnSurface   = AuralisPalette.OnLight,
        inversePrimary     = lerp(AuralisPalette.BgLight, accent, 0.3f),
    )
} else {
    val accent = Color(settings.accentArgb)
    lightColorScheme(
        primary            = accent,
        onPrimary          = if (isColorDark(accent)) AuralisPalette.OnDark else AuralisPalette.OnLight,
        primaryContainer   = lerp(AuralisPalette.SurfLight, accent, 0.15f),
        onPrimaryContainer = AuralisPalette.OnLight,
        secondary          = Color(0xFF6755C9),
        onSecondary        = AuralisPalette.OnDark,
        secondaryContainer = Color(0xFFEDE9FF),
        onSecondaryContainer = AuralisPalette.OnLight,
        tertiary           = Color(0xFF00897B),
        onTertiary         = AuralisPalette.OnDark,
        background         = AuralisPalette.BgLight,
        onBackground       = AuralisPalette.OnLight,         // ← correct contrast on light bg
        surface            = AuralisPalette.SurfLight,
        onSurface          = AuralisPalette.OnLight,
        surfaceVariant     = AuralisPalette.SurfVarLight,
        onSurfaceVariant   = AuralisPalette.OnLightMuted,
        outline            = AuralisPalette.OutlineLight,
        outlineVariant     = AuralisPalette.OutlineLight.copy(alpha = 0.5f),
        error              = Color(0xFFD32F2F),
        onError            = AuralisPalette.OnDark,
        scrim              = Color.Black,
        inverseSurface     = AuralisPalette.SurfDark,
        inverseOnSurface   = AuralisPalette.OnDark,
        inversePrimary     = lerp(AuralisPalette.SurfDark, accent, 0.4f),
    )
}

/** Returns true if a colour should use white/light foreground text on top of it. */
fun isColorDark(color: Color): Boolean {
    val lum = 0.2126f * linearize(color.red) +
              0.7152f * linearize(color.green) +
              0.0722f * linearize(color.blue)
    return lum < 0.35f
}

private fun linearize(v: Float): Float =
    if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).let { it * it * it }

/** Maps ThemeStyle → structural AuralisTokens for a given dark state. */
private fun buildTokens(settings: ThemeSettings, dark: Boolean): AuralisTokens {
    val corner = settings.effectiveCornerRadius
    return when (settings.style) {
        ThemeStyle.GLASS -> AuralisTokens(
            surfaceAlpha           = 0.75f,
            backgroundUsesArtwork  = settings.backgroundArtwork,
            glassBlur              = settings.visualQuality != dev.auralis.studio.core.model.VisualQuality.REDUCED,
            glassContrastScrimAlpha = settings.backgroundDim,
            cardBorderWidth        = 1.dp,
            cardBorderAlpha        = 0.18f,
            cardElevation          = 0.dp,
            cardCorner             = corner,
            buttonCorner           = corner * 0.7f,
            chipCorner             = 32f,
            navBarContainerAlpha   = 0.70f,
            miniPlayerElevation    = 4.dp,
            miniPlayerAlpha        = 0.80f,
            springStiffness        = 380f,
            springDamping          = 0.80f,
            accentGlowAlpha        = 0f,
        )
        ThemeStyle.AMOLED -> AuralisTokens(
            surfaceAlpha           = 0.92f,
            backgroundUsesArtwork  = false,
            glassBlur              = false,
            glassContrastScrimAlpha = 0.85f,
            cardBorderWidth        = 1.dp,
            cardBorderAlpha        = 0.12f,
            cardElevation          = 0.dp,
            cardCorner             = corner,
            buttonCorner           = corner * 0.7f,
            chipCorner             = 32f,
            navBarContainerAlpha   = 0.95f,
            miniPlayerElevation    = 2.dp,
            miniPlayerAlpha        = 0.96f,
            springStiffness        = 500f,
            springDamping          = 0.85f,
            accentGlowAlpha        = 0f,
        )
        ThemeStyle.MINIMAL -> AuralisTokens(
            surfaceAlpha           = if (dark) 0.96f else 1.0f,
            backgroundUsesArtwork  = false,
            glassBlur              = false,
            glassContrastScrimAlpha = 0f,
            cardBorderWidth        = 0.dp,
            cardBorderAlpha        = 0f,
            cardElevation          = if (dark) 0.dp else 1.dp,
            cardCorner             = corner * 0.6f,
            buttonCorner           = corner * 0.4f,
            chipCorner             = 8f,
            navBarContainerAlpha   = 1.0f,
            miniPlayerElevation    = 2.dp,
            miniPlayerAlpha        = 1.0f,
            springStiffness        = 600f,
            springDamping          = 0.9f,
            accentGlowAlpha        = 0f,
        )
        ThemeStyle.MATERIAL -> AuralisTokens(
            surfaceAlpha           = 1.0f,
            backgroundUsesArtwork  = false,
            glassBlur              = false,
            glassContrastScrimAlpha = 0f,
            cardBorderWidth        = 0.dp,
            cardBorderAlpha        = 0f,
            cardElevation          = if (dark) 0.dp else 2.dp,
            cardCorner             = 12f,
            buttonCorner           = 8f,
            chipCorner             = 8f,
            navBarContainerAlpha   = 1.0f,
            miniPlayerElevation    = 4.dp,
            miniPlayerAlpha        = 1.0f,
            springStiffness        = 700f,
            springDamping          = 0.95f,
            accentGlowAlpha        = 0f,
        )
        ThemeStyle.NEON -> AuralisTokens(
            surfaceAlpha           = 0.88f,
            backgroundUsesArtwork  = false,
            glassBlur              = false,
            glassContrastScrimAlpha = 0.6f,
            cardBorderWidth        = 1.dp,
            cardBorderAlpha        = 0.55f, // accent-coloured border for neon feel
            cardElevation          = 0.dp,
            cardCorner             = corner,
            buttonCorner           = corner * 0.7f,
            chipCorner             = 32f,
            navBarContainerAlpha   = 0.90f,
            miniPlayerElevation    = 2.dp,
            miniPlayerAlpha        = 0.90f,
            springStiffness        = 450f,
            springDamping          = 0.75f,
            accentGlowAlpha        = 0.35f, // neon accent glow on active controls
        )
        ThemeStyle.DYNAMIC_ART -> AuralisTokens(
            surfaceAlpha           = 0.72f,
            backgroundUsesArtwork  = true,
            glassBlur              = settings.visualQuality != dev.auralis.studio.core.model.VisualQuality.REDUCED,
            glassContrastScrimAlpha = settings.backgroundDim,
            cardBorderWidth        = 1.dp,
            cardBorderAlpha        = 0.14f,
            cardElevation          = 0.dp,
            cardCorner             = corner,
            buttonCorner           = corner * 0.7f,
            chipCorner             = 24f,
            navBarContainerAlpha   = 0.68f,
            miniPlayerElevation    = 2.dp,
            miniPlayerAlpha        = 0.75f,
            springStiffness        = 350f,
            springDamping          = 0.78f,
            accentGlowAlpha        = 0f,
        )
        ThemeStyle.CLASSIC -> AuralisTokens(
            surfaceAlpha           = 0.98f,
            backgroundUsesArtwork  = false,
            glassBlur              = false,
            glassContrastScrimAlpha = 0f,
            cardBorderWidth        = 1.dp,
            cardBorderAlpha        = 0.12f,
            cardElevation          = 2.dp,
            cardCorner             = 10f,
            buttonCorner           = 6f,
            chipCorner             = 6f,
            navBarContainerAlpha   = 1.0f,
            miniPlayerElevation    = 6.dp,
            miniPlayerAlpha        = 1.0f,
            springStiffness        = 600f,
            springDamping          = 0.90f,
            accentGlowAlpha        = 0f,
        )
    }
}

private fun defaultGlassTokens() = buildTokens(ThemeSettings(), dark = true)

// ─────────────────────────────────────────────────────────────────────────────
// M3 Typography — single canonical scale
// ─────────────────────────────────────────────────────────────────────────────
private val AuralisTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium= TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge    = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    titleSmall    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 20.sp),
    bodyLarge     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

// ─────────────────────────────────────────────────────────────────────────────
// Public composable entry point
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuralisTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.appearance) {
        AppearanceMode.SYSTEM  -> systemDark
        AppearanceMode.LIGHT   -> false
        AppearanceMode.DARK, AppearanceMode.AMOLED -> true
    }
    val colorScheme = buildColorScheme(settings, dark)
    val tokens      = buildTokens(settings, dark)

    CompositionLocalProvider(
        LocalThemeSettings provides settings,
        LocalAuralisTokens  provides tokens,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = AuralisTypography, content = content)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Background container — theme-aware
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuralisBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val settings = LocalThemeSettings.current
    val base     = MaterialTheme.colorScheme.background
    val accent   = MaterialTheme.colorScheme.primary
    val secondary= MaterialTheme.colorScheme.secondary

    val brush = when (settings.style) {
        ThemeStyle.GLASS, ThemeStyle.DYNAMIC_ART -> Brush.radialGradient(
            colors = listOf(
                lerp(base, accent, 0.18f),
                lerp(base, secondary, 0.08f),
                base,
            ),
            radius = 1_400f,
        )
        ThemeStyle.NEON -> Brush.linearGradient(
            colors = listOf(
                base,
                lerp(base, accent, 0.10f),
                lerp(base, secondary, 0.06f),
                base,
            ),
        )
        ThemeStyle.MINIMAL, ThemeStyle.MATERIAL -> Brush.linearGradient(listOf(base, base))
        ThemeStyle.AMOLED  -> Brush.linearGradient(listOf(Color.Black, Color.Black))
        ThemeStyle.CLASSIC -> Brush.linearGradient(listOf(base, lerp(base, accent, 0.04f)))
    }
    Box(modifier.background(brush)) { content() }
}
