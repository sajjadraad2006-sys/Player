package dev.auralis.studio.lyrics

/** Conservative cleanup for noisy local-file metadata used only for lookup. */
object LyricsMetadataNormalizer {
    private val extension = Regex("\\.(mp3|m4a|aac|flac|ogg|opus|wav)$", RegexOption.IGNORE_CASE)
    private val trackNumber = Regex("^\\s*\\d{1,3}[ ._-]+")
    private val bracketed = Regex("[\\[(]([^]\\)]*)[]\\)]")
    private val noise = Regex("\\b(official\\s+(audio|video)|lyrics?|lyric\\s+video|audio\\s+only|visuali[sz]er)\\b", RegexOption.IGNORE_CASE)

    fun title(raw: String): String {
        var value = raw.trim().replace(extension, "").replace('_', ' ')
        value = value.replace(trackNumber, "")
        value = bracketed.replace(value) { match -> if (noise.containsMatchIn(match.groupValues[1])) "" else match.value }
        value = noise.replace(value, "")
        return value.replace(Regex("\\s{2,}"), " ").trim(' ', '-', '–', '—')
    }

    fun artist(raw: String): String = raw.trim()
        .takeUnless { it.equals("unknown artist", true) || it.equals("<unknown>", true) }
        .orEmpty()
}
