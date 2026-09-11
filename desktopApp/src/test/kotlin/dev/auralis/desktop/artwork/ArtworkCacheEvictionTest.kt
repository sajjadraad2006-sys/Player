package dev.auralis.desktop.artwork

import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkCacheEvictionTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lyrune_art_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        DesktopArtworkManager.clearMemoryCache()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
        DesktopArtworkManager.clearMemoryCache()
    }

    private fun createAudioWithSidecar(trackName: String, hasCover: Boolean): File {
        val folder = File(tempDir, trackName).apply { mkdirs() }
        val audio = File(folder, "$trackName.mp3")
        FileOutputStream(audio).use { fos ->
            fos.write(ByteArray(2048) { 0 })
        }

        if (hasCover) {
            val cover = File(folder, "cover.jpg")
            val img = BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB)
            val g: Graphics2D = img.createGraphics()
            g.color = Color.MAGENTA
            g.fillRect(0, 0, 300, 300)
            g.dispose()
            ImageIO.write(img, "jpg", cover)
        }

        return audio
    }

    @Test
    fun testArtworkExtractionAndDiskCache() = runBlocking {
        val audioWithArt = createAudioWithSidecar("TrackArt", hasCover = true)
        val audioWithoutArt = createAudioWithSidecar("TrackNoArt", hasCover = false)

        val art = DesktopArtworkManager.getArtwork(audioWithArt.absolutePath)
        assertNotNull(art, "Sidecar cover.jpg must be extracted and returned as ImageBitmap")

        val noArt = DesktopArtworkManager.getArtwork(audioWithoutArt.absolutePath)
        assertNull(noArt, "Track without artwork must return null safely without throwing")
    }

    @Test
    fun testMemoryCacheBoundedToLimit() = runBlocking {
        // Create 180 tracks with artwork
        val tracks = (1..180).map { i ->
            createAudioWithSidecar("Track_$i", hasCover = true)
        }

        for (track in tracks) {
            DesktopArtworkManager.getArtwork(track.absolutePath)
        }

        val cacheSize = DesktopArtworkManager.getMemoryCacheSize()
        assertTrue(
            cacheSize <= 150,
            "Memory cache must strictly evict oldest items and not exceed 150 entries (actual: $cacheSize)",
        )
    }

    @Test
    fun testMalformedFileReturnsNullSafely() {
        val garbageFile = File(tempDir, "garbage.mp3")
        garbageFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val apic = DesktopArtworkManager.extractId3v2ApicBytes(garbageFile)
        assertNull(apic, "Malformed file must yield null APIC bytes without throwing")
    }
}
