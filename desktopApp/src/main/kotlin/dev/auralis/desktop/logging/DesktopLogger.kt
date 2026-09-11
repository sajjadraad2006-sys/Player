package dev.auralis.desktop.logging

import dev.auralis.desktop.storage.DesktopPaths
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-safe, bounded desktop diagnostic logger for Lyrune.
 *
 * Requirements satisfied:
 * - Writes to %LOCALAPPDATA%\Lyrune\logs\lyrune.log.
 * - Enforces hard file size bounding (default 5 MB) with single backup rotation (lyrune.log.1).
 * - Sanitizes sensitive credentials, tokens, and query strings.
 * - Caps exception stack traces to top 10 frames to avoid disk thrashing.
 * - Never throws runtime exceptions if logging fails due to permissions or disk constraints.
 */
object DesktopLogger {

    private const val MAX_LOG_BYTES = 5 * 1024 * 1024L // 5 MB
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    private val secretPatterns = listOf(
        Regex("(?i)(api[_-]?key|token|bearer|secret|password|passwd|auth)=([^\r\n&\\s]+)"),
        Regex("(?i)(https?://[^\\s?]+)\\?([^\\s]+)"),
    )

    var logFileOverride: File? = null

    val targetLogFile: File
        get() = logFileOverride ?: DesktopPaths.logFile

    fun info(tag: String, message: String) {
        log("INFO", tag, message, null)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        log("WARN", tag, message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log("ERROR", tag, message, throwable)
    }

    fun debug(tag: String, message: String) {
        log("DEBUG", tag, message, null)
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        try {
            val sanitized = sanitize(message)
            val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
            val threadName = Thread.currentThread().name

            val stringWriter = StringWriter()
            val pw = PrintWriter(stringWriter)
            pw.println("[$timestamp] [$level] [$threadName] [$tag] $sanitized")

            if (throwable != null) {
                pw.println("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                val stackTrace = throwable.stackTrace
                val framesToPrint = Math.min(10, stackTrace.size)
                for (i in 0 until framesToPrint) {
                    pw.println("    at ${stackTrace[i]}")
                }
                if (stackTrace.size > 10) {
                    pw.println("    ... (${stackTrace.size - 10} frames truncated)")
                }
            }
            pw.flush()

            val entry = stringWriter.toString()

            synchronized(lock) {
                val file = targetLogFile
                val parent = file.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }

                rotateIfNeeded(file)

                FileWriter(file, true).use { fw ->
                    fw.write(entry)
                }
            }
        } catch (_: Throwable) {
            // Never propagate logging failures to application runtime
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() < MAX_LOG_BYTES) return

        try {
            val backup = File(file.parentFile, "${file.name}.1")
            if (backup.exists()) {
                backup.delete()
            }
            file.renameTo(backup)
        } catch (_: Exception) {
            // If rotation rename fails, truncate current file
            try {
                file.writeText("")
            } catch (_: Exception) {}
        }
    }

    fun sanitize(raw: String): String {
        var result = raw
        // Redact key=value secrets
        result = secretPatterns[0].replace(result) { match ->
            val key = match.groupValues[1]
            "$key=[REDACTED]"
        }
        // Redact URL query strings that might hold tokens
        result = secretPatterns[1].replace(result) { match ->
            val baseUrl = match.groupValues[1]
            "$baseUrl?[QUERY_REDACTED]"
        }
        return result
    }

    fun clearLogs() {
        synchronized(lock) {
            try {
                val file = targetLogFile
                if (file.exists()) file.delete()
                val backup = File(file.parentFile, "${file.name}.1")
                if (backup.exists()) backup.delete()
            } catch (_: Exception) {}
        }
    }
}
