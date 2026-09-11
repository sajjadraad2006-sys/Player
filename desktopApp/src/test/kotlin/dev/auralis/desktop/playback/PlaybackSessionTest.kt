package dev.auralis.desktop.playback

import dev.auralis.desktop.library.DesktopTrack
import org.junit.Test
import org.junit.Assert.*

class PlaybackSessionTest {
    private fun track(name: String) = DesktopTrack(0, "https://www.youtube.com/watch?v=$name", name, "Artist", durationMs = 195_000)
    @Test fun lateResolutionAndErrorsCannotReplaceNewSelection() {
        val engine = DesktopPlaybackEngine()
        try {
            val old = engine.prepare(track("old"))
            val latest = engine.prepare(track("new"))
            engine.failPrepared(old, "Old failure")
            engine.playPrepared(old, "missing.mp3", 0)
            assertEquals("new", engine.state.value.metadata?.title)
            assertTrue(engine.state.value.isBuffering)
            assertFalse(engine.state.value.isPlaying)
            assertEquals(195_000L, engine.state.value.durationMs)
            engine.failPrepared(latest, "Can't play")
            engine.resume()
            assertFalse(engine.state.value.isPlaying)
            assertFalse(engine.state.value.isBuffering)
            assertNull(engine.state.value.metadata)
            assertNull(engine.state.value.currentUri)
            assertEquals(0L, engine.state.value.currentPositionMs)
            assertEquals(0L, engine.state.value.durationMs)
        } finally { engine.release() }
    }
    @Test fun stopInvalidatesPendingResolution() {
        val engine = DesktopPlaybackEngine()
        val pending = engine.prepare(track("pending"))
        engine.stop()
        engine.playPrepared(pending, "missing.mp3", 0)
        engine.resume()
        assertNull(engine.state.value.currentUri)
        assertFalse(engine.state.value.isPlaying)
    }
}
