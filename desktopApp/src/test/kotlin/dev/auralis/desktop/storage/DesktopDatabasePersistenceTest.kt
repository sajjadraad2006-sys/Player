package dev.auralis.desktop.storage

import dev.auralis.desktop.library.DesktopTrack
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDatabasePersistenceTest {

    private lateinit var tempDir: File
    private lateinit var dbFile: File

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lyrune_db_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        dbFile = File(tempDir, "library_test.json")
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testDatabaseWriteAndReopenPersistence() = runBlocking {
        val db1 = DesktopDatabase(dbFile = dbFile)

        val sampleTracks = listOf(
            DesktopTrack(
                id = 1L,
                path = "C:\\Music\\Track1.mp3",
                title = "Celestial Echoes",
                artist = "Auralis",
                album = "Cosmos",
                durationMs = 215000L,
                sizeBytes = 5000000L,
                lastModified = 1700000000L,
                folderPath = "C:\\Music",
            ),
            DesktopTrack(
                id = 2L,
                path = "C:\\Music\\Track2.wav",
                title = "Solar Flare",
                artist = "Studio",
                album = "Cosmos",
                durationMs = 180000L,
                sizeBytes = 15000000L,
                lastModified = 1700001000L,
                folderPath = "C:\\Music",
            ),
        )

        db1.setFolders(listOf("C:\\Music"))
        db1.updateTracks(sampleTracks, debounceMs = 0L)
        db1.toggleFavorite("C:\\Music\\Track1.mp3")
        db1.updatePlaybackPosition("C:\\Music\\Track1.mp3", 45000L)
        db1.savePlaylist(
            DesktopPlaylist(id = 10L, name = "Favorites Mix", trackPaths = listOf("C:\\Music\\Track1.mp3"))
        )
        db1.flushNow()

        assertTrue(dbFile.exists(), "Database file must exist on disk after flush")
        assertTrue(dbFile.length() > 0L, "Database file must not be empty")

        // Reopen database from disk in a fresh instance
        val db2 = DesktopDatabase(dbFile = dbFile)
        val loadedTracks = db2.getTracks()
        val loadedFolders = db2.getFolders()
        val loadedFavorites = db2.getFavorites()
        val loadedPlaylists = db2.getPlaylists()
        val (lastPath, lastPos) = db2.getLastPlaybackState()

        assertEquals(2, loadedTracks.size)
        assertEquals("Celestial Echoes", loadedTracks[0].title)
        assertEquals("Solar Flare", loadedTracks[1].title)
        assertEquals(listOf("C:\\Music"), loadedFolders)
        assertTrue(loadedFavorites.contains("C:\\Music\\Track1.mp3"))
        assertFalse(loadedFavorites.contains("C:\\Music\\Track2.wav"))
        assertEquals(1, loadedPlaylists.size)
        assertEquals("Favorites Mix", loadedPlaylists[0].name)
        assertEquals("C:\\Music\\Track1.mp3", lastPath)
        assertEquals(45000L, lastPos)
    }

    @Test
    fun continuousPlaybackCreatesCheckpointsBeforeStopping() = runBlocking {
        val session = File(tempDir, "checkpoints.json")
        val db = DesktopDatabase(dbFile = dbFile, sessionFile = session)
        repeat(20) { index ->
            db.updatePlaybackPosition("song.m4a", index * 1000L, debounceMs = 60)
            kotlinx.coroutines.delay(20)
        }
        assertTrue(session.exists(), "Continuous position updates must not postpone every checkpoint")
        val saved = DesktopDatabase(dbFile = dbFile, sessionFile = session).getLastPlaybackState()
        assertEquals("song.m4a", saved.first)
        assertTrue(saved.second >= 10_000)
        db.flushNow()
    }

    @Test
    fun seekingToStartDoesNotRestoreAnOldLegacyPosition() = runBlocking {
        dbFile.writeText("""{"lastPlayedTrackPath":"song.wav","lastPlaybackPositionMs":42000}""")
        val session = File(tempDir, "zero.json")
        val db = DesktopDatabase(dbFile = dbFile, sessionFile = session)
        assertEquals(42000L, db.getLastPlaybackState().second)
        db.updatePlaybackPosition("song.wav", 0, debounceMs = 0)
        val reopened = DesktopDatabase(dbFile = dbFile, sessionFile = session)
        assertEquals(0L, reopened.getLastPlaybackState().second)
    }

    @Test
    fun testAtomicWriteCrashSafety() {
        val target = File(tempDir, "atomic_test.txt")
        val content = "Sample Atomic Content for Lyrune"

        DesktopPaths.writeAtomically(target, content)
        assertTrue(target.exists())
        assertEquals(content, target.readText())

        // Ensure temp file is cleaned up
        val tmpFile = File(tempDir, "atomic_test.txt.tmp")
        assertFalse(tmpFile.exists(), "Temporary file must be deleted after atomic move")
    }
}
