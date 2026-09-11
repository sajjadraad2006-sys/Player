package dev.auralis.desktop.playback

import dev.auralis.studio.audio.dsp.StudioDspEngine
import dev.auralis.studio.core.model.AudioConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class DesktopPlaybackEngineTest {

    @Test
    fun testStudioDspEngineProcessesPcmAndTapsVisualizer() {
        val dsp = StudioDspEngine()
        dsp.configure(sampleRate = 44100, channelCount = 2)

        val config = AudioConfig(
            dspEnabled = true,
            equalizerEnabled = true,
            bassDb = 4f,
            trebleDb = -2f,
            eightDEnabled = true,
            reverbMix = 0.25f,
        )
        dsp.updateConfig(config)

        // Generate 1024 frames of 440 Hz test tone
        val frames = 1024
        val bytes = ByteArray(frames * 4) // stereo 16-bit
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val sample = (sin(2.0 * PI * 440.0 * i / 44100.0) * 16000.0).toInt().toShort()
            bb.putShort(sample)
            bb.putShort(sample)
        }

        // Copy original to compare
        val original = bytes.clone()

        // Process in-place
        dsp.processPcm16InPlace(bytes, 0, bytes.size)

        // Verify samples were modified by DSP pipeline
        var differences = 0
        for (i in bytes.indices) {
            if (bytes[i] != original[i]) differences++
        }
        assertTrue("DSP should modify PCM samples when effects are active", differences > 0)

        // Verify visualizer tap received frames
        assertTrue("Visualizer tap should receive audio samples", dsp.pcmTap.writePosition > 0L)
        assertTrue("Visualizer tap sample buffer should contain audio", dsp.pcmTap.samples.any { it != 0f })
    }

    @Test
    fun testDesktopPlaybackEngineLifecycle() {
        val tempWav = createTestWavFile(seconds = 1.0f)
        try {
            val engine = DesktopPlaybackEngine()
            assertEquals(false, engine.state.value.isPlaying)

            // Test play
            engine.play(tempWav.absolutePath)
            Thread.sleep(150)

            // Verify playback started or duration resolved
            val st = engine.state.value
            assertTrue("Duration should be resolved", st.durationMs > 0)

            // Test volume
            engine.setVolume(0.8f)
            assertEquals(0.8f, engine.state.value.volume, 0.01f)

            // Test pause
            engine.pause()
            Thread.sleep(50)
            assertEquals(false, engine.state.value.isPlaying)

            // Test resume
            engine.resume()
            Thread.sleep(50)
            assertTrue(engine.state.value.isPlaying)

            // Test seek
            engine.seekTo(200L)
            Thread.sleep(50)

            // Test stop & release
            engine.stop()
            assertEquals(false, engine.state.value.isPlaying)
            engine.release()
        } finally {
            tempWav.delete()
        }
    }

    @Test
    fun pausedSeekUpdatesTimelineWithoutStartingAudio() {
        val file = createTestWavFile(5f)
        val engine = DesktopPlaybackEngine()
        try {
            engine.play(file.absolutePath)
            val ready = System.nanoTime() + 3_000_000_000L
            while(!engine.state.value.isPlaying && engine.state.value.errorMessage == null && System.nanoTime() < ready) Thread.sleep(10)
            assertTrue(engine.state.value.isPlaying)
            engine.pause()
            engine.seekTo(3000)
            val sought = System.nanoTime() + 3_000_000_000L
            while(engine.state.value.currentPositionMs != 3000L && System.nanoTime() < sought) Thread.sleep(10)
            assertEquals(3000L, engine.state.value.currentPositionMs)
            assertEquals(false, engine.state.value.isPlaying)
            assertEquals(false, engine.state.value.isBuffering)
            engine.resume()
            assertTrue(engine.state.value.isPlaying)
        } finally { engine.release(); file.delete() }
    }

    @Test
    fun resumeStartsAtTheSavedPosition() {
        val file = createTestWavFile(5f)
        val engine = DesktopPlaybackEngine()
        try {
            val track = dev.auralis.desktop.library.DesktopTrack(1, file.absolutePath, "Resume test", "", durationMs = 5000)
            engine.playTrack(track, 3000)
            val deadline = System.nanoTime() + 3_000_000_000L
            while(!engine.state.value.isPlaying && engine.state.value.errorMessage == null && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue(engine.state.value.isPlaying)
            assertTrue(engine.state.value.currentPositionMs in 3000..3999)
            assertEquals(5000L, engine.state.value.durationMs)
        } finally { engine.release(); file.delete() }
    }

    private fun createTestWavFile(seconds: Float): File {
        val file = File.createTempFile("lyrune_test_", ".wav")
        file.deleteOnExit()

        val sampleRate = 44100
        val channels = 2
        val totalFrames = (sampleRate * seconds).toInt()
        val pcmDataSize = totalFrames * channels * 2
        val totalFileSize = 44 + pcmDataSize

        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            // RIFF header
            header.put("RIFF".toByteArray())
            header.putInt(totalFileSize - 8)
            header.put("WAVE".toByteArray())
            // fmt subchunk
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1) // AudioFormat 1 = PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * channels * 2) // ByteRate
            header.putShort((channels * 2).toShort()) // BlockAlign
            header.putShort(16) // BitsPerSample
            // data subchunk
            header.put("data".toByteArray())
            header.putInt(pcmDataSize)
            out.write(header.array())

            // Write sine wave PCM
            val chunk = ByteArray(4096)
            val chunkBb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
            var framesWritten = 0
            while (framesWritten < totalFrames) {
                chunkBb.clear()
                val framesThisChunk = minOf(chunk.size / 4, totalFrames - framesWritten)
                for (f in 0 until framesThisChunk) {
                    val frameIdx = framesWritten + f
                    val sample = (sin(2.0 * PI * 440.0 * frameIdx / sampleRate) * 12000.0).toInt().toShort()
                    chunkBb.putShort(sample)
                    chunkBb.putShort(sample)
                }
                out.write(chunk, 0, framesThisChunk * 4)
                framesWritten += framesThisChunk
            }
        }
        return file
    }
}
