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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
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
import dev.auralis.studio.core.model.ThemeModeResolver

val LocalThemeSettings = staticCompositionLocalOf { ThemeSettings() }
val LocalAuralisTokens = staticCompositionLocalOf { defaultGlassTokens() }
val LocalAuralisControlColors = staticCompositionLocalOf {
    AuralisControlColors(
        active = AccentPalette.MINT,
        onActive = Color(0xFF071B14),
        selectedContainer = Color(0xFF245B4B),
        selectedContent = Color.White,
        inactiveContainer = Color(0xFF262947),
        inactiveContent = Color.White,
        sliderActive = AccentPalette.MINT,
        sliderInactive = Color(0xFF77738D),
        sliderThumb = AccentPalette.MINT,
        controlBorder = Color(0xFFA09BBD),
        switchCheckedTrack = AccentPalette.MINT,
        switchCheckedThumb = Color(0xFF071B14),
        switchUncheckedTrack = Color(0xFF555269),
        switchUncheckedThumb = Color.White,
        disabledContainer = Color(0xFF343342),
        disabledContent = Color(0xFFAAA7B5),
    )
}
val LocalLyruneSemanticColors = staticCompositionLocalOf {
    LyruneSemanticColors(
        success = Color(0xFF52D99A), information = Color(0xFF60A5FA), warning = Color(0xFFFBBF24),
        dspActive = Color(0xFF5EE6B8), spatialActive = Color(0xFF60A5FA), lyricsActive = Color(0xFFA78BFA),
        visualizerBass = Color(0xFF6D8DFF), visualizerTreble = Color(0xFFE58BFF), artworkAccent = Color(0xFFA78BFA),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// High-Contrast, Vibrant Color Palette for Dark & Light Modes
// ─────────────────────────────────────────────────────────────────────────────
@Immutable
private object AuralisPalette {
    // ── Dark mode backgrounds ──
    val BgDeepDark     = Color(0xFF0F101E)    // Rich deep indigo-navy
    val BgAmoled       = Color(0xFF000000)     // Pure OLED black

    // ── Dark surfaces (high clarity & contrast) ──
    val SurfDark       = Color(0xFF181A2F)     // Distinct elevated indigo
    val SurfAmoled     = Color(0xFF0D0E15)     // Dark charcoal for AMOLED contrast
    val SurfVarDark    = Color(0xFF262947)     // Clear card/chip background
    val SurfElevDark   = Color(0xFF32365C)     // High-elevation surface

    // ── Light mode backgrounds ──
    val BgLight        = Color(0xFFF6F5FB)     // Clean warm off-white
    val SurfLight      = Color(0xFFFFFFFF)     // Pure white cards
    val SurfVarLight   = Color(0xFFEBE8F5)     // Lavender tinted surface

    // ── Dark mode text/content (High Contrast - No blending) ──
    val OnDark         = Color(0xFFFFFFFF)     // Bright white for headers & titles
    val OnDarkSub      = Color(0xFFCDC8E8)     // High-readability secondary text
    val OnDarkMuted    = Color(0xFFA09BBD)     // Clearly readable tertiary text

    // ── Light mode text/content ──
    val OnLight        = Color(0xFF131124)     // Deep black-indigo for strong readability
    val OnLightSub     = Color(0xFF534F6E)     // Readable secondary text
    val OnLightMuted   = Color(0xFF7E7A99)     // Readable tertiary text

    // ── Borders & Outlines ──
    val OutlineDark    = Color(0xFF45426E)     // Visible border for dark mode
    val OutlineLight   = Color(0xFFC8C3DE)     // Visible border for light mode

    // ── Semantic colors ──
    val Error          = Color(0xFFFF5252)
    val OnError        = Color(0xFFFFFFFF)
    val Success        = Color(0xFF4EFAAC)

    // ── Glass surfaces ──
    val GlassSurfDark  = Color(0xFF1F223D)
    val GlassBorder    = Color(0xFF5A568A)
}

/**
 * Derives a complete M3 color scheme ensuring all text, sliders,
 * chips, and buttons are always clearly visible with high contrast.
 */
private fun buildColorScheme(settings: ThemeSettings, dark: Boolean, artwork: ArtworkPalette?) = if (dark) {
    val isDynamic = settings.style == ThemeStyle.DYNAMIC_ART
    val accent = accessibleAccent(if (isDynamic) artwork?.vibrant ?: Color(settings.accentArgb) else Color(settings.accentArgb), dark = true)
    val amoled = ThemeModeResolver.isAmoled(settings.appearance)
    val isNeon = settings.style == ThemeStyle.NEON
    val isGlass = settings.style == ThemeStyle.GLASS || settings.style == ThemeStyle.DYNAMIC_ART

    val bg = when {
        amoled -> AuralisPalette.BgAmoled
        isDynamic && artwork != null -> lerp(AuralisPalette.BgDeepDark, artwork.dark, 0.42f)
        isNeon -> Color(0xFF0C0C20)
        isGlass -> Color(0xFF101124)
        else -> AuralisPalette.BgDeepDark
    }
    val surf = when {
        amoled -> AuralisPalette.SurfAmoled
        isDynamic && artwork != null -> lerp(AuralisPalette.SurfDark, artwork.muted, 0.24f)
        isNeon -> Color(0xFF161633)
        isGlass -> AuralisPalette.GlassSurfDark
        else -> AuralisPalette.SurfDark
    }
    val surfVar = when {
        amoled -> Color(0xFF1A1A24)
        isDynamic && artwork != null -> lerp(AuralisPalette.SurfVarDark, artwork.dominant, 0.30f)
        isNeon -> Color(0xFF22224A)
        isGlass -> Color(0xFF2A2E52)
        else -> AuralisPalette.SurfVarDark
    }

    val secondary = when (settings.style) {
        ThemeStyle.NEON    -> Color(0xFFFF52BF)
        ThemeStyle.DYNAMIC_ART -> artwork?.light ?: Color(0xFFB39DDB)
        ThemeStyle.GLASS -> artwork?.muted ?: Color(0xFFB39DDB)
        ThemeStyle.CLASSIC -> Color(0xFFB5B0D8)
        else               -> Color(0xFFB39DDB)
    }

    darkColorScheme(
        primary            = accent,
        onPrimary          = if (isColorDark(accent)) AuralisPalette.OnDark else AuralisPalette.OnLight,
        primaryContainer   = lerp(surfVar, accent, 0.28f),
        onPrimaryContainer = AuralisPalette.OnDark,
        secondary          = secondary,
        onSecondary        = AuralisPalette.OnDark,
        secondaryContainer = lerp(surfVar, secondary, 0.24f),
        onSecondaryContainer = AuralisPalette.OnDark,
        tertiary           = AuralisPalette.Success,
        onTertiary         = AuralisPalette.OnLight,
        background         = bg,
        onBackground       = AuralisPalette.OnDark,
        surface            = surf,
        onSurface          = AuralisPalette.OnDark,
        surfaceVariant     = surfVar,
        onSurfaceVariant   = AuralisPalette.OnDarkSub,
        outline            = if (isGlass) AuralisPalette.GlassBorder else AuralisPalette.OutlineDark,
        outlineVariant     = AuralisPalette.OutlineDark.copy(alpha = 0.6f),
        error              = AuralisPalette.Error,
        onError            = AuralisPalette.OnError,
        scrim              = Color.Black,
        inverseSurface     = AuralisPalette.BgLight,
        inverseOnSurface   = AuralisPalette.OnLight,
        inversePrimary     = lerp(AuralisPalette.BgLight, accent, 0.35f),
    )
} else {
    val isDynamic = settings.style == ThemeStyle.DYNAMIC_ART
    val accent = accessibleAccent(if (isDynamic) artwork?.vibrant ?: Color(settings.accentArgb) else Color(settings.accentArgb), dark = false)
    val isMinimal = settings.style == ThemeStyle.MINIMAL
    val isMaterial = settings.style == ThemeStyle.MATERIAL

    val bg = when {
        isDynamic && artwork != null -> lerp(AuralisPalette.BgLight, artwork.light, 0.22f)
        isMinimal -> Color(0xFFFAFAFC)
        isMaterial -> Color(0xFFF7F5FE)
        else -> AuralisPalette.BgLight
    }

    lightColorScheme(
        primary            = accent,
        onPrimary          = if (isColorDark(accent)) AuralisPalette.OnDark else AuralisPalette.OnLight,
        primaryContainer   = lerp(Color.White, accent, 0.18f),
        onPrimaryContainer = AuralisPalette.OnLight,
        secondary          = if (isDynamic && artwork != null) accessibleAccent(artwork.muted, false) else Color(0xFF674FD8),
        onSecondary        = AuralisPalette.OnDark,
        secondaryContainer = Color(0xFFE8E4FA),
        onSecondaryContainer = AuralisPalette.OnLight,
        tertiary           = Color(0xFF00897B),
        onTertiary         = AuralisPalette.OnDark,
        background         = bg,
        onBackground       = AuralisPalette.OnLight,
        surface            = AuralisPalette.SurfLight,
        onSurface          = AuralisPalette.OnLight,
        surfaceVariant     = AuralisPalette.SurfVarLight,
        onSurfaceVariant   = AuralisPalette.OnLightSub,
        outline            = AuralisPalette.OutlineLight,
        outlineVariant     = AuralisPalette.OutlineLight.copy(alpha = 0.65f),
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
    return lum < 0.38f
}

private fun linearize(v: Float): Float =
    if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).let { it * it * it }

private fun relativeLuminance(color: Color): Float =
    0.2126f * linearize(color.red) + 0.7152f * linearize(color.green) + 0.0722f * linearize(color.blue)

private fun contrastRatio(a: Color, b: Color): Float {
    val lighter = maxOf(relativeLuminance(a), relativeLuminance(b))
    val darker = minOf(relativeLuminance(a), relativeLuminance(b))
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** Keeps user accents identifiable while meeting non-text control contrast. */
private fun accessibleAccent(raw: Color, dark: Boolean): Color {
    val background = if (dark) AuralisPalette.BgDeepDark else AuralisPalette.BgLight
    val target = if (dark) Color.White else Color.Black
    var result = raw.copy(alpha = 1f)
    repeat(12) {
        if (contrastRatio(result, background) >= 3.1f) return result
        result = lerp(result, target, 0.10f)
    }
    return result
}

/** Maps ThemeStyle → structural AuralisTokens for a given dark state. */
internal fun buildTokens(settings: ThemeSettings, dark: Boolean): AuralisTokens {
    val corner = settings.effectiveCornerRadius
    return when (settings.style) {
        ThemeStyle.GLASS -> AuralisTokens(
            surfaceAlpha           = if (dark) 0.65f else 0.78f,
            backgroundUsesArtwork  = settings.backgroundArtwork,
            glassBlur              = settings.visualQuality != dev.auralis.studio.core.model.VisualQuality.REDUCED,
            glassContrastScrimAlpha = settings.backgroundDim.coerceIn(0.50f, 0.92f),
            cardBorderWidth        = 1.2.dp,
            cardBorderAlpha        = 0.35f,
            cardElevation          = 0.dp,
            cardCorner             = corner,
            buttonCorner           = corner * 0.7f,
            chipCorner             = 32f,
            navBarContainerAlpha   = 0.75f,
            miniPlayerElevation    = 4.dp,
            miniPlayerAlpha        = 0.78f,
            springStiffness        = 380f,
            springDamping          = 0.80f,
            accentGlowAlpha        = 0.12f,
            gradientOverlay        = true,
            surfaceTreatment       = SurfaceTreatment.GLASS,
            navigationFloating     = true,
            navigationCorner       = 28f,
            navigationElevation    = 3.dp,
            artworkCorner          = corner,
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
            gradientOverlay        = false,
            surfaceTreatment       = SurfaceTreatment.BORDERLESS,
            navigationFloating     = false,
            navigationCorner       = 0f,
            navigationElevation    = 0.dp,
            artworkCorner          = 4f,
        )
        ThemeStyle.MATERIAL -> AuralisTokens(
            surfaceAlpha           = 1.0f,
            backgroundUsesArtwork  = false,
            glassBlur              = false,
            glassContrastScrimAlpha = 0f,
            cardBorderWidth        = 0.dp,
            cardBorderAlpha        = 0f,
            cardElevation          = if (dark) 1.dp else 2.dp,
            cardCorner             = 16f,
            buttonCorner           = 12f,
            chipCorner             = 8f,
            navBarContainerAlpha   = 1.0f,
            miniPlayerElevation    = 4.dp,
            miniPlayerAlpha        = 1.0f,
            springStiffness        = 700f,
            springDamping          = 0.95f,
            accentGlowAlpha        = 0f,
            gradientOverlay        = false,
            surfaceTreatment       = SurfaceTreatment.MATERIAL_TONAL,
            navigationFloating     = false,
            navigationCorner       = 0f,
            navigationElevation    = 3.dp,
            artworkCorner          = 20f,
        )
        ThemeStyle.NEON -> AuralisTokens(
            surfaceAlpha           = 0.82f,
            backgroundUsesArtwork  = false,
            glassBlur              = false,
            glassContrastScrimAlpha = 0.6f,
            cardBorderWidth        = 1.8.dp,
            cardBorderAlpha        = 0.75f,
            cardElevation          = 0.dp,
            cardCorner             = corner,
            buttonCorner           = corner * 0.7f,
            chipCorner             = 32f,
            navBarContainerAlpha   = 0.88f,
            miniPlayerElevation    = 2.dp,
            miniPlayerAlpha        = 0.88f,
            springStiffness        = 450f,
            springDamping          = 0.75f,
            accentGlowAlpha        = 0.50f,
            gradientOverlay        = true,
            surfaceTreatment       = SurfaceTreatment.NEON_EDGE,
            navigationFloating     = true,
            navigationCorner       = 12f,
            navigationElevation    = 0.dp,
            artworkCorner          = 12f,
        )
        ThemeStyle.DYNAMIC_ART -> AuralisTokens(
            surfaceAlpha           = 0.62f,
            backgroundUsesArtwork  = true,
            glassBlur              = settings.visualQuality != dev.auralis.studio.core.model.VisualQuality.REDUCED,
            glassContrastScrimAlpha = settings.backgroundDim.coerceIn(0.50f, 0.92f),
            cardBorderWidth        = 1.2.dp,
            cardBorderAlpha        = 0.38f,
            cardElevation          = 0.dp,
            cardCorner             = corner,
            buttonCorner           = corner * 0.7f,
            chipCorner             = 24f,
            navBarContainerAlpha   = 0.65f,
            miniPlayerElevation    = 2.dp,
            miniPlayerAlpha        = 0.68f,
            springStiffness        = 350f,
            springDamping          = 0.78f,
            accentGlowAlpha        = 0f,
            gradientOverlay        = true,
            surfaceTreatment       = SurfaceTreatment.ARTWORK,
            navigationFloating     = true,
            navigationCorner       = 30f,
            navigationElevation    = 2.dp,
            artworkCorner          = 30f,
        )
        ThemeStyle.CLASSIC -> AuralisTokens(
            surfaceAlpha           = 0.98f,
            backgroundUsesArtwork  = false,
            glassBlur              = false,
            glassContrastScrimAlpha = 0f,
            cardBorderWidth        = 1.dp,
            cardBorderAlpha        = 0.42f,
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
            gradientOverlay        = false,
            surfaceTreatment       = SurfaceTreatment.CLASSIC_OUTLINE,
            navigationFloating     = false,
            navigationCorner       = 2f,
            navigationElevation    = 5.dp,
            artworkCorner          = 6f,
        )
    }
}

private fun defaultGlassTokens() = buildTokens(ThemeSettings(), dark = true)

private fun buildControlColors(
    scheme: androidx.compose.material3.ColorScheme,
    dark: Boolean,
): AuralisControlColors {
    // Inactive controls deliberately use outline-derived colours instead of
    // translucent surface colours. They remain legible over cards and artwork.
    val inactiveTrack = if (dark) lerp(scheme.surfaceVariant, Color.White, 0.32f)
    else lerp(scheme.surfaceVariant, Color.Black, 0.27f)
    val uncheckedTrack = if (dark) lerp(scheme.surfaceVariant, Color.White, 0.25f)
    else lerp(scheme.surfaceVariant, Color.Black, 0.22f)
    return AuralisControlColors(
        active = scheme.primary,
        onActive = scheme.onPrimary,
        selectedContainer = scheme.primaryContainer,
        selectedContent = scheme.onPrimaryContainer,
        inactiveContainer = scheme.surfaceVariant,
        inactiveContent = scheme.onSurfaceVariant,
        sliderActive = scheme.primary,
        sliderInactive = inactiveTrack,
        sliderThumb = scheme.primary,
        controlBorder = scheme.outline,
        switchCheckedTrack = scheme.primary,
        switchCheckedThumb = scheme.onPrimary,
        switchUncheckedTrack = uncheckedTrack,
        switchUncheckedThumb = scheme.onSurface,
        disabledContainer = scheme.surfaceVariant,
        disabledContent = scheme.onSurfaceVariant.copy(alpha = 0.62f),
    )
}

private fun buildSemanticColors(
    scheme: androidx.compose.material3.ColorScheme,
    dark: Boolean,
    artwork: ArtworkPalette?,
) = LyruneSemanticColors(
    success = if (dark) Color(0xFF52D99A) else Color(0xFF087A55),
    information = if (dark) Color(0xFF77B7FF) else Color(0xFF1769AA),
    warning = if (dark) Color(0xFFFFC857) else Color(0xFF9A5A00),
    dspActive = if (dark) Color(0xFF5EE6B8) else Color(0xFF087A55),
    spatialActive = if (dark) Color(0xFF72B8FF) else Color(0xFF1769AA),
    lyricsActive = scheme.primary,
    visualizerBass = if (dark) Color(0xFF6D8DFF) else Color(0xFF3156C9),
    visualizerTreble = if (dark) Color(0xFFE58BFF) else Color(0xFF9B3EBC),
    artworkAccent = artwork?.vibrant ?: scheme.secondary,
)

// ─────────────────────────────────────────────────────────────────────────────
// M3 Typography
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
fun AuralisTheme(settings: ThemeSettings, artworkPalette: ArtworkPalette? = null, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = ThemeModeResolver.isDark(settings.appearance, systemDark)
    val animatedArtwork = artworkPalette?.let { target ->
        val dominant by animateColorAsState(target.dominant, tween(AuralisMotion.CONTAINER_MS), label = "art_dominant")
        val vibrant by animateColorAsState(target.vibrant, tween(AuralisMotion.CONTAINER_MS), label = "art_vibrant")
        val muted by animateColorAsState(target.muted, tween(AuralisMotion.CONTAINER_MS), label = "art_muted")
        val darkColor by animateColorAsState(target.dark, tween(AuralisMotion.CONTAINER_MS), label = "art_dark")
        val lightColor by animateColorAsState(target.light, tween(AuralisMotion.CONTAINER_MS), label = "art_light")
        ArtworkPalette(dominant, vibrant, muted, darkColor, lightColor)
    }
    val colorScheme = buildColorScheme(settings, dark, animatedArtwork)
    val tokens      = buildTokens(settings, dark)
    val controls    = buildControlColors(colorScheme, dark)
    val semantic    = buildSemanticColors(colorScheme, dark, animatedArtwork)

    CompositionLocalProvider(
        LocalThemeSettings provides settings,
        LocalAuralisTokens  provides tokens,
        LocalAuralisControlColors provides controls,
        LocalLyruneSemanticColors provides semantic,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = AuralisTypography, content = content)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Background container
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuralisBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val settings = LocalThemeSettings.current
    val base     = MaterialTheme.colorScheme.background
    val accent   = MaterialTheme.colorScheme.primary
    val secondary= MaterialTheme.colorScheme.secondary

    val brush = if (ThemeModeResolver.isAmoled(settings.appearance)) {
        Brush.linearGradient(listOf(Color.Black, Color.Black))
    } else when (settings.style) {
        ThemeStyle.GLASS, ThemeStyle.DYNAMIC_ART -> Brush.verticalGradient(
            colors = listOf(
                lerp(base, accent, 0.16f),
                lerp(base, secondary, 0.08f),
                base,
                lerp(base, accent, 0.06f),
            ),
        )
        ThemeStyle.NEON -> Brush.verticalGradient(
            colors = listOf(
                base,
                lerp(base, accent, 0.12f),
                base,
                lerp(base, secondary, 0.07f),
                base,
            ),
        )
        ThemeStyle.MINIMAL, ThemeStyle.MATERIAL -> Brush.linearGradient(listOf(base, base))
        ThemeStyle.CLASSIC -> Brush.verticalGradient(
            listOf(base, lerp(base, accent, 0.04f), base),
        )
    }
    Box(modifier.background(brush)) { content() }
}
