package dev.auralis.desktop.playback

import dev.auralis.desktop.playback.decoder.*
import dev.auralis.desktop.playback.flac.FlacAudioDecoder
import dev.auralis.desktop.library.DesktopTrack
import dev.auralis.studio.audio.dsp.StudioDspEngine
import dev.auralis.studio.playback.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.*
import kotlin.math.log10

/** One session owns transport, metadata and timeline. Obsolete workers cannot publish state. */
class DesktopPlaybackEngine : PlaybackEngine {
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    override val dspEngine = StudioDspEngine()
    private val lock = Object()
    private var generation = 0L
    @Volatile private var config = dev.auralis.studio.core.model.AudioConfig()
    val canResume: Boolean get() = active?.let { !it.cancelled } == true
    @Volatile private var active: Session? = null
    private class Session(val generation: Long, val uri: String, val duration: Long, val headers: Map<String, String>, val config: dev.auralis.studio.core.model.AudioConfig) {
        @Volatile var cancelled = false
        @Volatile var paused = false
        @Volatile var decoder: DesktopAudioDecoder? = null
        @Volatile var line: SourceDataLine? = null
        val seek = AtomicLong(-1)
        fun close() {
            cancelled = true
            runCatching { line?.stop(); line?.flush(); line?.close() }
            runCatching { decoder?.close() }
        }
    }
    private fun publish(session: Session, change: (PlaybackState) -> PlaybackState) = synchronized(lock) {
        if (active === session && !session.cancelled) mutableState.update(change)
    }

    /** Start resolution with a clean timeline; return a token to reject late results. */
    fun prepare(track: DesktopTrack): Long = synchronized(lock) {
        retire()
        val token = ++generation
        mutableState.value = PlaybackState(
            completedSessions = state.value.completedSessions,
            currentUri = track.path,
            metadata = PlaybackMetadata(track.title, track.artist, track.album, track.artworkKey),
            durationMs = track.durationMs.coerceAtLeast(0),
            volume = state.value.volume, speed = if(config.dspEnabled) config.speed else 1f, isBuffering = true,
        )
        token
    }

    fun playTrack(track: DesktopTrack, startPositionMs: Long = 0) {
        val token = prepare(track)
        playPrepared(token, track.path, track.durationMs, startMs = startPositionMs)
    }

    fun failPrepared(token: Long, message: String) = synchronized(lock) {
        if (token != generation) return@synchronized
        retire()
        mutableState.update { it.copy(isPlaying = false, isBuffering = false, currentUri = null,
            metadata = null, durationMs = 0, currentPositionMs = 0, errorMessage = message) }
    }

    fun playPrepared(token: Long, uri: String, durationMs: Long, headers: Map<String, String> = emptyMap(), startMs: Long = 0) = synchronized(lock) {
        if (token != generation) return@synchronized
        val session = Session(token, uri, durationMs, headers, config)
        active = session
        session.seek.set(startMs.takeIf { it > 0 } ?: -1)
        mutableState.update { it.copy(durationMs = durationMs.coerceAtLeast(0), currentPositionMs = 0, isBuffering = true) }
        Thread({ runSession(session, uri, durationMs, headers) }, "Lyrune-Audio-$token").apply { isDaemon = true; start() }
    }

