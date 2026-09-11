package dev.auralis.desktop.online

import dev.auralis.desktop.library.DesktopTrack
import dev.auralis.desktop.playback.*
import dev.auralis.desktop.playback.decoder.FfmpegAudioDecoder
import dev.auralis.studio.online.*
import dev.auralis.studio.lyrics.*
import dev.auralis.desktop.resolver.DesktopMediaResolver
import dev.auralis.studio.resolver.api.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue

class YouTubeJourneyTest {
    @Test fun realSearchResolveDecodePlaybackAndLyrics() = runBlocking {
        assumeTrue("Opt-in live network/audio check", System.getenv("LYRUNE_LIVE_TEST") == "1")
        val youtube = YouTubeCatalogProvider()
        val resolver = DesktopMediaResolver()
        val engine = DesktopPlaybackEngine()
        val lrclib = LrclibClient()
        try {
            val results = youtube.search("i will survive") as? ProviderResult.Success ?: error("YouTube search failed")
            assertTrue("Real YouTube results required", results.data.isNotEmpty())
            results.data.take(5).forEach { println("RESULT ${it.title} | ${it.artist} | ${it.durationMs} | ${it.artworkUrl}") }
            val song = results.data.firstOrNull { it.title.contains("survive", true) && it.durationMs in 175_000..230_000 } ?: results.data.first()
            assertTrue(song.artworkUrl.isNotBlank()); assertTrue(song.durationMs > 0)
            val resolved = resolver.resolve(ResolverRequest(sourceUrl = song.sourceUrl, intent = ResolveIntent.PLAYBACK,
                extraOptions = mapOf("decoderPreset" to DesktopAudioCapabilities.PROFILE)))
            if(resolved is ResolverResult.Failure) error("Resolution failed: ${resolved.error.technicalCode}: ${resolved.error.safeUserMessage}")
            resolved as ResolverResult.Success
            val lease = resolved.lease ?: error("No compatible stream")
            val format = resolved.media.rawFormats.first { it.formatId == lease.formatId }
            assertTrue(DesktopAudioCapabilities.supports(format))
            println("SELECTED ${song.title} | ${format.extension}/${format.audioCodec} | ${resolved.media.durationMs}")
            val duration = resolved.media.durationMs ?: song.durationMs
            FfmpegAudioDecoder(lease.url, duration, lease.headers).use { decoder ->
                val pcm = ByteArray(8192)
                var audible = false
                repeat(30) { assertTrue(decoder.readPcm(pcm, 0, pcm.size) > 0); audible = audible || pcm.any { it != 0.toByte() } }
                assertTrue("Decoded non-silent PCM", audible)
                assertTrue(decoder.seekTo(60_000))
                assertTrue(decoder.readPcm(pcm, 0, pcm.size) > 0)
            }
            val token = engine.prepare(DesktopTrack(0, song.sourceUrl, song.title, song.artist, durationMs = duration, artworkKey = song.artworkUrl))
            engine.setVolume(.15f)
            engine.playPrepared(token, lease.url, duration, lease.headers)
            withTimeout(30_000) { while(!engine.state.value.isPlaying && engine.state.value.errorMessage == null) delay(100) }
            assertNull(engine.state.value.errorMessage)
            delay(1800)
            assertTrue(engine.state.value.currentPositionMs > 0)
            assertEquals(duration, engine.state.value.durationMs)
            engine.pause(); assertFalse(engine.state.value.isPlaying)
            engine.resume(); assertTrue(engine.state.value.isPlaying)
            engine.stop(); assertFalse(engine.state.value.isPlaying)
            val lyric = lrclib.resolve(song.title, song.artist, "", duration)
            assertNotNull("Automatic real lyrics match", lyric)
            val parsed = LyricsResolutionPipeline.parse(LyricsRecord(plain = lyric!!.plainLyrics.orEmpty(), lrc = lyric.syncedLyrics.orEmpty(), lyricsfile = lyric.lyricsfile.orEmpty()))
            assertTrue("Synchronized lyrics available", parsed.synced)
            assertTrue(parsed.lines.size > 10)
            println("PASS: decoded PCM, seek, audio-device playback, pause/resume, ${parsed.lines.size} synchronized lyric lines")
        } finally { engine.release(); resolver.shutdownWorker(); youtube.close(); lrclib.close() }
    }
}
