package dev.auralis.studio.playback

import dev.auralis.studio.audio.dsp.StudioDspEngine
import dev.auralis.studio.audio.visualizer.PcmAudioTap
import kotlinx.coroutines.flow.StateFlow

/**
 * State representing audio playback status.
 */
data class PlaybackMetadata(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val artwork: String? = null,
)

data class PlaybackState(
    val completedSessions: Long = 0,
    val metadata: PlaybackMetadata? = null,
    val isPlaying: Boolean = false,
    val currentUri: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val speed: Float = 1.0f,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Platform playback abstraction.
 *
 * Implemented on Android by AndroidPlaybackEngine (wrapping Media3/ExoPlayer),
 * and on Windows/Desktop by DesktopPlaybackEngine (wrapping streaming Java Sound + decoders).
 */
interface PlaybackEngine {
    val state: StateFlow<PlaybackState>
    val dspEngine: StudioDspEngine
    val audioTap: PcmAudioTap get() = dspEngine.pcmTap

    fun play(uri: String, startPositionMs: Long = 0L)
    fun resume()
    fun pause()
    fun seekTo(positionMs: Long)
    fun stop()
    fun setVolume(volume: Float)
    fun setSpeed(speed: Float)
    fun release()
}
