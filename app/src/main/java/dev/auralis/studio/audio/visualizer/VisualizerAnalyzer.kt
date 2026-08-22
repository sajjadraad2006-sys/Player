package dev.auralis.studio.audio.visualizer

import dev.auralis.studio.audio.dsp.StudioAudioProcessor
import dev.auralis.studio.core.model.VisualQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow

/**
 * Reads the PCM ring buffer from [StudioAudioProcessor], runs a real FFT on a
 * background coroutine, and emits downsampled band magnitudes as [StateFlow].
 *
 * Design rules (from spec):
 * - NEVER allocates inside the hot analysis loop (all arrays pre-allocated).
 * - NEVER modifies DSP parameters.
 * - Drops analysis frames when UI is not consuming fast enough — audio is unaffected.
 * - Respects [VisualQuality] tiers for target FPS and band count.
 */
class VisualizerAnalyzer(
    private val processor: StudioAudioProcessor,
) {
    // ── Configuration ────────────────────────────────────────────────────────
    private val fftSize = 2048
    private var bandCount = 48            // number of output bands (adjustable per quality)
    private var targetIntervalMs = 22L    // ~45 fps default (Balanced)

    // ── Pre-allocated buffers (never re-allocated during playback) ───────────
    private val window   = FloatArray(fftSize) { i -> hanningWindow(i, fftSize) }
    private val real     = FloatArray(fftSize)
    private val imag     = FloatArray(fftSize)
    private val bandMags = FloatArray(64)  // max output bands (64 ≥ any quality tier)
    private val smooth   = FloatArray(64)  // exponential smoothing accumulators

    // ── Output state ─────────────────────────────────────────────────────────
    /** Emits a snapshot of magnitude bands (0..1 normalised, smoothed). */
    private val _bands = MutableStateFlow(FloatArray(48))
    val bands: StateFlow<FloatArray> = _bands.asStateFlow()

    /** Emits raw waveform samples for wave-style renderers (downsampled). */
    private val _waveform = MutableStateFlow(FloatArray(128))
    val waveform: StateFlow<FloatArray> = _waveform.asStateFlow()

    // ── Lifecycle ────────────────────────────────────────────────────────────
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var analysisJob: Job? = null
    private var lastReadPos = 0

    fun start(quality: VisualQuality = VisualQuality.BALANCED) {
        applyQuality(quality)
        if (analysisJob?.isActive == true) return
        analysisJob = scope.launch { analysisLoop() }
    }

    fun stop() {
        analysisJob?.cancel()
        analysisJob = null
        // Clear output so canvas shows silence
        _bands.value = FloatArray(bandCount)
        _waveform.value = FloatArray(128)
        smooth.fill(0f)
    }

    fun setQuality(quality: VisualQuality) {
        applyQuality(quality)
    }

    private fun applyQuality(quality: VisualQuality) {
        when (quality) {
            VisualQuality.REDUCED -> {
                bandCount       = 24
                targetIntervalMs = 40   // ~25 fps
            }
            VisualQuality.BALANCED -> {
                bandCount       = 48
                targetIntervalMs = 22   // ~45 fps
            }
            VisualQuality.FULL -> {
                bandCount       = 64
                targetIntervalMs = 16   // ~60 fps
            }
        }
    }

    // ── Analysis loop — runs entirely on Dispatchers.Default ─────────────────
    private suspend fun analysisLoop() {
        while (currentCoroutineContext().isActive) {
            val frameStart = System.currentTimeMillis()
            analyze()
            val elapsed = System.currentTimeMillis() - frameStart
            val wait = targetIntervalMs - elapsed
            if (wait > 0) delay(wait)
        }
    }

    private fun analyze() {
        // Grab a snapshot from the ring buffer — lock-free read
        val writePos = processor.visualizerWritePos
        val buf = processor.visualizerRingBuffer
        val bufSize = buf.size

        if (writePos == lastReadPos) return // no new data

        // Fill FFT real array with windowed samples from ring buffer (newest fftSize samples)
        val startPos = (writePos - fftSize + bufSize) and (bufSize - 1)
        for (i in 0 until fftSize) {
            real[i] = buf[(startPos + i) and (bufSize - 1)] * window[i]
        }
        imag.fill(0f)
        lastReadPos = writePos

        // In-place Cooley-Tukey FFT (iterative, no allocation)
        fftInPlace(real, imag, fftSize)

        // Compute log-spaced magnitude bands
        val binToHz = 44100f / fftSize.toFloat()
        val minFreq  = 20f
        val maxFreq  = 18000f
        val logMin   = log10(minFreq)
        val logMax   = log10(maxFreq)

        for (band in 0 until bandCount) {
            val freqLo = 10.0.pow((logMin + (logMax - logMin) * band / bandCount).toDouble()).toFloat()
            val freqHi = 10.0.pow((logMin + (logMax - logMin) * (band + 1) / bandCount).toDouble()).toFloat()
            val binLo  = (freqLo / binToHz).toInt().coerceIn(1, fftSize / 2 - 1)
            val binHi  = (freqHi / binToHz).toInt().coerceIn(binLo + 1, fftSize / 2)
            var sum = 0f
            var count = 0
            for (b in binLo until binHi) {
                val mag = sqrt(real[b] * real[b] + imag[b] * imag[b])
                sum += mag
                count++
            }
            val avg = if (count > 0) sum / count else 0f
            // Convert to dB, normalise to 0..1 range (roughly -80..0 dB → 0..1)
            val db = 20f * log10(max(avg, 1e-6f))
            val norm = ((db + 80f) / 80f).coerceIn(0f, 1f)
            bandMags[band] = norm
        }

        // Exponential smoothing (attack fast, release slow)
        val alpha = 0.35f
        val release = 0.85f
        for (band in 0 until bandCount) {
            smooth[band] = if (bandMags[band] > smooth[band]) {
                smooth[band] + (bandMags[band] - smooth[band]) * alpha
            } else {
                smooth[band] * release
            }
        }

        // Emit snapshot — copy to avoid sharing mutable state
        _bands.value = smooth.copyOf(bandCount)

        // Waveform: emit 128 evenly spaced samples from the ring buffer
        val wave = FloatArray(128)
        for (i in 0 until 128) {
            val idx = (writePos - 128 + i + bufSize) and (bufSize - 1)
            wave[i] = buf[idx]
        }
        _waveform.value = wave
    }

    // ── Iterative Cooley-Tukey FFT ────────────────────────────────────────────
    // No memory allocation. Works in-place on pre-allocated real/imag arrays.
    private fun fftInPlace(re: FloatArray, im: FloatArray, n: Int) {
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { re[i] = re[j].also { re[j] = re[i] }; im[i] = im[j].also { im[j] = im[i] } }
        }
        // FFT
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = (-2.0 * PI / len).toFloat()
            val wRe = cos(angle.toDouble()).toFloat()
            val wIm = sin(angle.toDouble()).toFloat()
            var pos = 0
            while (pos < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until halfLen) {
                    val uRe = re[pos + k]
                    val uIm = im[pos + k]
                    val vRe = re[pos + k + halfLen] * curRe - im[pos + k + halfLen] * curIm
                    val vIm = re[pos + k + halfLen] * curIm + im[pos + k + halfLen] * curRe
                    re[pos + k] = uRe + vRe; im[pos + k] = uIm + vIm
                    re[pos + k + halfLen] = uRe - vRe; im[pos + k + halfLen] = uIm - vIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                pos += len
            }
            len = len shl 1
        }
    }

    private fun hanningWindow(i: Int, n: Int): Float =
        (0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))).toFloat()

    private fun Double.pow(exp: Double): Double = Math.pow(this, exp)
}
