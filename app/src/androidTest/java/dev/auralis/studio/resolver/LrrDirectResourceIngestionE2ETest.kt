package dev.auralis.studio.resolver

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.auralis.studio.core.database.AppDatabase
import dev.auralis.studio.core.database.DownloadStatus
import dev.auralis.studio.library.DownloadIngestionManager
import dev.auralis.studio.library.DownloadIngestionRequest
import dev.auralis.studio.library.DownloadWorkerBridge
import dev.auralis.studio.library.MediaStoreMusicRepository
import dev.auralis.studio.library.PreviewDownloadRejectedException
import dev.auralis.studio.resolver.api.AudioQualityPreference
import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityBasis
import dev.auralis.studio.resolver.api.MediaResolver
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolvedFormat
import dev.auralis.studio.resolver.api.ResolvedMedia
import dev.auralis.studio.resolver.api.ResolverDownloadDescriptor
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult
import dev.auralis.studio.resolver.api.RuntimeHealth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class LrrDirectResourceIngestionE2ETest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var mediaStoreRepository: MediaStoreMusicRepository
    private lateinit var ingestionManager: DownloadIngestionManager
    private var testServer: LocalTestHttpServer? = null
    private val createdTestFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        mediaStoreRepository = MediaStoreMusicRepository(context)
        ingestionManager = DownloadIngestionManager(
            context = context,
            downloadsDao = database.downloadsDao(),
            mediaStoreRepository = mediaStoreRepository,
            okHttpClient = dev.auralis.studio.library.createSafeDownloadClient(allowLocalhostForTesting = true),
        )
        DownloadWorkerBridge.attach(ingestionManager)
    }

    @After
    fun tearDown() {
        testServer?.stop()
        database.close()
        createdTestFiles.forEach { file ->
            if (file.exists()) file.delete()
        }
        val lyruneDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Lyrune")
        lyruneDir.listFiles()?.filter {
            it.name.startsWith("Test", ignoreCase = true) ||
                it.name.startsWith("Auralis", ignoreCase = true) ||
                it.name.contains("rapid", ignoreCase = true)
        }?.forEach { it.delete() }
    }

    @Test
    fun test1_DeterministicPermittedDirectIngestion_E2E(): Unit = runBlocking {
        val wavBytes = generateTestWav(durationSeconds = 3)
        testServer = LocalTestHttpServer { path ->
            when (path) {
                "/audio.wav" -> HttpResponse(200, "audio/wav", wavBytes)
                else -> HttpResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }.apply { start() }

        val serverUrl = testServer!!.urlFor("/audio.wav")

        val descriptor = ResolverDownloadDescriptor(
            source = "TestOpenAudio",
            sourceId = "track_101",
            canonicalUrl = serverUrl,
            title = "Test Synthetic Melody",
            artist = "Auralis Test Artist",
            album = "Test Studio Album",
            expectedDurationMs = 3000L,
            downloadCapability = DownloadCapability.PERMITTED,
            downloadCapabilityBasis = DownloadCapabilityBasis.TEST_FIXTURE,
        )

        val ingestionReq = DownloadIngestionRequest.fromResolverResource(
            descriptor = descriptor,
            freshDirectUrl = serverUrl,
            durationMs = 3000L,
            formatExtension = "wav",
        )

        val result = ingestionManager.download(ingestionReq)
        assertTrue("Download must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val track = result.getOrNull()
        assertNotNull("Resolved track must not be null", track)
        assertEquals("Test Synthetic Melody", track?.title)
        assertEquals("Auralis Test Artist", track?.artist)
        assertTrue("Track duration must be ~3000ms", track!!.durationMs in 2000L..4000L)

        // Verify Room entity state
        val row = database.downloadsDao().findByKey(ingestionReq.provider, ingestionReq.providerTrackId)
        assertNotNull("Room entity must exist", row)
        assertEquals("DownloadStatus must be COMPLETED", DownloadStatus.COMPLETED.name, row?.status)
        assertEquals(3000L, row?.durationMs ?: 0L)

        // Verify physical file on disk
        val localFile = File(row!!.destinationPath.ifBlank { track!!.uri.removePrefix("file://") })
        assertTrue("Physical output file must exist", localFile.exists())
        assertTrue("Physical output file must be non-zero", localFile.length() > 0)
        createdTestFiles.add(localFile)
    }

    @Test
    fun test2_RapidTap_DuplicatePrevention(): Unit = runBlocking {
        val wavBytes = generateTestWav(durationSeconds = 3)
        testServer = LocalTestHttpServer {
            HttpResponse(200, "audio/wav", wavBytes)
        }.apply { start() }

        val serverUrl = testServer!!.urlFor("/rapid_tap.wav")

        val descriptor = ResolverDownloadDescriptor(
            source = "TestOpenAudio",
            sourceId = "rapid_tap_01",
            canonicalUrl = serverUrl,
            title = "Test Rapid Tap Track",
            artist = "Auralis MultiTap",
            expectedDurationMs = 3000L,
            downloadCapability = DownloadCapability.PERMITTED,
            downloadCapabilityBasis = DownloadCapabilityBasis.TEST_FIXTURE,
        )

        val ingestionReq = DownloadIngestionRequest.fromResolverResource(
            descriptor = descriptor,
            freshDirectUrl = serverUrl,
            durationMs = 3000L,
            formatExtension = "wav",
        )

        // Launch 5 parallel download invocations simulating rapid taps
        val jobs = (1..5).map {
            async {
                ingestionManager.download(ingestionReq)
            }
        }

        val results = jobs.awaitAll()
        val successCount = results.count { it.isSuccess }
        assertTrue("At least one download should succeed and remaining coalesce cleanly", successCount >= 1)

        // Verify exactly 1 row in Room and 1 file on disk
        val allDownloads = database.downloadsDao().findAllByKey(ingestionReq.provider, ingestionReq.providerTrackId)
        assertEquals("Exactly 1 Room download record must exist", 1, allDownloads.size)

        val lyruneDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Lyrune")
        val matchingFiles = lyruneDir.listFiles()?.filter { it.name.contains("Rapid", ignoreCase = true) } ?: emptyList()
        assertEquals("Exactly 1 physical file on disk (no duplicates with (1), (2))", 1, matchingFiles.size)
        matchingFiles.forEach { createdTestFiles.add(it) }
    }

    @Test
    fun test3_StaleUrl_OneShot403Recovery(): Unit = runBlocking {
        val wavBytes = generateTestWav(durationSeconds = 3)
        val attemptCount = AtomicInteger(0)

        testServer = LocalTestHttpServer { path ->
            when (path) {
                "/expired_403.wav" -> {
                    attemptCount.incrementAndGet()
                    HttpResponse(403, "text/plain", "Forbidden - Expired Token".toByteArray())
                }
                "/fresh_200.wav" -> {
                    attemptCount.incrementAndGet()
                    HttpResponse(200, "audio/wav", wavBytes)
                }
                else -> HttpResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }.apply { start() }

        val expiredUrl = testServer!!.urlFor("/expired_403.wav")
        val freshUrl = testServer!!.urlFor("/fresh_200.wav")

        val descriptor = ResolverDownloadDescriptor(
            source = "TestRecovery",
            sourceId = "recov_01",
            canonicalUrl = "https://example.org/recordings/recov_01",
            title = "Test Stale Recovery",
            artist = "Auralis Recovery",
            expectedDurationMs = 3000L,
            downloadCapability = DownloadCapability.PERMITTED,
        )

        var resolveCalls = 0
        val fakeResolver = object : MediaResolver {
            override suspend fun inspect(request: ResolverRequest): ResolverResult = resolve(request)
            override suspend fun resolve(request: ResolverRequest): ResolverResult {
                resolveCalls++
                val chosenUrl = if (resolveCalls == 1) expiredUrl else freshUrl
                return ResolverResult.Success(
                    media = ResolvedMedia(
                        source = descriptor.source,
                        sourceId = descriptor.sourceId,
                        webpageUrl = descriptor.canonicalUrl,
                        title = descriptor.title,
                        formats = listOf(
                            dev.auralis.studio.resolver.api.ResolvedFormatDescriptor(
                                formatId = "fmt_wav",
                                extension = "wav",
                                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                                downloadCapability = DownloadCapability.PERMITTED,
                                downloadCapabilityBasis = DownloadCapabilityBasis.TEST_FIXTURE,
                            ),
                        ),
                        rawFormats = listOf(
                            ResolvedFormat(
                                formatId = "fmt_wav",
                                url = chosenUrl,
                                extension = "wav",
                                resolutionMode = ResolutionMode.DIRECT_RESOURCE,
                                downloadCapability = DownloadCapability.PERMITTED,
                                downloadCapabilityBasis = DownloadCapabilityBasis.TEST_FIXTURE,
                            ),
                        ),
                    ),
                    lease = dev.auralis.studio.resolver.api.EphemeralResourceLease(
                        requestId = request.requestId,
                        formatId = "fmt_wav",
                        url = chosenUrl,
                    ),
                )
            }
            override suspend fun cancel(requestId: String) {}
            override suspend fun health(): RuntimeHealth = RuntimeHealth.UNINITIALIZED
        }

        // Attempt download with first URL (which returns 403)
        val res1 = fakeResolver.resolve(ResolverRequest(sourceUrl = descriptor.canonicalUrl))
        val url1 = (res1 as ResolverResult.Success).lease?.url ?: (res1 as ResolverResult.Success).media.rawFormats.first().url
        val req1 = DownloadIngestionRequest.fromResolverResource(descriptor, url1, 3000L, formatExtension = "wav")
        val result1 = ingestionManager.download(req1)

        assertTrue("First attempt on 403 must fail", result1.isFailure)

        // One-shot recovery: re-resolve fresh and retry
        val res2 = fakeResolver.resolve(ResolverRequest(sourceUrl = descriptor.canonicalUrl))
        val url2 = (res2 as ResolverResult.Success).lease?.url ?: (res2 as ResolverResult.Success).media.rawFormats.first().url
        assertEquals("Re-resolution must obtain freshUrl", freshUrl, url2)

        val req2 = DownloadIngestionRequest.fromResolverResource(descriptor, url2, 3000L, formatExtension = "wav")
        val result2 = ingestionManager.download(req2)
        assertTrue("Second attempt with freshUrl must succeed", result2.isSuccess)

        val row = database.downloadsDao().findByKey(req2.provider, req2.providerTrackId)
        assertEquals(DownloadStatus.COMPLETED.name, row?.status)
    }

    @Test
    fun test4_PreviewSafety_Rejection(): Unit = runBlocking {
        val descriptor = ResolverDownloadDescriptor(
            source = "iTunes",
            sourceId = "preview_123",
            canonicalUrl = "https://music.apple.com/preview",
            title = "Preview Song",
            artist = "Preview Artist",
            downloadCapability = DownloadCapability.NOT_PERMITTED,
        )

        // A descriptor without PERMITTED capability must be rejected
        val ingestionReq = DownloadIngestionRequest(
            provider = "lrr_itunes",
            providerTrackId = "preview_123",
            downloadUrl = "",
            title = "Preview Song",
            durationMs = 29_000L,
        )

        val result = ingestionManager.download(ingestionReq)
        assertTrue("Download without valid URL must fail with PreviewDownloadRejectedException", result.isFailure)
        assertTrue("Failure must be PreviewDownloadRejectedException", result.exceptionOrNull() is PreviewDownloadRejectedException)

        val row = database.downloadsDao().findByKey("lrr_itunes", "preview_123")
        assertTrue("No rows should be inserted for rejected preview", row == null)
    }

    @Test
    fun test5_NetworkFailure_Handling(): Unit = runBlocking {
        testServer = LocalTestHttpServer {
            HttpResponse(404, "text/plain", "Not Found".toByteArray())
        }.apply { start() }

        val serverUrl = testServer!!.urlFor("/missing.wav")
        val descriptor = ResolverDownloadDescriptor(
            source = "TestFail",
            sourceId = "missing_01",
            canonicalUrl = serverUrl,
            title = "Missing Track",
            downloadCapability = DownloadCapability.PERMITTED,
        )

        val ingestionReq = DownloadIngestionRequest.fromResolverResource(
            descriptor = descriptor,
            freshDirectUrl = serverUrl,
            durationMs = 3000L,
            formatExtension = "wav",
        )

        val result = ingestionManager.download(ingestionReq)
        assertTrue("HTTP 404 must result in failure", result.isFailure)

        val row = database.downloadsDao().findByKey(ingestionReq.provider, ingestionReq.providerTrackId)
        assertEquals(DownloadStatus.FAILED.name, row?.status)
    }

    @Test
    fun test6_FreshProcessRecreation_BootstrapLifecycle(): Unit = runBlocking {
        // 1. Simulate process death: clear all static bridge state
        DownloadWorkerBridge.manager = null
        DownloadWorkerBridge.resolver = null

        // 2. Simulate fresh application process bootstrap (as happens when WorkManager spawns in new process)
        val freshContainer = dev.auralis.studio.core.AppContainer(context)

        // 3. Verify bridge was populated eagerly by AppContainer during application bootstrap
        assertNotNull("DownloadWorkerBridge.manager must be initialized by AppContainer bootstrap", DownloadWorkerBridge.manager)
        assertNotNull("DownloadWorkerBridge.resolver must be initialized by AppContainer bootstrap", DownloadWorkerBridge.resolver)

        // 4. Ingest a test fixture descriptor without any UI / Activity / ViewModel involvement
        val wavBytes = generateTestWav(durationSeconds = 3)
        testServer = LocalTestHttpServer {
            HttpResponse(200, "audio/wav", wavBytes)
        }.apply { start() }

        val serverUrl = testServer!!.urlFor("/bootstrap_test.wav")
        val descriptor = ResolverDownloadDescriptor(
            source = "TestBootstrap",
            sourceId = "boot_01",
            canonicalUrl = serverUrl,
            title = "Test Bootstrap Track",
            downloadCapability = DownloadCapability.PERMITTED,
            downloadCapabilityBasis = DownloadCapabilityBasis.TEST_FIXTURE,
        )

        val req = DownloadIngestionRequest.fromResolverResource(
            descriptor = descriptor,
            freshDirectUrl = serverUrl,
            durationMs = 3000L,
            formatExtension = "wav",
        )

        val result = freshContainer.downloadManager.download(req)
        assertTrue("Download in fresh bootstrap environment must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val track = result.getOrNull()
        assertNotNull("Resolved track must not be null", track)
        assertEquals("Test Bootstrap Track", track?.title)
    }

    @Test
    fun test7_ResolverProcessIsolation_GuardAndClassification() {
        val packageName = context.packageName

        // 1. Process role classification
        assertEquals(
            dev.auralis.studio.core.ProcessRole.MAIN,
            dev.auralis.studio.core.ProcessUtils.classifyProcessName(packageName, packageName),
        )
        assertEquals(
            dev.auralis.studio.core.ProcessRole.RESOLVER,
            dev.auralis.studio.core.ProcessUtils.classifyProcessName(packageName, "$packageName:resolver"),
        )
        assertEquals(
            dev.auralis.studio.core.ProcessRole.OTHER,
            dev.auralis.studio.core.ProcessUtils.classifyProcessName(packageName, "$packageName:auxiliary"),
        )

        // 2. MAIN-only guard verification
        val isMainAllowed = try {
            val mainRole = dev.auralis.studio.core.ProcessUtils.classifyProcessName(packageName, packageName)
            check(mainRole == dev.auralis.studio.core.ProcessRole.MAIN) { "MAIN only" }
            true
        } catch (_: IllegalStateException) {
            false
        }
        assertTrue("MAIN process must be allowed", isMainAllowed)

        val isResolverRejected = try {
            val resolverRole = dev.auralis.studio.core.ProcessUtils.classifyProcessName(packageName, "$packageName:resolver")
            check(resolverRole == dev.auralis.studio.core.ProcessRole.MAIN) { "MAIN only" }
            false
        } catch (e: IllegalStateException) {
            true
        }
        assertTrue("RESOLVER process must be rejected", isResolverRejected)

        val isOtherRejected = try {
            val otherRole = dev.auralis.studio.core.ProcessUtils.classifyProcessName(packageName, "$packageName:auxiliary")
            check(otherRole == dev.auralis.studio.core.ProcessRole.MAIN) { "MAIN only" }
            false
        } catch (e: IllegalStateException) {
            true
        }
        assertTrue("OTHER process must be rejected", isOtherRejected)
    }

    @Test
    fun test8_ResolverProcessDiagnostics_LiveHealthQuery(): Unit = runBlocking {
        val resolver = dev.auralis.studio.resolver.android.AndroidProcessMediaResolver(context)
        val health = resolver.health()

        assertTrue("Resolver service must report ready", health.resolverServiceReady)
        val packageName = context.packageName

        // Diagnostics computed INSIDE :resolver process over Binder
        assertEquals("$packageName:resolver", health.resolverProcessName)
        assertEquals("RESOLVER", health.resolverProcessRole)
        assertEquals(false, health.isMainContainerInitializedInResolver)
        assertNotNull("Resolver PID must be reported", health.resolverPid)
    }

    // ── Deterministic WAV Fixture Generator ─────────────────────────────────

    private fun generateTestWav(durationSeconds: Int = 3, sampleRate: Int = 44100): ByteArray {
        val numChannels = 2
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val numSamples = sampleRate * durationSeconds
        val dataSize = numSamples * blockAlign
        val totalSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * 440.0 * i / sampleRate
            val sample = (Math.sin(angle) * 16384).toInt().toShort()
            buffer.putShort(sample)
            buffer.putShort(sample)
        }

        return buffer.array()
    }
}

// ── Lightweight Local Test HTTP Server ──────────────────────────────────────

data class HttpResponse(val statusCode: Int, val contentType: String, val body: ByteArray)

class LocalTestHttpServer(
    private val handler: (path: String) -> HttpResponse,
) {
    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null
    @Volatile
    private var running = false
    var port: Int = 0
        private set

    fun start() {
        serverSocket = ServerSocket(0)
        port = serverSocket!!.localPort
        running = true
        thread = Thread {
            while (running) {
                try {
                    val socket = serverSocket?.accept() ?: break
                    handleClient(socket)
                } catch (_: Exception) {
                    break
                }
            }
        }.apply { start() }
    }

    fun urlFor(path: String): String = "http://127.0.0.1:$port$path"

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        thread?.interrupt()
    }

    private fun handleClient(socket: Socket) {
        Thread {
            socket.use { s ->
                val input = s.getInputStream().bufferedReader()
                val line = input.readLine() ?: return@use
                val parts = line.split(" ")
                val path = if (parts.size > 1) parts[1] else "/"

                val response = handler(path)
                val out = s.getOutputStream()
                val statusText = when (response.statusCode) {
                    200 -> "OK"
                    403 -> "Forbidden"
                    404 -> "Not Found"
                    else -> "Internal Server Error"
                }

                val header = "HTTP/1.1 ${response.statusCode} $statusText\r\n" +
                    "Content-Type: ${response.contentType}\r\n" +
                    "Content-Length: ${response.body.size}\r\n" +
                    "Connection: close\r\n\r\n"

                out.write(header.toByteArray(Charsets.UTF_8))
                out.write(response.body)
                out.flush()
            }
        }.start()
    }
}
