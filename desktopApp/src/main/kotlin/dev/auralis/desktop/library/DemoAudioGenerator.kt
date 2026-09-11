package dev.auralis.desktop.library

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

object DemoAudioGenerator {

    fun ensureDemoTracks(musicDir: File = File(System.getProperty("user.home"), "Music/Lyrune")): List<File> {
        if (!musicDir.exists()) musicDir.mkdirs()
        val track1 = File(musicDir, "Celestial Orbit (Lyrune Demo).wav")
        val track2 = File(musicDir, "Neon Echoes (Synthwave Demo).wav")

        if (!track1.exists()) {
            generateWav(track1, 20f, doubleArrayOf(55.0, 110.0, 220.0, 440.0, 880.0))
        }
        if (!track2.exists()) {
            generateWav(track2, 20f, doubleArrayOf(65.4, 130.8, 261.6, 523.2, 1046.5))
        }
        return listOf(track1, track2)
    }

    internal fun generateWav(file: File, seconds: Float, frequencies: DoubleArray) {
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
            val twoPi = 2.0 * PI

            while (framesWritten < totalFrames) {
                bb.clear()
                val framesThisChunk = minOf(chunk.size / 4, totalFrames - framesWritten)
                for (f in 0 until framesThisChunk) {
                    val frameIdx = framesWritten + f
                    val t = frameIdx.toDouble() / sampleRate
                    var sum = 0.0
                    for (freq in frequencies) {
                        sum += sin(twoPi * freq * t)
                    }
                    sum /= frequencies.size

                    // Envelope
                    val env = when {
                        t < 1.0 -> t
                        t > (seconds - 1.0) -> (seconds - t).coerceAtLeast(0.0)
                        else -> 1.0
                    }
                    val sampleShort = (sum * env * 14000.0).toInt().coerceIn(-32767, 32767).toShort()
                    bb.putShort(sampleShort)
                    bb.putShort(sampleShort)
                }
                out.write(chunk, 0, framesThisChunk * 4)
                framesWritten += framesThisChunk
            }
        }
    }
}
