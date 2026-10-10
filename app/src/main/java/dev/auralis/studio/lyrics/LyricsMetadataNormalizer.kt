package dev.auralis.studio.lyrics

import dev.auralis.studio.online.SearchNormalization

/**
 * Intelligent cleanup and multilingual normalization for audio metadata,
 * with comprehensive Arabic transliteration and artist alias resolution.
 */
object LyricsMetadataNormalizer {
    private val extension = Regex("\\.(mp3|m4a|aac|flac|ogg|opus|wav)$", RegexOption.IGNORE_CASE)
    private val trackNumber = Regex("^\\s*\\d{1,3}[ ._-]+")
    private val bracketed = Regex("[\\[(]([^]\\)]*)[]\\)]")
    private val noise = Regex(
        "\\b(official\\s+(music\\s+)?(audio|video)|lyrics?|lyric\\s+video|audio\\s+only|visuali[sz]er|4k|hd|remaster(ed)?|clip\\s+officiel)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val emptyBrackets = Regex("[\\[(]\\s*[]\\)]")

    private val channelPrefix = Regex(
        """^(?:TikTok(?:\s+Music)?|Status|BGM|Reels|Trending|Shorts|Full\s+Song|Remix|Slowed(?:\s+\+?\s*Reverb)?)\s*[-–—:]\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val arabicBracketNoiseWords = listOf(
        "فيديو كليب", "فيديو", "كليب", "حصريا", "حصرياً", "أغنية", "اغنية", "كاملة", "مترجمة", "كلمات",
        "مع الكلمات", "ريمكس", "أوديو", "اوديو", "المطرب", "الفنان", "الرسمي", "الرسمية",
        "جلسة", "حفلة", "طرب", "مكس", "روقان", "هادئ", "مسرع", "مبطأ", "بطيء", "بطيئ",
        "تراك", "كلبس", "بدون نت", "بدون موسيقى"
    )

    private val arabicPromotionalNoise = Regex(
        """(?<=^|[\s_—–\-|:])(?:فيديو\s+كليب|فيديو|كليب|حصريا|حصرياً|الرسمي|الرسمية|مترجمة|أوديو|اوديو|المطرب|الفنان)(?=$|[\s_—–\-|:])""",
        RegexOption.IGNORE_CASE,
    )

    private val arabicTrailingNoise = Regex(
        """\s*[-–—|:]\s*(?:فيديو\s+كليب|فيديو|كليب|حصريا|حصرياً|الرسمي|الرسمية|مترجمة|أوديو|اوديو|كلمات|مع\s+الكلمات|كاملة|روقان|مسرع|مبطأ|بدون\s+موسيقى)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private fun containsArabicNoise(text: String): Boolean {
        return arabicBracketNoiseWords.any { text.contains(it) }
    }

    // Curated bidirectional aliases for popular Arabic & regional artists in LRCLIB / Spotify / Apple
    private val ARABIC_TO_LATIN_ARTISTS = mapOf(
        "عمرو دياب" to listOf("Amr Diab"),
        "تامر حسني" to listOf("Tamer Hosny", "Tamer Hosni"),
        "محمد حماقي" to listOf("Mohamed Hamaki", "Hamaki"),
        "شيرين" to listOf("Sherine", "Sherine Abdel Wahab"),
        "شيرين عبد الوهاب" to listOf("Sherine", "Sherine Abdel Wahab"),
        "فيروز" to listOf("Fairouz", "Fairuz"),
        "كاظم الساهر" to listOf("Kadim Al Sahir", "Kazem Al Saher", "Kathem Al Saher"),
        "ماجد المهندس" to listOf("Majid Al Mohandis", "Maged El Mohandes"),
        "حسين الجسمي" to listOf("Hussain Al Jassmi", "Hussein Al Jasmi"),
        "نانسي عجرم" to listOf("Nancy Ajram"),
        "اليسا" to listOf("Elissa"),
        "إليسا" to listOf("Elissa"),
        "اصالة" to listOf("Assala", "Assala Nasri"),
        "أصالة" to listOf("Assala", "Assala Nasri"),
        "أصالة نصري" to listOf("Assala", "Assala Nasri"),
        "نجوى كرم" to listOf("Najwa Karam"),
        "وائل كفوري" to listOf("Wael Kfoury"),
        "راغب علامة" to listOf("Ragheb Alama"),
        "جورج وسوف" to listOf("George Wassouf"),
        "ام كلثوم" to listOf("Umm Kulthum", "Om Kalthoum", "Oum Kalthoum"),
        "أم كلثوم" to listOf("Umm Kulthum", "Om Kalthoum", "Oum Kalthoum"),
        "عبد الحليم حافظ" to listOf("Abdel Halim Hafez"),
        "محمد عبده" to listOf("Mohamed Abdo", "Mohammed Abdu"),
        "طلال مداح" to listOf("Talal Maddah"),
        "سيف نبيل" to listOf("Saif Nabeel"),
        "نور الزين" to listOf("Nour El Zein", "Noor Al Zain"),
        "محمود التركي" to listOf("Mahmoud Al Turky", "Mahmoud El Tourky"),
        "رحمة رياض" to listOf("Rahma Riad"),
        "اصيل هميم" to listOf("Aseel Hameem"),
        "أصيل هميم" to listOf("Aseel Hameem"),
        "علي جاسم" to listOf("Ali Jassim"),
        "مصطفى العبدالله" to listOf("Mustafa Al Abdullah"),
        "ياس خضر" to listOf("Yas Khodr", "Yass Khodr"),
        "ناظم الغزالي" to listOf("Nazem Al Ghazali"),
        "سعدون جابر" to listOf("Saadoun Jaber"),
        "سعد لمجرد" to listOf("Saad Lamjarred"),
        "زهير البهاوي" to listOf("Zouhair Bahaoui"),
        "بلقيس" to listOf("Balqees"),
        "حمزة نمرة" to listOf("Hamza Namira"),
        "احمد سعد" to listOf("Ahmed Saad"),
        "أحمد سعد" to listOf("Ahmed Saad"),
        "ويجز" to listOf("Wegz"),
        "مروان بابلو" to listOf("Marwan Pablo"),
        "مروان موسى" to listOf("Marwan Moussa"),
        "عفروتو" to listOf("Afroto"),
        "سيلاوي" to listOf("Siilawy"),
        "الشامي" to listOf("Al Shami", "El Shami"),
        "فضل شاكر" to listOf("Fadel Chaker"),
        "صابر الرباعي" to listOf("Saber Rebai"),
        "كارول سماحة" to listOf("Carole Samaha"),
        "ميريام فارس" to listOf("Myriam Fares"),
        "زياد برجي" to listOf("Ziad Bourji"),
        "ادهم نابلسي" to listOf("Adham Nabulsi"),
        "أدهم نابلسي" to listOf("Adham Nabulsi"),
        "ناصيف زيتون" to listOf("Nassif Zeytoun"),
        "حاتم العراقي" to listOf("Hatim Al Iraqi", "Hatem Al Iraqi"),
        "وليد الشامي" to listOf("Waleed Al Shami"),
        "حسن شاكوش" to listOf("Hassan Shakosh", "Hassan Shakoush"),
        "عمر كمال" to listOf("Omar Kamal"),
        "حمو بيكا" to listOf("Hamo Bika"),
        "رضا البحراوي" to listOf("Reda El Bahrawy"),
        "عبد المجيد عبد الله" to listOf("Abdul Majeed Abdullah", "Abdulmajeed Abdullah"),
        "عبدالمجيد عبدالله" to listOf("Abdul Majeed Abdullah", "Abdulmajeed Abdullah"),
        "راشد الماجد" to listOf("Rashed Al Majed", "Rashed Almajid"),
        "محمد رمضان" to listOf("Mohamed Ramadan"),
        "مسلم" to listOf("Muslim"),
        "نوال الزغبي" to listOf("Nawal El Zoghbi"),
        "سلطان العماني" to listOf("Sultan Al Omani"),
        "علي صابر" to listOf("Ali Saber"),
        "اوراس ستار" to listOf("Auras Star"),
        "حسام الرسام" to listOf("Hussam Al Rassam"),
        "جعفر الغزال" to listOf("Jaafar Al Ghazal"),
        "سارية السواس" to listOf("Saria Al Sawas"),
    )

    fun isArabic(text: String): Boolean = text.any { it in '\u0600'..'\u06FF' }

    /**
     * Aggressive Arabic normalization: strips diacritics, Tatweel, and normalizes
     * Alefs, Taa Marbuta, and Yaa.
     */
    fun normalizeArabic(text: String): String {
        if (text.isBlank()) return ""
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch in '\u064B'..'\u065F' || ch == '\u0670' || ch == 'ـ' -> { /* ignore tashkeel & tatweel */ }
                ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> sb.append('ا')
                ch == 'ة' -> sb.append('ه')
                ch == 'ى' || ch == 'ئ' || ch == 'ی' -> sb.append('ي')
                ch == 'ك' || ch == 'ک' -> sb.append('ك')
                ch.isWhitespace() -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Phonetic transliteration from Arabic characters to standard Latin representation.
     * Useful when querying LRCLIB which often catalogs Arabic songs under Latin titles.
     */
    fun transliterateArabicToLatin(arabic: String): String {
        if (arabic.isBlank()) return ""
        val clean = normalizeArabic(arabic).lowercase()
        val sb = StringBuilder(clean.length * 2)

        var i = 0
        while (i < clean.length) {
            val ch = clean[i]
            when (ch) {
                'ا' -> sb.append('a')
                'ب' -> sb.append('b')
                'ت' -> sb.append('t')
                'ث' -> sb.append("th")
                'ج' -> sb.append('j')
                'ح' -> sb.append('h')
                'خ' -> sb.append("kh")
                'د' -> sb.append('d')
                'ذ' -> sb.append("dh")
                'ر' -> sb.append('r')
                'ز' -> sb.append('z')
                'س' -> sb.append('s')
                'ش' -> sb.append("sh")
                'ص' -> sb.append('s')
                'ض' -> sb.append('d')
                'ط' -> sb.append('t')
                'ظ' -> sb.append('z')
                'ع' -> sb.append('a')
                'غ' -> sb.append("gh")
                'ف' -> sb.append('f')
                'ق' -> sb.append('q')
                'ك' -> sb.append('k')
                'ل' -> sb.append('l')
                'م' -> sb.append('m')
                'ن' -> sb.append('n')
                'ه' -> sb.append('h')
                'و' -> sb.append('w')
                'ي' -> sb.append('y')
                'ء' -> { /* glottal stop ignored */ }
                ' ' -> sb.append(' ')
                else -> {
                    if (ch.isLetterOrDigit()) sb.append(ch) else sb.append(' ')
                }
            }
            i++
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Checks if two artist strings refer to the same artist, handling Arabic <-> Latin
     * equivalencies and alias mapping.
     */
    fun areArtistsEquivalent(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val cleanA = a.trim()
        val cleanB = b.trim()
        if (cleanA.equals(cleanB, ignoreCase = true)) return true

        val normA = SearchNormalization.normalize(cleanA)
        val normB = SearchNormalization.normalize(cleanB)
        if (normA == normB) return true
        if (normA.contains(normB) || normB.contains(normA)) return true

        val normArabicA = normalizeArabic(cleanA)
        val normArabicB = normalizeArabic(cleanB)
        if (normArabicA == normArabicB && normArabicA.isNotBlank()) return true

        // Check alias mapping
        for ((ar, latinList) in ARABIC_TO_LATIN_ARTISTS) {
            val normAr = normalizeArabic(ar)
            val aIsAr = normArabicA == normAr || normArabicA.contains(normAr)
            val bIsAr = normArabicB == normAr || normArabicB.contains(normAr)

            for (latin in latinList) {
                val normLatin = SearchNormalization.normalize(latin)
                val aIsLatin = normA == normLatin || normA.contains(normLatin)
                val bIsLatin = normB == normLatin || normB.contains(normLatin)

                if ((aIsAr && bIsLatin) || (bIsAr && aIsLatin)) return true
            }
        }

        // Check transliterated phonetic similarity
        val transA = if (isArabic(cleanA)) transliterateArabicToLatin(cleanA) else normA
        val transB = if (isArabic(cleanB)) transliterateArabicToLatin(cleanB) else normB
        if (transA.isNotBlank() && transB.isNotBlank() && (transA == transB || transA.contains(transB) || transB.contains(transA))) {
            return true
        }

        return false
    }

    /**
     * Returns known Latin aliases for an Arabic artist name.
     */
    fun getArtistLatinAliases(artistName: String): List<String> {
        val normAr = normalizeArabic(artistName)
        for ((ar, latinList) in ARABIC_TO_LATIN_ARTISTS) {
            if (normalizeArabic(ar) == normAr || normAr.contains(normalizeArabic(ar))) {
                return latinList
            }
        }
        if (isArabic(artistName)) {
            val trans = transliterateArabicToLatin(artistName)
            if (trans.isNotBlank()) return listOf(trans)
        }
        return emptyList()
    }

    /**
     * Generates prioritized variations for title and artist to maximize LRCLIB lookup hits.
     */
    fun searchVariations(title: String, artist: String): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val norm = normalize(title, artist)
        val cleanT = norm.title
        val cleanA = norm.artist

        fun addVariationsFor(t: String, a: String) {
            if (t.isBlank()) return
            list.add(t to a)
            if (isArabic(a) && a.isNotBlank()) {
                val aliases = getArtistLatinAliases(a)
                for (alias in aliases) {
                    list.add(t to alias)
                }
            }
            if (isArabic(t)) {
                val transTitle = transliterateArabicToLatin(t)
                if (transTitle.isNotBlank()) {
                    val targetArtist = if (isArabic(a) && a.isNotBlank()) {
                        getArtistLatinAliases(a).firstOrNull() ?: a
                    } else a
                    list.add(transTitle to targetArtist)
                }
            }
        }

        addVariationsFor(cleanT, cleanA)

        val rawCleanT = title(title)
        val rawCleanA = artist(artist)
        if (rawCleanT.isNotBlank() && (rawCleanT != cleanT || rawCleanA != cleanA)) {
            addVariationsFor(rawCleanT, rawCleanA)
        }

        return list.distinct()
    }

    fun title(raw: String): String {
        var value = raw.trim().replace(extension, "").replace('_', ' ')
        value = channelPrefix.replace(value, "")
        value = value.replace(trackNumber, "")
        value = bracketed.replace(value) { match ->
            if (noise.containsMatchIn(match.groupValues[1]) || containsArabicNoise(match.groupValues[1])) "" else match.value
        }
        value = noise.replace(value, "")
        value = arabicTrailingNoise.replace(value, "")
        value = arabicPromotionalNoise.replace(value, "")
        value = emptyBrackets.replace(value, "")
        return value.replace(Regex("\\s{2,}"), " ").trim(' ', '-', '–', '—', '|', ':')
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
