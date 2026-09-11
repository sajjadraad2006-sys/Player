package dev.auralis.desktop.library

import dev.auralis.desktop.storage.DesktopDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import kotlin.system.measureTimeMillis
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Realistic filesystem indexing benchmark measuring actual disk I/O, file traversal,
 * and header metadata parsing (distinct from synthetic repository test).
 */
class RealFilesystemBenchmarkTest {

    private lateinit var tempDir: File
    private lateinit var libraryDir: File
    private lateinit var dbFile: File

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lyrune_fs_bench_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        libraryDir = File(tempDir, "Library").apply { mkdirs() }
        dbFile = File(tempDir, "library.json")
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testRealFilesystemEnumerationAndMetadataBenchmark() = runBlocking {
        val totalFiles = 250
        var validAudioCount = 0
        var unsupportedCount = 0
        var corruptCount = 0

        // Build nested directory structure with real files on disk
        for (i in 1..totalFiles) {
            val subDir = File(libraryDir, "Artist_${i % 10}/Album_${i % 5}").apply { mkdirs() }
            when {
                i % 25 == 0 -> {
                    // Corrupt audio file
                    corruptCount++
                    val f = File(subDir, "corrupt_$i.mp3")
                    f.writeBytes(ByteArray(100) { 0xFF.toByte() })
                }
                i % 15 == 0 -> {
                    // Unsupported file
                    unsupportedCount++
                    val f = File(subDir, "readme_$i.txt")
                    f.writeText("Lyrics and track notes")
                }
                else -> {
                    // Valid WAV file
                    validAudioCount++
                    val f = File(subDir, "song_$i.wav")
                    FileOutputStream(f).use { fos ->
                        // Standard RIFF/WAVE header
                        fos.write("RIFF".toByteArray())
                        fos.write(byteArrayOf(0x40, 0x1F, 0x00, 0x00)) // size ~8000
                        fos.write("WAVEfmt ".toByteArray())
                        fos.write(byteArrayOf(16, 0, 0, 0, 1, 0, 2, 0)) // PCM, stereo
                        fos.write(byteArrayOf(0x44, 0xAC.toByte(), 0, 0)) // 44100 Hz
                        fos.write(byteArrayOf(0x10, 0xB1.toByte(), 0x02, 0)) // byteRate
                        fos.write(byteArrayOf(4, 0, 16, 0)) // blockAlign, 16b
                        fos.write("data".toByteArray())
                        fos.write(byteArrayOf(0x00, 0x1F, 0x00, 0x00))
                        fos.write(ByteArray(2048) { 0 })
                    }
                }
            }
        }

        val runtime = Runtime.getRuntime()
        runtime.gc()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()

        val db = DesktopDatabase(dbFile = dbFile)
        val repo = DesktopMusicRepository(database = db)

        val elapsedMs = measureTimeMillis {
            repo.scanFolders(listOf(libraryDir))
        }

        runtime.gc()
        val heapAfter = runtime.totalMemory() - runtime.freeMemory()
        val heapUsedMb = (heapAfter - heapBefore) / (1024 * 1024)

        val indexedTracks = repo.tracks.value

        println("==========================================================")
        println("REAL FILESYSTEM INDEXING BENCHMARK RESULTS")
        println("==========================================================")
        println("Files Scanned on Disk:       $totalFiles")
        println("Valid Audio Files Expected:  $validAudioCount")
        println("Successfully Indexed Tracks: ${indexedTracks.size}")
        println("Unsupported Files Skipped:   $unsupportedCount")
        println("Corrupt Files Handled:       $corruptCount")
        println("Elapsed Scanning Time:       ${elapsedMs}ms")
        println("Heap Increase:               ${heapUsedMb} MB")
        println("==========================================================")

        // Verifications
        assertEquals(validAudioCount, indexedTracks.size, "All valid audio files must be indexed successfully")
        assertTrue(elapsedMs < 3000, "250 physical files across nested directories must index in < 3000ms (took ${elapsedMs}ms)")
        assertTrue(heapUsedMb < 15, "Filesystem indexing heap increase must remain under 15 MB")
    }
}
