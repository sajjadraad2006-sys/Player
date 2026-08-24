package dev.auralis.studio.core.model

/** Visual language only. Luminance is controlled exclusively by [AppearanceMode]. */
enum class ThemeStyle { GLASS, MINIMAL, MATERIAL, NEON, DYNAMIC_ART, CLASSIC }
enum class AppearanceMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class VisualQuality { FULL, BALANCED, REDUCED }
enum class CornerStyle { COMPACT, BALANCED, ROUND }
enum class AnimationStyle { REDUCED, SMOOTH, EXPRESSIVE }

data class ThemeSettings(
    val style: ThemeStyle = ThemeStyle.GLASS,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val accentArgb: Long = 0xFFA78BFA,
    val glassAlpha: Float = 0.16f,
    val cornerRadius: Float = 24f,
    val animationScale: Float = 1f,
    val visualQuality: VisualQuality = VisualQuality.BALANCED,
    val cornerStyle: CornerStyle = CornerStyle.BALANCED,
    val animationStyle: AnimationStyle = AnimationStyle.SMOOTH,
    val blurStrength: Float = 0.7f,
    val backgroundDim: Float = 0.55f,
    val backgroundArtwork: Boolean = true,
    val hapticFeedback: Boolean = true,
) {
    /** Derived corner radius based on CornerStyle, overriding manual slider. */
    val effectiveCornerRadius: Float get() = when (cornerStyle) {
        CornerStyle.COMPACT -> 8f
        CornerStyle.BALANCED -> 16f
        CornerStyle.ROUND -> 28f
    }
}

/** Pure resolver kept outside Compose so invalid combinations are unit-testable. */
object ThemeModeResolver {
    fun isDark(appearance: AppearanceMode, systemDark: Boolean): Boolean = when (appearance) {
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK, AppearanceMode.AMOLED -> true
    }

    fun isAmoled(appearance: AppearanceMode): Boolean = appearance == AppearanceMode.AMOLED

    /** Migrates the removed legacy visual style without changing explicit luminance. */
    fun migrateLegacyStyle(value: String?): ThemeStyle = when (value) {
        "AMOLED" -> ThemeStyle.MINIMAL
        else -> value?.let { stored -> ThemeStyle.entries.firstOrNull { it.name == stored } } ?: ThemeStyle.GLASS
    }
}
