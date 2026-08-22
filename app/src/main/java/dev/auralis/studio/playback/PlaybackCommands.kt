package dev.auralis.studio.playback

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.media3.session.SessionCommand
import dev.auralis.studio.core.model.AudioConfig
import dev.auralis.studio.core.model.ChannelMode
import dev.auralis.studio.core.model.OrbitDirection
import dev.auralis.studio.core.model.PitchTempoMode

object PlaybackCommands {
    const val SET_AUDIO_CONFIG = "dev.auralis.studio.SET_AUDIO_CONFIG"
    val setAudioConfigCommand = SessionCommand(SET_AUDIO_CONFIG, Bundle.EMPTY)

    fun AudioConfig.toBundle(): Bundle = bundleOf(
        "dsp" to dspEnabled,
        "pitch" to pitchSemitones,
        "speed" to speed,
        "mode" to mode.name,
        "eq" to equalizerEnabled,
        "bands" to eqBandsDb.toFloatArray(),
        "preamp" to preampDb,
        "bass" to bassDb,
        "treble" to trebleDb,
        "width" to stereoWidth,
        "balance" to balance,
        "channel" to channelMode.name,
        "eightD" to eightDEnabled,
        "eightDStrength" to eightDStrength,
        "eightDRate" to eightDRateHz,
        "orbit" to orbitDirection.name,
        "reverb" to reverbMix,
        "limiter" to limiterEnabled,
    )

    fun fromBundle(bundle: Bundle): AudioConfig = AudioConfig(
        dspEnabled = bundle.getBoolean("dsp", true),
        pitchSemitones = bundle.getFloat("pitch", 0f).coerceIn(-12f, 12f),
        speed = bundle.getFloat("speed", 1f).coerceIn(0.25f, 3f),
        mode = enumValue(bundle.getString("mode"), PitchTempoMode.TEMPO_LOCK),
        equalizerEnabled = bundle.getBoolean("eq", false),
        eqBandsDb = bundle.getFloatArray("bands")?.toList()?.takeIf { it.size == 10 } ?: List(10) { 0f },
        preampDb = bundle.getFloat("preamp", 0f).coerceIn(-12f, 6f),
        bassDb = bundle.getFloat("bass", 0f).coerceIn(-12f, 12f),
        trebleDb = bundle.getFloat("treble", 0f).coerceIn(-12f, 12f),
        stereoWidth = bundle.getFloat("width", 1f).coerceIn(0f, 2f),
        balance = bundle.getFloat("balance", 0f).coerceIn(-1f, 1f),
        channelMode = enumValue(bundle.getString("channel"), ChannelMode.STEREO),
        eightDEnabled = bundle.getBoolean("eightD", false),
        eightDStrength = bundle.getFloat("eightDStrength", 0.55f).coerceIn(0f, 1f),
        eightDRateHz = bundle.getFloat("eightDRate", 0.09f).coerceIn(0.02f, 0.5f),
        orbitDirection = enumValue(bundle.getString("orbit"), OrbitDirection.CLOCKWISE),
        reverbMix = bundle.getFloat("reverb", 0f).coerceIn(0f, 0.6f),
        limiterEnabled = bundle.getBoolean("limiter", true),
    )

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
