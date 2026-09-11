package dev.auralis.desktop.windows.smtc

import dev.auralis.desktop.artwork.DesktopArtworkManager
import dev.auralis.desktop.library.DesktopTrack
import dev.auralis.desktop.logging.DesktopLogger
import dev.auralis.desktop.playback.DesktopPlaybackEngine
import dev.auralis.studio.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Factory and coordinator bridging [DesktopPlaybackEngine], queue state,
 * and the native Windows System Media Transport Controls ([SystemMediaController]).
 */
class DesktopSmtcCoordinator(
    val controller: SystemMediaController = createDefaultController(),
    private val playbackEngine: DesktopPlaybackEngine,
    private val artworkManager: DesktopArtworkManager = DesktopArtworkManager,
    private val onPlayAction: () -> Unit = {
        if (!playbackEngine.state.value.isPlaying) {
            playbackEngine.resume()
        }
    },
    private val onPauseAction: () -> Unit = {
        if (playbackEngine.state.value.isPlaying) {
            playbackEngine.pause()
        }
    },
    private val onStopAction: () -> Unit = {
        playbackEngine.stop()
    },
    private val onNextAction: () -> Unit = {},
    private val onPreviousAction: () -> Unit = {},
    private val onSeekAction: (Long) -> Unit = { pos ->
        playbackEngine.seekTo(pos)
    }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var currentTrack: DesktopTrack? = null
    private var hasNextItem: Boolean = false
    private var hasPrevItem: Boolean = false

    init {
        controller.initialize(object : SmtcCommandHandler {
            override fun onPlay() {
                DesktopLogger.info(TAG, "SMTC command received: PLAY")
                onPlayAction()
            }

            override fun onPause() {
                DesktopLogger.info(TAG, "SMTC command received: PAUSE")
                onPauseAction()
            }

            override fun onStop() {
                DesktopLogger.info(TAG, "SMTC command received: STOP")
                onStopAction()
            }

            override fun onNext() {
                DesktopLogger.info(TAG, "SMTC command received: NEXT")
                onNextAction()
            }

            override fun onPrevious() {
                DesktopLogger.info(TAG, "SMTC command received: PREVIOUS")
                onPreviousAction()
            }

            override fun onSeek(positionMs: Long) {
                DesktopLogger.info(TAG, "SMTC command received: SEEK to $positionMs ms")
                onSeekAction(positionMs)
            }
        })

        // Listen to playback state transitions (playing/paused/buffering/error)
        scope.launch {
            playbackEngine.state.collect { state ->
                syncPlaybackState(state)
            }
        }
    }

    fun onTrackChanged(track: DesktopTrack?, hasNext: Boolean, hasPrevious: Boolean) {
        currentTrack = track
        hasNextItem = hasNext
        hasPrevItem = hasPrevious

        if (track == null) {
            controller.clear()
            return
        }

        val artworkPath = resolveArtworkPath(track)

        val metadata = SmtcMetadata(
            title = track.title.ifBlank { File(track.path).nameWithoutExtension },
            artist = track.artist.ifBlank { "Unknown Artist" },
            album = track.album.takeIf { it.isNotBlank() },
            artworkFilePath = artworkPath
        )
        controller.updateMetadata(metadata)

        val capabilities = SmtcCapabilities(
            canPlay = true,
            canPause = true,
            canStop = true,
            canNext = hasNext,
            canPrevious = hasPrevious,
            canSeek = track.durationMs > 0
        )
        controller.updateCapabilities(capabilities)

        controller.updateTimeline(
            SmtcTimeline(
                positionMs = 0L,
                durationMs = track.durationMs
            )
        )
    }

    fun onQueueCapabilitiesChanged(hasNext: Boolean, hasPrevious: Boolean) {
        hasNextItem = hasNext
        hasPrevItem = hasPrevious
        val capabilities = SmtcCapabilities(
            canPlay = true,
            canPause = true,
            canStop = true,
            canNext = hasNext,
            canPrevious = hasPrevious,
            canSeek = (currentTrack?.durationMs ?: 0L) > 0
        )
        controller.updateCapabilities(capabilities)
    }

    private fun syncPlaybackState(state: PlaybackState) {
        val status = when {
            state.errorMessage != null -> SmtcPlaybackStatus.STOPPED
            state.isBuffering -> SmtcPlaybackStatus.CHANGING
            state.isPlaying -> SmtcPlaybackStatus.PLAYING
            state.currentUri != null -> SmtcPlaybackStatus.PAUSED
            else -> SmtcPlaybackStatus.STOPPED
        }
        controller.updatePlaybackStatus(status)

        val duration = if (state.durationMs > 0) state.durationMs else (currentTrack?.durationMs ?: 0L)
        if (duration > 0) {
            controller.updateTimeline(
                SmtcTimeline(
                    positionMs = state.currentPositionMs,
                    durationMs = duration
                )
            )
        }
    }

    private fun resolveArtworkPath(track: DesktopTrack): String? {
        val cached = artworkManager.getCachedArtworkFile(track.path)
        return cached?.takeIf { it.isFile && it.length() > 0 }?.absolutePath
    }

    fun release() {
        scope.cancel()
        controller.release()
    }

    companion object {
        private const val TAG = "DesktopSmtcCoordinator"

        fun createDefaultController(): SystemMediaController {
            return try {
                val bridge = NativeSmtcBridge.loadOrNull()
                if (bridge != null) {
                    WindowsSmtcController(bridge)
                } else {
                    NoOpSystemMediaController()
                }
            } catch (t: Throwable) {
                NoOpSystemMediaController()
            }
        }
    }
}
