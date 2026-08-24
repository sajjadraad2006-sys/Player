package dev.auralis.studio.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.auralis.studio.core.model.AppearanceMode
import dev.auralis.studio.core.model.AudioConfig
import dev.auralis.studio.core.model.ChannelMode
import dev.auralis.studio.core.model.OrbitDirection
import dev.auralis.studio.core.model.PitchTempoMode
import dev.auralis.studio.core.model.ThemeSettings
import dev.auralis.studio.core.model.ThemeStyle
import dev.auralis.studio.core.model.ThemeModeResolver
import dev.auralis.studio.core.model.CornerStyle
import dev.auralis.studio.core.model.AnimationStyle
import dev.auralis.studio.core.model.VisualQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("auralis_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val dsp = booleanPreferencesKey("dsp")
        val pitch = floatPreferencesKey("pitch")
        val speed = floatPreferencesKey("speed")
        val pitchMode = stringPreferencesKey("pitch_mode")
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val preamp = floatPreferencesKey("preamp")
        val bass = floatPreferencesKey("bass")
        val treble = floatPreferencesKey("treble")
        val width = floatPreferencesKey("width")
        val balance = floatPreferencesKey("balance")
        val channel = stringPreferencesKey("channel")
        val eightD = booleanPreferencesKey("eight_d")
        val eightDStrength = floatPreferencesKey("eight_d_strength")
        val eightDRate = floatPreferencesKey("eight_d_rate")
        val orbit = stringPreferencesKey("orbit")
        val reverb = floatPreferencesKey("reverb")
        val limiter = booleanPreferencesKey("limiter")
        val eqBands = stringPreferencesKey("eq_bands")
        val theme = stringPreferencesKey("theme")
        val appearance = stringPreferencesKey("appearance")
        val accent = longPreferencesKey("accent")
        val glassAlpha = floatPreferencesKey("glass_alpha")
        val radius = floatPreferencesKey("radius")
        val animation = floatPreferencesKey("animation")
        val visualQuality = stringPreferencesKey("visual_quality")
        val cornerStyle = stringPreferencesKey("corner_style")
        val animationStyle = stringPreferencesKey("animation_style")
        val blurStrength = floatPreferencesKey("blur_strength")
        val backgroundDim = floatPreferencesKey("background_dim")
        val backgroundArtwork = booleanPreferencesKey("background_artwork")
        val hapticFeedback = booleanPreferencesKey("haptic_feedback")
        val minimumDuration = longPreferencesKey("minimum_duration")
        val musicFolders = stringSetPreferencesKey("music_folders")
        val queueUris = stringPreferencesKey("queue_uris")
        val queueIndex = longPreferencesKey("queue_index")
        val queuePosition = longPreferencesKey("queue_position")
        val recentSearches = stringPreferencesKey("recent_searches")
    }

    val recentSearches: Flow<List<String>> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.recentSearches]?.split('\n')?.filter(String::isNotBlank).orEmpty()
    }

    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.settingsDataStore.edit { preferences ->
            val current = preferences[Keys.recentSearches]?.split('\n')?.filter(String::isNotBlank).orEmpty().toMutableList()
            current.remove(trimmed)
            current.add(0, trimmed)
            val trimmedList = current.take(20)
            preferences[Keys.recentSearches] = trimmedList.joinToString("\n")
        }
    }

    suspend fun removeRecentSearch(query: String) {
        context.settingsDataStore.edit { preferences ->
            val current = preferences[Keys.recentSearches]?.split('\n')?.filter(String::isNotBlank).orEmpty().toMutableList()
            current.remove(query.trim())
            preferences[Keys.recentSearches] = current.joinToString("\n")
        }
    }

    suspend fun clearRecentSearches() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(Keys.recentSearches)
        }
    }

    val audioConfig: Flow<AudioConfig> = context.settingsDataStore.data.map { it.toAudioConfig() }
    val themeSettings: Flow<ThemeSettings> = context.settingsDataStore.data.map { it.toThemeSettings() }
    val minimumTrackDurationMs: Flow<Long> = context.settingsDataStore.data.map { it[Keys.minimumDuration] ?: 15_000L }
    val musicFolderUris: Flow<Set<String>> = context.settingsDataStore.data.map { it[Keys.musicFolders] ?: emptySet() }
    val queueSnapshot: Flow<QueueSnapshot> = context.settingsDataStore.data.map { preferences ->
        QueueSnapshot(
            uris = preferences[Keys.queueUris]?.split('\n')?.filter(String::isNotBlank).orEmpty(),
            index = (preferences[Keys.queueIndex] ?: 0L).toInt(),
            positionMs = preferences[Keys.queuePosition] ?: 0L,
        )
    }

    suspend fun setAudioConfig(config: AudioConfig) {
        context.settingsDataStore.edit { p ->
            p[Keys.dsp] = config.dspEnabled
            p[Keys.pitch] = config.pitchSemitones
            p[Keys.speed] = config.speed
            p[Keys.pitchMode] = config.mode.name
            p[Keys.eqEnabled] = config.equalizerEnabled
            p[Keys.preamp] = config.preampDb
            p[Keys.bass] = config.bassDb
            p[Keys.treble] = config.trebleDb
            p[Keys.width] = config.stereoWidth
            p[Keys.balance] = config.balance
            p[Keys.channel] = config.channelMode.name
            p[Keys.eightD] = config.eightDEnabled
            p[Keys.eightDStrength] = config.eightDStrength
            p[Keys.eightDRate] = config.eightDRateHz
            p[Keys.orbit] = config.orbitDirection.name
            p[Keys.reverb] = config.reverbMix
            p[Keys.limiter] = config.limiterEnabled
            p[Keys.eqBands] = config.eqBandsDb.joinToString(",")
        }
    }

    suspend fun setTheme(settings: ThemeSettings) {
        context.settingsDataStore.edit { p ->
            p[Keys.theme] = settings.style.name
            p[Keys.appearance] = settings.appearance.name
            p[Keys.accent] = settings.accentArgb
            p[Keys.glassAlpha] = settings.glassAlpha
            p[Keys.radius] = settings.cornerRadius
            p[Keys.animation] = settings.animationScale
            p[Keys.visualQuality] = settings.visualQuality.name
            p[Keys.cornerStyle] = settings.cornerStyle.name
            p[Keys.animationStyle] = settings.animationStyle.name
            p[Keys.blurStrength] = settings.blurStrength
            p[Keys.backgroundDim] = settings.backgroundDim
            p[Keys.backgroundArtwork] = settings.backgroundArtwork
            p[Keys.hapticFeedback] = settings.hapticFeedback
        }
    }

    suspend fun setMinimumTrackDuration(durationMs: Long) {
        context.settingsDataStore.edit { it[Keys.minimumDuration] = durationMs.coerceAtLeast(0) }
    }

    suspend fun addMusicFolder(uri: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.musicFolders] = (preferences[Keys.musicFolders] ?: emptySet()) + uri
        }
    }

    suspend fun removeMusicFolder(uri: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.musicFolders] = (preferences[Keys.musicFolders] ?: emptySet()) - uri
        }
    }

    suspend fun saveQueue(uris: List<String>, index: Int, positionMs: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.queueUris] = uris.joinToString("\n")
            preferences[Keys.queueIndex] = index.toLong()
            preferences[Keys.queuePosition] = positionMs.coerceAtLeast(0)
        }
    }

    private fun Preferences.toAudioConfig(): AudioConfig = AudioConfig(
        dspEnabled = this[Keys.dsp] ?: true,
        pitchSemitones = this[Keys.pitch] ?: 0f,
        speed = this[Keys.speed] ?: 1f,
        mode = enumValue(this[Keys.pitchMode], PitchTempoMode.TEMPO_LOCK),
        equalizerEnabled = this[Keys.eqEnabled] ?: false,
        eqBandsDb = this[Keys.eqBands]?.split(",")?.mapNotNull(String::toFloatOrNull)?.takeIf { it.size == 10 } ?: List(10) { 0f },
        preampDb = this[Keys.preamp] ?: 0f,
        bassDb = this[Keys.bass] ?: 0f,
        trebleDb = this[Keys.treble] ?: 0f,
        stereoWidth = this[Keys.width] ?: 1f,
        balance = this[Keys.balance] ?: 0f,
        channelMode = enumValue(this[Keys.channel], ChannelMode.STEREO),
        eightDEnabled = this[Keys.eightD] ?: false,
        eightDStrength = this[Keys.eightDStrength] ?: 0.55f,
        eightDRateHz = this[Keys.eightDRate] ?: 0.09f,
        orbitDirection = enumValue(this[Keys.orbit], OrbitDirection.CLOCKWISE),
        reverbMix = this[Keys.reverb] ?: 0f,
        limiterEnabled = this[Keys.limiter] ?: true,
    )

    private fun Preferences.toThemeSettings(): ThemeSettings = ThemeSettings(
        // `AMOLED` used to be a ThemeStyle. Map it to a neutral visual style while
        // preserving the independently stored appearance; appearance is now the
        // only source of luminance.
        style = ThemeModeResolver.migrateLegacyStyle(this[Keys.theme]),
        appearance = enumValue(this[Keys.appearance], AppearanceMode.SYSTEM),
        accentArgb = this[Keys.accent] ?: 0xFFA78BFA,
        glassAlpha = this[Keys.glassAlpha] ?: 0.16f,
        cornerRadius = this[Keys.radius] ?: 24f,
        animationScale = this[Keys.animation] ?: 1f,
        visualQuality = enumValue(this[Keys.visualQuality], VisualQuality.BALANCED),
        cornerStyle = enumValue(this[Keys.cornerStyle], CornerStyle.BALANCED),
        animationStyle = enumValue(this[Keys.animationStyle], AnimationStyle.SMOOTH),
        blurStrength = this[Keys.blurStrength] ?: 0.7f,
        backgroundDim = this[Keys.backgroundDim] ?: 0.55f,
        backgroundArtwork = this[Keys.backgroundArtwork] ?: true,
        hapticFeedback = this[Keys.hapticFeedback] ?: true,
    )

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}

data class QueueSnapshot(
    val uris: List<String> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
)
