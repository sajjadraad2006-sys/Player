package dev.auralis.desktop.playback

import dev.auralis.studio.resolver.api.*

object DesktopAudioCapabilities {
    val extensions = setOf("mp3", "wav", "flac", "ogg", "opus", "m4a", "aac", "aiff", "au", "webm")
    const val PROFILE = "desktop-pcm-v1"

    fun supports(format: ResolvedFormat): Boolean =
        format.extension?.lowercase() in extensions + "mp4" &&
            format.audioCodec?.lowercase()?.let { it == "mp3" || it == "aac" || it.startsWith("mp4a") || it == "opus" || it == "vorbis" || it == "flac" } == true &&
            format.resolutionMode != ResolutionMode.ENGINE_REQUIRED &&
            format.url.startsWith("https://")
}
