package dev.auralis.studio.audio.stems

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/** Exact host-side contract for UVR-MDX-NET-Voc_FT. Do not reuse 9482 dimensions here. */
object VocFtContract {
    const val SAMPLE_RATE_HZ = 44_100
    const val DIM_T = 256
    const val HOP = 1_024
    const val DIM_F = 3_072
    const val N_FFT = 6_144
    const val CENTER_PAD = N_FFT / 2
    const val CHUNK_SIZE = HOP * (DIM_T - 1)
    const val GENERATED_SIZE = CHUNK_SIZE - 2 * CENTER_PAD
    const val OVERLAP = 0.10f
    val STRIDE: Int = (GENERATED_SIZE * (1f - OVERLAP)).roundToInt().coerceIn(1, GENERATED_SIZE)
    const val TENSOR_FLOATS = 4 * DIM_F * DIM_T

    fun tensorIndex(plane: Int, bin: Int, frame: Int): Int =
        ((plane * DIM_F) + bin) * DIM_T + frame
}

data class StereoPcm(val left: FloatArray, val right: FloatArray) {
    init { require(left.size == right.size) }
}

internal fun periodicHann(size: Int): FloatArray = FloatArray(size) { index ->
    (0.5 * (1.0 - cos(2.0 * PI * index / size))).toFloat()
}

/**
 * Centered, reflect-padded, unnormalised STFT matching the upstream Python oracle.
 * Output plane order is [L_re, L_im, R_re, R_im], with the Nyquist bin omitted.
 */
class VocFtStft {
    private val fft = FloatFFT_1D(VocFtContract.N_FFT.toLong())
    private val hann = periodicHann(VocFtContract.N_FFT)
    private val complex = FloatArray(VocFtContract.N_FFT * 2)

    fun transform(left: FloatArray, right: FloatArray, output: FloatArray = FloatArray(VocFtContract.TENSOR_FLOATS)): FloatArray {
        require(left.size == VocFtContract.CHUNK_SIZE && right.size == VocFtContract.CHUNK_SIZE)
        require(output.size == VocFtContract.TENSOR_FLOATS)
        transformChannel(left, 0, 1, output)
        transformChannel(right, 2, 3, output)
        return output
    }

    private fun transformChannel(samples: FloatArray, realPlane: Int, imaginaryPlane: Int, output: FloatArray) {
        repeat(VocFtContract.DIM_T) { frame ->
            complex.fill(0f)
            val frameStart = frame * VocFtContract.HOP - VocFtContract.CENTER_PAD
            repeat(VocFtContract.N_FFT) { offset ->
                val sampleIndex = reflectIndex(frameStart + offset, samples.size)
                complex[offset * 2] = samples[sampleIndex] * hann[offset]
            }
            fft.complexForward(complex)
            repeat(VocFtContract.DIM_F) { bin ->
                output[VocFtContract.tensorIndex(realPlane, bin, frame)] = complex[bin * 2]
                output[VocFtContract.tensorIndex(imaginaryPlane, bin, frame)] = complex[bin * 2 + 1]
            }
        }
    }

    private fun reflectIndex(index: Int, size: Int): Int {
        var reflected = index
        while (reflected < 0 || reflected >= size) {
            reflected = if (reflected < 0) -reflected else (2 * size - 2) - reflected
        }
        return reflected
    }
}

/** Inverse of [VocFtStft], including synthesis Hann, envelope division, and center trimming. */
class VocFtIstft {
    private val fft = FloatFFT_1D(VocFtContract.N_FFT.toLong())
    private val hann = periodicHann(VocFtContract.N_FFT)
    private val complex = FloatArray(VocFtContract.N_FFT * 2)
    private val overlapLength = (VocFtContract.DIM_T - 1) * VocFtContract.HOP + VocFtContract.N_FFT
    private val inverseEnvelope = buildInverseEnvelope()

    // Preallocated reusable buffers to avoid ~4.2 MB heap allocation per chunk
    private val overlapAddLeft = FloatArray(overlapLength)
    private val overlapAddRight = FloatArray(overlapLength)
    private val outputLeft = FloatArray(VocFtContract.CHUNK_SIZE)
    private val outputRight = FloatArray(VocFtContract.CHUNK_SIZE)
    private val pcmResult = StereoPcm(outputLeft, outputRight)

    fun inverse(spectrum: FloatArray): StereoPcm {
        require(spectrum.size == VocFtContract.TENSOR_FLOATS)
        inverseChannel(spectrum, 0, 1, overlapAddLeft, outputLeft)
        inverseChannel(spectrum, 2, 3, overlapAddRight, outputRight)
        return pcmResult
    }

    private fun inverseChannel(
        spectrum: FloatArray,
        realPlane: Int,
        imaginaryPlane: Int,
        overlapAdd: FloatArray,
        output: FloatArray,
    ) {
        overlapAdd.fill(0f)
        repeat(VocFtContract.DIM_T) { frame ->
            complex.fill(0f)
            repeat(VocFtContract.DIM_F) { bin ->
                val re = spectrum[VocFtContract.tensorIndex(realPlane, bin, frame)]
                val im = spectrum[VocFtContract.tensorIndex(imaginaryPlane, bin, frame)]
                complex[bin * 2] = re
                complex[bin * 2 + 1] = im
                if (bin > 0) {
                    val mirror = VocFtContract.N_FFT - bin
                    complex[mirror * 2] = re
                    complex[mirror * 2 + 1] = -im
                }
            }
            // Nyquist remains exactly zero, matching the upstream cropped spectrum.
            fft.complexInverse(complex, true)
            val start = frame * VocFtContract.HOP
            repeat(VocFtContract.N_FFT) { offset ->
                overlapAdd[start + offset] += complex[offset * 2] * hann[offset]
            }
        }
        for (index in 0 until VocFtContract.CHUNK_SIZE) {
            output[index] = overlapAdd[VocFtContract.CENTER_PAD + index] * inverseEnvelope[index]
        }
    }

    private fun buildInverseEnvelope(): FloatArray {
        val envelope = FloatArray(overlapLength)
        repeat(VocFtContract.DIM_T) { frame ->
            val start = frame * VocFtContract.HOP
            repeat(VocFtContract.N_FFT) { offset ->
                val value = hann[offset]
                envelope[start + offset] += value * value
            }
        }
        return FloatArray(VocFtContract.CHUNK_SIZE) { index ->
            1f / (envelope[VocFtContract.CENTER_PAD + index] + 1e-8f)
        }
    }
}

internal fun buildMdxCrossfadeWindow(): FloatArray {
    val overlapLength = VocFtContract.GENERATED_SIZE - VocFtContract.STRIDE
    if (overlapLength <= 0) return FloatArray(VocFtContract.GENERATED_SIZE) { 1f }
    val denominator = (overlapLength + 1).toFloat()
    return FloatArray(VocFtContract.GENERATED_SIZE) { index ->
        minOf(1f, (index + 1f) / denominator, (VocFtContract.GENERATED_SIZE - index) / denominator)
    }
}

