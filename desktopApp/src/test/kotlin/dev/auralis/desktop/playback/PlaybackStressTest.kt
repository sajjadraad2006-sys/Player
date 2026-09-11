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

class PlaybackStressTest {

    @Test
    fun testRapidTrackSwitchingStress() {
        val tracks = listOf(
            createTestToneFile("track_a", 220.0, 1.0f),
            createTestToneFile("track_b", 440.0, 1.0f),
            createTestToneFile("track_c", 880.0, 1.0f),
        )

        val engine = DesktopPlaybackEngine()

        try {
            val initialThreads = Thread.activeCount()

            // Perform 100 rapid track switches with mixed operations
            for (i in 0 until 100) {
                val track = tracks[i % tracks.size]
                engine.play(track.absolutePath, startPositionMs = (i * 10L) % 500L)

                if (i % 10 == 0) {
                    engine.pause()
                } else if (i % 10 == 1) {
                    engine.resume()
                }

                if (i % 15 == 0) {
                    engine.seekTo(300L)
                }

                // Small realistic interval simulating rapid user clicking
                Thread.sleep(10)
            }

            // Let the last track play briefly
            Thread.sleep(150)
            val finalState = engine.state.value
            assertNotNull(finalState)

            engine.stop()
            engine.release()

            // Verify thread cleanup
            Thread.sleep(200)
            val finalThreads = Thread.activeCount()
            assertTrue(
                "Active threads should not grow catastrophically after 100 switches (initial: $initialThreads, final: $finalThreads)",
                finalThreads <= initialThreads + 5,
            )
        } finally {
            tracks.forEach { it.delete() }
        }
    }

    @Test
    fun testRapidSeekStress() {
        val track = createTestToneFile("long_track", 440.0, 5.0f)
        val engine = DesktopPlaybackEngine()

        try {
            engine.play(track.absolutePath)
            Thread.sleep(100)

            // Rapid seeks across duration: 0%, 10%, 50%, 90%, 20%
            val targets = listOf(0L, 500L, 2500L, 4500L, 1000L, 3000L, 500L, 0L)
            for (pos in targets) {
                engine.seekTo(pos)
                Thread.sleep(20)
            }

            // Seek while paused
            engine.pause()
            Thread.sleep(30)
            for (pos in targets) {
                engine.seekTo(pos)
                Thread.sleep(10)
            }
            engine.resume()
            Thread.sleep(50)

            assertTrue("Playback engine should remain alive after seek stress", engine.state.value.isPlaying)
            engine.stop()
            engine.release()
        } finally {
            track.delete()
        }
    }

    private fun createTestToneFile(prefix: String, freq: Double, seconds: Float): File {
        val file = File.createTempFile(prefix, ".wav")
        file.deleteOnExit()

        val sampleRate = 44100
        val channels = 2
        val totalFrames = (sampleRate * seconds).toInt()
        val dataSize = totalFrames * channels * 2
        val fileSize = 44 + dataSize

        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(fileSize - 8)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1.toShort())
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * channels * 2)
            header.putShort((channels * 2).toShort())
            header.putShort(16.toShort())
            header.put("data".toByteArray())
            header.putInt(dataSize)
            out.write(header.array())

            val chunk = ByteArray(4096)
            val bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
            var framesWritten = 0
            while (framesWritten < totalFrames) {
                bb.clear()
                val framesThisChunk = minOf(chunk.size / 4, totalFrames - framesWritten)
                for (f in 0 until framesThisChunk) {
                    val sample = (sin(2.0 * PI * freq * (framesWritten + f) / sampleRate) * 12000.0).toInt().toShort()
                    bb.putShort(sample)
                    bb.putShort(sample)
                }
                out.write(chunk, 0, framesThisChunk * 4)
                framesWritten += framesThisChunk
            }
        }
        return file
    }
}
