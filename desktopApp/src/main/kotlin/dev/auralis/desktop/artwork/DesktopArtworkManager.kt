package dev.auralis.desktop.artwork

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.auralis.desktop.storage.DesktopPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.imageio.ImageIO

/**
 * Two-tier bounded artwork pipeline for Lyrune Desktop.
 *
 * Architecture:
 * Audio file / Sidecar → Raw artwork bytes → Bilinear thumbnail downsample → Disk cache → Memory LRU
 *
 * Memory Hardening:
 * - In-memory LRU cache strictly bounded to [MAX_MEMORY_CACHE_ENTRIES] (150 thumbnails).
 * - Thumbnails are downscaled near target display size (default 128x128) rather than holding 4K bitmaps in RAM.
 * - Disk cache bounded to [MAX_DISK_CACHE_BYTES] (50 MB) with LRU file eviction.
 */
object DesktopArtworkManager {

    private const val MAX_MEMORY_CACHE_ENTRIES = 150
    const val MAX_MEMORY_CACHE_BYTES = 24 * 1024 * 1024L // 24 MiB
    private const val MAX_DISK_CACHE_BYTES = 50 * 1024 * 1024L // 50 MB
    private const val THUMBNAIL_SIZE = 128

    @Volatile
    private var currentMemoryBytes = 0L

