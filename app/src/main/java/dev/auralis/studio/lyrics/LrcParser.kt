package dev.auralis.studio.lyrics

import dev.auralis.studio.core.model.LyricsLine
import dev.auralis.studio.core.model.LyricsWord
import dev.auralis.studio.core.model.SyncedLyrics
import dev.auralis.studio.core.model.LyricsSource

/**
 * Parses LRC format lyrics strings (standard and extended [mm:ss.xx] timestamps, plus genuine <mm:ss.xx> word tags).
 * Produces a [SyncedLyrics] with sorted, deduplicated lines.
 */
object LrcParser {

    private val TIMESTAMP_RE = Regex("""^\[(\d{1,3}):(\d{2})[.:](\d{2,3})\](.*)$""")
    private val OFFSET_RE    = Regex("""^\[offset:\s*([+-]?\d+)\s*\]$""", RegexOption.IGNORE_CASE)
    private val META_RE      = Regex("""^\[(?:ti|ar|al|by|length|offset):[^\]]*\]$""", RegexOption.IGNORE_CASE)
    private val WORD_TAG_RE  = Regex("""<(\d{1,3}):(\d{2})[.:](\d{2,3})>([^<]*)""")

    /**
     * Parses a raw LRC string and returns a [SyncedLyrics].
     * If no timestamp lines are found, returns a plain (unsynced) lyrics result.
     */
    fun parse(lrc: String, source: LyricsSource = LyricsSource.LOCAL_LRC): SyncedLyrics {
        val lines = mutableListOf<LyricsLine>()
        var hasTimestamps = false
        var parsedOffsetMs = 0L

        for (rawLine in lrc.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // Check for standard LRC offset tag: [offset:+/-ms]
            val offsetMatch = OFFSET_RE.matchEntire(line)
            if (offsetMatch != null) {
                parsedOffsetMs += offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                continue
            }
            if (META_RE.matches(line)) continue

            // Handle multiple line timestamps: [00:01.23][00:03.45]text
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

            // Check for genuine word timestamps <mm:ss.xx> inside the line
            val wordMatches = WORD_TAG_RE.findAll(rest).toList()
            val words = if (wordMatches.isNotEmpty()) {
                val list = mutableListOf<LyricsWord>()
                for (match in wordMatches) {
                    val wMin = match.groupValues[1].toLongOrNull() ?: 0L
                    val wSec = match.groupValues[2].toLongOrNull() ?: 0L
                    val wCentis = match.groupValues[3].let { s ->
                        when (s.length) {
                            2 -> s.toLongOrNull() ?: 0L
                            3 -> (s.toLongOrNull() ?: 0L) / 10L
                            else -> 0L
                        }
                    }
                    val wStartMs = (wMin * 60 + wSec) * 1000L + wCentis * 10L
                    val wText = match.groupValues[4]
                    if (wText.isNotEmpty()) {
                        list.add(LyricsWord(startTimeMs = wStartMs, endTimeMs = -1L, text = wText))
                    }
                }
                if (list.isNotEmpty()) list else null
            } else {
                null
            }

            val cleanText = if (words != null) {
                words.joinToString("") { it.text }.trim()
            } else {
                rest.replace(Regex("""<[^>]+>"""), "").trim()
            }

            if (allTimestamps.isEmpty()) {
                // Plain line — treat as unsynced if we later find no timestamps at all
                lines.add(LyricsLine(-1L, cleanText, words))
            } else {
                for (ts in allTimestamps) {
                    lines.add(LyricsLine(ts, cleanText, words))
                }
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

        return SyncedLyrics(sorted, synced = true, source = source, offsetMs = parsedOffsetMs)
    }

    /** Converts a [SyncedLyrics] back to LRC string format for storage. */
    fun toLrcString(lyrics: SyncedLyrics): String {
        if (!lyrics.synced) return lyrics.lines.joinToString("\n") { it.text }
        return lyrics.lines.joinToString("\n") { line ->
            val totalSec = line.timeMs / 1000
            val min      = totalSec / 60
            val sec      = totalSec % 60
            val cs       = (line.timeMs % 1000) / 10
            if (line.words != null && line.words.isNotEmpty()) {
                val wordsStr = line.words.joinToString("") { w ->
                    val wTotalSec = w.startTimeMs / 1000
                    val wMin = wTotalSec / 60
                    val wSec = wTotalSec % 60
                    val wCs = (w.startTimeMs % 1000) / 10
                    "<%02d:%02d.%02d>%s".format(wMin, wSec, wCs, w.text)
                }
                "[%02d:%02d.%02d]%s".format(min, sec, cs, wordsStr)
            } else {
                "[%02d:%02d.%02d]%s".format(min, sec, cs, line.text)
            }
        }
    }
}

