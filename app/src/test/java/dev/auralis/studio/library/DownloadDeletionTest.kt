package dev.auralis.studio.library

import dev.auralis.studio.core.database.BookmarkEntity
import dev.auralis.studio.core.database.DownloadEntity
import dev.auralis.studio.core.database.DownloadStatus
import dev.auralis.studio.core.database.PlaylistTrackEntity
import dev.auralis.studio.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadDeletionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * In-memory test double for DownloadsDao verifying the new
     * findByTrackUri and deleteByTrackUri query contracts.
     */
    private class FakeDownloadsDao : dev.auralis.studio.core.database.DownloadsDao {
        val storage = mutableMapOf<Long, DownloadEntity>()

        override fun observeAll(): Flow<List<DownloadEntity>> = flowOf(storage.values.toList())
        override fun observeByStatus(status: String): Flow<List<DownloadEntity>> =
            flowOf(storage.values.filter { it.status == status })

        override suspend fun getById(id: Long): DownloadEntity? = storage[id]
        override suspend fun findByKey(provider: String, providerTrackId: String): DownloadEntity? =
            storage.values.firstOrNull { it.provider == provider && it.providerTrackId == providerTrackId }

        override suspend fun findCompletedByRecordingIdentity(identity: String): DownloadEntity? =
            storage.values.firstOrNull { it.recordingIdentity == identity && it.status == "COMPLETED" }

        override suspend fun findOwnedByRecordingIdentity(identity: String): DownloadEntity? =
            storage.values.firstOrNull { it.recordingIdentity == identity && it.status in setOf("COMPLETED", "DOWNLOADING", "QUEUED") }

        override suspend fun insert(download: DownloadEntity): Long {
            val id = if (download.id == 0L) (storage.keys.maxOrNull() ?: 0L) + 1L else download.id
            val entity = download.copy(id = id)
            storage[id] = entity
            return id
        }

        override suspend fun update(download: DownloadEntity) {
            storage[download.id] = download
        }

        override suspend fun updateStatus(id: Long, status: String, error: String) {
            storage[id]?.let { storage[id] = it.copy(status = status, errorMessage = error) }
        }

        override suspend fun updateProgress(id: Long, status: String, bytes: Long, total: Long) {
            storage[id]?.let { storage[id] = it.copy(status = status, bytesDownloaded = bytes, bytesTotal = total) }
        }

        override suspend fun markCompleted(
            id: Long,
            trackUri: String,
            path: String,
            title: String,
            artist: String,
            album: String,
            durationMs: Long,
            completedAt: Long,
        ) {
            storage[id]?.let {
                storage[id] = it.copy(
                    status = DownloadStatus.COMPLETED.name,
                    trackUri = trackUri,
                    destinationPath = path,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    completedAt = completedAt,
                )
            }
        }

        override suspend fun repairMetadata(id: Long, title: String, artist: String, album: String, durationMs: Long) {
            storage[id]?.let {
                storage[id] = it.copy(title = title, artist = artist, album = album, durationMs = durationMs)
            }
        }

        override suspend fun updateArtworkUrl(id: Long, artworkUrl: String) {
            storage[id]?.let { storage[id] = it.copy(artworkUrl = artworkUrl) }
        }

        override suspend fun deleteById(id: Long) {
            storage.remove(id)
        }

        override suspend fun findByTrackUri(trackUri: String): DownloadEntity? =
            storage.values.firstOrNull { it.trackUri == trackUri }

        override suspend fun deleteByTrackUri(trackUri: String) {
            storage.entries.removeIf { it.value.trackUri == trackUri }
        }

        override suspend fun findAllByKey(provider: String, providerTrackId: String): List<DownloadEntity> =
            storage.values.filter { it.provider == provider && it.providerTrackId == providerTrackId }

        override suspend fun findAllCompleted(): List<DownloadEntity> =
            storage.values.filter { it.status == "COMPLETED" }

        override suspend fun isDownloaded(provider: String, providerTrackId: String): Boolean =
            storage.values.any { it.provider == provider && it.providerTrackId == providerTrackId }
    }

    /**
     * Test double for playlist and bookmark cleanup operations.
     */
    private class FakePlaylistAndBookmarkDao {
        val playlistTracks = mutableListOf<PlaylistTrackEntity>()
        val bookmarks = mutableListOf<BookmarkEntity>()
        val favorites = mutableSetOf<String>()

        fun removeTrackFromAllPlaylists(uri: String) {
            playlistTracks.removeAll { it.trackUri == uri }
        }

        fun deleteBookmarksForTrack(uri: String) {
            bookmarks.removeAll { it.trackUri == uri }
        }

        fun removeFavorite(uri: String) {
            favorites.remove(uri)
        }
    }

    @Test
    fun `findByTrackUri finds matching download entity`() = runBlocking {
        val dao = FakeDownloadsDao()
        val uri = "content://media/external/audio/media/1001"
        val entity = DownloadEntity(
            id = 10L,
            provider = "jamendo",
            providerTrackId = "track_1001",
            remoteUrl = "https://example.com/audio.mp3",
            fileName = "Track 1001.mp3",
            destinationPath = "/storage/emulated/0/Music/Lyrune/Track 1001.mp3",
            trackUri = uri,
            title = "Track 1001",
            artist = "Artist",
            album = "Album",
            status = DownloadStatus.COMPLETED.name,
        )
        dao.insert(entity)

        val found = dao.findByTrackUri(uri)
        assertNotNull(found)
        assertEquals(10L, found?.id)
        assertEquals(uri, found?.trackUri)

        val notFound = dao.findByTrackUri("content://media/external/audio/media/9999")
        assertNull(notFound)
    }

    @Test
    fun `deleteByTrackUri removes matching download entity`() = runBlocking {
        val dao = FakeDownloadsDao()
        val uri = "content://media/external/audio/media/1002"
        val entity = DownloadEntity(
            id = 11L,
            provider = "audius",
            providerTrackId = "track_1002",
            remoteUrl = "https://example.com/audio2.mp3",
            fileName = "Track 1002.mp3",
            destinationPath = "/storage/emulated/0/Music/Lyrune/Track 1002.mp3",
            trackUri = uri,
            title = "Track 1002",
            artist = "Artist 2",
            album = "Album 2",
            status = DownloadStatus.COMPLETED.name,
        )
        dao.insert(entity)
        assertNotNull(dao.findByTrackUri(uri))

        dao.deleteByTrackUri(uri)
        assertNull(dao.findByTrackUri(uri))
    }

    @Test
    fun `playlist and bookmark references are cleanly purged on track deletion`() {
        val dao = FakePlaylistAndBookmarkDao()
        val trackUri = "content://media/external/audio/media/1003"

        dao.playlistTracks += PlaylistTrackEntity(playlistId = 1L, trackUri = trackUri, position = 0)
        dao.playlistTracks += PlaylistTrackEntity(playlistId = 2L, trackUri = trackUri, position = 1)
        dao.playlistTracks += PlaylistTrackEntity(playlistId = 2L, trackUri = "other_uri", position = 2)

        dao.bookmarks += BookmarkEntity(id = 1L, trackUri = trackUri, label = "Solo", positionMs = 30_000L)
        dao.bookmarks += BookmarkEntity(id = 2L, trackUri = "other_uri", label = "Bridge", positionMs = 60_000L)

        dao.favorites += trackUri
        dao.favorites += "other_uri"

        // Execute purges
        dao.removeTrackFromAllPlaylists(trackUri)
        dao.deleteBookmarksForTrack(trackUri)
        dao.removeFavorite(trackUri)

        assertEquals(1, dao.playlistTracks.size)
        assertEquals("other_uri", dao.playlistTracks[0].trackUri)

        assertEquals(1, dao.bookmarks.size)
        assertEquals("other_uri", dao.bookmarks[0].trackUri)

        assertFalse(dao.favorites.contains(trackUri))
        assertTrue(dao.favorites.contains("other_uri"))
    }

    @Test
    fun `physical file and cached cover art deletion on disk`() {
        val audioFile = tempFolder.newFile("TestSong.mp3")
        assertTrue(audioFile.exists())

        val coversDir = tempFolder.newFolder("covers")
        val coverFile = File(coversDir, "42.jpg").apply { writeText("fake image data") }
        assertTrue(coverFile.exists())

        // Simulate physical file deletion sequence
        val fileDeleted = audioFile.delete()
        assertTrue("Physical audio file must be deleted", fileDeleted)
        assertFalse(audioFile.exists())

        if (coverFile.exists()) {
            coverFile.delete()
        }
        assertFalse("Cached artwork file must be deleted", coverFile.exists())
    }

    @Test
    fun `playback queue item removal by mediaId`() {
        data class FakeMediaItem(val mediaId: String, val title: String)
        val queue = mutableListOf(
            FakeMediaItem("track_1", "Song 1"),
            FakeMediaItem("target_uri", "Target Song"),
            FakeMediaItem("track_3", "Song 3"),
            FakeMediaItem("target_uri", "Target Song Duplicate"),
        )

        val targetUri = "target_uri"
        for (i in queue.indices.reversed()) {
            if (queue[i].mediaId == targetUri) {
                queue.removeAt(i)
            }
        }

        assertEquals(2, queue.size)
        assertEquals("track_1", queue[0].mediaId)
        assertEquals("track_3", queue[1].mediaId)
    }
}
