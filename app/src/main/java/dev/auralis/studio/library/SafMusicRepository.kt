package dev.auralis.studio.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.auralis.studio.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafMusicRepository(private val context: Context) {
    suspend fun scan(treeUris: Set<String>, minimumDurationMs: Long): List<Track> = withContext(Dispatchers.IO) {
        treeUris.flatMap { treeUri ->
            DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.let(::walk).orEmpty()
        }.filter(::isAudio).mapNotNull { document ->
            readTrack(document)?.takeIf { it.durationMs >= minimumDurationMs }
        }.distinctBy(Track::uri)
    }

    private fun walk(root: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        val stack = ArrayDeque<DocumentFile>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            runCatching { current.listFiles() }.getOrDefault(emptyArray()).forEach { child ->
                if (child.isDirectory) stack.add(child) else result += child
            }
        }
        return result
    }

    private fun isAudio(file: DocumentFile): Boolean {
        if (file.type?.startsWith("audio/") == true) return true
        return file.name?.substringAfterLast('.', "")?.lowercase() in AUDIO_EXTENSIONS
    }

    private fun readTrack(file: DocumentFile): Track? = runCatching {
        val uri = file.uri
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf(String::isNotBlank)
                ?: file.name?.substringBeforeLast('.')
                ?: return null
            Track(
                id = uri.toString().hashCode().toLong(),
                uri = uri.toString(),
                title = title,
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                albumId = null,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                dateAddedSeconds = file.lastModified() / 1000,
                displayName = file.name.orEmpty(),
                mimeType = file.type,
                sizeBytes = file.length(),
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toIntOrNull(),
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull(),
            )
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private companion object {
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "amr")
    }
}
