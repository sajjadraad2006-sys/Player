package dev.auralis.studio.audio.stems

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import org.tensorflow.lite.Interpreter
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil

object VocFtModel {
    const val REVISION = "cfcf0bb8ae993732a23691b94cf05fa3fcf3d603"
    const val FILE_NAME = "UVR-MDX-NET-Voc_FT.fp16acc.tflite"
    const val DOWNLOAD_URL =
        "https://huggingface.co/gyoom-sa/UVR-MDX-LiteRT/resolve/$REVISION/$FILE_NAME"

    val descriptor = StemEngineDescriptor(
        engineId = "uvr-mdx-litert",
        modelId = "UVR-MDX-NET-Voc_FT.fp16acc",
        modelVersion = REVISION.take(12),
        supportedStems = setOf(StemKind.VOCALS, StemKind.INSTRUMENTAL),
        modelBytes = 66_848_828L,
        modelSha256 = "5ef47e3b3bafa14357532c0a3f6c5f18444d94b6efe3fd62b3d13f80051f1e58",
        license = "MIT (Gyoom / upstream UVR MDX-Net contributors)",
    )
}

/**
 * Real UVR-MDX-NET-Voc_FT inference. Decode, STFT, LiteRT and file IO all run away from playback.
 * CPU/XNNPACK is deliberately the production baseline until delegate parity is measured on devices.
 */
