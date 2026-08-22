package dev.auralis.studio.audio.dsp

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi

/** Keeps time-stretch/pitch ahead of tone and spatial processing in the PCM chain. */
@UnstableApi
class StudioAudioProcessorChain(
    private val studioProcessor: StudioAudioProcessor,
) : AudioProcessorChain {
    private val sonicProcessor = SonicAudioProcessor()
    private val processors = arrayOf<AudioProcessor>(sonicProcessor, studioProcessor)

    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
        sonicProcessor.setSpeed(playbackParameters.speed)
        sonicProcessor.setPitch(playbackParameters.pitch)
        return playbackParameters
    }

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = false

    override fun getMediaDuration(playoutDuration: Long): Long =
        if (sonicProcessor.isActive) sonicProcessor.getMediaDuration(playoutDuration) else playoutDuration

    override fun getSkippedOutputFrameCount(): Long = 0L
}
