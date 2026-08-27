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
    fun testIdentityNormalization_preservesArabicWawWithHamzaAndYehWithHamza() {
        // ؤ is NOT converted to و
        assertEquals("مؤتمر", IdentityNormalization.normalize("مؤتمر"))
        assertNotEquals("موتمر", IdentityNormalization.normalize("مؤتمر"))
        assertEquals("سؤال", IdentityNormalization.normalize("سؤال"))
        assertNotEquals("سوال", IdentityNormalization.normalize("سؤال"))
        assertEquals("رؤية", IdentityNormalization.normalize("رؤية"))
        assertNotEquals("رويه", IdentityNormalization.normalize("رؤية"))

        // ئ is NOT converted to ي
        assertEquals("شاطئ", IdentityNormalization.normalize("شاطئ"))
        assertNotEquals("شاطي", IdentityNormalization.normalize("شاطئ"))
        assertEquals("ذئب", IdentityNormalization.normalize("ذئب"))
        assertNotEquals("ذيب", IdentityNormalization.normalize("ذئب"))
        assertEquals("مسائل", IdentityNormalization.normalize("مسائل"))
        assertNotEquals("مسايل", IdentityNormalization.normalize("مسائل"))

        // ة is NOT converted to ه
        assertEquals("حبيبة", IdentityNormalization.normalize("حبيبة"))
        assertNotEquals("حبيبه", IdentityNormalization.normalize("حبيبة"))
    }

    @Test
    fun testCleanTitleForIdentity_stripsDisplayNoise_preservesSemanticQualifiers() {
        // Release / Display noise is stripped
        assertEquals("hello", IdentityNormalization.cleanTitleForIdentity("Hello (Official Video)"))
        assertEquals("hello", IdentityNormalization.cleanTitleForIdentity("Hello [Official Music Video]"))
        assertEquals("hello", IdentityNormalization.cleanTitleForIdentity("Hello (Official Audio)"))
        assertEquals("hello", IdentityNormalization.cleanTitleForIdentity("Hello [HD]"))
        assertEquals("hello", IdentityNormalization.cleanTitleForIdentity("Hello (Lyric Video)"))
        assertEquals("hello", IdentityNormalization.cleanTitleForIdentity("Hello VEVO"))

        // Semantic qualifiers are NOT stripped
        val live = IdentityNormalization.cleanTitleForIdentity("Hello (Live)")
        assertTrue("Live must be preserved in: $live", live.contains("live"))

        val remix = IdentityNormalization.cleanTitleForIdentity("Hello (Remix)")
        assertTrue("Remix must be preserved in: $remix", remix.contains("remix"))

        val acoustic = IdentityNormalization.cleanTitleForIdentity("Hello (Acoustic)")
        assertTrue("Acoustic must be preserved in: $acoustic", acoustic.contains("acoustic"))

        val radioEdit = IdentityNormalization.cleanTitleForIdentity("Hello (Radio Edit)")
        assertTrue("Radio edit must be preserved in: $radioEdit", radioEdit.contains("radio edit"))
    }

    @Test
    fun testIdentityNormalization_normalizesArtistConjunctions() {
        assertEquals("drake future", IdentityNormalization.normalizeArtist("Drake feat. Future"))
        assertEquals("calvin harris dua lipa", IdentityNormalization.normalizeArtist("Calvin Harris and Dua Lipa"))
        assertEquals("marshmello bastille", IdentityNormalization.normalizeArtist("Marshmello ft. Bastille"))
    }
}
