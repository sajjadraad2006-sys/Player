package dev.auralis.studio.online

import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityBasis
import dev.auralis.studio.resolver.api.ResolutionMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressiveMergeTest {

    private class TimedProvider(
        override val id: String,
        private val delayMs: Long,
        private val resultTrack: OnlineTrack,
    ) : MusicCatalogProvider {
        override val displayName: String = id
        override val isConfigured: Boolean = true

        override suspend fun search(query: String, page: Int): ProviderResult<List<OnlineTrack>> {
            delay(delayMs)
            return ProviderResult.Success(listOf(resultTrack))
        }
    }

    @Test
    fun testProgressiveSourceArrival_preservesStableIdAndUpgradesCapabilities() = runTest {
        // Provider 1: iTunes preview arrives fast at 50ms
        val itunesTrack = OnlineTrack(
            provider = "itunes",
            id = "it1",
            title = "Closer",
            artist = "The Chainsmokers",
            durationMs = 30_000L,
            previewUrl = "https://apple.com/preview.m4a",
            streamAllowed = false,
            downloadAllowed = false,
        )
        val itunesProvider = TimedProvider("itunes", 50L, itunesTrack)

        // Provider 2: Audius full stream arrives at 150ms
        val audiusTrack = OnlineTrack(
            provider = "audius",
            id = "au1",
            title = "Closer",
            artist = "The Chainsmokers",
            durationMs = 244_000L,
            streamUrl = "https://audius.co/stream",
            streamAllowed = true,
            downloadAllowed = false,
        )
        val audiusProvider = TimedProvider("audius", 150L, audiusTrack)

        // Provider 3: Jamendo permitted download arrives at 250ms
        val jamendoTrack = OnlineTrack(
            provider = "jamendo",
            id = "ja1",
            title = "Closer",
            artist = "The Chainsmokers",
            durationMs = 245_000L,
            streamUrl = "https://jamendo.com/stream",
            downloadUrl = "https://jamendo.com/download.flac",
            streamAllowed = true,
            downloadAllowed = true,
            audioFormat = "flac",
        )
        val jamendoProvider = TimedProvider("jamendo", 250L, jamendoTrack)

        val aggregator = UnifiedSearchAggregator(
            providers = listOf(itunesProvider, audiusProvider, jamendoProvider),
            timeoutMs = 5_000L,
        )

        val snapshots = aggregator.search("closer").toList()

        // We should have progressive snapshots
        assertEquals("Should receive progressive emission snapshots", 3, snapshots.size)

        // Snapshot 1 (iTunes preview only)
        val snap1 = snapshots[0]
        assertEquals(1, snap1.recordings.size)
        val rec1 = snap1.recordings.first()
        val stableSessionId = rec1.stableSessionId
        assertEquals(1, rec1.sources.size)
        assertEquals(PlaybackCapability.PREVIEW, rec1.bestPlaybackSource?.playbackCapability)
        assertNull("Snapshot 1 has no permitted download", rec1.bestDownloadSource)

        // Snapshot 2 (Audius full stream arrived)
        val snap2 = snapshots[1]
        assertEquals(1, snap2.recordings.size)
        val rec2 = snap2.recordings.first()
        assertEquals("Stable UI session ID must NOT change", stableSessionId, rec2.stableSessionId)
        assertEquals(2, rec2.sources.size)
        assertEquals("Full stream must take priority over preview for playback", "audius", rec2.bestPlaybackSource?.provider)
        assertEquals(PlaybackCapability.FULL_STREAM, rec2.bestPlaybackSource?.playbackCapability)
        assertNull("Snapshot 2 still has no permitted download", rec2.bestDownloadSource)

        // Snapshot 3 (Jamendo permitted download arrived)
        val snap3 = snapshots[2]
        assertEquals(1, snap3.recordings.size)
        val rec3 = snap3.recordings.first()
        assertEquals("Stable UI session ID must remain identical", stableSessionId, rec3.stableSessionId)
        assertEquals(3, rec3.sources.size)
        assertEquals(PlaybackCapability.FULL_STREAM, rec3.bestPlaybackSource?.playbackCapability)
        assertNotNull("Snapshot 3 now has a permitted download source", rec3.bestDownloadSource)
        assertEquals("jamendo", rec3.bestDownloadSource?.provider)
        assertEquals(DownloadCapability.PERMITTED, rec3.bestDownloadSource?.downloadCapability)
    }
}
