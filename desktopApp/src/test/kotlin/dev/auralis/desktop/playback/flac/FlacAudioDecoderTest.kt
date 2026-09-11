package dev.auralis.desktop.playback.flac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class FlacAudioDecoderTest {

    private fun extractResourceToTemp(resourcePath: String, suffix: String): File {
        val tempFile = File.createTempFile("flac_decoder_test_", suffix)
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
    fun testDecode16BitFlac() {
        val file = extractResourceToTemp("/audio/test_sine_16.flac", ".flac")
        try {
            val decoder = FlacAudioDecoder(file)
            try {
                assertEquals(44100.0f, decoder.format.sampleRate, 0.01f)
                assertEquals(16, decoder.format.sampleSizeInBits)
                assertEquals(2, decoder.format.channels)
                assertTrue("Duration should be positive", decoder.durationMs > 0)

                val buffer = ByteArray(4096)
                val bytesRead = decoder.readPcm(buffer, 0, buffer.size)
                assertTrue("Should read audio bytes", bytesRead > 0)
                assertEquals(0, bytesRead % 4) // Stereo 16-bit frame align

                // Verify non-silent samples
                var hasNonZero = false
                for (b in buffer) {
                    if (b != 0.toByte()) {
                        hasNonZero = true
                        break
                    }
                }
                assertTrue("Decoded buffer should contain non-zero audio samples", hasNonZero)
            } finally {
                decoder.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun testDecode24BitFlacWithNormalization() {
        val file = extractResourceToTemp("/audio/test_sine_24.flac", ".flac")
        try {
            val decoder = FlacAudioDecoder(file)
            try {
                assertEquals(96000.0f, decoder.format.sampleRate, 0.01f)
                assertEquals(16, decoder.format.sampleSizeInBits) // Normalized down to 16-bit
                assertEquals(2, decoder.format.channels)
                assertTrue("Duration should be positive", decoder.durationMs > 0)

                val buffer = ByteArray(4096)
                val bytesRead = decoder.readPcm(buffer, 0, buffer.size)
                assertTrue("Should read normalized bytes", bytesRead > 0)
                assertEquals(0, bytesRead % 4)

                var hasNonZero = false
                for (b in buffer) {
                    if (b != 0.toByte()) {
                        hasNonZero = true
                        break
                    }
                }
                assertTrue("Decoded 24-bit buffer should contain non-zero samples", hasNonZero)
            } finally {
                decoder.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun testFlacSeeking() {
        val file = extractResourceToTemp("/audio/test_sine_16.flac", ".flac")
        try {
            val decoder = FlacAudioDecoder(file)
            try {
                assertTrue("Seek to 200ms should succeed", decoder.seekTo(200L))

                val buffer = ByteArray(2048)
                val read = decoder.readPcm(buffer, 0, buffer.size)
                assertTrue("Should read samples after seeking", read > 0)
            } finally {
                decoder.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun testCloseSafety() {
        val file = extractResourceToTemp("/audio/test_sine_16.flac", ".flac")
        try {
            val decoder = FlacAudioDecoder(file)
            decoder.close()
            // Multiple calls must be safe (idempotent)
            decoder.close()

            val buf = ByteArray(1024)
            assertEquals(-1, decoder.readPcm(buf, 0, buf.size))
            assertFalse(decoder.seekTo(100L))
        } finally {
            file.delete()
        }
    }

    @Test
    fun testCorruptFlacInitializationThrowsGracefully() {
        val corruptFile = File.createTempFile("invalid_flac_", ".flac")
        corruptFile.deleteOnExit()
        FileOutputStream(corruptFile).use { it.write("fLaC_CORRUPTED_GARBAGE_DATA".toByteArray()) }

        try {
            var caught = false
            try {
                val decoder = FlacAudioDecoder(corruptFile)
                decoder.close()
            } catch (_: IOException) {
                caught = true
            } catch (_: IllegalArgumentException) {
                caught = true
            } catch (_: Exception) {
                caught = true
            }
            assertTrue("Corrupt FLAC file must throw an exception on decoder init", caught)
        } finally {
            corruptFile.delete()
        }
    }

    @Test
    fun testCorruptedNativeDllAutoRestoration() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "lyrune_flac_dll_test_${System.nanoTime()}").apply { mkdirs() }
        try {
            val dllFile = File(tempDir, "libFLAC.dll")

            // 1. Initial extraction into tempDir
            val extracted = FlacNativeLibrary.resolveOrExtractDll(customTargetDir = tempDir)
            assertTrue("Extracted DLL must exist in test dir", extracted.isFile)
            assertEquals(FlacNativeLibrary.EXPECTED_SHA256.lowercase(), FlacNativeLibrary.computeSha256(extracted).lowercase())

            // 2. Intentionally corrupt the DLL on disk with garbage
            dllFile.writeBytes("CORRUPTED_DLL_GARBAGE_PAYLOAD".toByteArray())
            val corruptedHash = FlacNativeLibrary.computeSha256(dllFile)
            assertTrue("Corrupted hash must differ from expected", !corruptedHash.equals(FlacNativeLibrary.EXPECTED_SHA256, ignoreCase = true))

            // 3. Resolve/load: it must detect the corruption, discard the bad file, and restore the authentic DLL
            val healedDll = FlacNativeLibrary.resolveOrExtractDll(customTargetDir = tempDir)
            assertTrue("Healed DLL must exist", healedDll.isFile)
            val healedHash = FlacNativeLibrary.computeSha256(healedDll)
            assertEquals("Healed DLL must match trusted SHA-256", FlacNativeLibrary.EXPECTED_SHA256.lowercase(), healedHash.lowercase())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testBufferHardUpperBoundsEnforced() {
        assertEquals(65535, FlacAudioDecoder.MAX_FLAC_BLOCK_SIZE)
        assertEquals(1024 * 1024, FlacAudioDecoder.MAX_BUFFER_CAPACITY)
    }
}
