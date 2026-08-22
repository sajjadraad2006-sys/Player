package dev.auralis.studio.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dev.auralis.studio.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MediaStoreMusicRepository(private val context: Context) {
    private val mutex = Mutex()
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private var mediaStoreTracks: List<Track> = emptyList()
    private var safTracks: List<Track> = emptyList()
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    val hasPermission: Boolean
        get() {
            val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    suspend fun refresh(minimumDurationMs: Long = 15_000L): Result<List<Track>> = mutex.withLock {
        if (!hasPermission) return Result.failure(SecurityException("Audio permission is not granted"))
        withContext(Dispatchers.IO) {
            runCatching {
                val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.YEAR,
                )
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
                val args = arrayOf(minimumDurationMs.toString())
                val songs = mutableListOf<Track>()
                context.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    args,
                    "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val trackIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                    val yearIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        songs += Track(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id).toString(),
                            title = cursor.getString(titleIndex)?.takeIf(String::isNotBlank) ?: cursor.getString(nameIndex),
                            artist = cursor.getString(artistIndex)?.takeUnless { it == "<unknown>" }.orEmpty(),
                            album = cursor.getString(albumIndex)?.takeUnless { it == "<unknown>" }.orEmpty(),
                            albumId = cursor.getLong(albumIdIndex).takeIf { it > 0 },
                            durationMs = cursor.getLong(durationIndex),
                            dateAddedSeconds = cursor.getLong(addedIndex),
                            displayName = cursor.getString(nameIndex).orEmpty(),
                            mimeType = cursor.getString(mimeIndex),
                            sizeBytes = cursor.getLong(sizeIndex),
                            trackNumber = cursor.getInt(trackIndex).takeIf { it > 0 },
                            year = cursor.getInt(yearIndex).takeIf { it > 0 },
                        )
                    }
                }
                mediaStoreTracks = songs
                publishMerged()
                songs
            }
        }
    }

    fun track(uri: String): Track? = _tracks.value.firstOrNull { it.uri == uri }

    fun updateSafTracks(tracks: List<Track>) {
        safTracks = tracks
        publishMerged()
    }

    private fun publishMerged() {
        _tracks.value = (mediaStoreTracks + safTracks)
            .distinctBy(Track::uri)
            .sortedBy { it.title.lowercase() }
    }
}
