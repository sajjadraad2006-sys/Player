package dev.auralis.studio.online

import java.text.Normalizer

/**
 * Normalization utilities for music discovery, search matching, and identity canonicalization.
 *
 * Strict architectural separation:
 * 1. [SearchNormalization]: Forgiving normalization used for fuzzy/token search matching.
 * 2. [IdentityNormalization]: Conservative normalization used for canonical recording grouping & merging.
 *
 * Display metadata must always remain completely untouched.
 */
object SearchNormalization {

    private val NOISE_PHRASES = listOf(
        "official music video",
        "official lyric video",
        "official audio",
        "official video",
        "official visualizer",
        "music video",
        "lyric video",
        "full audio",
        "official",
        "lyrics",
        "vevo",
        "4k",
        "hd",
        "hq",
        "remastered",
        "remaster",
    )

    /**
     * Forgiving normalizer for search input and query matching.
     * - Case folding
     * - Arabic Alef variants -> standard bare Alif (ا)
     * - Arabic Taa Marbuta -> Haa (ه) for search forgiveness
     * - Arabic Yaa / Alef Maksura variants -> Yaa (ي)
     * - Persian/Arabic Kaf -> standard Kaf (ك)
     * - Strips Arabic Tashkeel / Harakat and Tatweel (ـ)
     * - Strips display noise phrases (VEVO, Official Audio, etc.)
     * - Preserves semantic version qualifiers (Live, Remix, Acoustic, etc.)
     */
    fun normalize(text: String): String {
        if (text.isEmpty()) return ""
        val len = text.length
        val sb = StringBuilder(len)

        var lastWasSpace = true
        var i = 0
        while (i < len) {
            val ch = text[i]
            when {
                // Arabic Alif variants -> standard bare Alif
                ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> {
                    sb.append('ا')
                    lastWasSpace = false
                }
                // Taa Marbuta -> Haa (Forgiving for search queries)
                ch == 'ة' -> {
                    sb.append('ه')
                    lastWasSpace = false
                }
                // Yaa / Alef Maksura -> Yaa
                ch == 'ى' || ch == 'ئ' || ch == 'ی' -> {
                    sb.append('ي')
                    lastWasSpace = false
                }
                // Kaf variants -> Kaf
                ch == 'ك' || ch == 'ک' -> {
                    sb.append('ك')
                    lastWasSpace = false
                }
                // Skip Arabic Tashkeel / Harakat and Tatweel
                ch == 'ـ' || (ch in '\u064B'..'\u065F') || ch == '\u0670' -> {
                    // Ignored
                }
                // Punctuation and symbols -> space
                ch == '_' || ch == '-' || ch == '.' || ch == ',' || ch == '!' || ch == '?' ||
                    ch == ':' || ch == ';' || ch == '\'' || ch == '"' || ch == '(' || ch == ')' ||
                    ch == '[' || ch == ']' || ch == '{' || ch == '}' || ch == '/' || ch == '\\' ||
                    ch == '|' || ch == '+' || ch == '=' || ch == '*' || ch == '&' || ch == '#' ||
                    ch == '@' || ch == '~' || ch == '`' || ch == '<' || ch == '>' ||
                    ch.isWhitespace() -> {
                    if (!lastWasSpace) {
                        sb.append(' ')
                        lastWasSpace = true
                    }
                }
                else -> {
                    sb.append(ch.lowercaseChar())
                    lastWasSpace = false
                }
            }
            i++
        }

        var res = sb.toString().trim()

        // Strip non-semantic display noise (VEVO, Official Video, etc.)
        for (noise in NOISE_PHRASES) {
            if (res == noise) {
                res = ""
                break
            }
            if (res.endsWith(" $noise")) {
                res = res.substring(0, res.length - noise.length - 1).trim()
            } else if (res.startsWith("$noise ")) {
                res = res.substring(noise.length + 1).trim()
            } else if (res.contains(" $noise ")) {
                res = res.replace(" $noise ", " ").trim()
            }
        }

        return res
    }
}

object IdentityNormalization {

    /**
     * Conservative normalizer for canonical recording identification and merger comparison.
     * - Unicode NFKD decomposition to strip accents (é -> e, à -> a)
     * - Case folding
     * - Punctuation and whitespace collapse
     * - Arabic Alef variants -> bare Alif (ا)
     * - Strips Arabic Tashkeel / Harakat and Tatweel (ـ)
     * - DOES NOT convert ة -> ه (Taa Marbuta is retained as distinct for canonical identity)
     * - DOES NOT convert ؤ or ئ to generic vowels
     * - DOES NOT strip noise blindly
     */
    fun normalize(text: String): String {
        if (text.isEmpty()) return ""
        // Apply NFKD decomposition to separate base chars from combining diacritical marks
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD)
        val len = decomposed.length
        val sb = StringBuilder(len)

        var lastWasSpace = true
        var i = 0
        while (i < len) {
            val ch = decomposed[i]
            when {
                // Skip combining diacritical marks (Unicode category Mn)
                Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> {
                    // Stripped — this handles accents like é -> e, ñ -> n, etc.
                }
                // Arabic Alif variants -> standard bare Alif
                ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> {
                    sb.append('ا')
                    lastWasSpace = false
                }
                // Skip Arabic Tashkeel / Harakat and Tatweel only
                ch == 'ـ' || (ch in '\u064B'..'\u065F') || ch == '\u0670' -> {
                    // Ignored
                }
                // Punctuation and symbols -> single space
                ch == '_' || ch == '-' || ch == '.' || ch == ',' || ch == '!' || ch == '?' ||
                    ch == ':' || ch == ';' || ch == '\'' || ch == '"' || ch == '(' || ch == ')' ||
                    ch == '[' || ch == ']' || ch == '{' || ch == '}' || ch == '/' || ch == '\\' ||
                    ch == '|' || ch == '+' || ch == '=' || ch == '*' || ch == '&' || ch == '#' ||
                    ch == '@' || ch == '~' || ch == '`' || ch == '<' || ch == '>' ||
                    ch.isWhitespace() -> {
                    if (!lastWasSpace) {
                        sb.append(' ')
                        lastWasSpace = true
                    }
                }
                else -> {
                    sb.append(ch.lowercaseChar())
                    lastWasSpace = false
                }
            }
            i++
        }

        return sb.toString().trim()
    }

    /** Normalizes artist strings by removing conjunction noise ('and', 'feat', 'ft') */
    fun normalizeArtist(artist: String): String = normalize(artist)
        .replace(Regex("\\b(and|feat|featuring|ft|with)\\b"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
