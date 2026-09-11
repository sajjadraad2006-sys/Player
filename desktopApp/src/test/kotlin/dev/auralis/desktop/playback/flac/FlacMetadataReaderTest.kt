package dev.auralis.desktop.playback.flac

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FlacMetadataReaderTest {

    private fun extractResourceToTemp(resourcePath: String, suffix: String): File {
        val tempFile = File.createTempFile("flac_test_", suffix)
        tempFile.deleteOnExit()
        val stream = javaClass.getResourceAsStream(resourcePath)
            ?: error("Resource not found: $resourcePath")
        stream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    @Test
    fun testReal16BitFlacStreamInfo() {
        val file = extractResourceToTemp("/audio/test_sine_16.flac", ".flac")
        try {
            val metadata = FlacMetadataReader.readMetadata(file)
            assertNotNull("Metadata must be parsed", metadata)
            assertEquals(44100, metadata!!.sampleRate)
            assertEquals(2, metadata.channels)
            assertEquals(16, metadata.bitsPerSample)
            assertTrue("Duration should be positive", metadata.durationMs > 0)
            assertTrue("Total samples should be positive", metadata.totalSamples > 0)
        } finally {
            file.delete()
        }
    }

    @Test
    fun testReal24BitFlacStreamInfo() {
        val file = extractResourceToTemp("/audio/test_sine_24.flac", ".flac")
        try {
            val metadata = FlacMetadataReader.readMetadata(file)
            assertNotNull("Metadata must be parsed", metadata)
            assertEquals(96000, metadata!!.sampleRate)
            assertEquals(2, metadata.channels)
            assertEquals(24, metadata.bitsPerSample)
            assertTrue("Duration should be positive", metadata.durationMs > 0)
            assertTrue("Total samples should be positive", metadata.totalSamples > 0)
        } finally {
            file.delete()
        }
    }

    @Test
    fun testSyntheticVorbisCommentParsing() {
        val syntheticFlac = createSyntheticFlac(
            sampleRate = 48000,
            channels = 2,
            bitsPerSample = 16,
            totalSamples = 48000 * 3, // 3 seconds
            tags = mapOf(
                "TITLE" to "Starlight Serenade",
                "ARTIST" to "Auralis Ensemble",
                "ALBUM" to "Cosmic Horizons",
                "DATE" to "2026",
                "GENRE" to "Ambient",
                "TRACKNUMBER" to "7"
            )
        )

        try {
            val metadata = FlacMetadataReader.readMetadata(syntheticFlac)
            assertNotNull(metadata)
            assertEquals(48000, metadata!!.sampleRate)
            assertEquals(2, metadata.channels)
            assertEquals(16, metadata.bitsPerSample)
            assertEquals(3000L, metadata.durationMs)

            assertEquals("Starlight Serenade", metadata.title)
            assertEquals("Auralis Ensemble", metadata.artist)
            assertEquals("Cosmic Horizons", metadata.album)
            assertEquals("2026", metadata.date)
            assertEquals("Ambient", metadata.genre)
            assertEquals(7, metadata.trackNumber)
        } finally {
            syntheticFlac.delete()
        }
    }

    @Test
    fun testSyntheticPictureExtraction() {
        val fakeImageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02, 0x03)
        val syntheticFlac = createSyntheticFlac(
            sampleRate = 44100,
            channels = 2,
            bitsPerSample = 16,
            totalSamples = 44100,
            pictureData = fakeImageData,
            pictureMime = "image/png"
        )

        try {
            val pictureBytes = FlacMetadataReader.extractPicture(syntheticFlac)
            assertNotNull("Picture must be extracted", pictureBytes)
            assertArrayEquals(fakeImageData, pictureBytes)
        } finally {
            syntheticFlac.delete()
        }
    }

    @Test
    fun testCorruptFlacHeadersHandledSafely() {
        val corruptFile = File.createTempFile("corrupt_flac_", ".flac")
        corruptFile.deleteOnExit()

        try {
            // Case 1: Less than 4 bytes
            FileOutputStream(corruptFile).use { it.write("fL".toByteArray()) }
            assertNull(FlacMetadataReader.readMetadata(corruptFile))
            assertNull(FlacMetadataReader.extractPicture(corruptFile))

            // Case 2: Wrong magic bytes
            FileOutputStream(corruptFile).use { it.write("RIFF1234WAVEfmt ".toByteArray()) }
            assertNull(FlacMetadataReader.readMetadata(corruptFile))
            assertNull(FlacMetadataReader.extractPicture(corruptFile))

            // Case 3: Truncated STREAMINFO block
            FileOutputStream(corruptFile).use { it.write("fLaC\u0000\u0000\u0000\u0022truncated".toByteArray()) }
            assertNull(FlacMetadataReader.readMetadata(corruptFile))
        } finally {
            corruptFile.delete()
        }
    }

    private fun createSyntheticFlac(
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        totalSamples: Long,
        tags: Map<String, String> = emptyMap(),
        pictureData: ByteArray? = null,
        pictureMime: String = "image/jpeg"
    ): File {
        val file = File.createTempFile("synthetic_", ".flac")
        file.deleteOnExit()

        val blocks = mutableListOf<Pair<Int, ByteArray>>()

        // Block 0: STREAMINFO (34 bytes)
        val streamInfoBytes = ByteArray(34)
        val bb = ByteBuffer.wrap(streamInfoBytes).order(ByteOrder.BIG_ENDIAN)
        bb.putShort(4096.toShort()) // min blocksize
        bb.putShort(4096.toShort()) // max blocksize
        bb.put(0.toByte()); bb.putShort(0.toShort()) // min framesize (3 bytes)
        bb.put(0.toByte()); bb.putShort(0.toShort()) // max framesize (3 bytes)

        // 8 bytes: sample rate (20b), channels - 1 (3b), bps - 1 (5b), total samples (36b)
        val sr = (sampleRate.toLong() and 0xFFFFFL) shl 44
        val ch = ((channels.toLong() - 1L) and 0x7L) shl 41
        val bps = ((bitsPerSample.toLong() - 1L) and 0x1FL) shl 36
        val tot = totalSamples and 0xFFFFFFFFFL
        val combined = sr or ch or bps or tot
        bb.putLong(combined)
        // 16 bytes MD5 signature
        bb.put(ByteArray(16))
        blocks.add(0 to streamInfoBytes)

        // Block 4: VORBIS_COMMENT
        if (tags.isNotEmpty()) {
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)
            val vendor = "Lyrune FLAC Encoder".toByteArray(Charsets.UTF_8)
            writeLittleEndianInt(dos, vendor.size)
            dos.write(vendor)
            writeLittleEndianInt(dos, tags.size)
            for ((k, v) in tags) {
                val comment = "$k=$v".toByteArray(Charsets.UTF_8)
                writeLittleEndianInt(dos, comment.size)
                dos.write(comment)
            }
            blocks.add(4 to baos.toByteArray())
        }

        // Block 6: PICTURE
        if (pictureData != null) {
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)
            dos.writeInt(3) // Picture type: Cover (front)
            val mimeBytes = pictureMime.toByteArray(Charsets.US_ASCII)
            dos.writeInt(mimeBytes.size)
            dos.write(mimeBytes)
            dos.writeInt(0) // Description length
            dos.writeInt(500) // Width
            dos.writeInt(500) // Height
            dos.writeInt(24) // Color depth
            dos.writeInt(0) // Indexed colors
            dos.writeInt(pictureData.size)
            dos.write(pictureData)
            blocks.add(6 to baos.toByteArray())
        }

        FileOutputStream(file).use { out ->
            out.write("fLaC".toByteArray(Charsets.US_ASCII))
            for (i in blocks.indices) {
                val (type, data) = blocks[i]
                val isLast = i == blocks.size - 1
                val headerByte = ((if (isLast) 0x80 else 0x00) or (type and 0x7F)).toByte()
                out.write(headerByte.toInt())
                val len = data.size
                out.write((len shr 16) and 0xFF)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
                out.write(data)
            }
        }
        return file
    }

    private fun writeLittleEndianInt(out: DataOutputStream, v: Int) {
        out.writeByte(v and 0xFF)
        out.writeByte((v shr 8) and 0xFF)
        out.writeByte((v shr 16) and 0xFF)
        out.writeByte((v shr 24) and 0xFF)
    }
}
