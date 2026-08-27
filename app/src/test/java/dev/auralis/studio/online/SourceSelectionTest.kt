package dev.auralis.studio.online

import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DownloadCapabilityBasis
import dev.auralis.studio.resolver.api.ResolutionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SourceSelectionTest {

    private fun createOnline(
        provider: String,
        id: String,
        playback: PlaybackCapability,
        download: DownloadCapability,
        mode: ResolutionMode = ResolutionMode.DIRECT_RESOURCE,
        audioFormat: String? = "mp3",
        qualityLabel: String? = null,
        formats: List<String> = emptyList(),
    ): RecordingSource = OnlineRecordingSource(
        provider = provider,
        providerTrackId = id,
        title = "Track Title",
        artist = "Artist Name",
        playbackCapability = playback,
        downloadCapability = download,
        downloadCapabilityBasis = if (download == DownloadCapability.PERMITTED) DownloadCapabilityBasis.PROVIDER_EXPLICIT else DownloadCapabilityBasis.NONE,
        resolutionMode = mode,
        audioFormat = audioFormat,
        qualityLabel = qualityLabel,
        availableDownloadFormats = formats,
    )

    private fun createLocal(uri: String = "content://media/123"): RecordingSource = LocalRecordingSource(
        provider = "local",
        providerTrackId = "123",
        localDatabaseId = 123L,
        contentUri = uri,
        title = "Track Title",
        artist = "Artist Name",
        playbackCapability = PlaybackCapability.LOCAL,
        downloadCapability = DownloadCapability.NOT_PERMITTED,
        downloadCapabilityBasis = DownloadCapabilityBasis.NONE,
        resolutionMode = ResolutionMode.DIRECT_RESOURCE,
        canonicalUrl = null,
    )

    @Test
    fun testPlayback_audiusFullVsItunesPreview_audiusWins() {
        val audius = createOnline("audius", "a1", playback = PlaybackCapability.FULL_STREAM, download = DownloadCapability.PERMITTED)
        val itunes = createOnline("itunes", "i1", playback = PlaybackCapability.PREVIEW, download = DownloadCapability.NOT_PERMITTED)

        val selected = PlaybackSourceSelector.select(listOf(itunes, audius))

        assertNotNull(selected)
        assertEquals("Full stream must always beat a 30s preview", "audius", selected?.provider)
    }

    @Test
    fun testPlayback_localVsAudius_localWins() {
        val local = createLocal()
        val audius = createOnline("audius", "a1", playback = PlaybackCapability.FULL_STREAM, download = DownloadCapability.PERMITTED)

        val selected = PlaybackSourceSelector.select(listOf(audius, local))

        assertNotNull(selected)
        assertEquals("Local file must win for instant playback", "local", selected?.provider)
    }

    @Test
    fun testPlayback_onlyPreview_fallbackSelectsPreview() {
        val itunes = createOnline("itunes", "i1", playback = PlaybackCapability.PREVIEW, download = DownloadCapability.NOT_PERMITTED)

        val selected = PlaybackSourceSelector.select(listOf(itunes))

        assertNotNull(selected)
        assertEquals("Preview can be selected as fallback if no full stream exists", PlaybackCapability.PREVIEW, selected?.playbackCapability)
    }

    @Test
    fun testPlayback_localUnreadable_fallsBackToFullStream() {
        val local = createLocal("content://missing")
        val audius = createOnline("audius", "a1", playback = PlaybackCapability.FULL_STREAM, download = DownloadCapability.PERMITTED)

        val isReadable: (String) -> Boolean = { uri -> uri != "content://missing" }
        val selected = PlaybackSourceSelector.select(listOf(local, audius), isReadable)

        assertNotNull(selected)
        assertEquals("Unreadable local file must failover to next full stream", "audius", selected?.provider)
    }

    @Test
    fun testPlayback_localUnreadable_fallsBackToPreview() {
        val local = createLocal("content://missing")
        val itunes = createOnline("itunes", "i1", playback = PlaybackCapability.PREVIEW, download = DownloadCapability.NOT_PERMITTED)

        val isReadable: (String) -> Boolean = { uri -> uri != "content://missing" }
        val selected = PlaybackSourceSelector.select(listOf(local, itunes), isReadable)

        assertNotNull(selected)
        assertEquals("Unreadable local file must failover to preview when no full stream exists", "itunes", selected?.provider)
        assertEquals(PlaybackCapability.PREVIEW, selected?.playbackCapability)
    }

    @Test
    fun testDownload_jamendoPermittedVsItunesNotPermitted_jamendoWins() {
        val itunes = createOnline("itunes", "i1", playback = PlaybackCapability.PREVIEW, download = DownloadCapability.NOT_PERMITTED)
        val jamendo = createOnline("jamendo", "j1", playback = PlaybackCapability.FULL_STREAM, download = DownloadCapability.PERMITTED)

        val selected = DownloadSourceSelector.select(listOf(itunes, jamendo))

        assertNotNull(selected)
        assertEquals("Jamendo permitted source must win download", "jamendo", selected?.provider)
    }

    @Test
    fun testDownload_audiusPermittedVsYouTubeStreamOnly_audiusWins() {
        val yt = createOnline("youtube", "yt1", playback = PlaybackCapability.FULL_STREAM, download = DownloadCapability.STREAM_ONLY)
        val audius = createOnline("audius", "a1", playback = PlaybackCapability.FULL_STREAM, download = DownloadCapability.PERMITTED)

        val selected = DownloadSourceSelector.select(listOf(yt, audius))

        assertNotNull(selected)
        assertEquals("Audius permitted source must win", "audius", selected?.provider)
    }

    @Test
    fun testDownload_onlyUnknownOrStreamOnly_returnsNull() {
        val s1 = createOnline("generic", "g1", playback = PlaybackCapability.FULL_STREAM, download = DownloadCapability.UNKNOWN)
        val s2 = createOnline("youtube", "yt1", playback = PlaybackCapability.FULL_STREAM, download = DownloadCapability.STREAM_ONLY)

        val selected = DownloadSourceSelector.select(listOf(s1, s2))

        assertNull("Non-permitted sources must NOT be offered for download", selected)
    }

    @Test
    fun testDownload_permittedSegmentedResource_isRejected() {
        // Phase 4 constraint: HLS/DASH/segmented formats cannot enter direct downloader even if declared permitted
        val segmented = createOnline("test", "t1", playback = PlaybackCapability.FULL_STREAM, download = DownloadCapability.PERMITTED, mode = ResolutionMode.SEGMENTED_RESOURCE)

        val selected = DownloadSourceSelector.select(listOf(segmented))

        assertNull("Segmented resources MUST be rejected by direct download selector", selected)
    }

    @Test
    fun testDownload_dataSaverPreference_prefersCompactFormat() {
        val lossless = createOnline("jamendo", "flac_track", playback = PlaybackCapability.FULL_STREAM, download = DownloadCapability.PERMITTED, audioFormat = "flac", formats = listOf("flac"))
        val compact = createOnline("jamendo", "mp3_track", playback = PlaybackCapability.FULL_STREAM, download = DownloadCapability.PERMITTED, audioFormat = "mp3", qualityLabel = "96 kbps", formats = listOf("mp31"))

        val selected = DownloadSourceSelector.select(listOf(lossless, compact), DownloadQuality.DATA_SAVER)

        assertNotNull(selected)
        assertEquals("Data saver preference should prioritize compact format over FLAC", "mp3_track", selected?.providerTrackId)
    }

    @Test
    fun testLocalRecordingSource_semantics() {
        val local = createLocal("content://media/external/audio/media/42")
        org.junit.Assert.assertFalse("Local recording source must NEVER be downloadable full track", local.isDownloadableFullTrack)
        assertNull("Local recording source canonicalUrl must be null", local.canonicalUrl)
        assertEquals("content://media/external/audio/media/42", (local as LocalRecordingSource).contentUri)
        assertNull("Download selector must never choose a local recording source", DownloadSourceSelector.select(listOf(local)))
    }
}
