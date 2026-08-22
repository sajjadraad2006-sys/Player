package dev.auralis.studio.core.model

enum class ThemeStyle { GLASS, AMOLED, MINIMAL, MATERIAL, NEON, DYNAMIC_ART, CLASSIC }
enum class AppearanceMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class VisualQuality { FULL, BALANCED, REDUCED }

data class ThemeSettings(
    val style: ThemeStyle = ThemeStyle.GLASS,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val accentArgb: Long = 0xFF70E1C1,
    val glassAlpha: Float = 0.16f,
    val cornerRadius: Float = 24f,
    val animationScale: Float = 1f,
    val visualQuality: VisualQuality = VisualQuality.BALANCED,
)
