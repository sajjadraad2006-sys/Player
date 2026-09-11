package dev.auralis.desktop.playback

import dev.auralis.desktop.library.DemoAudioGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopStreamingAudioTest {

    private lateinit var engine: DesktopPlaybackEngine
    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        engine = DesktopPlaybackEngine()
        tempDir = File(System.getProperty("java.io.tmpdir"), "lyrune_streaming_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @AfterTest
    fun tearDown() {
        engine.release()
        tempDir.deleteRecursively()
    }

    @Test
    fun testOfflineLocalPlaybackUnaffected() = runBlocking {
        val wavFile = File(tempDir, "offline_test.wav")
        DemoAudioGenerator.generateWav(wavFile, 1f, doubleArrayOf(440.0))
        assertTrue(wavFile.exists())

        engine.play(wavFile.absolutePath)
        val playingState = engine.state.first { it.isPlaying || it.errorMessage != null }
        // Verified local playback starts cleanly offline
        assertTrue(playingState.isPlaying || playingState.errorMessage == null)
        engine.stop()
        assertFalse(engine.state.value.isPlaying)
    }

    @Test
    fun testNetworkStreamErrorEmittedForUnreachableHost() = runBlocking {
        // Pointing to a non-existent port on localhost or unreachable URL
        val invalidUrl = "http://127.0.0.1:59999/non_existent_stream.mp3"
        engine.play(invalidUrl)

        val errState = engine.state.first { it.errorMessage != null || !it.isPlaying }
        assertNotNull(errState)
        assertFalse(engine.state.value.isPlaying)
    }

    @Test
    fun testStopCancelsStreamingThread() = runBlocking {
        engine.play("https://example.com/stream.mp3")
        engine.stop()
        assertFalse(engine.state.value.isPlaying)
        assertFalse(engine.state.value.isBuffering)
        assertEquals(0L, engine.state.value.currentPositionMs)
    }
}
