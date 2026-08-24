package dev.auralis.studio.core.model

import kotlin.math.ln
import kotlin.math.pow

enum class PitchTempoMode { INDEPENDENT, TEMPO_LOCK, VINYL }
enum class OrbitDirection { CLOCKWISE, COUNTER_CLOCKWISE, PING_PONG }
enum class ChannelMode { STEREO, MONO, LEFT, RIGHT, SWAP }

/** Modes for real-time audio stem isolation & vocal/music/bass separation. */
enum class StemIsolationMode {
    OFF,           // Full mix (no isolation)
    VOCAL_SOLO,    // عزل الصوت البشري فقط (Acapella / Vocals only)
    INSTRUMENTAL,  // عزل اللحن والموسيقى (كاريوكي - بدون صوت بشري)
    BASS_SOLO,     // عزل البيس فقط (Bassline only)
    CUSTOM,        // تحكم حر بجميع المسارات (Custom multi-slider mixer)
}

data class AudioConfig(
    val dspEnabled: Boolean = true,
    val pitchSemitones: Float = 0f,
    val speed: Float = 1f,
    val mode: PitchTempoMode = PitchTempoMode.TEMPO_LOCK,
    val equalizerEnabled: Boolean = false,
    val eqBandsDb: List<Float> = List(10) { 0f },
    val preampDb: Float = 0f,
    val bassDb: Float = 0f,
    val trebleDb: Float = 0f,
    val stereoWidth: Float = 1f,
    val balance: Float = 0f,
    val channelMode: ChannelMode = ChannelMode.STEREO,
    val eightDEnabled: Boolean = false,
    val eightDStrength: Float = 0.55f,
    val eightDRateHz: Float = 0.09f,
    val orbitDirection: OrbitDirection = OrbitDirection.CLOCKWISE,
    val reverbMix: Float = 0f,
    val limiterEnabled: Boolean = true,
    // ── Stem Isolation & Vocal/Music/Bass Separation ──
    val stemIsolationEnabled: Boolean = false,
    val stemMode: StemIsolationMode = StemIsolationMode.OFF,
    val stemVocalVolume: Float = 1.0f,    // 0.0 (Mute) .. 1.0 (Normal) .. 2.0 (Boost)
    val stemMelodyVolume: Float = 1.0f,   // 0.0 (Mute) .. 1.0 (Normal) .. 2.0 (Boost)
    val stemBassVolume: Float = 1.0f,     // 0.0 (Mute) .. 1.0 (Normal) .. 2.0 (Boost)
) {
    val effectivePitchFactor: Float
        get() = when (mode) {
            PitchTempoMode.VINYL -> speed
            PitchTempoMode.INDEPENDENT -> PitchMath.semitonesToFactor(pitchSemitones)
            PitchTempoMode.TEMPO_LOCK -> 1f
        }

    val effectivePitchSemitones: Float
        get() = PitchMath.factorToSemitones(effectivePitchFactor)

    fun pure(): AudioConfig = AudioConfig(dspEnabled = false)

    /** Apply a stem preset cleanly. */
    fun withStemMode(mode: StemIsolationMode): AudioConfig = when (mode) {
        StemIsolationMode.OFF -> copy(
            stemIsolationEnabled = false,
            stemMode = StemIsolationMode.OFF,
            stemVocalVolume = 1.0f,
            stemMelodyVolume = 1.0f,
            stemBassVolume = 1.0f,
            dspEnabled = true,
        )
        StemIsolationMode.VOCAL_SOLO -> copy(
            stemIsolationEnabled = true,
            stemMode = StemIsolationMode.VOCAL_SOLO,
            stemVocalVolume = 1.4f,
            stemMelodyVolume = 0.0f,
            stemBassVolume = 0.0f,
            dspEnabled = true,
        )
        StemIsolationMode.INSTRUMENTAL -> copy(
            stemIsolationEnabled = true,
            stemMode = StemIsolationMode.INSTRUMENTAL,
            stemVocalVolume = 0.0f,
            stemMelodyVolume = 1.0f,
            stemBassVolume = 1.0f,
            dspEnabled = true,
        )
        StemIsolationMode.BASS_SOLO -> copy(
            stemIsolationEnabled = true,
            stemMode = StemIsolationMode.BASS_SOLO,
            stemVocalVolume = 0.0f,
            stemMelodyVolume = 0.0f,
            stemBassVolume = 1.5f,
            dspEnabled = true,
        )
        StemIsolationMode.CUSTOM -> copy(
            stemIsolationEnabled = true,
            stemMode = StemIsolationMode.CUSTOM,
            dspEnabled = true,
        )
    }
}

object PitchMath {
    fun semitonesToFactor(semitones: Float): Float = 2f.pow(semitones / 12f)
    fun factorToSemitones(factor: Float): Float = (12f * ln(factor) / ln(2f))
}

data class AudioPreset(
    val name: String,
    val config: AudioConfig,
)

object BuiltInPresets {
    val all = listOf(
        AudioPreset("Original", AudioConfig()),
        AudioPreset("Vocal Only", AudioConfig().withStemMode(StemIsolationMode.VOCAL_SOLO)),
        AudioPreset("Karaoke / Music", AudioConfig().withStemMode(StemIsolationMode.INSTRUMENTAL)),
        AudioPreset("Bass Only", AudioConfig().withStemMode(StemIsolationMode.BASS_SOLO)),
        AudioPreset("Slowed", AudioConfig(speed = 0.82f, pitchSemitones = -2f, mode = PitchTempoMode.INDEPENDENT)),
        AudioPreset("Slowed + Reverb", AudioConfig(speed = 0.8f, pitchSemitones = -2f, mode = PitchTempoMode.INDEPENDENT, reverbMix = 0.2f)),
        AudioPreset("Nightcore", AudioConfig(speed = 1.22f, pitchSemitones = 3f, mode = PitchTempoMode.INDEPENDENT)),
        AudioPreset("8D", AudioConfig(eightDEnabled = true)),
        AudioPreset("Deep Bass", AudioConfig(equalizerEnabled = true, bassDb = 6f, preampDb = -4f, eqBandsDb = listOf(5f, 4f, 2f, 0f, -1f, -1f, 0f, 1f, 1f, 0f))),
    )
}
