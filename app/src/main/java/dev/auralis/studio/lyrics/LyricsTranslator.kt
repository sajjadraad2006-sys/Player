package dev.auralis.studio.lyrics

import android.util.Log
import dev.auralis.studio.core.model.LyricsLine
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Universal lyrics translator engine.
 * Provides synchronized lyrics translation into Arabic or other target languages.
 * Operates as a transparent fallback when YouTube captions lack native target language captions,
 * and enables translation for LRCLIB and local timed lyrics.
 */
object LyricsTranslator {
    private const val TAG = "LyricsTranslator"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 6_000L
                connectTimeoutMillis = 4_000L
                socketTimeoutMillis = 6_000L
            }
        }
    }

    suspend fun translateText(text: String, targetLang: String = "ar"): String? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@withContext null
        if (targetLang == "ar" && LyricsMetadataNormalizer.isArabic(trimmed)) return@withContext null
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) return@withContext null

        try {
            val response = client.get("https://translate.googleapis.com/translate_a/single") {
                parameter("client", "gtx")
                parameter("sl", "auto")
                parameter("tl", targetLang)
                parameter("dt", "t")
                parameter("q", trimmed)
            }
            if (!response.status.isSuccess()) return@withContext null

            val body = response.bodyAsText()
            parseTranslationBody(body)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Translation error for '$trimmed': ${e.message}")
            null
        }
    }

    suspend fun translateLines(lines: List<LyricsLine>, targetLang: String = "ar"): List<LyricsLine> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext emptyList()

        val chunkSize = 30
        val result = mutableListOf<LyricsLine>()

        for (chunk in lines.chunked(chunkSize)) {
            val untranslatedIndices = mutableListOf<Int>()
            val textsToTranslate = mutableListOf<String>()

            chunk.forEachIndexed { index, line ->
                if (line.translation.isNullOrBlank() &&
                    line.text.isNotBlank() &&
                    !line.text.startsWith("[") &&
                    !(targetLang == "ar" && LyricsMetadataNormalizer.isArabic(line.text))
                ) {
                    untranslatedIndices.add(index)
                    textsToTranslate.add(line.text.replace("\n", " "))
                }
            }

            if (textsToTranslate.isEmpty()) {
                result.addAll(chunk)
                continue
            }

            val joinedQuery = textsToTranslate.joinToString("\n")
            val translatedBatch = translateText(joinedQuery, targetLang)

            val translatedLines = if (!translatedBatch.isNullOrBlank()) {
                translatedBatch.split("\n").map { it.trim() }
            } else {
                emptyList()
            }

            val updatedChunk = chunk.mapIndexed { idx, line ->
                val pos = untranslatedIndices.indexOf(idx)
                if (pos >= 0 && pos in translatedLines.indices && translatedLines[pos].isNotBlank()) {
                    line.copy(translation = translatedLines[pos])
                } else {
                    line
                }
            }
            result.addAll(updatedChunk)
        }

        result
    }

    fun parseTranslationBody(body: String): String? {
        return try {
            val root = json.parseToJsonElement(body)
            if (root !is JsonArray || root.isEmpty()) return null
            val outerArray = root[0] as? JsonArray ?: return null
            val sb = StringBuilder()
            for (segment in outerArray) {
                if (segment is JsonArray && segment.isNotEmpty()) {
                    val part = segment[0].jsonPrimitive.content
                    sb.append(part)
                }
            }
            val res = sb.toString().trim()
            res.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}
