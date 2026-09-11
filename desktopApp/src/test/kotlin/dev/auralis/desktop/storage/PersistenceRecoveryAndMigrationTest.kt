package dev.auralis.desktop.storage

import dev.auralis.desktop.library.DesktopTrack
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PersistenceRecoveryAndMigrationTest {

    private lateinit var tempDir: File
    private lateinit var libraryFile: File
    private lateinit var sessionFile: File
    private lateinit var settingsFile: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lyrune_recovery_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        libraryFile = File(tempDir, "library.json")
        sessionFile = File(tempDir, "session.json")
        settingsFile = File(tempDir, "settings.json")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testCorruptLibraryFilePreservedAsBackup() {
        // Write invalid/truncated JSON to library.json
        libraryFile.writeText("""{"schemaVersion":1,"tracks":[{"id":1,"title":"Truncated...""")

        // Initializing DesktopDatabase must NOT throw
        val store = DesktopDatabase(dbFile = libraryFile, sessionFile = sessionFile)

        runBlocking {
            val tracks = store.getTracks()
            assertEquals("Must fall back to empty tracks list upon corrupt file", 0, tracks.size)
        }

        // Verify that a .bak copy of the corrupted file was preserved
        val bakFiles = tempDir.listFiles()?.filter { it.name.contains(".corrupt.") } ?: emptyList()
        assertTrue("Corrupted library file must be preserved as .bak for recovery", bakFiles.isNotEmpty())
    }

    @Test
    fun testCorruptSettingsFilePreservedAsBackup() {
        // Write invalid JSON to settings.json
        settingsFile.writeText("!!! INVALID GARBAGE NON-JSON BYTES !!!")

        val repo = DesktopSettingsRepository(settingsFile = settingsFile)
        val currentSettings = repo.settings.value

        assertNotNull("Must safely recover default settings", currentSettings)
        assertEquals("MINT", currentSettings.themeAccent)
        assertEquals("EN", currentSettings.language)

        val bakFiles = tempDir.listFiles()?.filter { it.name.contains(".corrupt.") } ?: emptyList()
        assertTrue("Corrupted settings file must be preserved as .bak", bakFiles.isNotEmpty())
    }

    @Test
    fun testSchemaEvolutionWithUnknownFields() {
        // Simulate future schema version containing new fields that current code does not know about
        val futureJson = """
            {
                "schemaVersion": 2,
                "version": 2,
                "unknownFutureField": "quantum_audio",
                "tracks": [
                    {
                        "id": 101,
                        "path": "C:\\Music\\Song.mp3",
                        "title": "Quantum Leap",
                        "artist": "Nova",
                        "newCodecFeature": true
                    }
                ],
                "futureCloudSyncState": {"enabled": true}
            }
        """.trimIndent()
        libraryFile.writeText(futureJson)

        val store = DesktopDatabase(dbFile = libraryFile, sessionFile = sessionFile)

        runBlocking {
            val tracks = store.getTracks()
            assertEquals(1, tracks.size)
            assertEquals("Quantum Leap", tracks[0].title)
            assertEquals("Nova", tracks[0].artist)
        }
    }

    @Test
    fun testMissingOptionalFieldsReceiveDefaults() {
        // Simulate older minimal JSON missing modern fields
        val minimalJson = """
            {
                "tracks": [
                    {
                        "id": 1,
                        "path": "C:\\Music\\Old.mp3",
                        "title": "Vintage Tone",
                        "artist": "Classic"
                    }
                ]
            }
        """.trimIndent()
        libraryFile.writeText(minimalJson)

        val store = DesktopDatabase(dbFile = libraryFile, sessionFile = sessionFile)

        runBlocking {
            val tracks = store.getTracks()
            assertEquals(1, tracks.size)
            assertEquals(0L, tracks[0].durationMs)
            assertEquals(false, tracks[0].isFavorite)
            assertEquals("", tracks[0].folderPath)
        }
    }

    @Test
    fun testWriteAmplificationDecoupling() = runBlocking {
        val store = DesktopDatabase(dbFile = libraryFile, sessionFile = sessionFile)

        // Seed 10 sample tracks
        val tracks = (1..10).map { i ->
            DesktopTrack(
                id = i.toLong(),
                path = "C:\\Music\\Track_$i.mp3",
                title = "Track $i",
                artist = "Artist",
            )
        }
        store.updateTracks(tracks, debounceMs = 0L)
        store.flushNow()

        val initialLibraryWrites = store.libraryWriteCount.get()
        val initialSessionWrites = store.sessionWriteCount.get()
        assertTrue("Library should be written once on updateTracks", initialLibraryWrites >= 1)

        // Simulate 100 rapid playback position updates (mimicking 100 seeks or seconds of playback)
        for (pos in 1..100) {
            store.updatePlaybackPosition("C:\\Music\\Track_1.mp3", pos * 1000L, debounceMs = 0L)
        }
        store.flushNow()

        val postPlaybackLibraryWrites = store.libraryWriteCount.get()
        val postPlaybackSessionWrites = store.sessionWriteCount.get()

        // CRITICAL ASSERTION: library.json write count must NOT increase during playback position ticks!
        assertEquals(
            "library.json must NOT be rewritten on playback position updates (zero SSD amplification)",
            initialLibraryWrites,
            postPlaybackLibraryWrites,
        )
        assertTrue(
            "session.json must receive the playback updates",
            postPlaybackSessionWrites > initialSessionWrites,
        )
    }
}