class VocFtLiteRtEngine(
    private val modelManager: StemModelManager,
    private val decoder: OfflineAudioDecoder = OfflineAudioDecoder(),
) : StemSeparationEngine {
    override val descriptor: StemEngineDescriptor = VocFtModel.descriptor
    private val cancelled = AtomicBoolean(false)
    @Volatile private var activeInterpreter: Interpreter? = null

    override suspend fun availability(): StemModelAvailability = modelManager.availability(descriptor)

    override fun cancel() {
        cancelled.set(true)
        activeInterpreter?.setCancelled(true)
    }

    override suspend fun separate(
        request: StemSeparationRequest,
        onProgress: (Int) -> Unit,
    ): StemSeparationResult = withContext(Dispatchers.Default) {
        cancelled.set(false)
        val installed = availability() as? StemModelAvailability.Installed
            ?: error("Voc_FT model is not installed and verified")
        val cacheKey = StemCacheIdentity.create(request.sourceIdentity, descriptor, "voc-ft-oracle-v1")
        val outputDirectory = File(request.outputDirectory, cacheKey)
        val vocals = File(outputDirectory, "vocals.wav")
        val instrumental = File(outputDirectory, "instrumental.wav")
        val complete = File(outputDirectory, ".complete")
        if (complete.isFile && vocals.isFile && instrumental.isFile && vocals.length() > 44L && instrumental.length() > 44L) {
            return@withContext StemSeparationResult(
                cacheKey = cacheKey,
                files = mapOf(StemKind.VOCALS to vocals, StemKind.INSTRUMENTAL to instrumental),
                sampleRateHz = VocFtContract.SAMPLE_RATE_HZ,
                durationFrames = (vocals.length() - 44L) / 4L,
                metadata = mapOf("accelerator" to "CPU/XNNPACK", "cache" to "hit"),
            )
        }

        outputDirectory.mkdirs()
        complete.delete()
        vocals.delete()
        instrumental.delete()
        val work = File(outputDirectory, ".work").apply { deleteRecursively(); mkdirs() }
        try {
            checkCancellation()
            val decoded = decoder.decode(request.sourceFile, work, onProgress)
            checkCancellation()
            val result = runModel(
                modelFile = installed.file,
                decoded = decoded,
                vocals = vocals,
                instrumental = instrumental,
                work = work,
                onProgress = onProgress,
            )
            complete.writeText(
                "engine=${descriptor.engineId}\nmodel=${descriptor.modelId}\nversion=${descriptor.modelVersion}\n" +
                    "source=${request.sourceIdentity}\nframes=${decoded.frames}\naccelerator=CPU/XNNPACK\n",
            )
            onProgress(100)
            result.copy(cacheKey = cacheKey)
        } catch (error: Throwable) {
            vocals.delete()
            instrumental.delete()
            complete.delete()
            if (error is CancellationException) throw error
            throw error
        } finally {
            work.deleteRecursively()
        }
    }

    private suspend fun runModel(
        modelFile: File,
        decoded: DecodedPcmFile,
        vocals: File,
        instrumental: File,
        work: File,
        onProgress: (Int) -> Unit,
    ): StemSeparationResult {
        val sampleCount = decoded.frames
        require(sampleCount > 0L)
        val chunks = if (sampleCount <= VocFtContract.GENERATED_SIZE) 1 else
            ceil((sampleCount - VocFtContract.GENERATED_SIZE).toDouble() / VocFtContract.STRIDE).toInt() + 1
        val outputFrames = (chunks - 1L) * VocFtContract.STRIDE + VocFtContract.GENERATED_SIZE
        val accumulatorFile = File(work, "prediction-accumulator.f32le")
        PredictionAccumulator(accumulatorFile, outputFrames).use { accumulator ->
            val model = Interpreter(
                modelFile,
                Interpreter.Options()
                    .setUseXNNPACK(true)
                    .setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
                    .setCancellable(true),
            )
            activeInterpreter = model
            try {
                require(model.inputTensorCount == 1 && model.outputTensorCount == 1) {
                    "Voc_FT must expose exactly one input and output"
                }
                val expectedShape = intArrayOf(1, 4, VocFtContract.DIM_F, VocFtContract.DIM_T)
                require(model.getInputTensor(0).shape().contentEquals(expectedShape)) {
                    "Unexpected Voc_FT input shape: ${model.getInputTensor(0).shape().contentToString()}"
                }
                require(model.getOutputTensor(0).shape().contentEquals(expectedShape)) {
                    "Unexpected Voc_FT output shape: ${model.getOutputTensor(0).shape().contentToString()}"
                }
                val stft = VocFtStft()
                val istft = VocFtIstft()
                val left = FloatArray(VocFtContract.CHUNK_SIZE)
                val right = FloatArray(VocFtContract.CHUNK_SIZE)
                val tensor = FloatArray(VocFtContract.TENSOR_FLOATS)
                val prediction = FloatArray(VocFtContract.TENSOR_FLOATS)
                val inputBuffer = ByteBuffer.allocateDirect(VocFtContract.TENSOR_FLOATS * 4).order(ByteOrder.nativeOrder())
                val outputBuffer = ByteBuffer.allocateDirect(VocFtContract.TENSOR_FLOATS * 4).order(ByteOrder.nativeOrder())
                val window = buildMdxCrossfadeWindow()
                StereoFloatRandomAccess(decoded.file, "r").use { source ->
                    repeat(chunks) { chunkIndex ->
                        checkCancellation()
                        coroutineContext.ensureActive()
                        fillMixtureChunk(source, sampleCount, chunkIndex * VocFtContract.STRIDE.toLong(), left, right)
                        stft.transform(left, right, tensor)
                        inputBuffer.clear()
                        inputBuffer.asFloatBuffer().put(tensor)
                        outputBuffer.clear()
                        model.run(inputBuffer, outputBuffer)
                        outputBuffer.rewind()
                        outputBuffer.asFloatBuffer().get(prediction)
                        require(prediction.all(Float::isFinite)) { "Voc_FT produced NaN/Inf" }
                        val predictedPcm = istft.inverse(prediction)
                        accumulator.add(
                            startFrame = chunkIndex * VocFtContract.STRIDE.toLong(),
                            left = predictedPcm.left,
                            right = predictedPcm.right,
                            sourceOffset = VocFtContract.CENTER_PAD,
                            window = window,
                        )
                        onProgress(20 + (((chunkIndex + 1) * 70) / chunks).coerceIn(1, 70))
                    }
                }
            } finally {
                activeInterpreter = null
                model.close()
            }

            val outputGain = writeStemFiles(decoded, accumulator, vocals, instrumental, onProgress)
            return StemSeparationResult(
                cacheKey = "",
                files = mapOf(StemKind.VOCALS to vocals, StemKind.INSTRUMENTAL to instrumental),
                sampleRateHz = VocFtContract.SAMPLE_RATE_HZ,
                durationFrames = sampleCount,
                metadata = mapOf(
                    "accelerator" to "CPU/XNNPACK",
                    "model" to descriptor.modelId,
                    "tensor" to "[1,4,3072,256]",
                    "outputGain" to outputGain.toString(),
                ),
            )
        }
    }

    private fun fillMixtureChunk(
        source: StereoFloatRandomAccess,
        totalFrames: Long,
        outputStart: Long,
        left: FloatArray,
        right: FloatArray,
    ) {
        left.fill(0f); right.fill(0f)
        val sourceStart = outputStart - VocFtContract.CENTER_PAD
        val readableStart = maxOf(0L, sourceStart)
        val readableEnd = minOf(totalFrames, sourceStart + VocFtContract.CHUNK_SIZE)
        if (readableEnd <= readableStart) return
        val destinationOffset = (readableStart - sourceStart).toInt()
        val count = (readableEnd - readableStart).toInt()
        val readLeft = FloatArray(count)
        val readRight = FloatArray(count)
        source.readFrames(readableStart, readLeft, readRight)
        readLeft.copyInto(left, destinationOffset)
        readRight.copyInto(right, destinationOffset)
    }

    private suspend fun writeStemFiles(
        decoded: DecodedPcmFile,
        accumulator: PredictionAccumulator,
        vocals: File,
        instrumental: File,
        onProgress: (Int) -> Unit,
    ): Float {
        val outputGain = calculateHeadroomGain(decoded, accumulator)
        FloatStereoReader(decoded.file).use { original ->
            accumulator.reader().use { prediction ->
                Pcm16WavWriter(vocals).use { vocalWriter ->
                    Pcm16WavWriter(instrumental).use { instrumentalWriter ->
                        var frame = 0L
                        while (frame < decoded.frames) {
                            checkCancellation()
                            coroutineContext.ensureActive()
                            val mix = original.read() ?: error("Decoded PCM ended early")
                            val predicted = prediction.read() ?: error("Prediction accumulator ended early")
                            require(predicted.first.isFinite() && predicted.second.isFinite()) { "Invalid reconstructed stem" }
                            vocalWriter.write(predicted.first * outputGain, predicted.second * outputGain)
                            instrumentalWriter.write(
                                (mix.first - predicted.first) * outputGain,
                                (mix.second - predicted.second) * outputGain,
                            )
                            frame++
                            if (frame % 44_100L == 0L) {
                                onProgress(90 + ((frame * 9L) / decoded.frames).toInt().coerceIn(0, 9))
                            }
                        }
                    }
                }
            }
        }
        return outputGain
    }

    private suspend fun calculateHeadroomGain(decoded: DecodedPcmFile, accumulator: PredictionAccumulator): Float {
        var peak = 0f
        FloatStereoReader(decoded.file).use { original ->
            accumulator.reader().use { prediction ->
                var frame = 0L
                while (frame < decoded.frames) {
                    coroutineContext.ensureActive()
                    val mix = original.read() ?: error("Decoded PCM ended early")
                    val predicted = prediction.read() ?: error("Prediction accumulator ended early")
                    peak = maxOf(
                        peak,
                        kotlin.math.abs(predicted.first),
                        kotlin.math.abs(predicted.second),
                        kotlin.math.abs(mix.first - predicted.first),
                        kotlin.math.abs(mix.second - predicted.second),
                    )
                    frame++
                }
            }
        }
        return if (peak > 0.98f) 0.98f / peak else 1f
    }

    private fun checkCancellation() {
        if (cancelled.get()) throw CancellationException("Stem separation cancelled")
    }
}

