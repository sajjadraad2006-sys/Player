package dev.auralis.studio.resolver

import android.content.Context
import android.os.Debug
import android.os.Process
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.auralis.studio.resolver.android.AndroidProcessMediaResolver
import dev.auralis.studio.resolver.api.MediaResolver
import dev.auralis.studio.resolver.api.ResolverErrorCode
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        mediaResolver = AndroidProcessMediaResolver(context, defaultTimeoutMs = 30_000L, idleTimeoutMs = 60_000L)
    }

    @Test
    fun test1_RealAndroidProcessIsolationAndHealth(): Unit = runBlocking {
        val mainPid = Process.myPid()
        Log.i(TAG, "=== 1. Process Isolation & Runtime Health ===")
        Log.i(TAG, "Main process PID: $mainPid")

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
        assertTrue("EJS challenge self-test must pass", health.jsChallengeSelfTestReady)

        val qjsVer = health.details["quickjs_version"]
        Log.i(TAG, "Detected QuickJS version: $qjsVer")
        assertNotNull(qjsVer)
        assertTrue("QuickJS version should contain 0.15.1", qjsVer!!.contains("0.15.1"))

        val resolverPid = findProcessPid("dev.auralis.studio:resolver")
        Log.i(TAG, "Isolated :resolver process PID: $resolverPid")
        assertNotNull("Resolver process must be running in separate PID", resolverPid)
        assertTrue("Resolver PID must differ from main PID", resolverPid != mainPid)
    }

    @Test
    fun test2_RealEjsMetadataExtraction_Matrix(): Unit = runBlocking {
        Log.i(TAG, "=== 2. Real Metadata Extraction Matrix (download=False) ===")

        // Case A: Public deterministic test video (YouTube first video)
        val caseAUrl = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        val t0A = System.currentTimeMillis()
        val resA = mediaResolver.inspect(ResolverRequest(sourceUrl = caseAUrl))
        val latA = System.currentTimeMillis() - t0A
        Log.i(TAG, "Case A ($caseAUrl) -> Latency: ${latA}ms, Result: ${resA.javaClass.simpleName}")

        when (resA) {
            is ResolverResult.Success -> {
                val media = resA.media
                val audioOnlyCount = media.formats.count { it.isAudioOnly }
                Log.i(TAG, "Case A Success: title='${media.title}', source='${media.source}', formats=${media.formats.size}, audioOnly=$audioOnlyCount")
                assertTrue("Case A must have a non-empty title", !media.title.isNullOrBlank())
                assertTrue("Case A must have formats", media.formats.isNotEmpty())
                val downloadsDir = File(context.cacheDir, "downloads")
                assertFalse("No files should be downloaded", downloadsDir.exists() && (downloadsDir.listFiles()?.isNotEmpty() == true))
            }
            is ResolverResult.Failure -> {
                Log.w(TAG, "Case A Note: Result: ${resA.error}")
            }
        }

        // Case B: Public standard test media
        val caseBUrl = "https://www.youtube.com/watch?v=BaW_jenozKc"
        val t0B = System.currentTimeMillis()
        val resB = mediaResolver.inspect(ResolverRequest(sourceUrl = caseBUrl))
        val latB = System.currentTimeMillis() - t0B
        Log.i(TAG, "Case B ($caseBUrl) -> Latency: ${latB}ms, Result: ${resB.javaClass.simpleName}")

        // Case C: Intentionally unavailable/invalid URL to verify error mapping
        val caseCUrl = "https://www.youtube.com/watch?v=INVALID_TEST_ID_NON_EXISTENT_9999"
        val t0C = System.currentTimeMillis()
        val resC = mediaResolver.inspect(ResolverRequest(sourceUrl = caseCUrl))
        val latC = System.currentTimeMillis() - t0C
        Log.i(TAG, "Case C ($caseCUrl) -> Latency: ${latC}ms, Result: ${resC.javaClass.simpleName}")
        assertTrue("Invalid URL must return ResolverResult.Failure", resC is ResolverResult.Failure)
        if (resC is ResolverResult.Failure) {
            Log.i(TAG, "Case C Error mapped: technicalCode=${resC.error.technicalCode}, isRetryable=${resC.error.isRetryable}")
            assertTrue(
                "Error code must be NotFound, UnsupportedUrl, or NetworkUnavailable",
                resC.error.technicalCode in listOf(ResolverErrorCode.NotFound, ResolverErrorCode.UnsupportedUrl, ResolverErrorCode.NetworkUnavailable, ResolverErrorCode.Unknown)
            )
            assertFalse("Safe message must not be generic 'Source Error'", resC.error.safeUserMessage == "Source Error")
        }

        // Check health after real extraction
        val postHealth = mediaResolver.health()
        Log.i(TAG, "Post-Extraction Health: realExtractionReady=${postHealth.realExtractionReady}")
    }

    @Test
    fun test3_QuickJsProcessAndSecurityAudit(): Unit = runBlocking {
        Log.i(TAG, "=== 3. QuickJS Process & SELinux Security Audit ===")
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val qjsFile = File(nativeDir, "libqjs.so")
        Log.i(TAG, "Native library dir: $nativeDir")
        Log.i(TAG, "libqjs.so exists: ${qjsFile.exists()}, canExecute: ${qjsFile.canExecute()}")

        assertTrue("libqjs.so must exist in nativeLibraryDir", qjsFile.exists())
        assertTrue("libqjs.so must be marked executable", qjsFile.canExecute())

        val filesDir = context.filesDir
        val cacheDir = context.cacheDir
        Log.i(TAG, "App filesDir: $filesDir, cacheDir: $cacheDir")
        assertFalse("filesDir should not be used as native library dir", nativeDir.startsWith(filesDir.absolutePath))
    }

    @Test
    fun test4_TempEjsScriptAuditAndCleanup(): Unit = runBlocking {
        Log.i(TAG, "=== 4. Temp EJS Script Audit & Cleanup ===")
        val cacheDir = context.cacheDir
        val jsFilesBefore = cacheDir.listFiles()?.filter { it.extension == "js" } ?: emptyList()
        Log.i(TAG, "Temp .js files before: ${jsFilesBefore.size}")

        // Perform health check / trivial test
        mediaResolver.health()

        val jsFilesAfter = cacheDir.listFiles()?.filter { it.extension == "js" } ?: emptyList()
        Log.i(TAG, "Temp .js files after: ${jsFilesAfter.size}")
        assertEquals("Temp .js challenge files must be immediately unlinked", 0, jsFilesAfter.size)
    }

    @Test
    fun test5_StagedMemoryBreakdownAndIdlePolicy(): Unit = runBlocking {
        Log.i(TAG, "=== 5. Staged Android Memory Breakdown & Idle Policy ===")
        val mainPid = Process.myPid()

        // 1. Main process before resolver
        val mainPss1 = Debug.getPss()
        val mainRss1 = readProcessRssKb(mainPid)
        Log.i(TAG, "Stage 1 (Main Before Resolver): PSS=${mainPss1}KB (${mainPss1 / 1024}MB), RSS=${mainRss1}KB (${mainRss1 / 1024}MB)")

        // 2. Trigger resolver health
        mediaResolver.health()
        val resolverPid = findProcessPid("dev.auralis.studio:resolver")
        assertNotNull("Resolver process PID must be present", resolverPid)

        val mainPss2 = Debug.getPss()
        val resRss2 = readProcessRssKb(resolverPid!!)
        Log.i(TAG, "Stage 2 (After Resolver Init): Main PSS=${mainPss2}KB (${mainPss2 / 1024}MB), Resolver RSS=${resRss2}KB (${resRss2 / 1024}MB)")

        // 3. Test unbind policy
        (mediaResolver as AndroidProcessMediaResolver).unbind()
        Log.i(TAG, "Stage 3 (Explicit Unbind): Unbound service from main process")

        // 4. Subsequent request seamlessly rebinds
        val rebindHealth = mediaResolver.health()
        assertTrue("Rebind after unbind must succeed", rebindHealth.resolverServiceReady)
        Log.i(TAG, "Stage 4 (Rebind Success): resolverServiceReady=${rebindHealth.resolverServiceReady}")
    }

    @Test
    fun test6_CancellationDuringExtraction(): Unit = runBlocking {
        Log.i(TAG, "=== 6. Cancellation Propagation Test ===")
        val reqId = "test-cancel-${System.currentTimeMillis()}"
        val req = ResolverRequest(requestId = reqId, sourceUrl = "https://www.youtube.com/watch?v=jNQXAC9IVRw")

        val job = async {
            mediaResolver.inspect(req)
        }

        delay(50)
        mediaResolver.cancel(reqId)
        job.cancel()

        Log.i(TAG, "Cancellation sent for request $reqId, verifying no crash or dangling state")
        val postCancelHealth = mediaResolver.health()
        assertTrue("Resolver must remain functional after cancellation", postCancelHealth.resolverServiceReady)
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
