package dev.auralis.desktop.online

import dev.auralis.studio.lyrics.*
import dev.auralis.studio.core.model.LyricsSource
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class LyricsPipelineTest {
    private class Storage : LyricsStorage {
        var embeddedText: String? = "[00:01.00]Embedded"
        var sidecarText: String? = "[00:02.00]Sidecar"
        var record: LyricsRecord? = LyricsRecord(lrc = "[00:03.00]Manual", manual = true)
        override suspend fun embedded(track: LyricsTrack) = embeddedText
        override suspend fun sidecar(track: LyricsTrack) = sidecarText
        override suspend fun read(key: String) = record
        override suspend fun write(key: String, record: LyricsRecord) { this.record = record }
        override suspend fun lastMiss(key: String) = 0L
        override suspend fun setMiss(key: String, at: Long) {}
        override suspend fun offset(key: String) = 500L
        override suspend fun setOffset(key: String, offset: Long) {}
        override fun online() = false
    }
    @Test fun offlinePriorityManualChoiceAndOffsetMatchAndroid() = runBlocking {
        val storage = Storage()
        val client = LrclibClient()
        val pipeline = LyricsResolutionPipeline(storage, client)
        val track = LyricsTrack("stable-watch-url", "Song", "Artist", "", 180_000)
        try {
            val embedded = pipeline.resolve(track)
            assertEquals(LyricsSource.EMBEDDED, embedded.source)
            assertEquals(1500L, embedded.seekPositionFor(embedded.lines.first()))
            storage.embeddedText = null
            assertEquals("Manual", pipeline.resolve(track, forceRefresh = true).lines.first().text)
            storage.record = storage.record!!.copy(manual = false)
            assertEquals("Sidecar", pipeline.resolve(track).lines.first().text)
            storage.sidecarText = null
            assertEquals("Manual", pipeline.resolve(track).lines.first().text)
            storage.record = null
            assertTrue(pipeline.resolve(track).lines.isEmpty())
        } finally { client.close() }
    }
    @Test fun lookupCleanupPreservesSongAndVersionIdentity() {
        assertEquals("Song", LyricsMetadataNormalizer.title("Song [Official Video]"))
        assertEquals("Song (Live)", LyricsMetadataNormalizer.title("Song (Live)"))
        assertEquals("The Lyrics", LyricsMetadataNormalizer.title("The Lyrics"))
        assertEquals("Song (Remastered)", LyricsMetadataNormalizer.title("Song (Remastered)"))
        assertEquals("Song (Lyrics of my life)", LyricsMetadataNormalizer.title("Song (Lyrics of my life)"))
    }
}
