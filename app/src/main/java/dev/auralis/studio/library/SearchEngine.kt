package dev.auralis.studio.library

import dev.auralis.studio.core.model.Track
import kotlin.math.min

/**
 * High-performance, zero-regex multi-field normalized search and ranking engine for Lyrune.
 * Handles multi-field search (title, artist, album, filename), robust multilingual and Arabic normalization,
 * noise removal (VEVO, Official Audio, track prefixes, audio extensions), and strict tiered ranking
 * ensuring exact/prefix matches always outrank fuzzy/typo matches.
 */
object SearchEngine {

    private val AUDIO_EXTENSIONS_NO_DOT = listOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac")

    private val NOISE_SUFFIXES = listOf(
        "official audio",
        "official music video",
        "official video",
        "official lyric video",
        "official visualizer",
        "official",
        "music video",
        "lyric video",
        "lyrics",
        "vevo",
        "full audio",
        "hd",
        "hq",
        "4k",
        "remastered",
        "remaster",
        "audio",
    )

    /**
     * Normalizes text for search indexing and query scoring:
     * - Strips common track number prefixes ("01. ", "02 - ", "1_")
     * - Strips audio extensions (.mp3, .flac, etc.)
     * - Delegates to [dev.auralis.studio.online.SearchNormalization] as the authoritative production normalizer.
     */
    fun normalize(text: String): String {
        if (text.isEmpty()) return ""
        val len = text.length
        var i = 0

        // Skip leading track number like "01. ", "02 - "
        while (i < len && text[i].isDigit()) i++
        val stripped = if (i in 1..3 && i < len && (text[i] == '.' || text[i] == '-' || text[i] == '_' || text[i] == ' ')) {
            while (i < len && (text[i] == '.' || text[i] == '-' || text[i] == '_' || text[i] == ' ')) i++
            text.substring(i)
        } else {
            text
        }

        var withoutExt = stripped
        for (ext in AUDIO_EXTENSIONS_NO_DOT) {
            if (withoutExt.endsWith(".$ext", ignoreCase = true)) {
                withoutExt = withoutExt.substring(0, withoutExt.length - ext.length - 1)
                break
            }
        }

        return dev.auralis.studio.online.SearchNormalization.normalize(withoutExt)
    }

    fun normalizeArabic(text: String): String = normalize(text)

    /**
     * Scores a track against the pre-normalized query.
     */
    fun score(track: Track, normQuery: String): Int {
        if (normQuery.isEmpty()) return 0

        val normTitle = normalize(track.title)
        val normArtist = normalize(track.artist)
        val normAlbum = normalize(track.album)
        val normFile = normalize(track.displayName)

        var totalScore = 0

        // 1. Title matching (Highest priority)
        if (normTitle == normQuery) {
            totalScore += 10_000 // Exact title match
        } else if (normTitle.startsWith(normQuery)) {
            totalScore += 5_000 // Title starts with query
        } else if (isTokenMatch(normTitle, normQuery)) {
            totalScore += 3_000 // Title contains full query token
        } else if (normTitle.contains(normQuery)) {
            totalScore += 1_500 // Title contains query substring
        }

        // 2. Artist matching
        if (normArtist.isNotEmpty()) {
            if (normArtist == normQuery) {
                totalScore += 1_000 // Exact artist match
            } else if (normArtist.startsWith(normQuery)) {
                totalScore += 600 // Artist starts with query
            } else if (normArtist.contains(normQuery)) {
                totalScore += 400 // Artist contains query
            }
        }

        // 3. Album matching
        if (normAlbum.isNotEmpty()) {
            if (normAlbum == normQuery) {
                totalScore += 300
            } else if (normAlbum.contains(normQuery)) {
                totalScore += 200
            }
        }

        // 4. Filename & path fallback match
        if (totalScore == 0) {
            if (normFile.isNotEmpty() && normFile.contains(normQuery)) {
                totalScore += 150
            } else {
                val normUri = normalize(track.uri)
                if (normUri.isNotEmpty() && normUri.contains(normQuery)) {
                    totalScore += 120
                }
            }
        }

        // 5. Fuzzy / typo matching (Only if no exact/substring matches were found and query length >= 4)
        if (totalScore == 0 && normQuery.length >= 4) {
            val fuzzyScore = computeFuzzyScore(normQuery, normTitle, normArtist)
            if (fuzzyScore > 0) {
                // Strictly bounded between 30 and 100 so it can never outrank exact matches
                totalScore = fuzzyScore.coerceIn(30, 100)
            }
        }

        return totalScore
    }

    /**
     * Searches and ranks tracks against [query].
     */
    fun search(tracks: List<Track>, query: String): List<Track> {
        val normQuery = normalize(query)
        if (normQuery.isEmpty()) return emptyList()

        return tracks
            .mapNotNull { track ->
                val s = score(track, normQuery)
                if (s > 0) track to s else null
            }
            .sortedWith(
                compareByDescending<Pair<Track, Int>> { it.second }
                    .thenBy { it.first.title.lowercase() }
            )
            .map { it.first }
    }

    private fun isTokenMatch(target: String, query: String): Boolean {
        val tokens = target.split(' ')
        val queryTokens = query.split(' ')
        return queryTokens.all { qToken -> tokens.any { it == qToken || it.startsWith(qToken) } }
    }

    private fun computeFuzzyScore(query: String, title: String, artist: String): Int {
        // Compare against title tokens
        val titleTokens = title.split(' ')
        for (token in titleTokens) {
            if (token.length >= 3) {
                val dist = levenshteinDistance(query, token)
                if (dist == 1) return 80
                if (dist == 2 && query.length >= 6) return 60
            }
        }

        // Compare against artist tokens
        val artistTokens = artist.split(' ')
        for (token in artistTokens) {
            if (token.length >= 3) {
                val dist = levenshteinDistance(query, token)
                if (dist == 1) return 70
                if (dist == 2 && query.length >= 6) return 50
            }
        }

        return 0
    }

    /**
     * Computes the standard Levenshtein edit distance between two strings.
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        if (m == 0) return n
        if (n == 0) return m

        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost,
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[n]
    }
}
