package dev.auralis.studio.audio.visualizer

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
    private val tap: PcmAudioTap,
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
    private var lastReadPos = -1L
    private var lastAnalysisNanos = 0L

    fun start(quality: VisualQuality = VisualQuality.BALANCED) {
        applyQuality(quality)
        if (analysisJob?.isActive == true) return
        lastAnalysisNanos = 0L
        analysisJob = scope.launch { analysisLoop() }
    }

    fun stop() {
        analysisJob?.cancel()
        analysisJob = null
        // Clear output so canvas shows silence
        _bands.value = FloatArray(bandCount)
        _waveform.value = FloatArray(128)
        smooth.fill(0f)
        lastAnalysisNanos = 0L
    }

    fun setQuality(quality: VisualQuality) {
        applyQuality(quality)
    }

    private fun applyQuality(quality: VisualQuality) {
        when (quality) {
            VisualQuality.REDUCED -> {
                bandCount       = 48
                targetIntervalMs = 35   // ~30 fps
            }
            VisualQuality.BALANCED -> {
                bandCount       = 48
                targetIntervalMs = 22   // ~45 fps
            }
            VisualQuality.FULL -> {
                bandCount       = 48
                targetIntervalMs = 16   // ~60 fps
            }
        }
    }

    // ── Analysis loop — runs entirely on Dispatchers.Default ─────────────────
    private suspend fun analysisLoop() {
        while (currentCoroutineContext().isActive) {
            val frameStart = System.currentTimeMillis()
            analyzeFrame()
            val elapsed = System.currentTimeMillis() - frameStart
            val wait = targetIntervalMs - elapsed
            if (wait > 0) delay(wait)
        }
    }

    internal fun analyzeFrame() {
        val nowNanos = System.nanoTime()
        val deltaSeconds = if (lastAnalysisNanos == 0L) {
            targetIntervalMs / 1_000f
        } else {
            ((nowNanos - lastAnalysisNanos) / 1_000_000_000f).coerceIn(0.001f, 0.25f)
        }
        lastAnalysisNanos = nowNanos
        // Grab a snapshot from the ring buffer — lock-free read
        val writePos = tap.writePosition
        val buf = tap.samples
        val bufSize = buf.size

        if (writePos == lastReadPos) {
            decayWithoutAudio(deltaSeconds)
            return
        }

        // Fill FFT real array with windowed samples from ring buffer (newest fftSize samples)
        val startPos = writePos - fftSize
        for (i in 0 until fftSize) {
            real[i] = buf[((startPos + i).toInt() and (bufSize - 1))] * window[i]
        }
        imag.fill(0f)
        lastReadPos = writePos

        // In-place Cooley-Tukey FFT (iterative, no allocation)
        fftInPlace(real, imag, fftSize)

        // Compute log-spaced magnitude bands
        val sampleRate = tap.sampleRateHz.toFloat()
        val binToHz = sampleRate / fftSize.toFloat()
        val minFreq  = 20f
        val maxFreq  = minOf(16_500f, sampleRate * 0.45f).coerceAtLeast(minFreq * 2f)
        val logMin   = log10(minFreq)
        val logMax   = log10(maxFreq)

        for (band in 0 until bandCount) {
            val freqLo = 10.0.pow((logMin + (logMax - logMin) * band / bandCount).toDouble()).toFloat()
            val freqHi = 10.0.pow((logMin + (logMax - logMin) * (band + 1) / bandCount).toDouble()).toFloat()
            val binLo  = (freqLo / binToHz).toInt().coerceIn(1, fftSize / 2 - 1)
            val binHi  = (freqHi / binToHz).toInt().coerceIn(binLo + 1, fftSize / 2)
            var peak = 0f
            var sum = 0f
            val count = maxOf(1, binHi - binLo)
            for (b in binLo until binHi) {
                val amplitude = 2f * sqrt(real[b] * real[b] + imag[b] * imag[b]) / WINDOW_SUM
                if (amplitude > peak) peak = amplitude
                sum += amplitude
            }
            val avg = sum / count
            // Combine peak transient with sustained spectral energy for rich responsiveness
            val energy = max(peak, avg * 1.6f)

            // Equal-loudness / pink noise tilt compensation: music rolls off at ~3-4dB per octave,
            // so we progressively boost high bands so Treble and Air dance dynamically alongside Bass.
            val bandFraction = band.toFloat() / (bandCount - 1).coerceAtLeast(1)
            val tiltDb = bandFraction * 26.0f

            val db = 20f * log10(max(energy, 0.00000002f)) + tiltDb
            val norm = if (energy < 0.00000005f) 0f else ((db + 76f) / 76f).coerceIn(0f, 1f)
            // A gentle perceptual curve expands low-level musical detail. Zero stays zero.
            bandMags[band] = if (norm <= 0f) 0f else norm.pow(0.75f)
        }

        // Time-correct analysis smoothing: fast transient attack, musical release.
        for (band in 0 until bandCount) {
            smooth[band] = TemporalSmoothing.next(
                current = smooth[band],
                target = bandMags[band],
                deltaSeconds = deltaSeconds,
                attackSeconds = 0.040f,
                releaseSeconds = 0.190f,
            )
        }

        // Emit snapshot — copy to avoid sharing mutable state
        _bands.value = smooth.copyOf(bandCount)

        // Waveform: emit 128 evenly spaced samples from the ring buffer
        val wave = FloatArray(128)
        for (i in 0 until 128) {
            val idx = ((writePos - 128 + i).toInt() and (bufSize - 1))
            wave[i] = buf[idx]
        }
        _waveform.value = wave
    }

    private fun decayWithoutAudio(deltaSeconds: Float) {
        var changed = false
        for (band in 0 until bandCount) {
            val nextValue = TemporalSmoothing.next(smooth[band], 0f, deltaSeconds, 0.04f, 0.16f)
            val next = if (nextValue < 0.002f) 0f else nextValue
            changed = changed || next != smooth[band]
            smooth[band] = next
        }
        if (changed) _bands.value = smooth.copyOf(bandCount)
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

    private companion object {
        // Hann window sum for fftSize=2048. Kept explicit to avoid hot-loop work.
        const val WINDOW_SUM = 1023.5f
    }
}
