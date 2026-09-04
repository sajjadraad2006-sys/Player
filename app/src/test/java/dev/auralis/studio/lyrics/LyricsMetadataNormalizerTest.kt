package dev.auralis.studio.lyrics

import org.junit.Assert.assertEquals
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
}
