package dev.auralis.desktop.online

import dev.auralis.desktop.library.DesktopTrack
import dev.auralis.desktop.playback.DesktopAudioCapabilities
import dev.auralis.desktop.playback.DesktopPlaybackEngine
import dev.auralis.studio.online.OnlineTrack
import dev.auralis.studio.resolver.api.*
import kotlinx.coroutines.*

class YouTubePlaybackCoordinator(private val resolver: MediaResolver, private val engine: DesktopPlaybackEngine, private val scope: CoroutineScope) {
    private var job: Job? = null
    fun play(track: OnlineTrack, startPositionMs: Long = 0) {
        job?.cancel()
        val selected = DesktopTrack(id = track.id.hashCode().toLong(), path = track.sourceUrl,
            title = track.title, artist = track.artist, album = "YouTube", durationMs = track.durationMs,
            artworkKey = track.artworkUrl.takeIf(String::isNotBlank))
        val token = engine.prepare(selected)
        job = scope.launch {
            try {
                val result = resolver.resolve(ResolverRequest(sourceUrl = track.sourceUrl, intent = ResolveIntent.PLAYBACK,
                    extraOptions = mapOf("decoderPreset" to DesktopAudioCapabilities.PROFILE)))
                ensureActive()
                when(result) {
                    is ResolverResult.Success -> {
                        val lease = result.lease
                        val format = result.media.rawFormats.firstOrNull { it.formatId == lease?.formatId }
                        if (lease == null || lease.isExpired || format == null || !DesktopAudioCapabilities.supports(format)) {
                            engine.failPrepared(token, "This version is unavailable. Try another YouTube result.")
                        } else {
                            engine.playPrepared(token, lease.url, result.media.durationMs?.takeIf { it > 0 } ?: track.durationMs, lease.headers, startMs = startPositionMs)
                        }
                    }
                    is ResolverResult.Failure -> engine.failPrepared(token, "Couldn't load this song from YouTube. Try again or choose another result.")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { engine.failPrepared(token, "Couldn't connect to YouTube. Check your connection and try again.") }
        }
    }
    fun close() { job?.cancel() }
}
