package dev.auralis.desktop.library

import kotlinx.serialization.json.*
import org.bytedeco.javacpp.Loader
import java.io.File
import java.util.concurrent.TimeUnit

/** Bounded container metadata probe for tags that Java Sound doesn't expose. Never reads PCM. */
object DesktopMediaProbe {
    private val executable by lazy { Loader.load(org.bytedeco.ffmpeg.ffprobe::class.java) }
    fun read(file: File): JsonObject? = runCatching {
        val process = ProcessBuilder(executable, "-v", "error", "-show_entries", "format=duration:format_tags", "-of", "json", file.absolutePath)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        var bytes: ByteArray? = null
        val reader = Thread({ bytes = runCatching { process.inputStream.use { it.readNBytes(1_048_577) } }.getOrNull() }, "Lyrune-Metadata").apply { isDaemon = true; start() }
        try {
            if (!process.waitFor(12, TimeUnit.SECONDS)) return@runCatching null
            reader.join(500)
            val result = bytes?.takeIf { it.size <= 1_048_576 } ?: return@runCatching null
            Json.parseToJsonElement(result.toString(Charsets.UTF_8)).jsonObject["format"]?.jsonObject
        } finally { process.destroyForcibly(); runCatching { process.inputStream.close() } }
    }.getOrNull()

    fun tags(format: JsonObject?): Map<String, String> = format?.get("tags")?.jsonObject?.entries
        ?.associate { it.key.lowercase() to it.value.jsonPrimitive.content }.orEmpty()
}
