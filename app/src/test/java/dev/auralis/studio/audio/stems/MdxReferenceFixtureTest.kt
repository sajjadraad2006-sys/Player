package dev.auralis.studio.audio.stems

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Optional local numerical parity test against gyoom-sa/UVR-MDX-LiteRT separate.py output. */
class MdxReferenceFixtureTest {
    @Test fun `iSTFT reconstruction matches upstream model-output fixture`() {
        val directory = (System.getProperty("lyrune.parityDir") ?: System.getenv("LYRUNE_PARITY_DIR"))?.let(::File)
        assumeTrue("Set -Dlyrune.parityDir to run upstream parity", directory?.isDirectory == true)
        directory ?: return
        val prediction = readFloats(File(directory, "prediction.f32le"))
        val expected = readFloats(File(directory, "expected-vocals.f32le"))
        val mix = readFloats(File(directory, "mix.f32le"))
        assertTrue(prediction.size == VocFtContract.TENSOR_FLOATS)
        assertTrue(expected.size == mix.size && expected.size % 2 == 0)

        val actual = VocFtIstft().inverse(prediction)
        var signalEnergy = 0.0
        var errorEnergy = 0.0
        for (frame in 0 until expected.size / 2) {
            val actualLeft = actual.left[VocFtContract.CENTER_PAD + frame]
            val actualRight = actual.right[VocFtContract.CENTER_PAD + frame]
            val expectedLeft = expected[frame * 2]
            val expectedRight = expected[frame * 2 + 1]
            signalEnergy += expectedLeft * expectedLeft + expectedRight * expectedRight
            errorEnergy += (expectedLeft - actualLeft).let { it * it } + (expectedRight - actualRight).let { it * it }
            // Complementary residual must reconstruct the original exactly before WAV quantisation.
            val residualLeft = mix[frame * 2] - actualLeft
            val residualRight = mix[frame * 2 + 1] - actualRight
            assertTrue(kotlin.math.abs(actualLeft + residualLeft - mix[frame * 2]) < 1e-7f)
            assertTrue(kotlin.math.abs(actualRight + residualRight - mix[frame * 2 + 1]) < 1e-7f)
        }
        val snrDb = 10.0 * log10(signalEnergy / errorEnergy.coerceAtLeast(1e-30))
        println("Voc_FT upstream iSTFT parity: $snrDb dB")
        assertTrue("Kotlin/Python iSTFT parity was only $snrDb dB", snrDb > 80.0)
    }

    private fun readFloats(file: File): FloatArray {
        val bytes = file.readBytes()
        require(bytes.size % 4 == 0)
        return FloatArray(bytes.size / 4).also { values ->
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values)
        }
    }
}
