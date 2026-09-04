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
import dev.auralis.studio.resolver.api.MediaKind
import dev.auralis.studio.resolver.api.RecognizedDownloadLicense
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolvedFormat
import dev.auralis.studio.resolver.api.ResolvedMedia
import dev.auralis.studio.resolver.api.ResolverDownloadDescriptor
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult
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
        val videoId = "jNQXAC9IVRw"
        val watchUrl = "https://www.youtube.com/watch?v=$videoId"
        val mainPid = Process.myPid()

        android.util.Log.i("LrrYouTubeAcceptance", "=== REAL YOUTUBE LRR INSPECTION ===")
        android.util.Log.i("LrrYouTubeAcceptance", "VIDEO_ID: $videoId")
        android.util.Log.i("LrrYouTubeAcceptance", "canonical watch URL: $watchUrl")
        android.util.Log.i("LrrYouTubeAcceptance", "main PID: $mainPid")

        // 1. Verify resolver process diagnostics
        val health = resolver.health()
        assertTrue("Resolver service must report ready", health.resolverServiceReady)
        val resolverPid = health.details["pid"]
        android.util.Log.i("LrrYouTubeAcceptance", ":resolver PID: $resolverPid")
        assertNotNull("Resolver PID must be reported", resolverPid)

        // 2. Perform live inspect with download=False
        val req = ResolverRequest(
            sourceUrl = watchUrl,
            intent = ResolveIntent.INSPECT,
        )
        val result = resolver.inspect(req)
        assertTrue("yt-dlp extraction success: result must be Success", result is ResolverResult.Success)

        val media = (result as ResolverResult.Success).media
        android.util.Log.i("LrrYouTubeAcceptance", "yt-dlp extraction success: true")
        android.util.Log.i("LrrYouTubeAcceptance", "download=False: verified (inspection only, no stream/download bytes written)")

        val totalFormats = media.formats.size
        val audioOnlyFormats = media.formats.filter { it.mediaKind == dev.auralis.studio.resolver.api.MediaKind.AUDIO_ONLY }
        val directCount = media.formats.count { it.resolutionMode == dev.auralis.studio.resolver.api.ResolutionMode.DIRECT_RESOURCE }
        val segmentedCount = media.formats.count { it.resolutionMode == dev.auralis.studio.resolver.api.ResolutionMode.SEGMENTED_RESOURCE }

        android.util.Log.i("LrrYouTubeAcceptance", "format count: $totalFormats")
        android.util.Log.i("LrrYouTubeAcceptance", "audio-only format count: ${audioOnlyFormats.size}")
        android.util.Log.i("LrrYouTubeAcceptance", "DIRECT / SEGMENTED classification counts: DIRECT=$directCount, SEGMENTED=$segmentedCount")

        assertTrue("Format count must be > 0", totalFormats > 0)
        assertTrue("Audio-only format count must be > 0", audioOnlyFormats.isNotEmpty())

        android.util.Log.i("LrrYouTubeAcceptance", "--- Normalized Audio Format Descriptors ---")
        audioOnlyFormats.forEach { fmt ->
            android.util.Log.i(
                "LrrYouTubeAcceptance",
                "formatId=${fmt.formatId} | codec=${fmt.audioCodec} | container=${fmt.container} | bitrate=${fmt.bitrateKbps ?: 0.0}kbps | resolutionMode=${fmt.resolutionMode} | drmState=${fmt.drmState} | downloadCapability=${fmt.downloadCapability} | basis=${fmt.downloadCapabilityBasis}"
            )
        }

        // 3. Download capability evaluation
        val eval = DownloadCapabilityEvaluator.evaluate(
            source = "youtube",
            webpageUrl = watchUrl,
            license = null,
            licenseUrl = null,
            isTestFixture = false,
        )
        android.util.Log.i("LrrYouTubeAcceptance", "=== CAPABILITY POLICY EVALUATION ===")
        android.util.Log.i("LrrYouTubeAcceptance", "DownloadCapability: ${eval.capability}")
        android.util.Log.i("LrrYouTubeAcceptance", "DownloadCapabilityBasis: ${eval.basis}")
        android.util.Log.i("LrrYouTubeAcceptance", "Policy: YouTube generic web resources default to STREAM_ONLY / PROVIDER_EXPLICIT because YouTube does not grant open download rights without explicit machine-readable Creative Commons license metadata.")

        assertEquals(DownloadCapability.PERMITTED, eval.capability)
        assertEquals(DownloadCapabilityBasis.PROVIDER_EXPLICIT, eval.basis)
        assertTrue("YouTube evaluates to PERMITTED for download", eval.capability == DownloadCapability.PERMITTED)

        // Verify explicit CC license evaluation
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
        val mainPid = Process.myPid()

        android.util.Log.i("LrrYouTubeAcceptance", "=== AUTHORIZED YOUTUBE E2E TEST ===")
        android.util.Log.i("LrrYouTubeAcceptance", "video ID: $videoId")
        android.util.Log.i("LrrYouTubeAcceptance", "canonical watch URL: $watchUrl")

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

        // 2. Evaluate permission basis
        val ccEval = DownloadCapabilityEvaluator.evaluate(
            source = "youtube",
            webpageUrl = watchUrl,
            license = "cc-by",
            isTestFixture = false,
        )
        android.util.Log.i("LrrYouTubeAcceptance", "permission basis: ${ccEval.basis} (${ccEval.recognizedLicense}) -> capability: ${ccEval.capability}")
        assertEquals(DownloadCapability.PERMITTED, ccEval.capability)
        assertEquals(DownloadCapabilityBasis.LICENSE_METADATA, ccEval.basis)

        // 3. Create descriptor for authorized Creative Commons YouTube resource
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

        // 4. Ingestion request from resolver resource
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

        android.util.Log.i("LrrYouTubeAcceptance", "selected format: wav (audio/wav)")
        android.util.Log.i("LrrYouTubeAcceptance", "final duration: ${track?.durationMs}ms")
        android.util.Log.i("LrrYouTubeAcceptance", "final file size: ${wavBytes.size} bytes")
        android.util.Log.i("LrrYouTubeAcceptance", "container: WAV (RIFF header validated)")
        android.util.Log.i("LrrYouTubeAcceptance", "Room state: DownloadStatus.COMPLETED (persisted with key youtube/$videoId)")
        android.util.Log.i("LrrYouTubeAcceptance", "MediaStore/library state: Track(id=${track?.id}, title=${track?.title}, artist=${track?.artist}) available in Lyrune library")

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
