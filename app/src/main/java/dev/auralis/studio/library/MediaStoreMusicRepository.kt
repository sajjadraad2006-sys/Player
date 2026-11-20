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
                val projection = buildList {
                    add(MediaStore.Audio.Media._ID)
                    add(MediaStore.Audio.Media.TITLE)
                    add(MediaStore.Audio.Media.ARTIST)
                    add(MediaStore.Audio.Media.ALBUM)
                    add(MediaStore.Audio.Media.ALBUM_ID)
                    add(MediaStore.Audio.Media.DURATION)
                    add(MediaStore.Audio.Media.DATE_ADDED)
                    add(MediaStore.Audio.Media.DISPLAY_NAME)
                    add(MediaStore.Audio.Media.MIME_TYPE)
                    add(MediaStore.Audio.Media.SIZE)
                    add(MediaStore.Audio.Media.TRACK)
                    add(MediaStore.Audio.Media.YEAR)
                    add(MediaStore.Audio.Media.DATA)
                    add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        add(MediaStore.Audio.Media.RELATIVE_PATH)
                    }
                }.toTypedArray()
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ? " +
                    "AND ${MediaStore.Audio.Media.TITLE} NOT LIKE 'PTT-%' " +
                    "AND ${MediaStore.Audio.Media.DISPLAY_NAME} NOT LIKE 'PTT-%' " +
                    "AND ${MediaStore.Audio.Media.DISPLAY_NAME} NOT LIKE 'AUD-%-WA%'"
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
                    val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val bucketIndex = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                    val relativePathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    } else -1

                    while (cursor.moveToNext()) {
                        val displayName = cursor.getString(nameIndex).orEmpty()
                        val title = cursor.getString(titleIndex)?.takeIf(String::isNotBlank) ?: displayName
                        val artist = cursor.getString(artistIndex)?.takeUnless { it == "<unknown>" }.orEmpty()
                        val album = cursor.getString(albumIndex)?.takeUnless { it == "<unknown>" }.orEmpty()
                        val duration = cursor.getLong(durationIndex)
                        val mimeType = cursor.getString(mimeIndex)
                        val filePath = if (dataIndex >= 0) cursor.getString(dataIndex) else null
                        val bucketName = if (bucketIndex >= 0) cursor.getString(bucketIndex) else null
                        val relativePath = if (relativePathIndex >= 0) cursor.getString(relativePathIndex) else null

                        if (VoiceNoteFilter.isVoiceNote(
                                path = filePath,
                                relativePath = relativePath,
                                bucketName = bucketName,
                                displayName = displayName,
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = duration,
                                mimeType = mimeType,
                            )
                        ) {
                            continue
                        }

                        val id = cursor.getLong(idIndex)
                        val persistentCover = resolvePersistentCoverUri(filePath, displayName)
                        songs += Track(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id).toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = cursor.getLong(albumIdIndex).takeIf { it > 0 },
                            durationMs = duration,
                            dateAddedSeconds = cursor.getLong(addedIndex),
                            displayName = displayName,
                            mimeType = mimeType,
                            sizeBytes = cursor.getLong(sizeIndex),
                            trackNumber = cursor.getInt(trackIndex).takeIf { it > 0 },
                            year = cursor.getInt(yearIndex).takeIf { it > 0 },
                            customArtworkUri = persistentCover,
                        )
                    }
                }
                mediaStoreTracks = songs
                publishMerged()
                songs
            }
        }
    }

    private fun resolvePersistentCoverUri(filePath: String?, displayName: String): String? {
        val lyruneDir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "Lyrune")
        val baseName = displayName.substringBeforeLast('.').ifBlank { displayName }

        // 1. Check companion cover in Lyrune public folder (persists across reinstall)
        if (lyruneDir.exists() && baseName.isNotBlank()) {
            val candidates = listOf(
                java.io.File(lyruneDir, "$baseName.cover.jpg"),
                java.io.File(lyruneDir, "$baseName.cover.png"),
                java.io.File(lyruneDir, "$baseName.jpg"),
                java.io.File(lyruneDir, "$baseName.png"),
            )
            val match = candidates.firstOrNull { it.exists() && it.length() > 200 }
            if (match != null) return android.net.Uri.fromFile(match).toString()
        }

        // 2. Check sibling cover in audio file parent folder
        if (!filePath.isNullOrBlank()) {
            val file = java.io.File(filePath)
            val parent = file.parentFile
            if (parent != null && parent.exists()) {
                val fBase = file.nameWithoutExtension
                val candidates = listOf(
                    java.io.File(parent, "$fBase.cover.jpg"),
                    java.io.File(parent, "$fBase.cover.png"),
                    java.io.File(parent, "$fBase.jpg"),
                    java.io.File(parent, "cover.jpg"),
                    java.io.File(parent, "folder.jpg"),
                )
                val match = candidates.firstOrNull { it.exists() && it.length() > 200 }
                if (match != null) return android.net.Uri.fromFile(match).toString()
            }
        }
        return null
    }

    fun track(uri: String): Track? {
        val direct = _tracks.value.firstOrNull { it.uri == uri }
        if (direct != null) return direct
        val id = uri.substringAfterLast('/')
        return if (id.isNotEmpty() && id.all { it.isDigit() }) {
            _tracks.value.firstOrNull { it.uri.substringAfterLast('/') == id }
        } else {
            null
        }
    }

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
