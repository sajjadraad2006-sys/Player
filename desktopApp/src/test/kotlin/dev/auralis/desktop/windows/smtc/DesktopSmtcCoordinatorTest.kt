package dev.auralis.desktop.windows.smtc

import dev.auralis.desktop.library.DesktopTrack
import dev.auralis.desktop.playback.DesktopPlaybackEngine
import dev.auralis.studio.playback.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeSystemMediaController : SystemMediaController {
    var handler: SmtcCommandHandler? = null
    val metadataList = mutableListOf<SmtcMetadata>()
    val statusList = mutableListOf<SmtcPlaybackStatus>()
    val timelineList = mutableListOf<SmtcTimeline>()
    val capabilitiesList = mutableListOf<SmtcCapabilities>()
    var clearCallCount = 0
    var releaseCallCount = 0

    override fun initialize(commandHandler: SmtcCommandHandler) {
        handler = commandHandler
    }

    override fun updateMetadata(metadata: SmtcMetadata) {
        metadataList.add(metadata)
    }

    override fun updatePlaybackStatus(status: SmtcPlaybackStatus) {
        statusList.add(status)
    }

    override fun updateTimeline(timeline: SmtcTimeline) {
        timelineList.add(timeline)
    }

    override fun updateCapabilities(capabilities: SmtcCapabilities) {
        capabilitiesList.add(capabilities)
    }

    override fun clear() {
        clearCallCount++
    }

    override fun release() {
        releaseCallCount++
    }
}

class DesktopSmtcCoordinatorTest {

    @Test
    fun testMetadataMappingWithFullTags() {
        val fakeController = FakeSystemMediaController()
        val engine = DesktopPlaybackEngine()

        val coordinator = DesktopSmtcCoordinator(
            controller = fakeController,
            playbackEngine = engine
        )

        val track = DesktopTrack(
            id = 1L,
            path = "C:/Music/test_song.mp3",
            title = "Starry Night",
            artist = "Vincent",
            album = "Masterpieces",
            durationMs = 240000L,
            sizeBytes = 5000000L,
            lastModified = System.currentTimeMillis(),
            mimeType = "audio/mpeg"
        )

        coordinator.onTrackChanged(track, hasNext = true, hasPrevious = false)

        assertEquals(1, fakeController.metadataList.size)
        val meta = fakeController.metadataList.first()
        assertEquals("Starry Night", meta.title)
        assertEquals("Vincent", meta.artist)
        assertEquals("Masterpieces", meta.album)

        assertEquals(1, fakeController.capabilitiesList.size)
        val caps = fakeController.capabilitiesList.first()
        assertTrue(caps.canPlay)
        assertTrue(caps.canPause)
        assertTrue(caps.canNext)
        assertFalse(caps.canPrevious)

        assertTrue(fakeController.timelineList.isNotEmpty())
        val timeline = fakeController.timelineList.first()
        assertEquals(0L, timeline.positionMs)
        assertEquals(240000L, timeline.durationMs)

        coordinator.release()
        engine.release()
    }

    @Test
    fun testMetadataFallbackForBlankFields() {
        val fakeController = FakeSystemMediaController()
        val engine = DesktopPlaybackEngine()

        val coordinator = DesktopSmtcCoordinator(
            controller = fakeController,
            playbackEngine = engine
        )

        val track = DesktopTrack(
            id = 2L,
            path = "C:/Music/untitled_audio.mp3",
            title = "",
            artist = "",
            album = "",
            durationMs = 180000L,
            sizeBytes = 3000000L,
            lastModified = System.currentTimeMillis(),
            mimeType = "audio/mpeg"
        )

        coordinator.onTrackChanged(track, hasNext = false, hasPrevious = true)

        assertEquals(1, fakeController.metadataList.size)
        val meta = fakeController.metadataList.first()
        assertEquals("untitled_audio", meta.title)
        assertEquals("Unknown Artist", meta.artist)
        assertEquals(null, meta.album)

        val caps = fakeController.capabilitiesList.first()
        assertFalse(caps.canNext)
        assertTrue(caps.canPrevious)

        coordinator.release()
        engine.release()
    }

    @Test
    fun testCommandRoutingToActions() {
        val fakeController = FakeSystemMediaController()
        val engine = DesktopPlaybackEngine()

        var playInvoked = 0
        var pauseInvoked = 0
        var stopInvoked = 0
        var nextInvoked = 0
        var prevInvoked = 0
        var seekInvokedMs = -1L

        val coordinator = DesktopSmtcCoordinator(
            controller = fakeController,
            playbackEngine = engine,
            onPlayAction = { playInvoked++ },
            onPauseAction = { pauseInvoked++ },
            onStopAction = { stopInvoked++ },
            onNextAction = { nextInvoked++ },
            onPreviousAction = { prevInvoked++ },
            onSeekAction = { pos -> seekInvokedMs = pos }
        )

        val handler = assertNotNull(fakeController.handler)

        handler.onPlay()
        assertEquals(1, playInvoked)

        handler.onPause()
        assertEquals(1, pauseInvoked)

        handler.onStop()
        assertEquals(1, stopInvoked)

        handler.onNext()
        assertEquals(1, nextInvoked)

        handler.onPrevious()
        assertEquals(1, prevInvoked)

        handler.onSeek(45000L)
        assertEquals(45000L, seekInvokedMs)

        coordinator.release()
        engine.release()
    }

    @Test
    fun testClearOnTrackNull() {
        val fakeController = FakeSystemMediaController()
        val engine = DesktopPlaybackEngine()

        val coordinator = DesktopSmtcCoordinator(
            controller = fakeController,
            playbackEngine = engine
        )

        coordinator.onTrackChanged(null, false, false)
        assertEquals(1, fakeController.clearCallCount)

        coordinator.release()
        assertEquals(1, fakeController.releaseCallCount)
        engine.release()
    }
}
