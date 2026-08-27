package dev.auralis.studio.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryNormalizationTest {

    @Test
    fun testSearchNormalization_arabicAlefVariants() {
        // أ إ آ ٱ -> standard bare Alif (ا)
        // Note: أغنية ends with ة which search normalization converts to ه
        assertEquals("اغنيه", SearchNormalization.normalize("أغنية"))
        assertEquals("اغنيه", SearchNormalization.normalize("إغنية"))
        assertEquals("اغنيه", SearchNormalization.normalize("آغنية"))
        assertEquals("اغنيه", SearchNormalization.normalize("ٱغنية"))
        // Word without ة at end
        assertEquals("احمد", SearchNormalization.normalize("أحمد"))
        assertEquals("احمد", SearchNormalization.normalize("إحمد"))
    }

    @Test
    fun testSearchNormalization_arabicTaaMarbutaAndYaa() {
        // In search normalization, ة -> ه for forgiving query search
        assertEquals("حبيبه", SearchNormalization.normalize("حبيبة"))
        // Yaa variants -> standard Yaa
        assertEquals("علي", SearchNormalization.normalize("على"))
        assertEquals("شيي", SearchNormalization.normalize("شيئ"))
    }

    @Test
    fun testSearchNormalization_stripsTashkeelAndTatweel() {
        // Tashkeel / Harakat stripped
        assertEquals("مرحبا", SearchNormalization.normalize("مَرْحَبًا"))
        // Tatweel stripped — note: the characters between ـ are the base letters
        assertEquals("موسيقي", SearchNormalization.normalize("مـوسـيـقـى"))
    }

    @Test
    fun testSearchNormalization_stripsReleaseNoise() {
        assertEquals("adele hello", SearchNormalization.normalize("Adele - Hello (Official Video)"))
        assertEquals("song title", SearchNormalization.normalize("Song Title [Official Music Video]"))
        assertEquals("artist name track", SearchNormalization.normalize("Artist Name - Track (Lyrics)"))
        assertEquals("track name", SearchNormalization.normalize("Track Name VEVO"))
    }

    @Test
    fun testSearchNormalization_preservesSemanticVersionQualifiers() {
        // Version qualifiers MUST NOT be stripped from search queries
        val liveQuery = SearchNormalization.normalize("Hotel California Live")
        assertTrue("Live should be preserved in: $liveQuery", liveQuery.contains("live"))

        val remixQuery = SearchNormalization.normalize("Blinding Lights Remix")
        assertTrue("Remix should be preserved in: $remixQuery", remixQuery.contains("remix"))

        val acousticQuery = SearchNormalization.normalize("Layla Acoustic")
        assertTrue("Acoustic should be preserved in: $acousticQuery", acousticQuery.contains("acoustic"))

        val coverQuery = SearchNormalization.normalize("Fast Car Cover")
        assertTrue("Cover should be preserved in: $coverQuery", coverQuery.contains("cover"))
    }

    @Test
    fun testIdentityNormalization_conservativeArabic() {
        // Alef variants normalized
        assertEquals("اغنية", IdentityNormalization.normalize("أغنية"))
        assertEquals("اغنية", IdentityNormalization.normalize("إغنية"))

        // Tashkeel / Tatweel stripped
        assertEquals("مرحبا", IdentityNormalization.normalize("مَرْحَبًا"))

        // CRITICAL INVARIANT: Taa Marbuta is NOT converted to Haa in IdentityNormalization!
        val identityResult = IdentityNormalization.normalize("حبيبة")
        assertEquals("حبيبة", identityResult)
        // Search normalization DOES convert it
        val searchResult = SearchNormalization.normalize("حبيبة")
        assertEquals("حبيبه", searchResult)
        // They must differ
        assertNotEquals(identityResult, searchResult)
    }

    @Test
    fun testIdentityNormalization_normalizesArtistConjunctions() {
        assertEquals("drake future", IdentityNormalization.normalizeArtist("Drake feat. Future"))
        assertEquals("calvin harris dua lipa", IdentityNormalization.normalizeArtist("Calvin Harris and Dua Lipa"))
        assertEquals("marshmello bastille", IdentityNormalization.normalizeArtist("Marshmello ft. Bastille"))
    }
}
