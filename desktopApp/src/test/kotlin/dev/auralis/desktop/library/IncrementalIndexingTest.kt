package dev.auralis.desktop.library

import dev.auralis.desktop.storage.DesktopDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncrementalIndexingTest {

    private lateinit var tempDir: File
    private lateinit var musicDir: File
    private lateinit var dbFile: File

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lyrune_incremental_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        musicDir = File(tempDir, "Music").apply { mkdirs() }
        dbFile = File(tempDir, "library.json")
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createDummyAudioFile(name: String, sizeKb: Int): File {
        val f = File(musicDir, name)
        FileOutputStream(f).use { fos ->
            // Minimal dummy WAV-like header + payload
            fos.write("RIFF".toByteArray())
            fos.write(ByteArray(40) { 0 })
            fos.write(ByteArray(sizeKb * 1024) { 0x55.toByte() })
        }
        return f
    }

    @Test
    fun testIncrementalScanAndReconciliation() = runBlocking {
        val file1 = createDummyAudioFile("TrackA.wav", 10)
        val file2 = createDummyAudioFile("TrackB.wav", 12)

        val db = DesktopDatabase(dbFile = dbFile)
        val repo = DesktopMusicRepository(database = db)

        // 1. Initial scan
        repo.scanFolders(listOf(musicDir))
        val initialTracks = repo.tracks.value
        assertEquals(2, initialTracks.size)
        assertTrue(initialTracks.any { it.title == "TrackA" })
        assertTrue(initialTracks.any { it.title == "TrackB" })

        // 2. Add new file
        val file3 = createDummyAudioFile("TrackC.wav", 15)
        repo.scanFolders(listOf(musicDir))
        val afterAdd = repo.tracks.value
        assertEquals(3, afterAdd.size)
        assertTrue(afterAdd.any { it.title == "TrackC" })

        // 3. Modify existing file
        val originalTime = file1.lastModified()
        file1.setLastModified(originalTime + 10000L)
        repo.scanFolders(listOf(musicDir))
        val afterMod = repo.tracks.value
        assertEquals(3, afterMod.size)
        val modTrack = afterMod.find { it.title == "TrackA" }
        assertEquals(file1.lastModified(), modTrack?.lastModified)

        // 4. Delete physical file (file2)
        file2.delete()
        assertFalse(file2.exists())
        repo.scanFolders(listOf(musicDir))
        val afterDelete = repo.tracks.value
        assertEquals(2, afterDelete.size)
        assertFalse(afterDelete.any { it.title == "TrackB" }, "Deleted physical file must be reconciled out of library")

        // 5. Remove folder from Lyrune
        repo.removeFolder(musicDir.absolutePath)
        val afterFolderRemove = repo.tracks.value
        assertEquals(0, afterFolderRemove.size, "Removing folder must remove tracks from library index")
        assertTrue(file1.exists(), "Removing folder from Lyrune must NEVER delete physical files")
        assertTrue(file3.exists(), "Removing folder from Lyrune must NEVER delete physical files")
    }
}
