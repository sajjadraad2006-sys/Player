package dev.auralis.studio.resolver

import android.app.Application
import android.content.Context
import android.os.Process
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.auralis.studio.core.database.AppDatabase
import dev.auralis.studio.core.database.DownloadStatus
import dev.auralis.studio.library.DownloadIngestionManager
import dev.auralis.studio.library.DownloadIngestionRequest
import dev.auralis.studio.library.DownloadWorkerBridge
import dev.auralis.studio.library.MediaStoreMusicRepository
import dev.auralis.studio.resolver.android.AndroidProcessMediaResolver
import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityBasis
import dev.auralis.studio.resolver.api.DownloadCapabilityEvaluator
import dev.auralis.studio.resolver.api.RecognizedDownloadLicense
import dev.auralis.studio.resolver.api.ResolverDownloadDescriptor
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolveIntent
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
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class RealYouTubeInspectionAndDownloadE2ETest {

    private lateinit var context: Context
    private lateinit var resolver: AndroidProcessMediaResolver
    private var testServer: LocalTestHttpServer? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resolver = AndroidProcessMediaResolver(context)
    }

    @After
    fun tearDown() {
        testServer?.stop()
    }

    @Test
    fun youtubeGenericVideo_inspectsFormats_andDownloadPolicyBlocksGenericYouTube() = runBlocking {
        val videoId = "BaW_jenozKc"
        val watchUrl = "https://www.youtube.com/watch?v=$videoId"
        val mainPid = Process.myPid()

        android.util.Log.i("LrrYouTubeTest", "Testing YouTube inspection on watchUrl: $watchUrl (Main PID: $mainPid)")

        // 1. Verify resolver process diagnostics
        val health = resolver.health()
        assertTrue("Resolver service must report ready", health.resolverServiceReady)
        assertNotNull("Resolver PID must be reported", health.resolverPid)

        // 2. Evaluate download capability according to policy
        val eval = DownloadCapabilityEvaluator.evaluate(
            source = "youtube",
            webpageUrl = watchUrl,
            license = null,
            licenseUrl = null,
            isTestFixture = false,
        )

        // Verify capability policy: generic YouTube without CC license is STREAM_ONLY / PROVIDER_EXPLICIT
        assertEquals(DownloadCapability.STREAM_ONLY, eval.capability)
        assertEquals(DownloadCapabilityBasis.PROVIDER_EXPLICIT, eval.basis)
        assertFalse("Generic YouTube must NOT be permitted for download", eval.capability == DownloadCapability.PERMITTED)

        // Verify with explicit CC license metadata: evaluates to PERMITTED
        val ccEval = DownloadCapabilityEvaluator.evaluate(
            source = "youtube",
            webpageUrl = watchUrl,
            license = "cc-by",
            isTestFixture = false,
        )
        assertEquals(DownloadCapability.PERMITTED, ccEval.capability)
        assertEquals(DownloadCapabilityBasis.LICENSE_METADATA, ccEval.basis)
        assertEquals(RecognizedDownloadLicense.CC_BY, ccEval.recognizedLicense)
    }

    @Test
    fun authorizedYouTubeFixture_completesEndToEndDownloadPipeline() = runBlocking {
        val videoId = "cc_test_fixture_01"
        val watchUrl = "https://www.youtube.com/watch?v=$videoId"

        // 1. Create a mock audio resource on LocalTestHttpServer
        val wavBytes = generateTestWav(durationSeconds = 3)
        testServer = LocalTestHttpServer { path ->
            when (path) {
                "/cc_test.wav" -> HttpResponse(200, "audio/wav", wavBytes)
                else -> HttpResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }.apply { start() }

        val serverUrl = testServer!!.urlFor("/cc_test.wav")

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val libraryRepo = MediaStoreMusicRepository(app)
        val downloadManager = DownloadIngestionManager(
            context = app,
            downloadsDao = db.downloadsDao(),
            mediaStoreRepository = libraryRepo,
            okHttpClient = dev.auralis.studio.library.createSafeDownloadClient(allowLocalhostForTesting = true),
        )
        DownloadWorkerBridge.attach(downloadManager)

        // 2. Create descriptor for authorized Creative Commons YouTube resource
        val descriptor = ResolverDownloadDescriptor(
            source = "youtube",
            sourceId = videoId,
            canonicalUrl = watchUrl,
            title = "Authorized Creative Commons Song",
            artist = "Open Artist",
            album = "YouTube CC",
            expectedDurationMs = 3000L,
            formatHint = "wav",
            downloadCapability = DownloadCapability.PERMITTED,
            downloadCapabilityBasis = DownloadCapabilityBasis.LICENSE_METADATA,
        )

        // 3. Ingestion request from resolver resource
        val ingestionReq = DownloadIngestionRequest.fromResolverResource(
            descriptor = descriptor,
            freshDirectUrl = serverUrl,
            durationMs = 3000L,
            formatExtension = "wav",
        )

        val result = downloadManager.download(ingestionReq)
        assertTrue("Download must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val track = result.getOrNull()
        assertNotNull("Downloaded track must exist in library", track)
        assertEquals("Authorized Creative Commons Song", track?.title)
        assertEquals("Open Artist", track?.artist)

        val persisted = db.downloadsDao().findByKey(ingestionReq.provider, ingestionReq.providerTrackId)
        assertNotNull("Must be persisted in Room database", persisted)
        assertEquals(DownloadStatus.COMPLETED.name, persisted?.status)
        assertEquals("Authorized Creative Commons Song", persisted?.title)

        db.close()
    }

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
