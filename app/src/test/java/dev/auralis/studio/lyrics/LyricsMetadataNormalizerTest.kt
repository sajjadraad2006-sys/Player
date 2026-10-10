package dev.auralis.studio.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMetadataNormalizerTest {
    @Test fun removesFilenameAndPromotionalNoise() {
        assertEquals("Song Name", LyricsMetadataNormalizer.title("01 - Song_Name (Official Video).mp3"))
        assertEquals("Song Name", LyricsMetadataNormalizer.title("Song Name [Lyrics]"))
    }

    @Test fun preservesLegitimateBracketedTitle() {
        assertEquals("Song (Live at Home)", LyricsMetadataNormalizer.title("Song (Live at Home).flac"))
    }

    @Test fun unknownArtistBecomesAbsent() {
        assertEquals("", LyricsMetadataNormalizer.artist("Unknown Artist"))
        assertEquals("The Artist", LyricsMetadataNormalizer.artist("The Artist"))
        assertEquals("Selena Gomez", LyricsMetadataNormalizer.artist("SelenaGomezVEVO"))
        assertEquals("Adele", LyricsMetadataNormalizer.artist("Adele - Topic"))
    }

    @Test fun normalizesYouTubeTitleAndChannel() {
        val norm = LyricsMetadataNormalizer.normalize(
            "Selena Gomez - Love You Like A Love Song (Official Music Video)",
            "SelenaGomezVEVO",
        )
        assertEquals("Love You Like A Love Song", norm.title)
        assertEquals("Selena Gomez", norm.artist)
        assertEquals("Selena Gomez Love You Like A Love Song", norm.query)
    }

    @Test fun normalizesArabicNoiseAndAliases() {
        assertEquals("تملي معاك", LyricsMetadataNormalizer.title("تملي معاك (فيديو كليب حصريا)"))
        val norm = LyricsMetadataNormalizer.normalize("عمرو دياب - تملي معاك (فيديو كليب حصريا)", "")
        assertEquals("تملي معاك", norm.title)
        assertEquals("عمرو دياب", norm.artist)
        assertTrue(LyricsMetadataNormalizer.areArtistsEquivalent("عمرو دياب", "Amr Diab"))
        assertTrue(LyricsMetadataNormalizer.areArtistsEquivalent("فيروز", "Fairouz"))
        assertTrue(LyricsMetadataNormalizer.areArtistsEquivalent("كاظم الساهر", "Kadim Al Sahir"))
        assertTrue(LyricsMetadataNormalizer.areArtistsEquivalent("تامر حسني", "Tamer Hosny"))
    }

    @Test fun generatesArabicSearchVariations() {
        val variations = LyricsMetadataNormalizer.searchVariations("تملي معاك", "عمرو دياب")
        assertTrue(variations.isNotEmpty())
        assertTrue(variations.any { it.first == "تملي معاك" && it.second == "Amr Diab" })
    }

    @Test fun preservesRealWordsLikeOudAndTarabInArabicTitles() {
        assertEquals("عود البطل", LyricsMetadataNormalizer.title("عود البطل (فيديو كليب حصريا)"))
        assertEquals("سلطان الطرب", LyricsMetadataNormalizer.title("سلطان الطرب (جلسة)"))
        assertEquals("تقاسيم عود", LyricsMetadataNormalizer.title("تقاسيم عود - كاملة"))
    }
}
