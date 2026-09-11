package dev.auralis.desktop.storage

import dev.auralis.studio.core.model.AudioConfig
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSettingsPersistenceTest {

    private lateinit var tempDir: File
    private lateinit var settingsFile: File

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lyrune_settings_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        settingsFile = File(tempDir, "settings_test.json")
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testSettingsSerializationAndRestoration() = runBlocking {
        val repo1 = DesktopSettingsRepository(settingsFile = settingsFile)

        repo1.update(debounceMs = 0L) { s ->
            s.copy(
                themeAccent = "CYAN",
                isAmoled = true,
                language = "AR",
                windowWidth = 1400,
                windowHeight = 900,
                isMaximized = true,
                lastTab = "AUDIO_LAB",
                volume = 0.85f,
                dspSettings = DesktopDspSettings.from(
                    AudioConfig(
                        dspEnabled = true,
                        bassDb = 6f,
                        trebleDb = 4f,
                        eightDEnabled = true,
                        eightDStrength = 0.75f,
                    )
                ),
            )
        }
        repo1.flushNow()

        assertTrue(settingsFile.exists())
        assertTrue(settingsFile.length() > 0L)

        // Reopen in a fresh repository instance
        val repo2 = DesktopSettingsRepository(settingsFile = settingsFile)
        val loaded = repo2.settings.value

        assertEquals("CYAN", loaded.themeAccent)
        assertTrue(loaded.isAmoled)
        assertEquals("AR", loaded.language)
        assertEquals(1400, loaded.windowWidth)
        assertEquals(900, loaded.windowHeight)
        assertTrue(loaded.isMaximized)
        assertEquals("AUDIO_LAB", loaded.lastTab)
        assertEquals(0.85f, loaded.volume)
        assertTrue(loaded.dspConfig.dspEnabled)
        assertEquals(6f, loaded.dspConfig.bassDb)
        assertTrue(loaded.dspConfig.eightDEnabled)
        assertEquals(0.75f, loaded.dspConfig.eightDStrength)
    }

    @Test
    fun testWindowGeometryClamping() = runBlocking {
        val repo = DesktopSettingsRepository(settingsFile = settingsFile)

        // Test absurd/off-screen window dimensions
        repo.updateWindowGeometry(width = 200, height = 100, isMaximized = false)
        repo.flushNow()

        val loaded = DesktopSettingsRepository(settingsFile = settingsFile).settings.value
        assertTrue(loaded.windowWidth >= 900, "Window width must be clamped to minimum 900dp (was ${loaded.windowWidth})")
        assertTrue(loaded.windowHeight >= 600, "Window height must be clamped to minimum 600dp (was ${loaded.windowHeight})")
    }

    @Test
    fun testCorruptedFileFallback() {
        settingsFile.writeText("{ this is invalid json content }")
        val repo = DesktopSettingsRepository(settingsFile = settingsFile)
        val loaded = repo.settings.value

        // Must fallback to safe defaults without crashing
        assertEquals("MINT", loaded.themeAccent)
        assertEquals("EN", loaded.language)
        assertFalse(loaded.isAmoled)
    }
}
