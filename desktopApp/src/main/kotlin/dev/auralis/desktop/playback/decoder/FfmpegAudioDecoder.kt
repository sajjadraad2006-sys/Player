package dev.auralis.desktop.playback.decoder

import org.bytedeco.javacpp.Loader
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat

/** Pull-based PCM pipe. Backpressure bounds both native and JVM buffering; no media download/cache. */
class FfmpegAudioDecoder(
    private val uri: String,
    override val durationMs: Long = 0,
    private val headers: Map<String, String> = emptyMap(),
    private val audioConfig: dev.auralis.studio.core.model.AudioConfig = dev.auralis.studio.core.model.AudioConfig(),
) : DesktopAudioDecoder {
    override val format = AudioFormat(48_000f, 16, 2, true, false)
    @Volatile private var process: Process? = null
    @Volatile private var closed = false
    private var output: InputStream? = null

    private fun start(positionMs: Long = 0) {
        check(!closed)
        val args = mutableListOf(executable, "-nostdin", "-hide_banner", "-loglevel", "error", "-threads", "1")
        val remote = uri.startsWith("https://") || uri.startsWith("http://")
        if (remote) {
            args += listOf("-rw_timeout", "15000000", "-protocol_whitelist", "http,https,tcp,tls,crypto")
            val safe = dev.auralis.studio.resolver.api.SecretSanitizer.extractSafeHeaders(headers)
                .filter { (k, v) -> !k.contains('\r') && !k.contains('\n') && !v.contains('\r') && !v.contains('\n') }
            if (safe.isNotEmpty()) args += listOf("-headers", safe.entries.joinToString("") { "${it.key}: ${it.value}\r\n" })
        }
        if (positionMs > 0) args += listOf("-ss", "${positionMs / 1000.0}")
        args += listOf("-i", uri, "-map", "0:a:0", "-vn", "-sn", "-dn", "-threads", "1")
        tempoFilter(audioConfig).takeIf(String::isNotBlank)?.let { args += listOf("-af", it) }
        args += listOf("-ac", "2", "-ar", "48000", "-f", "s16le", "pipe:1")
        val child = ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        synchronized(this) {
            if (closed) { child.destroyForcibly(); throw IOException("Playback cancelled") }
            process = child
            output = child.inputStream
        }
    }

    override fun readPcm(target: ByteArray, offset: Int, length: Int): Int {
        if (closed) return -1
        if (process == null) start()
        // PCM frames must remain aligned even when an OS pipe returns a short read.
        val count = output!!.readNBytes(target, offset, length - length % 4)
        if (count == 0) {
            val child = process ?: return -1
            if (!child.waitFor(2, TimeUnit.SECONDS) || child.exitValue() != 0) throw IOException("Audio stream interrupted")
            return -1
        }
        return count - count % 4
    }

    override fun seekTo(positionMs: Long): Boolean {
        if (closed) return false
        stopChild()
        start(positionMs.coerceAtLeast(0))
        return true
    }

    private fun stopChild() {
        process?.destroyForcibly()
        runCatching { output?.close() }
        process = null
        output = null
    }

    @Synchronized override fun close() { closed = true; stopChild() }

    companion object {
        fun tempoFilter(config: dev.auralis.studio.core.model.AudioConfig): String {
            if(!config.dspEnabled) return ""
            val pitch = config.effectivePitchFactor.toDouble()
            var tempo = config.speed.toDouble().coerceIn(.25, 3.0) / pitch
            val filters = mutableListOf("aresample=48000", "asetrate=${48000 * pitch}", "aresample=48000")
            while(tempo < .5) { filters += "atempo=0.5"; tempo /= .5 }
            while(tempo > 2) { filters += "atempo=2.0"; tempo /= 2 }
            filters += "atempo=$tempo"
            return if(pitch == 1.0 && tempo == 1.0) "" else filters.joinToString(",")
        }
        val executable: String by lazy { Loader.load(org.bytedeco.ffmpeg.ffmpeg::class.java) }
    }
}
