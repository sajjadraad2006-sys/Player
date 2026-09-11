package dev.auralis.desktop.lyrics

import dev.auralis.desktop.library.DesktopMediaProbe
import dev.auralis.desktop.library.DesktopTrack
import dev.auralis.desktop.storage.DesktopPaths
import dev.auralis.studio.lyrics.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

fun DesktopTrack.lyricsTrack() = LyricsTrack(path, title, artist, album.takeUnless { path.startsWith("http") }.orEmpty(), durationMs)

class DesktopLyricsRepository(directory: File = File(DesktopPaths.appDataDir, "lyrics")) : LyricsStorage {
    private val dir = directory.apply { mkdirs() }
    @Volatile var allowOnline = true
    override fun online() = allowOnline
    val pipeline = LyricsResolutionPipeline(this, LrclibClient())
    private val json = Json { ignoreUnknownKeys = true }
    @Serializable private data class Entry(val record: LyricsRecord? = null, val offset: Long? = null, val miss: Long = 0)
    private fun file(key: String) = File(dir, MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) } + ".json")
    private fun entry(key: String): Entry = runCatching { json.decodeFromString<Entry>(file(key).readText()) }.getOrDefault(Entry())
    private fun save(key: String, entry: Entry) = DesktopPaths.writeAtomically(file(key), json.encodeToString(entry))
    override suspend fun embedded(track: LyricsTrack): String? = withContext(Dispatchers.IO) {
        if (track.key.startsWith("http")) return@withContext null
        val tags = DesktopMediaProbe.tags(DesktopMediaProbe.read(File(track.key)))
        tags.entries.sortedBy { if (it.key.contains("synced")) 0 else 1 }
            .firstOrNull { it.key.contains("lyrics") && it.value.isNotBlank() }?.value
    }
    override suspend fun sidecar(track: LyricsTrack): String? = withContext(Dispatchers.IO) {
        if(track.key.startsWith("http")) return@withContext null
        val audio = File(track.key)
        audio.parentFile?.listFiles()?.firstOrNull { it.isFile && it.length() <= 1_048_576 && it.extension.equals("lrc", true) && it.nameWithoutExtension.equals(audio.nameWithoutExtension, true) }
            ?.readText(Charsets.UTF_8)
    }
    override suspend fun read(key: String) = withContext(Dispatchers.IO) { entry(key).record }
    override suspend fun write(key: String, record: LyricsRecord) = withContext(Dispatchers.IO) { synchronized(this@DesktopLyricsRepository) { save(key, entry(key).copy(record = record)) } }
    override suspend fun lastMiss(key: String) = withContext(Dispatchers.IO) { entry(key).miss }
    override suspend fun setMiss(key: String, at: Long) = withContext(Dispatchers.IO) { synchronized(this@DesktopLyricsRepository) { save(key, entry(key).copy(miss = at)) } }
    override suspend fun offset(key: String) = withContext(Dispatchers.IO) { entry(key).offset }
    override suspend fun setOffset(key: String, offset: Long) = withContext(Dispatchers.IO) { synchronized(this@DesktopLyricsRepository) { save(key, entry(key).copy(offset = offset)) } }
    fun close() = pipeline.client.close()
}
