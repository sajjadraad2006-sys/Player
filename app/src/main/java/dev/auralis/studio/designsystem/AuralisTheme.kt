package dev.auralis.studio.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.auralis.studio.core.model.AppearanceMode
import dev.auralis.studio.core.model.ThemeSettings
import dev.auralis.studio.core.model.ThemeStyle

val LocalThemeSettings = staticCompositionLocalOf { ThemeSettings() }

@Composable
fun AuralisTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.appearance) {
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK, AppearanceMode.AMOLED -> true
    }
    val accent = Color(settings.accentArgb)
    val amoled = settings.style == ThemeStyle.AMOLED || settings.appearance == AppearanceMode.AMOLED
    val colors = if (dark) {
        darkColorScheme(
            primary = accent,
            secondary = when (settings.style) {
                ThemeStyle.NEON -> Color(0xFFFF62D6)
                ThemeStyle.CLASSIC -> Color(0xFFE7B76A)
                else -> Color(0xFF9F91FF)
            },
            background = if (amoled) Color.Black else Color(0xFF090A12),
            surface = if (amoled) Color(0xFF050505) else Color(0xFF131522),
            surfaceVariant = if (amoled) Color(0xFF101010) else Color(0xFF202334),
            onBackground = Color(0xFFF4F3FA),
            onSurface = Color(0xFFF4F3FA),
        )
    } else {
        lightColorScheme(
            primary = accent,
            secondary = Color(0xFF6755C9),
            background = Color(0xFFF5F3FA),
            surface = Color.White,
            surfaceVariant = Color(0xFFEAE7F2),
            onBackground = Color(0xFF17151E),
            onSurface = Color(0xFF17151E),
        )
    }
    CompositionLocalProvider(LocalThemeSettings provides settings) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

@Composable
fun AuralisBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val settings = LocalThemeSettings.current
    val base = MaterialTheme.colorScheme.background
    val brush = when (settings.style) {
        ThemeStyle.GLASS, ThemeStyle.DYNAMIC_ART -> Brush.radialGradient(
            colors = listOf(
                lerp(base, MaterialTheme.colorScheme.primary, 0.20f),
                lerp(base, MaterialTheme.colorScheme.secondary, 0.10f),
                base,
            ),
            radius = 1_350f,
        )
        ThemeStyle.NEON -> Brush.linearGradient(
            listOf(base, lerp(base, MaterialTheme.colorScheme.primary, 0.14f), base),
        )
        else -> Brush.linearGradient(listOf(base, base))
    }
    Box(modifier.background(brush)) { content() }
}
