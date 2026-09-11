package dev.auralis.desktop.windows.smtc

import com.sun.jna.WString
import dev.auralis.desktop.logging.DesktopLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production implementation of [SystemMediaController] interfacing with Windows
 * System Media Transport Controls (SMTC) via [NativeSmtcBridge].
 *
 * All WinRT interactions are confined to a dedicated single-threaded executor
 * running in a Multi-Threaded Apartment (MTA) to maintain complete COM apartment safety
 * and prevent UI/playback thread contention.
 */
class WindowsSmtcController(
    private val bridge: NativeSmtcBridge
) : SystemMediaController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Dedicated single-thread worker for all WinRT calls (initialized in MTA)
    private val smtcExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Lyrune-SMTC-Worker").apply {
            isDaemon = true
        }
    }

    private val isInitialized = AtomicBoolean(false)
    private val isReleased = AtomicBoolean(false)

    // Strong references to JNA callbacks to prevent garbage collection
    private var onPlayCallback: NativeSmtcBridge.ButtonCallback? = null
    private var onPauseCallback: NativeSmtcBridge.ButtonCallback? = null
    private var onStopCallback: NativeSmtcBridge.ButtonCallback? = null
    private var onNextCallback: NativeSmtcBridge.ButtonCallback? = null
    private var onPreviousCallback: NativeSmtcBridge.ButtonCallback? = null
    private var onSeekCallback: NativeSmtcBridge.SeekCallback? = null

    // Timeline throttling cache
    private var lastTimelinePublishWallMs = 0L
    private var lastPublishedPositionMs = -1L
    private var lastPublishedDurationMs = -1L

    override fun initialize(commandHandler: SmtcCommandHandler) {
        if (isReleased.get() || isInitialized.getAndSet(true)) return

        smtcExecutor.execute {
            try {
                bridge.init()
                bridge.setEnabled(true)
                bridge.setMediaType(0) // 0 = Music

                // Register native callbacks and retain strong references
                onPlayCallback = NativeSmtcBridge.ButtonCallback {
                    scope.launch { runCatching { commandHandler.onPlay() } }
                }
                onPauseCallback = NativeSmtcBridge.ButtonCallback {
                    scope.launch { runCatching { commandHandler.onPause() } }
                }
                onStopCallback = NativeSmtcBridge.ButtonCallback {
                    scope.launch { runCatching { commandHandler.onStop() } }
                }
                onNextCallback = NativeSmtcBridge.ButtonCallback {
                    scope.launch { runCatching { commandHandler.onNext() } }
                }
                onPreviousCallback = NativeSmtcBridge.ButtonCallback {
                    scope.launch { runCatching { commandHandler.onPrevious() } }
                }
                onSeekCallback = NativeSmtcBridge.SeekCallback { pos ->
                    scope.launch { runCatching { commandHandler.onSeek(pos) } }
                }

                bridge.setOnPlay(onPlayCallback)
                bridge.setOnPause(onPauseCallback)
                bridge.setOnStop(onStopCallback)
                bridge.setOnNext(onNextCallback)
                bridge.setOnPrevious(onPreviousCallback)
                bridge.setOnSeek(onSeekCallback)

                // Initial baseline: disabled until media is loaded
                bridge.setPlayEnabled(false)
                bridge.setPauseEnabled(false)
                bridge.setStopEnabled(false)
                bridge.setNextEnabled(false)
                bridge.setPreviousEnabled(false)
                bridge.setPlaybackState(0) // Closed

                DesktopLogger.info(TAG, "Windows SMTC bridge initialized successfully in MTA worker.")
            } catch (t: Throwable) {
                DesktopLogger.error(TAG, "Failed to initialize Windows SMTC bridge: ${t.message}")
            }
        }
    }

    override fun updateMetadata(metadata: SmtcMetadata) {
        if (!isInitialized.get() || isReleased.get()) return

        smtcExecutor.execute {
            try {
                bridge.setMusicTitle(WString(metadata.title))
                bridge.setMusicArtist(WString(metadata.artist))
                if (metadata.album != null) {
                    bridge.setMusicAlbumTitle(WString(metadata.album))
                }
                if (metadata.albumArtist != null) {
                    bridge.setMusicAlbumArtist(WString(metadata.albumArtist))
                }
                if (metadata.trackNumber != null && metadata.trackNumber > 0) {
                    bridge.setMusicTrack(metadata.trackNumber)
                }

                if (!metadata.artworkFilePath.isNullOrBlank()) {
                    val artworkFile = File(metadata.artworkFilePath)
                    if (artworkFile.isFile && artworkFile.length() > 0) {
                        bridge.setThumbnail(WString(artworkFile.absolutePath))
                    }
                }

                bridge.update()
            } catch (t: Throwable) {
                DesktopLogger.warn(TAG, "Failed to publish metadata to SMTC: ${t.message}")
            }
        }
    }

    override fun updatePlaybackStatus(status: SmtcPlaybackStatus) {
        if (!isInitialized.get() || isReleased.get()) return

        smtcExecutor.execute {
            try {
                val stateCode = when (status) {
                    SmtcPlaybackStatus.CLOSED -> 0
                    SmtcPlaybackStatus.PAUSED -> 1
                    SmtcPlaybackStatus.STOPPED -> 2
                    SmtcPlaybackStatus.PLAYING -> 3
                    SmtcPlaybackStatus.CHANGING -> 4
                }
                bridge.setPlaybackState(stateCode)
            } catch (t: Throwable) {
                DesktopLogger.warn(TAG, "Failed to update SMTC playback status: ${t.message}")
            }
        }
    }

    override fun updateTimeline(timeline: SmtcTimeline) {
        if (!isInitialized.get() || isReleased.get()) return

        val now = System.currentTimeMillis()
        val durationChanged = timeline.durationMs != lastPublishedDurationMs
        val positionJumped = kotlin.math.abs(timeline.positionMs - lastPublishedPositionMs) > 1500L
        val intervalElapsed = (now - lastTimelinePublishWallMs) >= 1000L

        // Throttle updates: publish immediately on seek / duration discovery, or every ~1 second
        if (!durationChanged && !positionJumped && !intervalElapsed) {
            return
        }

        lastTimelinePublishWallMs = now
        lastPublishedPositionMs = timeline.positionMs
        lastPublishedDurationMs = timeline.durationMs

        smtcExecutor.execute {
            try {
                if (durationChanged) {
                    bridge.setTimelineProperties(
                        0L,
                        timeline.durationMs,
                        timeline.minSeekMs,
                        timeline.maxSeekMs
                    )
                }
                bridge.setPosition(timeline.positionMs)
            } catch (t: Throwable) {
                DesktopLogger.warn(TAG, "Failed to update SMTC timeline: ${t.message}")
            }
        }
    }

    override fun updateCapabilities(capabilities: SmtcCapabilities) {
        if (!isInitialized.get() || isReleased.get()) return

        smtcExecutor.execute {
            try {
                bridge.setPlayEnabled(capabilities.canPlay)
                bridge.setPauseEnabled(capabilities.canPause)
                bridge.setStopEnabled(capabilities.canStop)
                bridge.setNextEnabled(capabilities.canNext)
                bridge.setPreviousEnabled(capabilities.canPrevious)
            } catch (t: Throwable) {
                DesktopLogger.warn(TAG, "Failed to update SMTC capabilities: ${t.message}")
            }
        }
    }

    override fun clear() {
        if (!isInitialized.get() || isReleased.get()) return

        smtcExecutor.execute {
            try {
                bridge.reset()
                bridge.setPlaybackState(0) // Closed
                bridge.setPlayEnabled(false)
                bridge.setPauseEnabled(false)
                bridge.setStopEnabled(false)
                bridge.setNextEnabled(false)
                bridge.setPreviousEnabled(false)
                lastPublishedPositionMs = -1L
                lastPublishedDurationMs = -1L
            } catch (t: Throwable) {
                DesktopLogger.warn(TAG, "Failed to clear SMTC display: ${t.message}")
            }
        }
    }

    override fun release() {
        if (isReleased.getAndSet(true)) return

        smtcExecutor.execute {
            try {
                bridge.revokeCallbacks()
                bridge.reset()
                bridge.setPlaybackState(0)
                bridge.setEnabled(false)
                DesktopLogger.info(TAG, "Windows SMTC bridge revoked and released cleanly.")
            } catch (t: Throwable) {
                DesktopLogger.warn(TAG, "Error releasing SMTC bridge: ${t.message}")
            } finally {
                onPlayCallback = null
                onPauseCallback = null
                onStopCallback = null
                onNextCallback = null
                onPreviousCallback = null
                onSeekCallback = null
            }
        }

        scope.cancel()
        smtcExecutor.shutdown()
    }

    companion object {
        private const val TAG = "WindowsSmtcController"
    }
}
