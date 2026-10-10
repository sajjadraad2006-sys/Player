package dev.auralis.studio.core.model

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class PitchTempoMode { INDEPENDENT, TEMPO_LOCK, VINYL }
enum class OrbitDirection { CLOCKWISE, COUNTER_CLOCKWISE, PING_PONG }
enum class EightDMode { DYNAMIC_ORBIT, BASS_SEPARATION, VOCAL_SEPARATION, BINAURAL_3D }
enum class ChannelMode { STEREO, MONO, LEFT, RIGHT, SWAP }

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
    val eightDMode: EightDMode = EightDMode.DYNAMIC_ORBIT,
    val eightDBassSeparation: Float = 0.75f,
    val eightDVocalSeparation: Float = 0.70f,
    val eightDBinauralDepth: Float = 0.80f,
    val reverbMix: Float = 0f,
    val limiterEnabled: Boolean = true,
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

}

object PitchMath {
    fun semitonesToFactor(semitones: Float): Float = 2f.pow(semitones / 12f)
    fun factorToSemitones(factor: Float): Float = (12f * ln(factor) / ln(2f))
}

/** Deterministic spatial trajectories used by the PCM processor and tests. */
object OrbitMath {
    fun pan(phaseRadians: Double, direction: OrbitDirection): Float = when (direction) {
        OrbitDirection.CLOCKWISE -> sin(phaseRadians).toFloat()
        OrbitDirection.COUNTER_CLOCKWISE -> -sin(phaseRadians).toFloat()
        // A front/back orbit is not claimed here: this is an explicit smooth
        // left → right → left headphone motion.
        OrbitDirection.PING_PONG -> -cos(phaseRadians).toFloat()
    }

    /**
     * Spatial trajectory for bass with phase-offset and asymmetric right-channel weighting,
     * allowing sub-bass to orbit independently or oppose mid-range vocals.
     */
    fun bassPan(phaseRadians: Double, direction: OrbitDirection, separation: Float): Float {
        val offsetPhase = phaseRadians + PI * separation.coerceIn(0f, 1f)
        val basePan = pan(offsetPhase, direction)
        // Gentle organic asymmetric weighting favoring right-ear excursion
        val asymmetric = basePan + 0.15f * separation.coerceIn(0f, 1f) * (1f - basePan * basePan)
        return asymmetric.coerceIn(-1f, 1f)
    }

    /**
     * Counter-orbit trajectory for side channel (backing vocals, harmonies, stereo width).
     */
    fun sidePan(phaseRadians: Double, direction: OrbitDirection, separation: Float): Float {
        val offsetPhase = phaseRadians - PI * separation.coerceIn(0f, 1f)
        return pan(offsetPhase, direction).coerceIn(-1f, 1f)
    }

    fun advance(phaseRadians: Double, rateHz: Float, sampleRateHz: Int): Double {
        val next = phaseRadians + 2.0 * PI * rateHz.coerceAtLeast(0f) / sampleRateHz.coerceAtLeast(1)
        return if (next >= 2.0 * PI) next - 2.0 * PI else next
    }
}

data class AudioPreset(
    val name: String,
    val config: AudioConfig,
)

object BuiltInPresets {
    val all = listOf(
        AudioPreset("Original", AudioConfig()),
        AudioPreset("Slowed", AudioConfig(speed = 0.82f, pitchSemitones = -2f, mode = PitchTempoMode.INDEPENDENT)),
        AudioPreset("Slowed + Reverb", AudioConfig(speed = 0.8f, pitchSemitones = -2f, mode = PitchTempoMode.INDEPENDENT, reverbMix = 0.2f)),
        AudioPreset("Nightcore", AudioConfig(speed = 1.22f, pitchSemitones = 3f, mode = PitchTempoMode.INDEPENDENT)),
        AudioPreset("8D Classic", AudioConfig(eightDEnabled = true, eightDMode = EightDMode.DYNAMIC_ORBIT)),
        AudioPreset("8D Bass Separation", AudioConfig(eightDEnabled = true, eightDMode = EightDMode.BASS_SEPARATION, eightDBassSeparation = 0.85f)),
        AudioPreset("8D Dual Voice 3D", AudioConfig(eightDEnabled = true, eightDMode = EightDMode.VOCAL_SEPARATION, eightDVocalSeparation = 0.80f)),
        AudioPreset("Deep Bass", AudioConfig(equalizerEnabled = true, bassDb = 6f, preampDb = -4f, eqBandsDb = listOf(5f, 4f, 2f, 0f, -1f, -1f, 0f, 1f, 1f, 0f))),
    )
}

enum class StemRuntimeState { UNAVAILABLE, IDLE, PREPARING, READY, ERROR }

data class StemPreparationStatus(
    val state: StemRuntimeState,
    val progress: Int? = null,
    val message: String? = null,
) {
    init {
        require(progress == null || progress in 0..100)
        require(state == StemRuntimeState.PREPARING || progress == null)
    }

    companion object {
        fun unavailable(reason: String) = StemPreparationStatus(StemRuntimeState.UNAVAILABLE, message = reason)
        fun preparing(realProgress: Int) = StemPreparationStatus(StemRuntimeState.PREPARING, realProgress.coerceIn(0, 100))
        fun ready() = StemPreparationStatus(StemRuntimeState.READY)
    }
}
