package dev.auralis.studio.core.model

enum class ThemeStyle { GLASS, AMOLED, MINIMAL, MATERIAL, NEON, DYNAMIC_ART, CLASSIC }
enum class AppearanceMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class VisualQuality { FULL, BALANCED, REDUCED }
enum class CornerStyle { COMPACT, BALANCED, ROUND }
enum class AnimationStyle { REDUCED, SMOOTH, EXPRESSIVE }

data class ThemeSettings(
    val style: ThemeStyle = ThemeStyle.GLASS,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val accentArgb: Long = 0xFF5EE6B8,
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
