package dev.auralis.studio.core.model

/** Visual language only. Luminance is controlled exclusively by [AppearanceMode]. */
enum class ThemeStyle {
    GLASS,
    NEON,
    MINIMAL,
    AURORA,
    SKY_BREEZE,
    RETRO_SYNTH,
    DYNAMIC_ART,
    MATERIAL,
}
enum class AppearanceMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class VisualQuality { FULL, BALANCED, REDUCED }
enum class CornerStyle { COMPACT, BALANCED, ROUND }
enum class AnimationStyle {
    SMOOTH,
    SPRING,
    FADE_ZOOM,
    FAST,
    EXPRESSIVE,
    REDUCED,
}

enum class LyricsDisplayStyle {
    APPLE_MUSIC_BLUR,
    KARAOKE_GLOW,
    DYNAMIC_CARD,
    CLASSIC_CLEAN,
}

enum class LyricsTextSize {
    COMPACT,
    STANDARD,
    LARGE,
    EXTRA_LARGE,
}

enum class PlayerVisualStyle {
    MODERN_GLASS,
    WAVEFORM_HERO,
    VINYL_STUDIO,
    CINEMATIC_BLUR,
}

enum class SeekbarStyle {
    WAVY,
    WAVEFORM_BAR,
    MINIMAL_LINE,
}

enum class NavigationBarStyle {
    LIQUID_BUBBLE,
    CLASSIC_DOCK,
}

enum class HomeLayoutStyle {
    CLEAN_APPLE_MUSIC,
    STUDIO_HUB,
    GRID_CARDS,
    DISCOVER_SHELVES,
    MINIMAL_STREAM,
    VINYL_TURNTABLE,
    IMMERSIVE_CAROUSEL,
}

data class HomeLayoutSettings(
    val style: HomeLayoutStyle = HomeLayoutStyle.CLEAN_APPLE_MUSIC,
    val showQuickCategories: Boolean = true,
    val showPlaylists: Boolean = true,
    val showRecentlyPlayed: Boolean = true,
    val showRecentTracks: Boolean = true,
    val showFloatingShuffle: Boolean = true,
    val showFilterChips: Boolean = true,
    val showRecommendations: Boolean = true,
    val showGreetingHeader: Boolean = true,
)

data class ThemeSettings(
    val style: ThemeStyle = ThemeStyle.GLASS,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val accentArgb: Long = 0xFFA78BFA,
    val secondaryArgb: Long = 0xFF38BDF8,
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
    val lyricsStyle: LyricsDisplayStyle = LyricsDisplayStyle.APPLE_MUSIC_BLUR,
    val lyricsTextSize: LyricsTextSize = LyricsTextSize.STANDARD,
    val playerStyle: PlayerVisualStyle = PlayerVisualStyle.MODERN_GLASS,
    val seekbarStyle: SeekbarStyle = SeekbarStyle.WAVY,
    val navBarStyle: NavigationBarStyle = NavigationBarStyle.LIQUID_BUBBLE,
    val lyricsGlowActive: Boolean = true,
    val lyricsBlurInactive: Boolean = true,
    val lyricsWordByWordAnimation: Boolean = true,
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
        "CLASSIC" -> ThemeStyle.MATERIAL
        "MIDNIGHT_PURPLE" -> ThemeStyle.AURORA
        "RETRO_VINYL" -> ThemeStyle.RETRO_SYNTH
        else -> value?.let { stored -> ThemeStyle.entries.firstOrNull { it.name == stored } } ?: ThemeStyle.GLASS
    }
}
