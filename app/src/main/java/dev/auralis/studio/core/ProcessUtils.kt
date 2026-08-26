package dev.auralis.studio.core

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

/**
 * Roles that an OS process belonging to the Lyrune package can assume.
 */
enum class ProcessRole {
    /** Main application process hosting UI, Media3 playback, DSP, Room DB, and DownloadWorker. */
    MAIN,

    /** Dedicated isolated process (:resolver) hosting LrrResolverService, Python, yt-dlp, and QuickJS. */
    RESOLVER,

    /** Any other auxiliary or test runner process. */
    OTHER,
}

object ProcessUtils {

    /**
     * Resolves the current process name reliably across Android API versions.
     */
    fun currentProcessName(context: Context): String {
        // API 28+ provides Application.getProcessName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }

        // API 26-27 Fallback #1: Read /proc/self/cmdline
        try {
            val cmdline = File("/proc/self/cmdline").readText().trim('\u0000', ' ', '\n', '\r')
            if (cmdline.isNotEmpty()) {
                return cmdline
            }
        } catch (_: Throwable) {
            // Fallback to ActivityManager
        }

        // API 26-27 Fallback #2: ActivityManager.getRunningAppProcesses()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val myPid = Process.myPid()
        am?.runningAppProcesses?.firstOrNull { it.pid == myPid }?.processName?.let {
            return it
        }

        return context.packageName
    }

    /**
     * Detects the [ProcessRole] for the given [context] and current process.
     */
    fun detectProcessRole(context: Context): ProcessRole {
        val processName = currentProcessName(context)
        return classifyProcessName(context.packageName, processName)
    }

    /**
     * Pure classification function for testability.
     */
    fun classifyProcessName(packageName: String, processName: String): ProcessRole {
        return when {
            processName == packageName -> ProcessRole.MAIN
            processName == "$packageName:resolver" || processName.endsWith(":resolver") -> ProcessRole.RESOLVER
            else -> ProcessRole.OTHER
        }
    }
}
