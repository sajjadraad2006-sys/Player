package dev.auralis.desktop.storage

import dev.auralis.studio.core.model.AudioConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Serializable
data class DesktopDspSettings(
    val pitchSemitones: Float = 0f,
    val speed: Float = 1f,
    val mode: dev.auralis.studio.core.model.PitchTempoMode = dev.auralis.studio.core.model.PitchTempoMode.TEMPO_LOCK,
    val preampDb: Float = 0f,
    val stereoWidth: Float = 1f,
    val balance: Float = 0f,
    val channelMode: dev.auralis.studio.core.model.ChannelMode = dev.auralis.studio.core.model.ChannelMode.STEREO,
    val eightDRateHz: Float = .09f,
    val orbitDirection: dev.auralis.studio.core.model.OrbitDirection = dev.auralis.studio.core.model.OrbitDirection.CLOCKWISE,

    val dspEnabled: Boolean = true,
    val bassDb: Float = 0f,
    val trebleDb: Float = 0f,
    val equalizerEnabled: Boolean = false,
    val eqBandsDb: List<Float> = emptyList(),
    val eightDEnabled: Boolean = false,
    val eightDStrength: Float = 0.5f,
    val reverbMix: Float = 0f,
    val limiterEnabled: Boolean = true,
) {
    fun toAudioConfig(): AudioConfig = AudioConfig(
        pitchSemitones = pitchSemitones,
        speed = speed,
        mode = mode,
        preampDb = preampDb,
        stereoWidth = stereoWidth,
        balance = balance,
        channelMode = channelMode,
        eightDRateHz = eightDRateHz,
        orbitDirection = orbitDirection,

        dspEnabled = dspEnabled,
        bassDb = bassDb,
        trebleDb = trebleDb,
        equalizerEnabled = equalizerEnabled,
        eqBandsDb = eqBandsDb,
        eightDEnabled = eightDEnabled,
        eightDStrength = eightDStrength,
        reverbMix = reverbMix,
        limiterEnabled = limiterEnabled,
    )

    companion object {
        fun from(config: AudioConfig): DesktopDspSettings = DesktopDspSettings(
            pitchSemitones = config.pitchSemitones,
            speed = config.speed,
            mode = config.mode,
            preampDb = config.preampDb,
            stereoWidth = config.stereoWidth,
            balance = config.balance,
            channelMode = config.channelMode,
            eightDRateHz = config.eightDRateHz,
            orbitDirection = config.orbitDirection,

            dspEnabled = config.dspEnabled,
            bassDb = config.bassDb,
            trebleDb = config.trebleDb,
            equalizerEnabled = config.equalizerEnabled,
            eqBandsDb = config.eqBandsDb,
            eightDEnabled = config.eightDEnabled,
            eightDStrength = config.eightDStrength,
            reverbMix = config.reverbMix,
            limiterEnabled = config.limiterEnabled,
        )
    }
}

typealias PersistentSettingsStore = DesktopSettingsRepository

@Serializable
data class DesktopSettings(
    val savedOnlineTracks: List<dev.auralis.desktop.library.DesktopTrack> = emptyList(),
    val theme: dev.auralis.studio.core.model.ThemeSettings? = null,
    val homeLayout: dev.auralis.studio.core.model.HomeLayoutSettings = dev.auralis.studio.core.model.HomeLayoutSettings(),
    val recentTracks: List<dev.auralis.desktop.library.DesktopTrack> = emptyList(),
    val rememberSearches: Boolean = true,
    val onlineLyrics: Boolean = true,
    val nowPlayingVisible: Boolean = true,
    val searchHistory: List<String> = emptyList(),
    val onlineFavorites: List<dev.auralis.studio.online.OnlineTrack> = emptyList(),
    val schemaVersion: Int = 1,
    val themeAccent: String = "MINT",
    val isAmoled: Boolean = false,
    val language: String = "EN",
    val windowWidth: Int = 1200,
    val windowHeight: Int = 820,
    val isMaximized: Boolean = false,
    val lastTab: String = "LIBRARY",
    val volume: Float = 1.0f,
    val dspSettings: DesktopDspSettings = DesktopDspSettings(),
) {
    val dspConfig: AudioConfig get() = dspSettings.toAudioConfig()
}

/**
 * Manages persistent user preferences, window geometry, and audio configuration.
 *
 * Requirements satisfied:
 * - Persists settings to %LOCALAPPDATA%\Lyrune\settings.json.
 * - Restores window size and maximized state with bounds clamping to prevent off-screen coordinates.
 * - Debounces writes to avoid disk thrashing when dragging sliders or resizing windows.
 * - Corrupt recovery: safely preserves corrupt settings as .bak and recovers defaults.
 */
class DesktopSettingsRepository(
    private val settingsFile: File = DesktopPaths.settingsFile,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private val _settings = MutableStateFlow(loadSync())
    val settings: StateFlow<DesktopSettings> = _settings.asStateFlow()

    private var debounceJob: Job? = null

    private fun loadSync(): DesktopSettings {
        if (!settingsFile.exists() || settingsFile.length() == 0L) {
            return DesktopSettings()
        }

        return try {
            val content = settingsFile.readText(Charsets.UTF_8)
            val decoded = json.decodeFromString<DesktopSettings>(content)
            // Clamp dimensions to prevent off-screen or unreadable windows
            decoded.copy(
                windowWidth = max(900, min(3840, decoded.windowWidth)),
                windowHeight = max(600, min(2160, decoded.windowHeight)),
            )
        } catch (e: Exception) {
            try {
                if (settingsFile.exists() && settingsFile.length() > 0L) {
                    val backup = File(settingsFile.parentFile, "${settingsFile.name}.corrupt.${System.currentTimeMillis()}.bak")
                    settingsFile.copyTo(backup, overwrite = true)
                }
            } catch (_: Exception) {}
            DesktopSettings()
        }
    }

    suspend fun update(debounceMs: Long = 400L, transform: (DesktopSettings) -> DesktopSettings) = mutex.withLock {
        val updated = transform(_settings.value)
        _settings.value = updated
        scheduleSave(debounceMs)
    }

    suspend fun updateWindowGeometry(width: Int, height: Int, isMaximized: Boolean) = mutex.withLock {
        val clampedWidth = max(900, min(3840, width))
        val clampedHeight = max(600, min(2160, height))
        _settings.value = _settings.value.copy(
            windowWidth = clampedWidth,
            windowHeight = clampedHeight,
            isMaximized = isMaximized,
        )
        scheduleSave(500L)
    }

    private fun scheduleSave(debounceMs: Long) {
        debounceJob?.cancel()
        if (debounceMs <= 0L) {
            commitToDisk()
        } else {
            debounceJob = scope.launch {
                delay(debounceMs)
                mutex.withLock {
                    commitToDisk()
                }
            }
        }
    }

    private fun commitToDisk() {
        try {
            val serialized = json.encodeToString(_settings.value)
            DesktopPaths.writeAtomically(settingsFile, serialized)
        } catch (_: Exception) {
        }
    }

    suspend fun flushNow() = mutex.withLock {
        debounceJob?.cancel()
        commitToDisk()
    }
}
