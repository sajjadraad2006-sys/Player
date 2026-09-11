package dev.auralis.desktop.artwork

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import javax.imageio.ImageIO

class LargeArtworkAndByteBudgetTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lyrune_art_large_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        DesktopArtworkManager.clearMemoryCache()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        DesktopArtworkManager.clearMemoryCache()
    }

    private fun createTrackWithImage(name: String, width: Int, height: Int, format: String = "jpg"): File {
        val folder = File(tempDir, name).apply { mkdirs() }
        val audio = File(folder, "$name.mp3")
        FileOutputStream(audio).use { fos ->
            fos.write(ByteArray(2048) { 0 })
        }

        val cover = File(folder, "cover.$format")
        val imgType = if (format == "png") BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val img = BufferedImage(width, height, imgType)
        val g: Graphics2D = img.createGraphics()
        g.color = Color.CYAN
        g.fillRect(0, 0, width, height)
        g.color = Color.BLACK
        g.drawString("Test $name ($width x $height)", 20, 40)
        g.dispose()

        ImageIO.write(img, format, cover)
        img.flush()

        return audio
    }

    @Test
    fun testLargeArtworkDecoding500x500() = runBlocking {
        val audio = createTrackWithImage("Art500", 500, 500)
        val bitmap = DesktopArtworkManager.getArtwork(audio.absolutePath)
        assertNotNull("500x500 cover must decode cleanly", bitmap)
        assertEquals(128, bitmap!!.width)
        assertEquals(128, bitmap.height)
    }

    @Test
    fun testLargeArtworkDecoding1500x1500() = runBlocking {
        val audio = createTrackWithImage("Art1500", 1500, 1500)
        val bitmap = DesktopArtworkManager.getArtwork(audio.absolutePath)
        assertNotNull("1500x1500 cover must decode cleanly", bitmap)
        assertEquals(128, bitmap!!.width)
        assertEquals(128, bitmap.height)
    }

    @Test
    fun testLargeArtworkDecoding3000x3000() = runBlocking {
        val audio = createTrackWithImage("Art3000", 3000, 3000)
        val bitmap = DesktopArtworkManager.getArtwork(audio.absolutePath)
        assertNotNull("3000x3000 high-res cover must decode and downsample cleanly", bitmap)
        assertEquals(128, bitmap!!.width)
        assertEquals(128, bitmap.height)
    }

    @Test
    fun testLargePngArtwork() = runBlocking {
        val audio = createTrackWithImage("ArtPng", 800, 800, format = "png")
        val bitmap = DesktopArtworkManager.getArtwork(audio.absolutePath)
        assertNotNull("PNG cover must decode cleanly", bitmap)
        assertEquals(128, bitmap!!.width)
        assertEquals(128, bitmap.height)
    }

    @Test
    fun testMemoryByteBudgetEnforced() = runBlocking {
        DesktopArtworkManager.clearMemoryCache()

        // Create 20 distinct tracks with images
        val tracks = (1..20).map { i ->
            createTrackWithImage("Track_$i", 400, 400)
        }

        for (track in tracks) {
            DesktopArtworkManager.getArtwork(track.absolutePath)
        }

        val cacheEntries = DesktopArtworkManager.getMemoryCacheSize()
        val cacheBytes = DesktopArtworkManager.getMemoryCacheBytes()

        assertTrue("Cache entry count must be > 0", cacheEntries > 0)
        assertTrue("Cache byte count must be > 0", cacheBytes > 0)
        // Each 128x128 32-bit thumbnail is 128 * 128 * 4 = 65,536 bytes (64 KB)
        assertEquals("Each thumbnail must contribute exactly 65,536 bytes", cacheEntries * 65536L, cacheBytes)

        assertTrue(
            "Cache bytes must not exceed MAX_MEMORY_CACHE_BYTES",
            cacheBytes <= DesktopArtworkManager.MAX_MEMORY_CACHE_BYTES,
        )
    }

    @Test
    fun testDiskCacheSurvivesMemoryClear() = runBlocking {
        val audio = createTrackWithImage("DiskCacheTest", 600, 600)
        val initialBitmap = DesktopArtworkManager.getArtwork(audio.absolutePath)
        assertNotNull(initialBitmap)

        // Clear in-memory cache to force read from disk cache
        DesktopArtworkManager.clearMemoryCache()
        assertEquals(0, DesktopArtworkManager.getMemoryCacheSize())

        val reloadedBitmap = DesktopArtworkManager.getArtwork(audio.absolutePath)
        assertNotNull("Artwork must be reloaded cleanly from disk cache", reloadedBitmap)
        assertEquals(1, DesktopArtworkManager.getMemoryCacheSize())
    }

    @Test
    fun testCorruptDiskCacheThumbnailIsPrunedAndRecovered() = runBlocking {
        val audio = createTrackWithImage("CorruptDiskTest", 400, 400)
        val initialBitmap = DesktopArtworkManager.getArtwork(audio.absolutePath)
        assertNotNull(initialBitmap)

        DesktopArtworkManager.clearMemoryCache()

        // Find cached thumbnail in diskCacheDir and overwrite with corrupted bytes
        val diskCacheDir = File(DesktopArtworkManager.getArtwork(audio.absolutePath)?.let { "" } ?: "").let {
            dev.auralis.desktop.storage.DesktopPaths.artworkCacheDir
        }
        val cachedFiles = diskCacheDir.listFiles() ?: emptyArray()
        assertTrue(cachedFiles.isNotEmpty())

        // Corrupt all cached thumbnails
        for (f in cachedFiles) {
            f.writeBytes(byteArrayOf(0, 0, 0, 0))
        }

        DesktopArtworkManager.clearMemoryCache()

        // getArtwork must detect the corrupt thumbnail, prune it, and successfully recover from source
        val recoveredBitmap = DesktopArtworkManager.getArtwork(audio.absolutePath)
        assertNotNull("Must gracefully recover from corrupted disk thumbnail without error", recoveredBitmap)
    }
}
