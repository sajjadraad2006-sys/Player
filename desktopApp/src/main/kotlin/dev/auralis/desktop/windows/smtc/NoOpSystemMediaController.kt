package dev.auralis.desktop.windows.smtc

/**
 * Safe no-op fallback implementation of [SystemMediaController].
 *
 * Used when running on non-Windows platforms, during unit testing,
 * or when native library loading fails.
 */
class NoOpSystemMediaController : SystemMediaController {
    override fun initialize(commandHandler: SmtcCommandHandler) {}
    override fun updateMetadata(metadata: SmtcMetadata) {}
    override fun updatePlaybackStatus(status: SmtcPlaybackStatus) {}
    override fun updateTimeline(timeline: SmtcTimeline) {}
    override fun updateCapabilities(capabilities: SmtcCapabilities) {}
    override fun clear() {}
    override fun release() {}
}
