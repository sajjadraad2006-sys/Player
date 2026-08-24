package dev.auralis.studio.lyrics

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.charset.Charset
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads real container metadata through Media3; Android's platform retriever has no lyrics key. */
@OptIn(UnstableApi::class)
object EmbeddedLyricsReader {
    suspend fun read(context: Context, uri: Uri): String? {
        val future = MetadataRetriever.retrieveMetadata(context, MediaItem.fromUri(uri))
        val groups = suspendCancellableCoroutine { continuation ->
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess(continuation::resume)
                        .onFailure(continuation::resumeWithException)
                },
                Executor(Runnable::run),
            )
            continuation.invokeOnCancellation { future.cancel(true) }
        }
        for (groupIndex in 0 until groups.length) {
            val group = groups[groupIndex]
            for (formatIndex in 0 until group.length) {
                val metadata = group.getFormat(formatIndex).metadata ?: continue
                parse(metadata)?.let { return it }
            }
        }
        return null
    }

    internal fun parse(metadata: Metadata): String? {
        var plain: String? = null
        for (index in 0 until metadata.length()) {
            val entry = metadata[index]
            when (entry) {
                is VorbisComment -> if (entry.key.equals("SYNCEDLYRICS", true) || entry.key.equals("LYRICS", true)) {
                    if (entry.value.contains(Regex("\\[\\d{1,3}:\\d{2}"))) return entry.value
                    plain = plain ?: entry.value
                } else if (entry.key.equals("UNSYNCEDLYRICS", true)) plain = plain ?: entry.value
                is TextInformationFrame -> if (entry.description?.contains("LYRICS", true) == true) {
                    plain = plain ?: entry.values.firstOrNull()
                }
                is CommentFrame -> if (entry.description.contains("LYRICS", true)) plain = plain ?: entry.text
                is BinaryFrame -> when (entry.id) {
                    "SYLT" -> parseSylt(entry.data)?.let { return it }
                    "USLT" -> plain = plain ?: parseUslt(entry.data)
                }
            }
        }
        return plain?.takeIf(String::isNotBlank)
    }

    internal fun parseUslt(data: ByteArray): String? {
        if (data.size < 5) return null
        val encoding = data[0].toInt() and 0xFF
        val descriptorStart = 4
        val descriptorEnd = findTerminator(data, descriptorStart, encoding)
        val textStart = descriptorEnd + terminatorSize(encoding)
        if (textStart > data.size) return null
        return decode(data, textStart, data.size, encoding).trim('\u0000', ' ', '\n', '\r').takeIf(String::isNotBlank)
    }

    internal fun parseSylt(data: ByteArray): String? {
        if (data.size < 11) return null
        val encoding = data[0].toInt() and 0xFF
        val timestampFormat = data[4].toInt() and 0xFF
        if (timestampFormat != 2) return null // MPEG-frame timestamps need stream-specific conversion.
        var cursor = findTerminator(data, 6, encoding) + terminatorSize(encoding)
        val result = StringBuilder()
        while (cursor < data.size) {
            val textEnd = findTerminator(data, cursor, encoding)
            val timestampOffset = textEnd + terminatorSize(encoding)
            if (timestampOffset + 4 > data.size) break
            val text = decode(data, cursor, textEnd, encoding).trim()
            val timeMs = ((data[timestampOffset].toLong() and 0xFF) shl 24) or
                ((data[timestampOffset + 1].toLong() and 0xFF) shl 16) or
                ((data[timestampOffset + 2].toLong() and 0xFF) shl 8) or
                (data[timestampOffset + 3].toLong() and 0xFF)
            if (text.isNotBlank()) {
                val minutes = timeMs / 60_000
                val seconds = (timeMs % 60_000) / 1_000
                val hundredths = (timeMs % 1_000) / 10
                result.append("[%02d:%02d.%02d]%s\n".format(minutes, seconds, hundredths, text))
            }
            cursor = timestampOffset + 4
        }
        return result.toString().trim().takeIf(String::isNotBlank)
    }

    private fun findTerminator(data: ByteArray, start: Int, encoding: Int): Int {
        val width = terminatorSize(encoding)
        var index = start
        while (index + width <= data.size) {
            if (data[index] == 0.toByte() && (width == 1 || data[index + 1] == 0.toByte())) return index
            index += width
        }
        return data.size
    }

    private fun terminatorSize(encoding: Int): Int = if (encoding == 1 || encoding == 2) 2 else 1

    private fun decode(data: ByteArray, start: Int, end: Int, encoding: Int): String {
        if (start >= end || start >= data.size) return ""
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charset.forName("UTF-16")
            2 -> Charset.forName("UTF-16BE")
            else -> Charsets.UTF_8
        }
        return data.copyOfRange(start, end.coerceAtMost(data.size)).toString(charset)
    }
}
