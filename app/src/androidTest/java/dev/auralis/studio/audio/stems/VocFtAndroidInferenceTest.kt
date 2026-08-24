package dev.auralis.studio.audio.stems

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Optional device/emulator contract test. The 64 MB model and licensed audio fixture are deliberately
 * not committed; place them at the normal private model path and files/vocft-test-input.wav.
 */
@RunWith(AndroidJUnit4::class)
class VocFtAndroidInferenceTest {
    @Test fun realVocFtInferenceWritesComplementaryPlayableStems() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = StemModelManager(File(context.filesDir, "stem_models"))
        val source = File(context.filesDir, "vocft-test-input.wav")
        assumeTrue("Voc_FT model fixture is not installed", manager.modelFile(VocFtModel.descriptor).isFile)
        assumeTrue("Audio fixture is not installed", source.isFile)

        val output = File(context.cacheDir, "vocft-android-test").apply { deleteRecursively(); mkdirs() }
        val result = VocFtLiteRtEngine(manager).separate(
            StemSeparationRequest("android-inference-fixture", source, output),
        ) { }
        val vocals = result.files.getValue(StemKind.VOCALS)
        val instrumental = result.files.getValue(StemKind.INSTRUMENTAL)
        assertTrue(vocals.isFile && vocals.length() > 44L)
        assertTrue(instrumental.isFile && instrumental.length() == vocals.length())
        assertEquals("CPU/XNNPACK", result.metadata["accelerator"])
        assertEquals("[1,4,3072,256]", result.metadata["tensor"])

        val vocalPcm = readWav16(vocals)
        val instrumentalPcm = readWav16(instrumental)
        assertNotEquals(vocalPcm.contentHashCode(), instrumentalPcm.contentHashCode())
        assertTrue(vocalPcm.any { kotlin.math.abs(it.toInt()) > 32 })
        assertTrue(instrumentalPcm.any { kotlin.math.abs(it.toInt()) > 32 })

        // Both files must be complementary outputs from one run, not unrelated predictions.
        val decodedDir = File(context.cacheDir, "vocft-android-test-decode").apply { deleteRecursively(); mkdirs() }
        val decoded = OfflineAudioDecoder().decode(source, decodedDir) { }
        val gain = result.metadata.getValue("outputGain").toFloat()
        FloatStereoReader(decoded.file).use { mix ->
            val framesToCheck = minOf(decoded.frames.toInt(), vocalPcm.size / 2, 44_100)
            var absoluteError = 0.0
            repeat(framesToCheck) { frame ->
                val original = mix.read()!!
                val sumLeft = (vocalPcm[frame * 2].toInt() + instrumentalPcm[frame * 2].toInt()) / 32_767f
                val sumRight = (vocalPcm[frame * 2 + 1].toInt() + instrumentalPcm[frame * 2 + 1].toInt()) / 32_767f
                absoluteError += kotlin.math.abs(sumLeft - original.first * gain)
                absoluteError += kotlin.math.abs(sumRight - original.second * gain)
            }
            assertTrue("complementary residual error was ${absoluteError / (framesToCheck * 2)}", absoluteError / (framesToCheck * 2) < 0.00012)
        }
    }

    private fun readWav16(file: File): ShortArray = RandomAccessFile(file, "r").use { wav ->
        wav.seek(40)
        val dataBytes = readIntLe(wav)
        val output = ShortArray(dataBytes / 2)
        repeat(output.size) { index ->
            val low = wav.readUnsignedByte()
            val high = wav.readUnsignedByte()
            output[index] = (low or (high shl 8)).toShort()
        }
        output
    }

    private fun readIntLe(input: RandomAccessFile): Int =
        input.readUnsignedByte() or
            (input.readUnsignedByte() shl 8) or
            (input.readUnsignedByte() shl 16) or
            (input.readUnsignedByte() shl 24)
}
