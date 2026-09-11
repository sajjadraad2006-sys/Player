package dev.auralis.desktop.windows.smtc

/**
 * Immutable metadata published to the system media transport controls (SMTC).
 */
data class SmtcMetadata(
    val title: String,
    val artist: String = "Unknown Artist",
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val artworkFilePath: String? = null
)

/**
 * High-level playback status mapped to Windows MediaPlaybackStatus.
 */
enum class SmtcPlaybackStatus {
    CLOSED,
    PAUSED,
    STOPPED,
    PLAYING,
    CHANGING
}

/**
 * Playback timeline details published to Windows SMTC.
 */
data class SmtcTimeline(
    val positionMs: Long,
    val durationMs: Long,
    val minSeekMs: Long = 0L,
    val maxSeekMs: Long = durationMs
)

/**
 * Media button capabilities enabled on the Windows SMTC control surface.
 */
data class SmtcCapabilities(
    val canPlay: Boolean = true,
    val canPause: Boolean = true,
    val canStop: Boolean = true,
    val canNext: Boolean = false,
    val canPrevious: Boolean = false,
    val canSeek: Boolean = true
)

/**
 * Receiver interface for system transport commands emitted from Windows OS flyouts
 * or hardware multimedia keys.
 */
interface SmtcCommandHandler {
    fun onPlay()
    fun onPause()
    fun onStop()
    fun onNext()
    fun onPrevious()
    fun onSeek(positionMs: Long)
}

/**
 * Narrow platform abstraction isolating Compose UI and audio playback from
 * native Windows Runtime (WinRT) / COM details.
 */
interface SystemMediaController {
    fun initialize(commandHandler: SmtcCommandHandler)
    fun updateMetadata(metadata: SmtcMetadata)
    fun updatePlaybackStatus(status: SmtcPlaybackStatus)
    fun updateTimeline(timeline: SmtcTimeline)
    fun updateCapabilities(capabilities: SmtcCapabilities)
    fun clear()
    fun release()

    companion object {
        fun isAvailable(): Boolean {
            val os = System.getProperty("os.name", "").lowercase()
            if (!os.contains("win")) return false
            return try {
                NativeSmtcBridge.loadOrNull() != null
            } catch (t: Throwable) {
                false
            }
        }
    }
}

