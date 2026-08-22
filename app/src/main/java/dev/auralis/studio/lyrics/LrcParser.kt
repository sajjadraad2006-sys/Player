package dev.auralis.studio.lyrics

import dev.auralis.studio.core.model.LyricsLine
import dev.auralis.studio.core.model.SyncedLyrics
import dev.auralis.studio.core.model.LyricsSource

/**
 * Parses LRC format lyrics strings (extended [mm:ss.xx] timestamps).
 * Produces a [SyncedLyrics] with sorted, deduplicated lines.
 */
object LrcParser {

    private val TIMESTAMP_RE = Regex("""^\[(\d{1,3}):(\d{2})[.:](\d{2,3})\](.*)$""")
    private val META_RE      = Regex("""^\[(?:ti|ar|al|by|length|offset):[^\]]*\]$""", RegexOption.IGNORE_CASE)

    /**
     * Parses a raw LRC string and returns a [SyncedLyrics].
     * If no timestamp lines are found, returns a plain (unsynced) lyrics result.
     */
    fun parse(lrc: String, source: LyricsSource = LyricsSource.LOCAL_LRC): SyncedLyrics {
        val lines = mutableListOf<LyricsLine>()
        var hasTimestamps = false

        for (rawLine in lrc.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || META_RE.matches(line)) continue

            // Handle multiple timestamps on one line: [00:01.23][00:03.45]text
            val allTimestamps = mutableListOf<Long>()
            var rest = line
            while (rest.startsWith("[")) {
                val m = TIMESTAMP_RE.find(rest) ?: break
                val min    = m.groupValues[1].toLongOrNull() ?: 0L
                val sec    = m.groupValues[2].toLongOrNull() ?: 0L
                val centis = m.groupValues[3].let { s ->
                    when (s.length) {
                        2 -> s.toLongOrNull() ?: 0L   // centiseconds
                        3 -> (s.toLongOrNull() ?: 0L) / 10L // milliseconds → centiseconds
                        else -> 0L
                    }
                }
                allTimestamps.add((min * 60 + sec) * 1000L + centis * 10L)
                rest = m.groupValues[4]
                hasTimestamps = true
            }

            val text = rest.trim()
            if (allTimestamps.isEmpty()) {
                // Plain line — treat as unsynced if we later find no timestamps at all
                lines.add(LyricsLine(-1L, text))
            } else {
                for (ts in allTimestamps) lines.add(LyricsLine(ts, text))
            }
        }

        if (!hasTimestamps) {
            // Return unsynced lyrics
            val plainText = lines.filter { it.text.isNotBlank() }
            return SyncedLyrics(plainText, synced = false, source = source)
        }

        // Sort by timestamp, remove blanks at t=0 (often metadata artifacts)
        val sorted = lines
            .filter { it.timeMs >= 0 }
            .sortedBy { it.timeMs }

        return SyncedLyrics(sorted, synced = true, source = source)
    }

    /** Converts a [SyncedLyrics] back to LRC string format for storage. */
    fun toLrcString(lyrics: SyncedLyrics): String {
        if (!lyrics.synced) return lyrics.lines.joinToString("\n") { it.text }
        return lyrics.lines.joinToString("\n") { line ->
            val totalSec = line.timeMs / 1000
            val min      = totalSec / 60
            val sec      = totalSec % 60
            val cs       = (line.timeMs % 1000) / 10
            "[%02d:%02d.%02d]%s".format(min, sec, cs, line.text)
        }
    }
}