/** Disk-backed weighted overlap-add: keeps long tracks out of the Java heap. */
private class PredictionAccumulator(private val file: File, frames: Long) : AutoCloseable {
    private val access = RandomAccessFile(file, "rw").apply { setLength(frames * 12L) }

    fun add(startFrame: Long, left: FloatArray, right: FloatArray, sourceOffset: Int, window: FloatArray) {
        val bytes = ByteArray(window.size * 12)
        access.seek(startFrame * 12L)
        access.readFully(bytes)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (index in window.indices) {
            val byteOffset = index * 12
            val weight = window[index]
            buffer.putFloat(byteOffset, buffer.getFloat(byteOffset) + left[sourceOffset + index] * weight)
            buffer.putFloat(byteOffset + 4, buffer.getFloat(byteOffset + 4) + right[sourceOffset + index] * weight)
            buffer.putFloat(byteOffset + 8, buffer.getFloat(byteOffset + 8) + weight)
        }
        access.seek(startFrame * 12L)
        access.write(bytes)
    }

    fun reader(): PredictionReader {
        access.fd.sync()
        return PredictionReader(file)
    }

    override fun close() = access.close()
}

private class PredictionReader(file: File) : AutoCloseable {
    private val input = BufferedInputStream(file.inputStream(), 256 * 1024)
    private val bytes = ByteArray(12)

    fun read(): Pair<Float, Float>? {
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count < 0) return if (offset == 0) null else error("Truncated prediction accumulator")
            offset += count
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val envelope = buffer.getFloat(8)
        return if (envelope <= 1e-8f) 0f to 0f else
            (buffer.getFloat(0) / envelope) to (buffer.getFloat(4) / envelope)
    }

    override fun close() = input.close()
}
