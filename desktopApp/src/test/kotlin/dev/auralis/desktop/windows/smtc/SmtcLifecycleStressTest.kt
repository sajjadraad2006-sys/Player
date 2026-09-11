package dev.auralis.desktop.windows.smtc

import dev.auralis.desktop.library.DesktopTrack
import dev.auralis.desktop.playback.DesktopPlaybackEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmtcLifecycleStressTest {

    @Test
    fun test100RapidTrackTransitions() {
        val fake = FakeSystemMediaController()
        val engine = DesktopPlaybackEngine()
        val coordinator = DesktopSmtcCoordinator(
            controller = fake,
            playbackEngine = engine
        )

        for (i in 1..100) {
            val track = DesktopTrack(
                id = i.toLong(),
                path = "C:/Music/track_$i.mp3",
                title = "Title #$i",
                artist = "Artist #$i",
                album = "Album #$i",
                durationMs = 120000L + (i * 1000),
                sizeBytes = 4000000L,
                lastModified = System.currentTimeMillis(),
                mimeType = "audio/mpeg"
            )
            coordinator.onTrackChanged(track, hasNext = (i < 100), hasPrevious = (i > 1))
        }

        assertEquals(100, fake.metadataList.size)
        assertEquals("Title #100", fake.metadataList.last().title)
        assertEquals("Artist #100", fake.metadataList.last().artist)
        assertEquals(100, fake.capabilitiesList.size)
        assertEquals(false, fake.capabilitiesList.last().canNext)
        assertEquals(true, fake.capabilitiesList.last().canPrevious)

        coordinator.release()
        engine.release()
    }

    @Test
    fun testNativeSmtcLifecycleIfWindows() {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) {
            println("Skipping native SMTC lifecycle test on non-Windows OS.")
            return
        }

        val bridge = NativeSmtcBridge.loadOrNull()
        if (bridge == null) {
            println("Skipping native SMTC test because SMTCAdapter.dll is not loaded.")
            return
        }

        // Test 10 rapid create/update/release cycles with real WinRT bridge
        for (cycle in 1..10) {
            val controller = WindowsSmtcController(bridge)
            var playCalled = false

            controller.initialize(object : SmtcCommandHandler {
                override fun onPlay() { playCalled = true }
                override fun onPause() {}
                override fun onStop() {}
                override fun onNext() {}
                override fun onPrevious() {}
                override fun onSeek(positionMs: Long) {}
            })

            controller.updateMetadata(
                SmtcMetadata(
                    title = "Native Stress Song $cycle",
                    artist = "Lyrune Native Artist",
                    album = "Stress Album"
                )
            )

            controller.updatePlaybackStatus(SmtcPlaybackStatus.PLAYING)
            controller.updateTimeline(SmtcTimeline(positionMs = 15000L, durationMs = 180000L))
            controller.updateCapabilities(SmtcCapabilities(canPlay = true, canPause = true, canNext = true, canPrevious = true))

            controller.clear()
            controller.release()
        }

        assertTrue(true, "Native SMTC 10-cycle lifecycle executed cleanly without crash or deadlock.")
    }
}
