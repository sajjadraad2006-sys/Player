package dev.auralis.desktop.playback

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

class CodecSupportMatrixTest {

    @Test
    fun testWav16Bit44100HzStereo() {
        val file = createWavFile(sampleRate = 44100, channels = 2, bitsPerSample = 16, seconds = 0.5f)
        try {
            val engine = DesktopPlaybackEngine()
            engine.play(file.absolutePath)
            awaitPlaybackDecision(engine)

            val st = engine.state.value
            assertTrue("Duration should be detected", st.durationMs > 0)
            assertEquals(null, st.errorMessage)
            assertTrue("Audio should be playing", st.isPlaying)
            engine.stop()
            engine.release()
        } finally {
            file.delete()
        }
    }

    @Test
    fun testWav16Bit48000HzStereo() {
        val file = createWavFile(sampleRate = 48000, channels = 2, bitsPerSample = 16, seconds = 0.5f)
        try {
            val engine = DesktopPlaybackEngine()
            engine.play(file.absolutePath)
            awaitPlaybackDecision(engine)

            val st = engine.state.value
            assertTrue("Duration should be detected for 48kHz", st.durationMs > 0)
            assertEquals(null, st.errorMessage)
            assertTrue("48kHz audio should play", st.isPlaying)
            engine.stop()
            engine.release()
        } finally {
            file.delete()
        }
    }

    @Test
    fun testWav16BitMonoUpmixing() {
        val file = createWavFile(sampleRate = 44100, channels = 1, bitsPerSample = 16, seconds = 0.5f)
        try {
            val engine = DesktopPlaybackEngine()
            engine.play(file.absolutePath)
            awaitPlaybackDecision(engine)

            val st = engine.state.value
            assertTrue("Duration should be detected for Mono WAV", st.durationMs > 0)
            assertEquals(null, st.errorMessage)
            assertTrue("Mono audio should play via normalizer", st.isPlaying)
            engine.stop()
            engine.release()
        } finally {
            file.delete()
        }
    }

    @Test
    fun testCorruptTruncatedFileHandling() {
        val file = File.createTempFile("corrupt_test_", ".wav")
        file.deleteOnExit()
        // Write invalid/truncated 12 bytes
        FileOutputStream(file).use { out ->
            out.write("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray())
        }

        try {
            val engine = DesktopPlaybackEngine()
            engine.play(file.absolutePath)
            awaitPlaybackDecision(engine)

            val st = engine.state.value
            assertEquals(false, st.isPlaying)
            assertNotNull("Should surface error on corrupt file", st.errorMessage)
            engine.release()
        } finally {
            file.delete()
        }
    }

    @Test
    fun testUnsupportedFileRenamedToMp3() {
        val file = File.createTempFile("fake_mp3_", ".mp3")
        file.deleteOnExit()
        // Write garbage binary data (not an MP3 frame)
        FileOutputStream(file).use { out ->
            out.write(ByteArray(1024) { 0x55.toByte() })
        }

        try {
            val engine = DesktopPlaybackEngine()
            engine.play(file.absolutePath)
            awaitPlaybackDecision(engine)

            val st = engine.state.value
            assertEquals(false, st.isPlaying)
            assertNotNull("Should detect invalid MP3 stream gracefully", st.errorMessage)
            engine.release()
        } finally {
            file.delete()
        }
    }

    @Test
    fun testFlac16BitPlayback() {
        val file = extractResourceToTemp("/audio/test_sine_16.flac", ".flac")
        try {
            val engine = DesktopPlaybackEngine()
            engine.play(file.absolutePath)
            
            var st = engine.state.value
            val deadline = System.currentTimeMillis() + 3000L
            while (System.currentTimeMillis() < deadline && !st.isPlaying && st.errorMessage == null) {
                Thread.sleep(20)
                st = engine.state.value
            }

            assertTrue("Duration should be detected for 16-bit FLAC", st.durationMs > 0)
            assertEquals(null, st.errorMessage)
            assertTrue("16-bit FLAC should be playing", st.isPlaying)
            engine.stop()
            engine.release()
        } finally {
            file.delete()
        }
    }

    @Test
    fun testFlac24Bit96kHzPlayback() {
        val file = extractResourceToTemp("/audio/test_sine_24.flac", ".flac")
        try {
            val engine = DesktopPlaybackEngine()
            engine.play(file.absolutePath)

            var st = engine.state.value
            val deadline = System.currentTimeMillis() + 3000L
            while (System.currentTimeMillis() < deadline && !st.isPlaying && st.errorMessage == null) {
                Thread.sleep(20)
                st = engine.state.value
            }

            assertTrue("Duration should be detected for 24-bit 96kHz FLAC", st.durationMs > 0)
            assertEquals(null, st.errorMessage)
            assertTrue("24-bit FLAC should be playing", st.isPlaying)
            engine.stop()
            engine.release()
        } finally {
            file.delete()
        }
    }

    @Test
    fun testCorruptFlacHandledGracefully() {
        val file = File.createTempFile("corrupt_test_", ".flac")
        file.deleteOnExit()
        FileOutputStream(file).use { out ->
            out.write("fLaC\u0000\u0000\u0000\u0004invalid".toByteArray())
        }

        try {
            val engine = DesktopPlaybackEngine()
            engine.play(file.absolutePath)
            awaitPlaybackDecision(engine)

            val st = engine.state.value
            assertEquals(false, st.isPlaying)
            assertNotNull("Should surface error on corrupt FLAC file", st.errorMessage)
            engine.release()
        } finally {
            file.delete()
        }
    }

    private fun awaitPlaybackDecision(engine: DesktopPlaybackEngine) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline && !engine.state.value.isPlaying && engine.state.value.errorMessage == null) {
            Thread.sleep(10)
        }
    }

    private fun extractResourceToTemp(resourcePath: String, suffix: String): File {
        val tempFile = File.createTempFile("test_audio_", suffix)
        tempFile.deleteOnExit()
        val stream = javaClass.getResourceAsStream(resourcePath)
            ?: error("Resource not found: $resourcePath")
        stream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    private fun createWavFile(sampleRate: Int, channels: Int, bitsPerSample: Int, seconds: Float): File {
        val file = File.createTempFile("wav_codec_test_", ".wav")
        file.deleteOnExit()

        val totalFrames = (sampleRate * seconds).toInt()
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = channels * bytesPerSample
        val byteRate = sampleRate * blockAlign
        val dataSize = totalFrames * blockAlign
        val fileSize = 44 + dataSize

        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(fileSize - 8)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1.toShort()) // PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray())
            header.putInt(dataSize)
            out.write(header.array())

            val chunk = ByteArray(4096)
            val bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
            var framesWritten = 0
            while (framesWritten < totalFrames) {
                bb.clear()
                val framesThisChunk = minOf(chunk.size / blockAlign, totalFrames - framesWritten)
                for (f in 0 until framesThisChunk) {
                    val sample = (sin(2.0 * PI * 440.0 * (framesWritten + f) / sampleRate) * 14000.0).toInt().toShort()
                    for (c in 0 until channels) {
                        bb.putShort(sample)
                    }
                }
                out.write(chunk, 0, framesThisChunk * blockAlign)
                framesWritten += framesThisChunk
            }
        }
        return file
    }
}