    override fun play(uri: String, startPositionMs: Long) {
        val track = DesktopTrack(0, uri, File(uri).nameWithoutExtension, "")
        val token = prepare(track)
        if(uri.startsWith("http://") || uri.startsWith("https://")) {
            playPrepared(token, uri, 0, startMs = startPositionMs)
        } else Thread({
            val info = runCatching { dev.auralis.desktop.library.DesktopTrackMetadata.read(File(uri)) }.getOrDefault(track)
            synchronized(lock) {
                if(token == generation) {
                    mutableState.update { it.copy(metadata = PlaybackMetadata(info.title, info.artist, info.album, info.artworkKey)) }
                    playPrepared(token, uri, info.durationMs, startMs = startPositionMs)
                }
            }
        }, "Lyrune-Open-Music").apply { isDaemon = true; start() }
    }
    override fun pause() = synchronized(lock) {
        active?.let { session ->
            session.paused = true
            session.line?.stop()
            mutableState.update { it.copy(isPlaying = false) }
        }
        Unit
    }
    override fun resume() = synchronized(lock) {
        active?.takeUnless { it.cancelled }?.let { session ->
            session.paused = false
            session.line?.start()
            mutableState.update { it.copy(isPlaying = session.line != null && !it.isBuffering) }
            lock.notifyAll()
        }
        Unit
    }
    override fun seekTo(positionMs: Long) = synchronized(lock) {
        val duration = state.value.durationMs
        if (duration > 0) active?.seek?.set(positionMs.coerceIn(0, duration))
        lock.notifyAll()
    }
    private fun retire() {
        val previous = active
        active = null
        previous?.cancelled = true
        lock.notifyAll()
        // Closing a blocked network decoder must never block the Compose event thread.
        if (previous != null) Thread({ previous.close() }, "Lyrune-Audio-Close").apply { isDaemon = true; start() }
    }
    override fun stop() = synchronized(lock) {
        ++generation
        retire()
        mutableState.update { PlaybackState(volume = it.volume, speed = it.speed, completedSessions = it.completedSessions) }
    }
    override fun setVolume(volume: Float) {
        mutableState.update { it.copy(volume = volume.coerceIn(0f, 1f)) }
        active?.line?.let { applyVolume(it) }
    }
    override fun setSpeed(speed: Float) = updateAudioConfig(config.copy(speed = speed.coerceIn(.25f, 3f)))
    fun updateAudioConfig(next: dev.auralis.studio.core.model.AudioConfig) = synchronized(lock) {
        val previous = config
        config = next
        dspEngine.updateConfig(next)
        val speed = if(next.dspEnabled) next.speed else 1f
        val pitch = if(next.dspEnabled) next.effectivePitchFactor else 1f
        val oldSpeed = if(previous.dspEnabled) previous.speed else 1f
        val oldPitch = if(previous.dspEnabled) previous.effectivePitchFactor else 1f
        mutableState.update { it.copy(speed = speed) }
        if(speed != oldSpeed || pitch != oldPitch) active?.let { old ->
            val position = state.value.currentPositionMs
            val paused = old.paused
            retire()
            playPrepared(generation, old.uri, old.duration, old.headers, position)
            if(paused) pause()
        }
        Unit
    }
    override fun release() = stop()

