package dev.auralis.desktop.playback

/**
 * Structured taxonomy of desktop playback errors.
 * Provides user-friendly messages for the UI and technical details for logging.
 */
sealed class DesktopPlaybackError(
    val userFriendlyMessage: String,
    val technicalDetails: String? = null,
    val isRecoverable: Boolean = false,
) {
    class UnsupportedFormat(val format: String, reason: String) : DesktopPlaybackError(
        userFriendlyMessage = "Audio format '$format' is not supported on Desktop.",
        technicalDetails = reason,
    )

    class DecoderFailure(message: String, cause: Throwable? = null) : DesktopPlaybackError(
        userFriendlyMessage = "Unable to decode audio stream.",
        technicalDetails = "${cause?.javaClass?.simpleName ?: "Error"}: $message",
        isRecoverable = true,
    )

    class AudioDeviceUnavailable(details: String) : DesktopPlaybackError(
        userFriendlyMessage = "Audio playback device is unavailable or disconnected.",
        technicalDetails = details,
        isRecoverable = true,
    )

    class FileNotFound(val path: String) : DesktopPlaybackError(
        userFriendlyMessage = "Audio file could not be found.",
        technicalDetails = "Path: $path",
    )

    class CorruptedMedia(details: String) : DesktopPlaybackError(
        userFriendlyMessage = "The audio file appears to be damaged or corrupted.",
        technicalDetails = details,
    )

    class NetworkStreamError(val url: String, details: String) : DesktopPlaybackError(
        userFriendlyMessage = "Unable to connect to online audio stream.",
        technicalDetails = details,
        isRecoverable = true,
    )
}
