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

    fun stripArabicPrefixes(word: String): String {
        var w = word
        if (w.startsWith("ال") && w.length > 3) {
            w = w.substring(2)
        } else if (w.startsWith("وال") && w.length > 4) {
            w = w.substring(3)
        } else if ((w.startsWith("و") || w.startsWith("ف") || w.startsWith("ب") || w.startsWith("ل")) && w.length > 3) {
            w = w.substring(1)
        }
        return w
    }

    /**
     * Calculates flexible token match ratio between a target string and a query.
     * Returns a float between 0f (no match) and 1f (perfect match).
     * Handles:
     * - Arabic Al- prefixes ("الساعدي" vs "ساعدي")
     * - Arabic conjunctions ("والسمر" vs "سمر")
     * - Multi-word partial matches (e.g. 3 of 4 words match)
     * - Substring tokens (prefix matching)
     */
    fun matchRatio(target: String, query: String): Float {
        val normTarget = normalize(target)
        val normQuery = normalize(query)
        if (normTarget.isEmpty() || normQuery.isEmpty()) return 0f
        if (normTarget == normQuery) return 1f
        if (normTarget.contains(normQuery)) return 0.95f

        val targetTokens = normTarget.split(' ').filter { it.isNotBlank() }
        val queryTokens = normQuery.split(' ').filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return 0f

        val targetClean = targetTokens.map { stripArabicPrefixes(it) }
        var matched = 0

        for (q in queryTokens) {
            val qc = stripArabicPrefixes(q)
            val isNumeric = q.all { it.isDigit() }
            val matchFound = if (isNumeric) {
                targetTokens.any { it == q }
            } else {
                targetTokens.any { it == q || (q.length >= 3 && it.startsWith(q)) } ||
                    targetClean.any { it == qc || (qc.length >= 3 && it.startsWith(qc)) }
            }
            if (matchFound) {
                matched++
            }
        }

        return matched.toFloat() / queryTokens.size.toFloat()
    }
}

object IdentityNormalization {

    private val NOISE_PATTERNS = listOf(
        Regex("""(?i)\s*[\(\[\{]\s*(?:official\s+)?(?:music\s+)?(?:video|audio|visualizer|lyric\s+video|lyrics|hd|4k|remastered|remaster|full\s+audio)\s*[\)\]\}]"""),
        Regex("""(?i)\s*\b(?:official\s+music\s+video|official\s+lyric\s+video|official\s+visualizer|official\s+video|official\s+audio|music\s+video|lyric\s+video|full\s+audio|vevo|remastered|remaster)\b"""),
    )

    /**
     * Preprocesses a raw display title for identity canonicalization.
     * Strips non-semantic display noise (e.g. '(Official Video)', '[HD]', 'VEVO')
     * while strictly preserving semantic version qualifiers (e.g. '(Live)', '(Remix)', '(Acoustic)').
     */
    fun cleanTitleForIdentity(rawTitle: String): String {
        if (rawTitle.isBlank()) return ""
        var cleaned = rawTitle
        for (pattern in NOISE_PATTERNS) {
            cleaned = cleaned.replace(pattern, " ")
        }
        return normalize(cleaned)
    }

    private val VERSION_NOISE_TERMS = listOf(
        "live at the forum", "live at", "live session", "concert", "live",
        "club mix", "extended mix", "dub mix", "vip mix", "mashup", "remix",
        "acoustic", "unplugged", "cover", "tribute", "karaoke",
        "instrumental", "backing track", "demo",
        "radio edit", "radio version", "clean edit", "clean version",
        "slowed reverb", "slowed down", "slowed",
        "sped up", "speed up", "speedup", "nightcore",
        "re recorded", "rerecorded", "taylor s version"
    )

    /**
     * Extracts the core title without version terms or display noise.
     * Used for title matching when version profile compatibility is verified separately.
     */
    fun coreTitle(rawTitle: String): String {
        var res = cleanTitleForIdentity(rawTitle)
        for (term in VERSION_NOISE_TERMS) {
            if (res == term) {
                res = ""
                break
            }
            if (res.endsWith(" $term")) {
                res = res.substring(0, res.length - term.length - 1).trim()
            } else if (res.startsWith("$term ")) {
                res = res.substring(term.length + 1).trim()
            } else if (res.contains(" $term ")) {
                res = res.replace(" $term ", " ").trim()
            }
        }
        return res
    }

    /**
     * Conservative normalizer for canonical recording identification and merger comparison.
     * - Preserves distinct Arabic letters: ة, ؤ, ئ
     * - Normalizes Arabic Alef variants (أ, إ, آ, ٱ -> ا)
     * - Strips Arabic Tashkeel / Harakat and Tatweel (ـ)
     * - Strips combining diacritical marks from Latin/accented letters (é -> e, ñ -> n)
     * - Punctuation and whitespace collapse
     * - Case folding
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
                // Taa Marbuta is RETAINED as distinct for canonical identity (ة != ه)
                ch == 'ة' -> {
                    sb.append('ة')
                    lastWasSpace = false
                }
                // Waw with Hamza is RETAINED as distinct for canonical identity (ؤ != و)
                ch == 'ؤ' -> {
                    sb.append('ؤ')
                    lastWasSpace = false
                }
                // Yeh with Hamza is RETAINED as distinct for canonical identity (ئ != ي)
                ch == 'ئ' -> {
                    sb.append('ئ')
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
                // Arabic characters in Unicode Arabic block -> append as is (preserving distinct letters)
                ch in '\u0600'..'\u06FF' -> {
                    sb.append(ch)
                    lastWasSpace = false
                }
                // Latin / Other accented characters -> decompose and strip combining marks
                else -> {
                    val decomposed = Normalizer.normalize(ch.toString(), Normalizer.Form.NFKD)
                    for (d in decomposed) {
                        if (Character.getType(d) != Character.NON_SPACING_MARK.toInt()) {
                            sb.append(d.lowercaseChar())
                            lastWasSpace = false
                        }
                    }
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