    private fun runSession(session: Session, uri: String, durationHint: Long, headers: Map<String, String>) {
        try {
            val remote = uri.startsWith("https://") || uri.startsWith("http://")
            val file = File(uri)
            val rateEffect = session.config.dspEnabled && (session.config.speed != 1f || session.config.effectivePitchFactor != 1f)
            val decoder = if (remote || rateEffect) FfmpegAudioDecoder(uri, durationHint, headers, session.config) else {
                require(file.isFile) { "Missing music file" }
                when(file.extension.lowercase()) {
                    "mp3" -> Mp3AudioDecoder(file)
                    "flac" -> FlacAudioDecoder(file)
                    "wav", "aiff", "au" -> WavAudioDecoder(file)
                    in DesktopAudioCapabilities.extensions -> FfmpegAudioDecoder(uri, durationHint)
                    else -> throw IllegalArgumentException("Unsupported music file")
                }
            }
            session.decoder = decoder
            if (session.cancelled) { decoder.close(); return }
            val duration = durationHint.takeIf { it > 0 } ?: decoder.durationMs
            publish(session) { it.copy(durationMs = duration) }
            val raw = decoder.format
            val rate = raw.sampleRate.toInt()
            val canonical = AudioFormat(rate.toFloat(), 16, 2, true, false)
            val line = AudioSystem.getSourceDataLine(canonical)
            session.line = line
            line.open(canonical, 16_384)
            if (session.cancelled) { line.close(); return }
            applyVolume(line)
            val normalizer = PcmNormalizer()
            val buffer = ByteArray(8192)
            var basePosition = 0L
            var baseFrame = 0L
            var started = false
            synchronized(lock) {
                if (active !== session) return
                dspEngine.reset()
                dspEngine.configure(rate, 2)
            }
            while (!session.cancelled) {
                synchronized(lock) {
                    while(session.paused && session.seek.get() < 0 && !session.cancelled) lock.wait(100)
                }
                if(session.cancelled) break
                val target = session.seek.getAndSet(-1)
                if(target >= 0) {
                    publish(session) { it.copy(isBuffering = true) }
                    if (decoder.seekTo(target)) {
                        line.flush()
                        basePosition = target
                        baseFrame = line.longFramePosition
                        publish(session) { it.copy(currentPositionMs = target, isBuffering = !session.paused) }
                    }
                }
                if(session.paused) continue
                val count = decoder.readPcm(buffer, 0, buffer.size)
                if(count < 0) break
                if(count == 0) continue
                val pcm = normalizer.normalize(buffer, 0, count, raw)
                synchronized(lock) {
                    if (active !== session || session.cancelled) return
                    if (!started) { started = true; if (!session.paused) line.start() }
                    publish(session) { it.copy(isPlaying = !session.paused, isBuffering = false) }
                    dspEngine.processPcm16InPlace(pcm.data, pcm.offset, pcm.length)
                }
                line.write(pcm.data, pcm.offset, pcm.length)
                val position = basePosition + ((line.longFramePosition - baseFrame) * 1000.0 * (if(session.config.dspEnabled) session.config.speed else 1f) / rate).toLong()
                publish(session) { it.copy(currentPositionMs = if(duration > 0) position.coerceIn(0, duration) else position.coerceAtLeast(0)) }
            }
            if(!session.cancelled) {
                line.drain()
                publish(session) { it.copy(isPlaying = false, isBuffering = false, completedSessions = it.completedSessions + 1,
                    currentPositionMs = if(duration > 0) duration else it.currentPositionMs) }
            }
        } catch (error: Exception) {
            publish(session) { it.copy(isPlaying = false, isBuffering = false, currentUri = null, metadata = null,
                currentPositionMs = 0, durationMs = 0,
                errorMessage = if(error is LineUnavailableException) "Your audio device is unavailable. Check your sound output and try again."
                    else if(uri.startsWith("http")) "This song could not be played. Check your connection or try another result."
                    else "This file could not be played. It may have moved or be damaged.") }
        } finally {
            session.close()
            synchronized(lock) { if(active === session) active = null }
        }
    }
    private fun applyVolume(line: SourceDataLine) {
        runCatching {
            if(line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val control = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                control.value = if(state.value.volume < .0001f) control.minimum else
                    (20 * log10(state.value.volume.toDouble())).toFloat().coerceIn(control.minimum, control.maximum)
            }
        }
    }

    companion object {
        const val MAX_HTTP_REDIRECTS = 5

        /**
         * Safely resolves HTTP/HTTPS connections up to [MAX_HTTP_REDIRECTS] hops.
         * Validates schemes strictly against http/https to reject file/jar schemes.
         */
        @Throws(java.io.IOException::class, IllegalArgumentException::class)
        fun openHttpConnectionWithRedirects(initialUrlStr: String): Pair<java.net.HttpURLConnection, String> {
            var currentUrl = initialUrlStr
            var redirects = 0

            while (redirects <= MAX_HTTP_REDIRECTS) {
                val uri = java.net.URI(currentUrl)
                val scheme = uri.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") {
                    throw IllegalArgumentException("Unsupported streaming protocol '$scheme'. Only HTTP and HTTPS are permitted.")
                }

                val url = uri.toURL()
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", "Lyrune/1.0.0 (Windows Desktop)")
                conn.setRequestProperty("Accept", "*/*")
                conn.connect()

                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) {
                        throw java.io.IOException("HTTP redirect with no Location header (status $code)")
                    }
                    val targetUri = uri.resolve(location)
                    currentUrl = targetUri.toString()
                    redirects++
                    if (redirects > MAX_HTTP_REDIRECTS) {
                        throw java.io.IOException("Too many HTTP redirects (exceeded limit of $MAX_HTTP_REDIRECTS)")
                    }
                    continue
                }

                return Pair(conn, currentUrl)
            }

            throw java.io.IOException("Too many HTTP redirects (exceeded limit of $MAX_HTTP_REDIRECTS)")
        }
    }

}
