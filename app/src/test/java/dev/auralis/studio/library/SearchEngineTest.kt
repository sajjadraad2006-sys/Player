package dev.auralis.studio.library

import dev.auralis.studio.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    private fun track(id: Long, title: String, artist: String = "", album: String = "", displayName: String = ""): Track {
        return Track(
            id = id,
            uri = "content://media/external/audio/media/$id",
            title = title,
            artist = artist,
            album = album,
            displayName = displayName.ifBlank { "$title.mp3" },
            durationMs = 180_000L,
            dateAddedSeconds = 1700000000L,
            sizeBytes = 5_000_000L,
        )
    }

    @Test
    fun normalization_stripsNoiseAndExtensions() {
        assertEquals("creep", SearchEngine.normalize("01. Creep (Official Audio).mp3"))
        assertEquals("karma police", SearchEngine.normalize("02 - Karma Police [Official Video].flac"))
        assertEquals("paranoid android", SearchEngine.normalize("Paranoid Android (Remastered)"))
        assertEquals("numb", SearchEngine.normalize("Numb [Lyrics].wav"))
    }

    @Test
    fun arabicNormalization_safelyMatchesVariantsAndStripsTashkeel() {
        val withTashkeel = "فَيْروز"
        val normalized = SearchEngine.normalizeArabic(withTashkeel)
        assertEquals("فيروز", normalized)

        val alifVariants = "أحمد إبراهيم آسر ٱحمد"
        assertEquals("احمد ابراهيم اسر احمد", SearchEngine.normalizeArabic(alifVariants))

        val taaMarbuta = "أغنية جميلة"
        assertEquals("اغنيه جميله", SearchEngine.normalizeArabic(taaMarbuta))
    }

    @Test
    fun ranking_exactTitleOutranksSubstringAndArtist() {
        val tracks = listOf(
            track(1, "Creep (Acoustic)", "Radiohead"),
            track(2, "Creep", "Radiohead"),
            track(3, "The Creep Show", "Various"),
            track(4, "Something Else", "Creep"),
        )

        val results = SearchEngine.search(tracks, "Creep")
        assertEquals(4, results.size)
        // Exact title must be #1
        assertEquals("Creep", results[0].title)
        // Starts with title #2
        assertEquals("Creep (Acoustic)", results[1].title)
        // Token match #3
        assertEquals("The Creep Show", results[2].title)
        // Artist match #4
        assertEquals("Something Else", results[3].title)
    }

    @Test
    fun ranking_fuzzyMatchesNeverOutrankExactMatches() {
        val tracks = listOf(
            track(1, "Karma Police", "Radiohead"),
            track(2, "Something Else", "Other"),
            track(3, "Radio", "Artist"),
        )

        // "radiohed" typo should match Radiohead via fuzzy
        val results = SearchEngine.search(tracks, "radiohed")
        assertTrue("Typo query 'radiohed' should find Radiohead tracks", results.isNotEmpty())
        assertEquals("Karma Police", results[0].title) // artist is Radiohead
    }

    @Test
    fun arabicSearch_crossMatchesCorrectly() {
        val tracks = listOf(
            track(1, "سألوني الناس", "فيروز"),
            track(2, "نسم علينا الهوى", "فيروز"),
            track(3, "أغدا ألقاك", "أم كلثوم"),
        )

        val resultsFairuz = SearchEngine.search(tracks, "فيروز")
        assertEquals(2, resultsFairuz.size)

        // Query with Alif variant and Tashkeel
        val resultsOm = SearchEngine.search(tracks, "ام كلثوم")
        assertEquals(1, resultsOm.size)
        assertEquals("أغدا ألقاك", resultsOm[0].title)
    }

    @Test
    fun arabicFlexibleSearch_matchesVariationsAndSubPhrases() {
        val tracks = listOf(
            track(1, "احمد الساعدي عاشت ايمينك يابو زنود السمر", "علي حسين"),
        )

        // Without "ال" prefix
        assertTrue("Query without Al- prefix should match", SearchEngine.search(tracks, "احمد ساعدي").isNotEmpty())

        // Sub-phrase in middle
        assertTrue("Sub-phrase should match", SearchEngine.search(tracks, "زنود السمر").isNotEmpty())

        // Skipping words in between
        assertTrue("Skipping middle words should match", SearchEngine.search(tracks, "احمد الساعدي زنود السمر").isNotEmpty())
    }

    @Test
    fun searchScalesTo10kTracks() {
        // Correctness/scale smoke test, NOT a device benchmark: wall-clock timing
        // on shared CI JVMs is unreliable, so only functional results are asserted.
        val generatedTracks = (1..10_000).map { i ->
            track(
                id = i.toLong(),
                title = "Song Title Number $i Special Edition",
                artist = "Famous Artist ${i % 200}",
                album = "Greatest Album ${i % 50}",
                displayName = "0$i. Song Title Number $i Special Edition (Official Audio).mp3",
            )
        }

        val byTitle = SearchEngine.search(generatedTracks, "Song Title 500")
        assertTrue("Should find matching tracks by title", byTitle.isNotEmpty())
        assertTrue(byTitle.any { it.title.contains("500") })

        val byArtist = SearchEngine.search(generatedTracks, "Artist 42")
        assertTrue("Should find matching tracks by artist", byArtist.isNotEmpty())
        assertTrue(byArtist.all { it.artist == "Famous Artist 42" })
    }
}
