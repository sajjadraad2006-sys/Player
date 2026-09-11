package dev.auralis.desktop.library

import dev.auralis.studio.online.SearchNormalization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class LibraryScalabilityTest {

    @Test
    fun testScalabilityWith10000Tracks() {
        val count = 10_000
        val sampleTitles = listOf(
            "Midnight City", "Celestial Orbit", "Neon Dreams", "فيروز نسيم علينا الهوى",
            "Clair de Lune", "Synthwave Horizon", "عمرو دياب تملي معاك", "Echoes of Eternity",
            "Starry Night [Remastered]", "Astral Projection (Live @ Forum)"
        )
        val sampleArtists = listOf(
            "M83", "Lyrune Studio", "Fairuz", "Debussy", "Amr Diab", "Kavinsky", "Hans Zimmer"
        )

        // Measure generation & allocation of 10,000 lightweight track summaries
        val runtime = Runtime.getRuntime()
        runtime.gc()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()

        val indexingTime = measureTimeMillis {
            val tracks = ArrayList<DesktopTrack>(count)
            for (i in 0 until count) {
                val title = sampleTitles[i % sampleTitles.size] + " #$i"
                val artist = sampleArtists[i % sampleArtists.size]
                tracks.add(
                    DesktopTrack(
                        id = i.toLong(),
                        path = "C:\\Music\\Song_$i.mp3",
                        title = title,
                        artist = artist,
                        album = "Album ${i / 10}",
                        durationMs = 180_000L + (i % 60) * 1000L,
                        sizeBytes = 4_500_000L,
                        lastModified = 1700000000L + i,
                        mimeType = "audio/mpeg",
                    )
                )
            }

            assertEquals(count, tracks.size)

            // 1. Measure direct UI search (contains) across 10,000 tracks
            val directSearchTime = measureTimeMillis {
                val results = tracks.filter { track ->
                    track.title.contains("Celestial", ignoreCase = true) ||
                        track.artist.contains("Celestial", ignoreCase = true)
                }
                assertTrue("Search should locate matching tracks", results.isNotEmpty())
            }
            assertTrue("Direct search over 10,000 tracks must be under 50ms (took ${directSearchTime}ms)", directSearchTime < 50)

            // 2. Measure fuzzy Levenshtein search across 10,000 tracks
            val fuzzySearchTime = measureTimeMillis {
                val results = tracks.filter { track ->
                    track.title.contains("Celestial", ignoreCase = true) ||
                        SearchNormalization.matchRatio(track.title, "celestial") > 0.4f
                }
                assertTrue("Fuzzy search should locate matching tracks", results.isNotEmpty())
            }
            assertTrue("Fuzzy search over 10,000 tracks must complete in reasonable time (took ${fuzzySearchTime}ms)", fuzzySearchTime < 1000)
        }

        runtime.gc()
        val heapAfter = runtime.totalMemory() - runtime.freeMemory()
        val heapUsedMb = (heapAfter - heapBefore) / (1024 * 1024)

        println("Library Scalability: 10,000 tracks indexed in ${indexingTime}ms, heap increase: ${heapUsedMb} MB")
        // 10,000 tracks should consume under 20 MB heap
        assertTrue("10,000 track summaries should use under 20 MB heap (used: $heapUsedMb MB)", heapUsedMb < 20)
    }

    @Test
    fun testMetadataRobustnessWithWeirdUnicodeAndArabic() {
        val weirdInputs = listOf(
            "أغنية جميلة جداً بدون عنوان",
            "Track with emoji 🎵🔥 [Official 4K Video]",
            "   --- Extremely padded title with symbols ---   ",
            "01. 02 - Artist - Long Title (Remastered 2024)",
            "VEVO Official Audio Only",
            "",
            "A".repeat(1000), // Extremely long title
        )

        for (input in weirdInputs) {
            val normalized = SearchNormalization.normalize(input)
            assertTrue("Normalizer should never throw", normalized.length >= 0)
        }
    }
}
