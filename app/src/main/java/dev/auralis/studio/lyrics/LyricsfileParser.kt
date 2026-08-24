package dev.auralis.studio.lyrics

import dev.auralis.studio.core.model.LyricsLine
import dev.auralis.studio.core.model.LyricsSource
import dev.auralis.studio.core.model.LyricsWord
import dev.auralis.studio.core.model.SyncedLyrics

/**
 * Parser for the LRCLIB `lyricsfile` field — an open YAML-based lyrics format
 * (https://lrclib.net/lyricsfile, CC0 spec draft 1.0).
 *
 * Supports:
 * - line timing (`lines[].start_ms` / `end_ms`)
 * - genuine word timing (`lines[].words[].start_ms` / `end_ms`) — NEVER synthesized.
 *   Words lacking an explicit start timestamp are dropped rather than invented.
 * - overlapping vocals (simultaneous lines kept and sorted)
 * - plain-only documents (`plain:` block → unsynced result)
 *
 * Intentionally does not reuse [LrcParser]: YAML and tagged-text formats must
 * not share parsing heuristics.
 */
object LyricsfileParser {

    private val LIST_KEY_RE = Regex("""^(\s*)-\s+([\w-]+)\s*:\s*(.*)$""")
    private val KEY_RE = Regex("""^(\s*)([\w-]+)\s*:\s*(.*)$""")
    private val SECTION_LINE = "lines"
    private val SECTION_WORDS = "words"
    private val SECTION_PLAIN = "plain"

    /** Parsed result of one document. */
    data class LyricsfileDoc(
        val version: String,
        val instrumental: Boolean,
        val lines: List<LyricsLine>,
        val plainText: String,
    ) {
        val hasTimedLines: Boolean get() = lines.isNotEmpty()
    }

    /**
     * Parses a Lyricsfile document into [SyncedLyrics].
     * Returns null when there is nothing usable (no valid YAML, no timed lines,
     * no plain block). Never fabricates timestamps.
     */
    fun parse(raw: String, source: LyricsSource = LyricsSource.LRCLIB): SyncedLyrics? {
        val doc = parseDocument(raw) ?: return null
        if (doc.hasTimedLines) {
            return SyncedLyrics(doc.lines.sortedBy { it.timeMs }, synced = true, source = source)
        }
        val plainLines = doc.plainText.lines().map(String::trim).filter(String::isNotEmpty)
        if (plainLines.isEmpty()) return null
        return SyncedLyrics(plainLines.map { LyricsLine(-1L, it) }, synced = false, source = source)
    }

