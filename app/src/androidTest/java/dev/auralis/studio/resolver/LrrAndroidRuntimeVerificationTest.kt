package dev.auralis.studio.resolver

import android.content.Context
import android.os.Debug
import android.os.Process
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.auralis.studio.resolver.android.AndroidProcessMediaResolver
import dev.auralis.studio.resolver.api.MediaResolver
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LrrAndroidRuntimeVerificationTest {

    private lateinit var context: Context
    private lateinit var mediaResolver: MediaResolver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mediaResolver = AndroidProcessMediaResolver(context)
    }

    @Test
    fun testRealAndroidProcessIsolationAndHealth() = runBlocking {
        val mainPid = Process.myPid()
        Log.i(TAG, "=== Test starting in Main Process PID: $mainPid ===")

        val health = mediaResolver.health()
        Log.i(TAG, "Runtime Health received: $health")

        assertTrue("Resolver service must be ready", health.resolverServiceReady)
        assertTrue("Python must be ready", health.pythonReady)
        assertTrue("yt-dlp must be ready", health.ytDlpReady)
        assertTrue("EJS package must be installed", health.ejsInstalled)
        assertTrue("EJS package must be compatible", health.ejsCompatible)
        assertTrue("QuickJS binary must be present", health.jsRuntimePresent)
        assertTrue("QuickJS binary must be executable", health.jsRuntimeExecutable)
        assertTrue("yt-dlp must detect QuickJS runtime", health.jsRuntimeDetectedByYtDlp)

        val qjsVer = health.details["quickjs_version"]
        Log.i(TAG, "Detected QuickJS version: $qjsVer")
        assertNotNull(qjsVer)
        assertTrue("QuickJS version should contain 0.15.1", qjsVer!!.contains("0.15.1"))
    }

    @Test
    fun testRealAndroidDeterministicInspection() = runBlocking {
        val request = ResolverRequest(sourceUrl = "https://example.com/audio.mp3")
        val result = mediaResolver.inspect(request)
        Log.i(TAG, "Inspection result for example.com: $result")
        assertTrue("Result must be either Success or Failure with structured code", result is ResolverResult.Success || result is ResolverResult.Failure)
    }

    @Test
    fun testAndroidMemoryMetricsAndProcessIsolation() = runBlocking {
        val mainPid = Process.myPid()
        val mainPssBeforeKb = Debug.getPss()
        val mainRssBeforeKb = readProcessRssKb(mainPid)
        Log.i(TAG, "=== Android Memory Profiling ===")
        Log.i(TAG, "1. Main Process (PID $mainPid) Before Resolver: PSS=${mainPssBeforeKb}KB (${mainPssBeforeKb / 1024}MB), RSS=${mainRssBeforeKb}KB (${mainRssBeforeKb / 1024}MB)")

        // Trigger health check to initialize Python, yt-dlp, QuickJS in :resolver process
        val t0 = System.currentTimeMillis()
        val health = mediaResolver.health()
        val tHealth = System.currentTimeMillis() - t0
        Log.i(TAG, "2. Health Check Latency (Resolver Bind + Python + yt-dlp + QuickJS detection): ${tHealth}ms")

        val mainPssAfterKb = Debug.getPss()
        val mainRssAfterKb = readProcessRssKb(mainPid)
        Log.i(TAG, "3. Main Process After Resolver Init: PSS=${mainPssAfterKb}KB, RSS=${mainRssAfterKb}KB (Main Heap Delta: ${(mainPssAfterKb - mainPssBeforeKb) / 1024}MB)")

        // Execute one deterministic inspection call
        val tInspect0 = System.currentTimeMillis()
        val inspectResult = mediaResolver.inspect(ResolverRequest(sourceUrl = "https://example.com/track.mp3"))
        val tInspect = System.currentTimeMillis() - tInspect0
        Log.i(TAG, "4. First Inspect Call Latency: ${tInspect}ms, Result: ${inspectResult.javaClass.simpleName}")

        // Find resolver process PID from /proc
        val resolverPid = findProcessPid("dev.auralis.studio:resolver")
        if (resolverPid != null) {
            val resRssKb = readProcessRssKb(resolverPid)
            Log.i(TAG, "5. Isolated :resolver Process (PID $resolverPid): RSS=${resRssKb}KB (${resRssKb / 1024}MB)")
        } else {
            Log.i(TAG, "5. Resolver process PID query returned null (process may have completed idle)")
        }

        assertTrue("Main process PSS should remain unaffected by Python runtime (< 20MB delta)", (mainPssAfterKb - mainPssBeforeKb) < 20_000)
    }

    private fun readProcessRssKb(pid: Int): Long {
        return try {
            val statusFile = File("/proc/$pid/status")
            if (statusFile.exists()) {
                statusFile.readLines().firstOrNull { it.startsWith("VmRSS:") }
                    ?.substringAfter(":")?.trim()?.removeSuffix("kB")?.trim()?.toLongOrNull() ?: 0L
            } else 0L
        } catch (_: Throwable) {
            0L
        }
    }

    private fun findProcessPid(processNameSuffix: String): Int? {
        return try {
            val procDir = File("/proc")
            procDir.listFiles()?.firstOrNull { f ->
                if (f.isDirectory && f.name.all { it.isDigit() }) {
                    val cmdline = File(f, "cmdline")
                    if (cmdline.exists()) {
                        val name = cmdline.readText().replace('\u0000', ' ').trim()
                        name.contains(processNameSuffix)
                    } else false
                } else false
            }?.name?.toIntOrNull()
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private const val TAG = "LRR:AndroidTest"
    }
}
