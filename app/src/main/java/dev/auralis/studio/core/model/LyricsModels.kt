package dev.auralis.studio.core.model

/**
 * Represents a single word inside a timed lyric line.

 *
 * @param startTimeMs  Word start timestamp in milliseconds.
 * @param endTimeMs    Word end timestamp if explicitly provided, or -1L if unknown (UI will transition at next word's start).
 * @param text         The word text string.
 */
data class LyricsWord(
    val startTimeMs: Long,
    val endTimeMs: Long = -1L,
    val text: String,
)

/**
 * Represents a single line in a lyrics display (synced or plain).
 *
 * @param timeMs  Start timestamp in milliseconds. -1 for plain/unsynced lyrics.
 * @param text    The lyric line content.
 * @param words   Optional list of word timestamps for genuine word-by-word synchronization.
 */
data class LyricsLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricsWord>? = null,
) {
    val hasWordTiming: Boolean get() = !words.isNullOrEmpty()
}


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

    /**
     * Returns the active timed line using a lower-bound binary search.
     * [offsetMs] is playback-time correction only; source timestamps are never rewritten.
     */
    fun activeIndex(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        val adjusted = positionMs - offsetMs
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val middle = (low + high).ushr(1)
            if (lines[middle].timeMs <= adjusted) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return result
    }

    fun windowAt(positionMs: Long): LyricsWindow {
        val active = activeIndex(positionMs)
        return LyricsWindow(
            previous = lines.getOrNull(active - 1),
            current = lines.getOrNull(active),
            next = lines.getOrNull(active + 1),
            activeIndex = active,
        )
    }

    /** Playback position at which a timed line should become active. */
    fun seekPositionFor(line: LyricsLine): Long = (line.timeMs + offsetMs).coerceAtLeast(0L)
}

data class LyricsWindow(
    val previous: LyricsLine?,
    val current: LyricsLine?,
    val next: LyricsLine?,
    val activeIndex: Int,
)

sealed interface LyricsUiState {
    data object Loading : LyricsUiState
    data class Found(val lyrics: SyncedLyrics) : LyricsUiState
    data object NotFound : LyricsUiState
    data object Offline : LyricsUiState
    data class Error(val message: String) : LyricsUiState
}
