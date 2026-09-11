package dev.auralis.desktop.playback

import dev.auralis.desktop.playback.decoder.FfmpegAudioDecoder
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class CompressedAudioDecoderTest {
    @Test fun readsM4aTagsDurationAndAttachedCover() = kotlinx.coroutines.runBlocking {
        val directory = Files.createTempDirectory("lyrune-m4a-art").toFile()
        try {
            val cover = java.io.File(directory, "source.jpg")
            val bitmap = java.awt.image.BufferedImage(512, 512, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val graphics = bitmap.createGraphics()
            graphics.color = java.awt.Color.CYAN; graphics.fillRect(0, 0, 512, 512); graphics.dispose()
            javax.imageio.ImageIO.write(bitmap, "jpg", cover); bitmap.flush()
            val file = java.io.File(directory, "tagged.m4a")
            val process = ProcessBuilder(FfmpegAudioDecoder.executable, "-v", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-i", cover.absolutePath,
                "-map", "0:a", "-map", "1:v", "-c:a", "aac", "-c:v", "copy", "-disposition:v", "attached_pic",
                "-metadata", "title=My Song (Official Audio)", "-metadata", "artist=Test artist", "-metadata", "album=Test album", file.absolutePath).start()
            assertTrue(process.waitFor(15, TimeUnit.SECONDS)); assertEquals(0, process.exitValue())
            val metadata = dev.auralis.desktop.library.DesktopTrackMetadata.read(file)
            assertEquals("My Song (Official Audio)", metadata.title)
            assertEquals("Test artist", metadata.artist)
            assertEquals("Test album", metadata.album)
            assertTrue(metadata.durationMs in 1900..2200)
            val artwork = dev.auralis.desktop.artwork.DesktopArtworkManager.getArtwork(file.absolutePath)
            assertNotNull(artwork)
            assertTrue(artwork!!.width <= 128 && artwork.height <= 128)
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }

    @Test fun tempoAndPitchChangeActualDecodedAudio() {
        val file = java.io.File.createTempFile("lyrune-tempo", ".wav")
        try {
            val process = ProcessBuilder(FfmpegAudioDecoder.executable, "-v", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=4", file.absolutePath).start()
            assertTrue(process.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, process.exitValue())
            val configurations = listOf(
                dev.auralis.studio.core.model.AudioConfig(speed = 2f, mode = dev.auralis.studio.core.model.PitchTempoMode.TEMPO_LOCK) to 440.0,
                dev.auralis.studio.core.model.AudioConfig(speed = 2f, mode = dev.auralis.studio.core.model.PitchTempoMode.VINYL) to 880.0,
                dev.auralis.studio.core.model.AudioConfig(pitchSemitones = 12f, mode = dev.auralis.studio.core.model.PitchTempoMode.INDEPENDENT) to 880.0,
            )
            configurations.forEach { (config, expectedFrequency) ->
                FfmpegAudioDecoder(file.absolutePath, 4000, audioConfig = config).use { decoder ->
                    val pcm = ByteArray(8192)
                    var frames = 0L
                    var crossings = 0L
                    var previous = 0
                    while(true) {
                        val read = decoder.readPcm(pcm, 0, pcm.size)
                        if(read < 0) break
                        for(i in 0 until read step 4) {
                            val sample = ((pcm[i].toInt() and 255) or (pcm[i + 1].toInt() shl 8)).toShort().toInt()
                            if(previous < 0 && sample >= 0) crossings++
                            previous = sample
                            frames++
                        }
                    }
                    val seconds = frames / 48000.0
                    assertEquals(4.0 / config.speed, seconds, .16)
                    assertEquals(expectedFrequency, crossings / seconds, 25.0)
                }
            }
        } finally { file.delete() }
    }
    @Test fun decodesAndSeeksYouTubeAndOggFormatsWithBundledRuntime() {
        val dir = Files.createTempDirectory("lyrune-codecs").toFile()
        try {
            for ((extension, codec) in listOf("m4a" to "aac", "webm" to "libopus", "ogg" to "vorbis")) {
                val file = java.io.File(dir, "tone.$extension")
                val command = ProcessBuilder(FfmpegAudioDecoder.executable, "-v", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-c:a", codec, "-strict", "-2", "-ac", "2", file.absolutePath)
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start()
                assertTrue(command.waitFor(15, TimeUnit.SECONDS)); assertEquals(0, command.exitValue())
                FfmpegAudioDecoder(file.absolutePath, 2000).use { decoder ->
                    val pcm = ByteArray(8192)
                    assertTrue(decoder.readPcm(pcm, 0, pcm.size) > 0)
                    assertTrue(pcm.any { it != 0.toByte() })
                    assertTrue(decoder.seekTo(1000))
                    assertTrue(decoder.readPcm(pcm, 0, pcm.size) > 0)
                }
            }
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
}
