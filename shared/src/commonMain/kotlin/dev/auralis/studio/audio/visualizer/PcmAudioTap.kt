package dev.auralis.studio.audio.visualizer

/**
 * Lock-free, single-writer PCM tap shared by the playback service and analyzer.
 * Samples are post-DSP (or original PCM while bypassed) and retain the real
 * sample rate. The audio thread never waits for the visualizer.
 */
class PcmAudioTap(capacity: Int = 8192) {
    init { require(capacity > 0 && capacity.countOneBits() == 1) }

    val samples = FloatArray(capacity)
    @Volatile var writePosition: Long = 0L
        private set
    @Volatile var sampleRateHz: Int = 48_000
        private set

    fun configure(sampleRateHz: Int) {
        this.sampleRateHz = sampleRateHz.coerceAtLeast(1)
        clear()
    }

    fun write(monoSample: Float) {
        val position = writePosition
        samples[(position.toInt() and (samples.size - 1))] = monoSample.coerceIn(-1f, 1f)
        writePosition = position + 1L
    }

    fun clear() {
        samples.fill(0f)
        writePosition = 0L
    }
}