    /** Structural parse used by tests; [parse] wraps it into [SyncedLyrics]. */
    fun parseDocument(raw: String): LyricsfileDoc? {
        var version = ""
        var instrumental = false

        var section: String? = null          // active top-level section key
        var plainBlockIndent = -1            // indent of the `plain: |` block content

        // Accumulators for the line being built / word being built.
        var lineText = ""
        var lineStartMs = -1L
        var lineEndMs = -1L
        var inWords = false
        var wordsIndent = -1                // indent of `words:` key relative to line item
        val currentWords = mutableListOf<LyricsWord>()
        var wordText = ""
        var wordStartMs = -1L
        var wordEndMs = -1L

        val lines = mutableListOf<LyricsLine>()
        val plain = StringBuilder()

        fun commitWord() {
            if (!inWords) return
            // Only words carrying an EXPLICIT start timestamp are accepted.
            if (wordStartMs >= 0L && wordText.isNotEmpty()) {
                currentWords.add(LyricsWord(wordStartMs, wordEndMs, wordText))
            }
            wordText = ""
            wordStartMs = -1L
            wordEndMs = -1L
        }

        fun commitLine() {
            commitWord()
            if (lineStartMs >= 0L && lineText.isNotBlank()) {
                lines.add(LyricsLine(lineStartMs, lineText.trim(), currentWords.toList().ifEmpty { null }))
            } else if (lineText.isNotBlank() && currentWords.isNotEmpty()) {
                // Word timing without a line start: derive the line start from the
                // FIRST word's explicit timestamp — still genuine data, not invented.
                lines.add(LyricsLine(currentWords.first().startTimeMs, lineText.trim(), currentWords.toList()))
            }
            lineText = ""
            lineStartMs = -1L
            lineEndMs = -1L
            currentWords.clear()
            inWords = false
            wordsIndent = -1
        }

        for (rawLine in raw.lines()) {
            val expanded = rawLine.replace("\t", "    ")
            val trimmed = expanded.trim()
            val indent = expanded.indexOfFirst { it != ' ' }.let { if (it == -1) expanded.length else it }

            // Plain block scalar: collect verbatim until a non-indented key appears.
            if (section == SECTION_PLAIN) {
                if (trimmed.isEmpty()) {
                    plain.appendLine()
                    continue
                }
                if (plainBlockIndent != -1 && indent < plainBlockIndent) {
                    section = null
                    plainBlockIndent = -1
                    // fall through to normal parsing of this line
                } else {
                    val dropBy = if (plainBlockIndent == -1) indent else plainBlockIndent
                    plain.appendLine(expanded.drop(dropBy))
                    continue
                }
            }

            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            val listKey = LIST_KEY_RE.find(expanded)
            val key = KEY_RE.find(expanded)

            when {
                // `- text: ...` / `- start_ms: ...` — begins a line or word entry
                listKey != null -> {
                    val name = listKey.groupValues[2]
                    val value = unquote(listKey.groupValues[3].trim())
                    when (name) {
                        "text" -> if (inWords && wordsIndent in 1..indent) {
                            commitWord(); wordText = value
                        } else {
                            commitWord(); commitLine(); lineText = value
                            inWords = false; wordsIndent = -1
                        }
                        "start_ms" -> value.toLongOrNull()?.let {
                            if (inWords) wordStartMs = it else lineStartMs = it
                        }
                        "end_ms" -> value.toLongOrNull()?.let {
                            if (inWords) wordEndMs = it else lineEndMs = it
                        }
                    }
                }

                key != null -> {
                    val name = key.groupValues[2]
                    val valueRaw = key.groupValues[3]
                    val value = unquote(valueRaw.trim())
                    when {
                        indent == 0 && name == "version" -> version = value
                        name == "instrumental" -> instrumental = value.equals("true", true)

                        // Entering the lines section
                        name == SECTION_LINE -> { commitLine(); section = SECTION_LINE }

                        // Entering the words sub-block of the current line
                        name == SECTION_WORDS -> {
                            section = SECTION_LINE
                            inWords = true
                            wordsIndent = indent
                        }

                        // Plain: block scalar or inline value
                        name == SECTION_PLAIN -> {
                            commitLine()
                            section = SECTION_PLAIN
                            plain.setLength(0)
                            plainBlockIndent = -1
                            if (valueRaw.trim().endsWith("|") || valueRaw.trim().endsWith(">")) {
                                // content on following indented lines
                            } else if (value.isNotEmpty()) {
                                plain.appendLine(value)
                                section = null
                            }
                        }

                        // Attribute of current line/word written as `key: value`
                        name == "text" -> if (inWords) { commitWord(); wordText = value } else lineText = value
                        name == "start_ms" -> value.toLongOrNull()?.let {
                            if (inWords) wordStartMs = it else lineStartMs = it
                        }
                        name == "end_ms" -> value.toLongOrNull()?.let {
                            if (inWords) wordEndMs = it else lineEndMs = it
                        }
                    }
                }
            }
        }
        commitLine()

        val plainText = plain.toString().trim('\n')
        if (version.isBlank() && lines.isEmpty() && plainText.isBlank()) return null
        return LyricsfileDoc(version, instrumental, lines, plainText)
    }

    private fun unquote(value: String): String {
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length - 1)
            }
        }
        return value
    }
}
