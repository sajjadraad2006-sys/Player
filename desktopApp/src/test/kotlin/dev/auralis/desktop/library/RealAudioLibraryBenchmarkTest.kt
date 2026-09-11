package dev.auralis.desktop.library

import dev.auralis.desktop.storage.DatabaseSchema
import dev.auralis.desktop.storage.DesktopDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RealAudioLibraryBenchmarkTest {

    private lateinit var tempDir: File
    private lateinit var dbFile: File
    private lateinit var sessionFile: File

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lyrune_real_benchmark_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        dbFile = File(tempDir, "library.json")
        sessionFile = File(tempDir, "session.json")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun generateValidWavFile(file: File, durationSeconds: Float, sampleRate: Int = 44100) {
        val channels = 2
        val bitsPerSample = 16
        val blockAlign = channels * (bitsPerSample / 8)
        val byteRate = sampleRate * blockAlign
        val totalFrames = (sampleRate * durationSeconds).toInt()
        val dataSize = totalFrames * blockAlign
        val fileSize = 44 + dataSize

        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(fileSize - 8)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1.toShort()) // PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray())
            header.putInt(dataSize)
            fos.write(header.array())

            // Write actual non-zero PCM audio bytes
            val chunk = ByteArray(2048)
            var written = 0
            while (written < dataSize) {
                val toWrite = minOf(chunk.size, dataSize - written)
                fos.write(chunk, 0, toWrite)
                written += toWrite
            }
        }
    }

    private fun generateValidMp3WithId3(file: File, title: String, artist: String) {
        FileOutputStream(file).use { fos ->
            // ID3v2.3 tag header (10 bytes)
            val id3Header = byteArrayOf(
                'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
                0x03, 0x00, // Version 2.3
                0x00, // Flags
                0x00, 0x00, 0x08, 0x00, // 1024 bytes synchsafe size
            )
            fos.write(id3Header)

            // TIT2 Frame
            val titleBytes = title.toByteArray(Charsets.ISO_8859_1)
            fos.write("TIT2".toByteArray())
            val titSize = titleBytes.size + 1
            fos.write(byteArrayOf(0, 0, (titSize shr 8).toByte(), (titSize and 0xFF).toByte()))
            fos.write(byteArrayOf(0, 0)) // flags
            fos.write(0) // encoding: ISO-8859-1
            fos.write(titleBytes)

            // TPE1 Frame (Artist)
            val artistBytes = artist.toByteArray(Charsets.ISO_8859_1)
            fos.write("TPE1".toByteArray())
            val artSize = artistBytes.size + 1
            fos.write(byteArrayOf(0, 0, (artSize shr 8).toByte(), (artSize and 0xFF).toByte()))
            fos.write(byteArrayOf(0, 0))
            fos.write(0)
            fos.write(artistBytes)

            // Pad remaining tag
            fos.write(ByteArray(500) { 0 })

            // Write real MPEG audio sync frame (MPEG 1 Layer 3, 128 kbps, 44.1 kHz, Stereo)
            val mp3Frame = byteArrayOf(
                0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64.toByte(),
            )
            for (i in 0 until 50) {
                fos.write(mp3Frame)
                fos.write(ByteArray(413) { 0x55.toByte() })
            }
        }
    }

    @Test
    fun testRealAudioMetadataBenchmark50Files() = runBlocking {
        val folder = File(tempDir, "music_50").apply { mkdirs() }

        for (i in 1..25) {
            generateValidWavFile(File(folder, "Track_Wav_$i.wav"), durationSeconds = 1.0f)
        }
        for (i in 1..25) {
            generateValidMp3WithId3(File(folder, "Track_Mp3_$i.mp3"), title = "Title $i", artist = "Artist $i")
        }

        val database = DesktopDatabase(dbFile = dbFile, sessionFile = sessionFile)
        val repository = DesktopMusicRepository(database = database)

        val coldStartTime = System.nanoTime()
        repository.addFolder(folder)
        val coldDurationMs = (System.nanoTime() - coldStartTime) / 1_000_000

        val tracks = repository.tracks.value
        assertEquals("Should index all 50 real audio files", 50, tracks.size)
        assertTrue("Cold scan of 50 files should complete in < 2000ms", coldDurationMs < 2000)

        // Incremental scan (cache hit comparison on timestamps & sizes)
        val incStartTime = System.nanoTime()
        repository.scanFolders(listOf(folder))
        val incDurationMs = (System.nanoTime() - incStartTime) / 1_000_000

        assertTrue("Incremental scan of unchanged 50 files should be fast (< 200ms)", incDurationMs < 200)
    }

    @Test
    fun testRealAudioMetadataBenchmark250Files() = runBlocking {
        val folder = File(tempDir, "music_250").apply { mkdirs() }

        for (i in 1..125) {
            generateValidWavFile(File(folder, "Wav_$i.wav"), durationSeconds = 0.5f)
        }
        for (i in 1..125) {
            generateValidMp3WithId3(File(folder, "Mp3_$i.mp3"), title = "Real Song $i", artist = "Performer $i")
        }

        val database = DesktopDatabase(dbFile = dbFile, sessionFile = sessionFile)
        val repository = DesktopMusicRepository(database = database)

        val coldStartTime = System.nanoTime()
        repository.addFolder(folder)
        val coldDurationMs = (System.nanoTime() - coldStartTime) / 1_000_000

        assertEquals(250, repository.tracks.value.size)
        assertTrue("Cold scan of 250 real audio files should complete within 3500ms", coldDurationMs < 3500)

        // Incremental scan
        val incStartTime = System.nanoTime()
        repository.scanFolders(listOf(folder))
        val incDurationMs = (System.nanoTime() - incStartTime) / 1_000_000

        assertTrue("Incremental scan of 250 files should take < 400ms", incDurationMs < 400)
    }

    @Test
    fun testPersistenceSizeAndSerializationScalability() {
        // Benchmark 100, 1,000, and 10,000 synthetic records
        val scales = listOf(100, 1000, 10000)

        for (count in scales) {
            val syntheticTracks = (1..count).map { i ->
                DesktopTrack(
                    id = i.toLong(),
                    path = "D:\\Music\\Collection\\Artist_${i % 50}\\Album_${i % 100}\\Track_${i}.mp3",
                    title = "Song Title #$i (Remastered Edition)",
                    artist = "Featured Artist ${i % 50}",
                    album = "Greatest Hits Vol. ${i % 20}",
                    durationMs = 210000L + (i * 100L),
                    sizeBytes = 6500000L,
                    lastModified = 1700000000L + i,
                    mimeType = "audio/mpeg",
                    isFavorite = i % 7 == 0,
                    folderPath = "D:\\Music\\Collection",
                )
            }

            val schema = DatabaseSchema(
                schemaVersion = 1,
                tracks = syntheticTracks,
                folders = listOf("D:\\Music\\Collection"),
                favorites = syntheticTracks.filter { it.isFavorite }.map { it.path }.toSet(),
            )

            // Serialization benchmark
            val serStart = System.nanoTime()
            val serialized = json.encodeToString(schema)
            val serDurationMs = (System.nanoTime() - serStart) / 1_000_000

            val sizeBytes = serialized.toByteArray(Charsets.UTF_8).size

            // Deserialization benchmark
            val deserStart = System.nanoTime()
            val deserialized = json.decodeFromString<DatabaseSchema>(serialized)
            val deserDurationMs = (System.nanoTime() - deserStart) / 1_000_000

            assertEquals(count, deserialized.tracks.size)

            println("[PersistenceBenchmark] Count: $count | Size: ${sizeBytes / 1024} KB | Serialize: ${serDurationMs}ms | Parse: ${deserDurationMs}ms")

            if (count == 100) {
                assertTrue("100 tracks JSON should be < 50 KB", sizeBytes < 50 * 1024)
                assertTrue("100 tracks parse should be < 50ms", deserDurationMs < 50)
            } else if (count == 1000) {
                assertTrue("1,000 tracks JSON should be < 500 KB", sizeBytes < 500 * 1024)
                assertTrue("1,000 tracks parse should be < 200ms", deserDurationMs < 200)
            } else if (count == 10000) {
                assertTrue("10,000 tracks JSON should be < 5 MB", sizeBytes < 5 * 1024 * 1024)
                assertTrue("10,000 tracks parse should be < 1000ms", deserDurationMs < 1000)
            }
        }
    }
}