    private val memoryCache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            val shouldEvict = size > MAX_MEMORY_CACHE_ENTRIES || currentMemoryBytes > MAX_MEMORY_CACHE_BYTES
            if (shouldEvict && eldest != null) {
                val bmp = eldest.value
                val bytes = bmp.width.toLong() * bmp.height.toLong() * 4L
                currentMemoryBytes = (currentMemoryBytes - bytes).coerceAtLeast(0L)
            }
            return shouldEvict
        }
    }

    private val cacheMutex = Mutex()
    private val diskCacheDir: File
        get() = try {
            DesktopPaths.artworkCacheDir.apply { if (!exists()) mkdirs() }
        } catch (_: Exception) {
            DesktopPaths.artworkCacheDir
        }

    /**
     * Loads thumbnail artwork for the given track or URL asynchronously.
     * Checks memory cache -> disk thumbnail cache -> extracts from file/network -> scales and caches.
     */
    suspend fun getArtwork(trackPath: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (trackPath.startsWith("http://", ignoreCase = true) || trackPath.startsWith("https://", ignoreCase = true)) {
            return@withContext getRemoteArtwork(trackPath)
        }
        val file = File(trackPath)
        if (!file.exists()) return@withContext null

        val cacheKey = computeCacheKey(file)

        // 1. Check in-memory LRU cache
        cacheMutex.withLock {
            memoryCache[cacheKey]?.let { return@withContext it }
        }

        // 2. Check disk thumbnail cache
        try {
            val diskCachedThumbnail = File(diskCacheDir, "$cacheKey.jpg")
            if (diskCachedThumbnail.exists() && diskCachedThumbnail.length() > 0L) {
                try {
                    val buffered = ImageIO.read(diskCachedThumbnail)
                    if (buffered != null) {
                        val bitmap = buffered.toComposeImageBitmap()
                        cacheMutex.withLock {
                            putIntoMemoryCache(cacheKey, bitmap)
                        }
                        return@withContext bitmap
                    } else {
                        diskCachedThumbnail.delete()
                    }
                } catch (_: Exception) {
                    diskCachedThumbnail.delete()
                }
            }
        } catch (_: Exception) {
        }

        // 3. Extract raw image from sidecar or embedded ID3v2 APIC
        val rawImage = extractRawArtwork(file) ?: return@withContext null

        // 4. Downsample to thumbnail size
        val thumbnail = scaleThumbnail(rawImage, THUMBNAIL_SIZE, THUMBNAIL_SIZE) ?: return@withContext null

        // 5. Save scaled thumbnail to disk cache
        try {
            val diskCachedThumbnail = File(diskCacheDir, "$cacheKey.jpg")
            ImageIO.write(thumbnail, "jpg", diskCachedThumbnail)
            pruneDiskCacheIfNeeded()
        } catch (_: Exception) {
        }

        // 6. Store in memory cache
        val composeBitmap = thumbnail.toComposeImageBitmap()
        cacheMutex.withLock {
            putIntoMemoryCache(cacheKey, composeBitmap)
        }

        composeBitmap
    }

    private fun putIntoMemoryCache(cacheKey: String, bitmap: ImageBitmap) {
        val existing = memoryCache[cacheKey]
        if (existing != null) {
            currentMemoryBytes = (currentMemoryBytes - (existing.width.toLong() * existing.height.toLong() * 4L)).coerceAtLeast(0L)
        }
        val bytes = bitmap.width.toLong() * bitmap.height.toLong() * 4L
        currentMemoryBytes += bytes
        memoryCache[cacheKey] = bitmap

        // Enforce both limits: entry count AND byte budget
        val iterator = memoryCache.entries.iterator()
        while ((memoryCache.size > MAX_MEMORY_CACHE_ENTRIES || currentMemoryBytes > MAX_MEMORY_CACHE_BYTES) && iterator.hasNext()) {
            val entry = iterator.next()
            val bmp = entry.value
            currentMemoryBytes = (currentMemoryBytes - (bmp.width.toLong() * bmp.height.toLong() * 4L)).coerceAtLeast(0L)
            iterator.remove()
        }
    }

    private fun extractRawArtwork(audioFile: File): BufferedImage? {
        // Priority 1: Sidecar image file in same directory
        val parent = audioFile.parentFile
        if (parent != null && parent.isDirectory) {
            val candidateNames = listOf("folder.jpg", "cover.jpg", "front.jpg", "album.jpg", "folder.png", "cover.png")
            for (name in candidateNames) {
                val sidecar = File(parent, name)
                if (sidecar.exists() && sidecar.length() in 1024..15_000_000) {
                    try {
                        val img = ImageIO.read(sidecar)
                        if (img != null) return img
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // Priority 2: Embedded ID3v2 APIC frame (for MP3 files)
        if (audioFile.extension.equals("mp3", ignoreCase = true)) {
            val embeddedBytes = extractId3v2ApicBytes(audioFile)
            if (embeddedBytes != null && embeddedBytes.isNotEmpty()) {
                try {
                    return ImageIO.read(ByteArrayInputStream(embeddedBytes))
                } catch (_: Exception) {
                }
            }
        }

        // Priority 3: Embedded PICTURE block (for FLAC files)
        if (audioFile.extension.equals("flac", ignoreCase = true)) {
            val embeddedBytes = dev.auralis.desktop.playback.flac.FlacMetadataReader.extractPicture(audioFile)
            if (embeddedBytes != null && embeddedBytes.isNotEmpty()) {
                try {
                    return ImageIO.read(ByteArrayInputStream(embeddedBytes))
                } catch (_: Exception) {
                }
            }
        }

        // AAC/MP4 and Vorbis can carry covers in their container. Decode only one
        // thumbnail on demand, never while indexing or into a full media buffer.
        if (audioFile.extension.lowercase() in setOf("m4a", "aac", "ogg", "opus", "webm", "wav")) {
            return extractContainerArtwork(audioFile)
        }
        return null
    }

    private fun extractContainerArtwork(file: File): BufferedImage? = runCatching {
        val process = ProcessBuilder(
            dev.auralis.desktop.playback.decoder.FfmpegAudioDecoder.executable,
            "-nostdin", "-v", "error", "-threads", "1", "-i", file.absolutePath,
            "-map", "0:v:0", "-an", "-frames:v", "1", "-threads", "1",
            "-vf", "scale=256:256:force_original_aspect_ratio=decrease",
            "-f", "image2pipe", "-vcodec", "png", "pipe:1",
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        var bytes: ByteArray? = null
        val reader = Thread({ bytes = runCatching { process.inputStream.use { it.readNBytes(1_048_577) } }.getOrNull() }, "Lyrune-Cover").apply { isDaemon = true; start() }
        try {
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) return@runCatching null
            reader.join(500)
            bytes?.takeIf { it.isNotEmpty() && it.size <= 1_048_576 }?.let { ImageIO.read(ByteArrayInputStream(it)) }
        } finally { process.destroyForcibly(); runCatching { process.inputStream.close() } }
    }.getOrNull()

    /**
     * Efficiently extracts embedded APIC frame bytes from MP3 ID3v2 tag without loading the audio file into memory.
     */
    fun extractId3v2ApicBytes(file: File): ByteArray? {
        if (!file.exists() || file.length() < 128) return null

        try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(10)
                raf.readFully(header)

                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                    return null
                }

                val version = header[3].toInt()
                val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                    ((header[7].toInt() and 0x7F) shl 14) or
                    ((header[8].toInt() and 0x7F) shl 7) or
                    (header[9].toInt() and 0x7F)

                val maxRead = Math.min(tagSize.toLong(), 8 * 1024 * 1024L) // Max 8MB tag scan
                val tagBytes = ByteArray(maxRead.toInt())
                raf.readFully(tagBytes)

                var offset = 0
                val targetId = "APIC".toByteArray(Charsets.US_ASCII)

                while (offset + 10 < tagBytes.size) {
                    // Search for "APIC" frame header
                    if (tagBytes[offset] == targetId[0] &&
                        tagBytes[offset + 1] == targetId[1] &&
                        tagBytes[offset + 2] == targetId[2] &&
                        tagBytes[offset + 3] == targetId[3]
                    ) {
                        val frameSize = if (version == 4) {
                            // Synchsafe size in ID3v2.4
                            ((tagBytes[offset + 4].toInt() and 0x7F) shl 21) or
                                ((tagBytes[offset + 5].toInt() and 0x7F) shl 14) or
                                ((tagBytes[offset + 6].toInt() and 0x7F) shl 7) or
                                (tagBytes[offset + 7].toInt() and 0x7F)
                        } else {
                            // Standard 32-bit big endian in ID3v2.3
                            ((tagBytes[offset + 4].toInt() and 0xFF) shl 24) or
                                ((tagBytes[offset + 5].toInt() and 0xFF) shl 16) or
                                ((tagBytes[offset + 6].toInt() and 0xFF) shl 8) or
                                (tagBytes[offset + 7].toInt() and 0xFF)
                        }

                        if (frameSize <= 0 || offset + 10 + frameSize > tagBytes.size) break

                        val frameDataStart = offset + 10
                        // Search for image magic signature within frame:
                        // JPEG: FF D8 FF
                        // PNG:  89 50 4E 47
                        for (i in frameDataStart until (frameDataStart + Math.min(frameSize, 256))) {
                            if (i + 3 < tagBytes.size) {
                                val isJpeg = (tagBytes[i].toInt() and 0xFF == 0xFF) &&
                                    (tagBytes[i + 1].toInt() and 0xFF == 0xD8) &&
                                    (tagBytes[i + 2].toInt() and 0xFF == 0xFF)
                                val isPng = (tagBytes[i].toInt() and 0xFF == 0x89) &&
                                    (tagBytes[i + 1] == 'P'.code.toByte()) &&
                                    (tagBytes[i + 2] == 'N'.code.toByte()) &&
                                    (tagBytes[i + 3] == 'G'.code.toByte())

                                if (isJpeg || isPng) {
                                    val imgSize = (frameDataStart + frameSize) - i
                                    val imgBytes = ByteArray(imgSize)
                                    System.arraycopy(tagBytes, i, imgBytes, 0, imgSize)
                                    return imgBytes
                                }
                            }
                        }
                        break
                    }
                    offset++
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun scaleThumbnail(source: BufferedImage, targetWidth: Int, targetHeight: Int): BufferedImage? {
        val srcWidth = source.width
        val srcHeight = source.height
        if (srcWidth <= 0 || srcHeight <= 0) return null

        val scale = Math.min(targetWidth.toDouble() / srcWidth, targetHeight.toDouble() / srcHeight)
        val scaledW = Math.max(1, (srcWidth * scale).toInt())
        val scaledH = Math.max(1, (srcHeight * scale).toInt())

        val thumbnail = BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_RGB)
        val g = thumbnail.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(source, 0, 0, scaledW, scaledH, null)
        } finally {
            g.dispose()
            try {
                source.flush() // Immediately release source native AWT raster memory
            } catch (_: Exception) {
            }
        }
        return thumbnail
    }

    /**
     * Loads thumbnail artwork for a remote HTTP/HTTPS URL with 5MB download limit,
     * downscaling to thumbnail size, and LRU memory + disk caching.
     */
    suspend fun getRemoteArtwork(urlStr: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!urlStr.startsWith("http://", ignoreCase = true) && !urlStr.startsWith("https://", ignoreCase = true)) {
            return@withContext null
        }
        val cacheKey = computeUrlCacheKey(urlStr)

        // 1. Check in-memory LRU cache
        cacheMutex.withLock {
            memoryCache[cacheKey]?.let { return@withContext it }
        }

        // 2. Check disk thumbnail cache
        val diskCachedThumbnail = File(diskCacheDir, "$cacheKey.jpg")
        if (diskCachedThumbnail.exists() && diskCachedThumbnail.length() > 0L) {
            try {
                val buffered = ImageIO.read(diskCachedThumbnail)
                if (buffered != null) {
                    val bitmap = buffered.toComposeImageBitmap()
                    cacheMutex.withLock {
                        putIntoMemoryCache(cacheKey, bitmap)
                    }
                    return@withContext bitmap
                } else {
                    diskCachedThumbnail.delete()
                }
            } catch (_: Exception) {
                diskCachedThumbnail.delete()
            }
        }

        // 3. Download bounded raw image (max 5 MB)
        try {
            val (conn, _) = dev.auralis.desktop.playback.DesktopPlaybackEngine.openHttpConnectionWithRedirects(urlStr)
            val bytes = conn.inputStream.use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val temp = ByteArray(8192)
                var read: Int
                var total = 0
                while (input.read(temp).also { read = it } != -1) {
                    buffer.write(temp, 0, read)
                    total += read
                    if (total > 5 * 1024 * 1024) break
                }
                buffer.toByteArray()
            }
            conn.disconnect()

            val rawImage = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@withContext null
            val thumbnail = scaleThumbnail(rawImage, THUMBNAIL_SIZE, THUMBNAIL_SIZE) ?: return@withContext null

            try {
                ImageIO.write(thumbnail, "jpg", diskCachedThumbnail)
                pruneDiskCacheIfNeeded()
            } catch (_: Exception) {}

            val composeBitmap = thumbnail.toComposeImageBitmap()
            cacheMutex.withLock {
                putIntoMemoryCache(cacheKey, composeBitmap)
            }
            return@withContext composeBitmap
        } catch (_: Exception) {
            return@withContext null
        }
    }

    private fun computeUrlCacheKey(urlStr: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(urlStr.toByteArray(Charsets.UTF_8))
        return "net_" + bytes.joinToString("") { "%02x".format(it) }
    }

    private fun computeCacheKey(file: File): String {
        val raw = "${file.absolutePath}_${file.length()}_${file.lastModified()}"
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun pruneDiskCacheIfNeeded() {
        val files = diskCacheDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= MAX_DISK_CACHE_BYTES) return

        // Sort by last accessed / last modified to evict oldest
        val sortedFiles = files.sortedBy { it.lastModified() }
        for (f in sortedFiles) {
            totalSize -= f.length()
            f.delete()
            if (totalSize <= MAX_DISK_CACHE_BYTES * 0.8) break
        }
    }

    fun clearMemoryCache() {
        memoryCache.clear()
        currentMemoryBytes = 0L
    }

    fun getMemoryCacheSize(): Int = memoryCache.size

    fun getMemoryCacheBytes(): Long = currentMemoryBytes

    fun getCachedArtworkFile(trackPath: String): File? {
        val cacheKey = if (trackPath.startsWith("http://", ignoreCase = true) || trackPath.startsWith("https://", ignoreCase = true)) {
            computeUrlCacheKey(trackPath)
        } else {
            val file = File(trackPath)
            if (!file.exists()) return null
            computeCacheKey(file)
        }
        val diskCachedThumbnail = File(diskCacheDir, "$cacheKey.jpg")
        return if (diskCachedThumbnail.isFile && diskCachedThumbnail.length() > 0L) diskCachedThumbnail else null
    }
}
