package dev.auralis.studio.online

/**
 * Domain model describing the playback capability of a recording source.
 *
 * Deterministic ranking order:
 * LOCAL > FULL_STREAM > PREVIEW > AUTH_REQUIRED > UNAVAILABLE
 */
enum class PlaybackCapability {
    /** On-device local audio file, confirmed readable. */
    LOCAL,
    /** Verified full-length streamable audio. */
    FULL_STREAM,
    /** Promotional 30-second preview clip. */
    PREVIEW,
    /** Requires private authentication or subscription tier. */
    AUTH_REQUIRED,
    /** No playback mechanism available. */
    UNAVAILABLE;

    val isPlayable: Boolean
        get() = this == LOCAL || this == FULL_STREAM || this == PREVIEW

    val isFullTrack: Boolean
        get() = this == LOCAL || this == FULL_STREAM
}
