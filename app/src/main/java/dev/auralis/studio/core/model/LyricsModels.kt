package dev.auralis.studio.core.model

/**
 * Represents a single line in a lyrics display (synced or plain).
 *
 * @param timeMs  Start timestamp in milliseconds. -1 for plain/unsynced lyrics.
 * @param text    The lyric line content.
 */
data class LyricsLine(
    val timeMs: Long,
    val text: String,
)

/** Where a lyrics result came from — used for cache provenance and priority. */
enum class LyricsSource {
    EMBEDDED,       // read directly from audio file metadata tags
    LOCAL_LRC,      // matched from a local .lrc file alongside the audio
    CACHE,          // fetched from a provider and cached
    LRCLIB,         // live result from LRCLIB API
}

/**
 * Complete resolved lyrics for a track.
 *
 * @param lines   Ordered lyric lines. Empty for no lyrics.
 * @param synced  True if [lines] carry valid timestamps (synced LRC).
 * @param source  Where these lyrics originated.
 * @param offsetMs  User-adjustable timing correction applied to all synced timestamps.
 */
data class SyncedLyrics(
    val lines: List<LyricsLine>,
    val synced: Boolean,
    val source: LyricsSource,
    val offsetMs: Long = 0L,
) {
    companion object {
        val EMPTY = SyncedLyrics(emptyList(), synced = false, source = LyricsSource.EMBEDDED)
    }

    /** Returns the index of the currently active line for [positionMs]. */
    fun activeIndex(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        val adjusted = positionMs - offsetMs
        var result = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= adjusted) result = i else break
        }
        return result
    }
}
