package dev.auralis.studio.lyrics

/** Conservative cleanup for noisy local-file metadata used only for lookup. */
object LyricsMetadataNormalizer {
    private val extension = Regex("\\.(mp3|m4a|aac|flac|ogg|opus|wav)$", RegexOption.IGNORE_CASE)
    private val trackNumber = Regex("^\\s*\\d{1,3}[ ._-]+")
    private val bracketed = Regex("[\\[(]([^]\\)]*)[]\\)]")
    private val noise = Regex(
        "\\b(official\\s+(music\\s+)?(audio|video)|lyrics?|lyric\\s+video|audio\\s+only|visuali[sz]er|4k|hd|clip\\s+officiel)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val emptyBrackets = Regex("[\\[(]\\s*[]\\)]")

    private val channelPrefix = Regex(
        """^(?:TikTok(?:\s+Music)?|Status|BGM|Reels|Trending|Shorts|Full\s+Song|Remix|Slowed(?:\s+\+?\s*Reverb)?)\s*[-–—:]\s*""",
        RegexOption.IGNORE_CASE
    )
    private val arabicNoise = Regex(
        """\b(فيديو\s+كليب|حصريا|حصرياً|أغنية|اغنية|كاملة|مترجمة|كلمات|ريمكس|أوديو|اوديو|المطرب|الفنان|الرسمي|الرسمية)\b""",
        RegexOption.IGNORE_CASE
    )

    fun title(raw: String): String {
        var value = raw.trim().replace(extension, "").replace('_', ' ')
        value = channelPrefix.replace(value, "")
        value = value.replace(trackNumber, "")
        value = bracketed.replace(value) { match -> if (noise.replace(match.groupValues[1], "").trim().isBlank() || arabicNoise.replace(match.groupValues[1], "").trim().isBlank()) "" else match.value }
        // Promotional suffixes only; retain meaningful words inside song titles.
        value = value.replace(Regex("""\s+(?:[-|]\s*)?(?:official\s+(?:music\s+)?(?:audio|video)|lyric\s+video|visuali[sz]er|audio\s+only|4k|hd)\s*$""", RegexOption.IGNORE_CASE), "")

        value = emptyBrackets.replace(value, "")
        return value.ifBlank { raw.trim() }.replace(Regex("\\s{2,}"), " ").trim(' ', '-', '–', '—', '|', ':')
    }

    fun artist(raw: String): String {
        var value = raw.trim()
        if (value.equals("unknown artist", true) || value.equals("<unknown>", true) || value.equals("YouTube", true)) {
            return ""
        }
        if (value.endsWith("VEVO", ignoreCase = true) && value.length > 4) {
            value = value.dropLast(4).trim()
        }
        if (value.endsWith("- Topic", ignoreCase = true) && value.length > 7) {
            value = value.dropLast(7).trim()
        }
        if (!value.contains(" ")) {
            value = value.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        }
        return value.trim()
    }

    data class NormalizedMetadata(
        val title: String,
        val artist: String,
        val query: String,
    )

    fun normalize(rawTitle: String, rawArtist: String): NormalizedMetadata {
        var cleanT = title(rawTitle)
        var cleanA = artist(rawArtist)

        cleanT = channelPrefix.replace(cleanT, "").trim()

        var finalTitle = cleanT
        var finalArtist = cleanA

        // If title contains "Artist - Title" or "Prefix - Artist - Title"
        val delimiters = listOf(" - ", " – ", " — ", " | ")
        val matchedDelimiter = delimiters.firstOrNull { cleanT.contains(it) }
        if (matchedDelimiter != null) {
            val parts = cleanT.split(matchedDelimiter).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size >= 2) {
                val candidateArtist = if (parts.size >= 3 && (parts[0].contains("TikTok", true) || parts[0].contains("Music", true) || parts[0].contains("Status", true))) {
                    parts[1]
                } else {
                    parts[0]
                }
                val candidateTitle = if (parts.size >= 3 && (parts[0].contains("TikTok", true) || parts[0].contains("Music", true) || parts[0].contains("Status", true))) {
                    parts.subList(2, parts.size).joinToString(" - ")
                } else {
                    parts.subList(1, parts.size).joinToString(" - ")
                }
                val extA = artist(candidateArtist)
                val extT = title(candidateTitle)
                if (extT.isNotBlank()) {
                    finalTitle = extT
                    if (extA.isNotBlank() && (finalArtist.isBlank() || finalArtist.equals("YouTube", true))) {
                        finalArtist = extA
                    }
                }
            }
        }

        val q = if (finalArtist.isNotBlank() && !finalTitle.contains(finalArtist, ignoreCase = true)) {
            "$finalArtist $finalTitle"
        } else {
            finalTitle
        }.trim()

        return NormalizedMetadata(title = finalTitle, artist = finalArtist, query = q)
    }
}
