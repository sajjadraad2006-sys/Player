package dev.auralis.studio.ui

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.auralis.studio.AuralisApplication
import dev.auralis.studio.R
import dev.auralis.studio.core.database.DownloadEntity
import dev.auralis.studio.core.database.DownloadStatus
import dev.auralis.studio.core.database.PlaylistEntity
import dev.auralis.studio.core.database.PlaylistTrackEntity
import dev.auralis.studio.core.model.AudioConfig
import dev.auralis.studio.core.model.ThemeSettings
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.core.model.LyricsUiState
import dev.auralis.studio.library.DownloadIngestionRequest
import dev.auralis.studio.lyrics.LrclibTrack
import dev.auralis.studio.lyrics.LyricsClock
import dev.auralis.studio.online.CanonicalRecording
import dev.auralis.studio.online.DownloadQuality
import dev.auralis.studio.online.LocalRecordingSource
import dev.auralis.studio.online.OnlineRecordingSource
import dev.auralis.studio.online.OnlineTrack
import dev.auralis.studio.online.PlaybackSourceSelector
import dev.auralis.studio.online.ProviderResult
import dev.auralis.studio.online.UnifiedTrack
import dev.auralis.studio.online.toStreamableTrack
import dev.auralis.studio.playback.PlaybackConnection
import dev.auralis.studio.audio.visualizer.VisualizerAnalyzer
import dev.auralis.studio.audio.stems.StemKind
import dev.auralis.studio.audio.stems.StemErrorCode
import dev.auralis.studio.audio.stems.StemModelAvailability
import dev.auralis.studio.audio.stems.StemModelManager
import dev.auralis.studio.audio.stems.StemProcessingStage
import dev.auralis.studio.audio.stems.StemSeparationRequest
import dev.auralis.studio.audio.stems.StemUiState
import dev.auralis.studio.audio.stems.VocFtLiteRtEngine
import dev.auralis.studio.audio.stems.VocFtModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application
    private val container = (application as AuralisApplication).container
    private val dao = container.database.dao()
    private val downloadsDao = container.database.downloadsDao()
    val playback = PlaybackConnection(application, viewModelScope)
    val playbackState = playback.state
    private val visualizerAnalyzer = VisualizerAnalyzer(container.pcmAudioTap)
    val visualizerBands = visualizerAnalyzer.bands
    val visualizerWaveform = visualizerAnalyzer.waveform
    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.NotFound)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()
    val lyricsClock = LyricsClock()
    private val _lyricsCandidates = MutableStateFlow<List<LrclibTrack>>(emptyList())
    val lyricsCandidates: StateFlow<List<LrclibTrack>> = _lyricsCandidates.asStateFlow()
    private val _isSearchingLyricsCandidates = MutableStateFlow(false)
    val isSearchingLyricsCandidates: StateFlow<Boolean> = _isSearchingLyricsCandidates.asStateFlow()
    private val stemModelManager = StemModelManager(File(application.filesDir, "stem_models"))
    private val stemEngine = VocFtLiteRtEngine(stemModelManager)
    private val _stemState = MutableStateFlow<StemUiState>(StemUiState.CheckingModel)
    val stemState: StateFlow<StemUiState> = _stemState.asStateFlow()
    private var stemJob: Job? = null
    private val generatedStemTracks = ConcurrentHashMap<String, Track>()
    private val generatedStemSources = ConcurrentHashMap<String, Track>()
    private var currentLyricsTrack: Track? = null
    val currentPlayingOnlineTrack = MutableStateFlow<Track?>(null)

    val tracks: StateFlow<List<Track>> = combine(container.library.tracks, downloadsDao.observeAll()) { libTracks, downloadList ->
        if (downloadList.isEmpty()) return@combine libTracks
        val byUri = downloadList.filter { it.trackUri.isNotBlank() }.associateBy { it.trackUri }
        val byPath = downloadList.filter { it.destinationPath.isNotBlank() }.associateBy { it.destinationPath }
        val byIdentity = downloadList.filter { it.recordingIdentity.isNotBlank() }.associateBy { it.recordingIdentity }
        libTracks.map { track ->
            val match = byUri[track.uri]
                ?: byPath[track.uri]
                ?: byIdentity[DownloadIngestionRequest.deriveRecordingIdentity(track.artist, track.title)]
            if (match != null) {
                // If title was corrupted by underscores from older downloads or media scanner, restore proper title
                val isCorruptedTitle = track.title.all { it == '_' || it == '-' || it == ' ' } || track.title.contains("___")
                val restoredTitle = if (isCorruptedTitle && match.title.isNotBlank()) match.title else track.title
                val restoredArtist = if (track.artist.isBlank() && match.artist.isNotBlank()) match.artist else track.artist
                val artwork = match.artworkUrl.takeIf { it.isNotBlank() } ?: track.customArtworkUri
                track.copy(
                    title = restoredTitle,
                    artist = restoredArtist,
                    customArtworkUri = artwork,
                )
            } else {
                track
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites: StateFlow<Set<String>> = dao.observeFavoriteUris()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val historyUris: StateFlow<List<String>> = dao.observeHistory()
        .map { rows -> rows.map { it.trackUri } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists = dao.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val audioConfig = MutableStateFlow(AudioConfig())
    val themeSettings = MutableStateFlow(ThemeSettings())
    val musicFolders = container.settings.musicFolderUris
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val minimumTrackDuration = container.settings.minimumTrackDurationMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 15_000L)
    val searchQuery = MutableStateFlow("")
    val isScanning = MutableStateFlow(false)
    val libraryError = MutableStateFlow<String?>(null)
    val sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    private var sleepTimerJob: Job? = null
    private var audioSaveJob: Job? = null
    private var themeSaveJob: Job? = null

    // ── Online Search & Downloads ─────────────────────────────────────────────
    private val prefs = application.getSharedPreferences("aura_studio_prefs", Context.MODE_PRIVATE)
    val downloadFolderLocation = MutableStateFlow(prefs.getString("download_folder_name", "Music/Lyrune/") ?: "Music/Lyrune/")
    val downloadFolderUri = MutableStateFlow(prefs.getString("download_folder_uri", null))

    val mediaResolver: dev.auralis.studio.resolver.api.MediaResolver get() = container.mediaResolver

    private val providerRegistry = container.musicProviders

    fun setDownloadLocation(uri: Uri, folderName: String) {
        prefs.edit()
            .putString("download_folder_uri", uri.toString())
            .putString("download_folder_name", folderName)
            .apply()
        downloadFolderUri.value = uri.toString()
        downloadFolderLocation.value = folderName
    }

    fun resetDownloadLocation() {
        prefs.edit()
            .remove("download_folder_uri")
            .putString("download_folder_name", "Music/Lyrune/")
            .apply()
        downloadFolderUri.value = null
        downloadFolderLocation.value = "Music/Lyrune/"
    }

    private val _onlineSearchQuery = MutableStateFlow("")
    val onlineSearchQuery: StateFlow<String> = _onlineSearchQuery.asStateFlow()
    private val _onlineResults = MutableStateFlow<List<UnifiedTrack>>(emptyList())
    val onlineResults: StateFlow<List<UnifiedTrack>> = _onlineResults.asStateFlow()
    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()
    private val _onlineProviderFailures = MutableStateFlow(0)
    val onlineProviderFailures: StateFlow<Int> = _onlineProviderFailures.asStateFlow()
    private val _providersPending = MutableStateFlow(0)
    val providersPending: StateFlow<Int> = _providersPending.asStateFlow()
    val downloadQuality: StateFlow<DownloadQuality> = container.settings.downloadQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadQuality.BEST_AVAILABLE)
    val downloads: StateFlow<List<DownloadEntity>> = downloadsDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentDownloads: StateFlow<List<DownloadEntity>> = downloadsDao.observeByStatus(DownloadStatus.COMPLETED.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentSearches: StateFlow<List<String>> = container.settings.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val youtubeApiKey: StateFlow<String> = container.settings.youtubeApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setYoutubeApiKey(key: String) {
        viewModelScope.launch {
            container.settings.setYoutubeApiKey(key)
        }
    }

    val searchResults: StateFlow<List<Track>> = combine(
        searchQuery.debounce(180),
        tracks,
    ) { query, allTracks ->
        dev.auralis.studio.library.SearchEngine.search(allTracks, query)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            container.settings.youtubeApiKey.collect { key ->
                container.youtubeProvider.updateApiKey(key)
            }
        }
        viewModelScope.launch {
            audioConfig.value = container.settings.audioConfig.first()
            playback.applyAudioConfig(audioConfig.value)
        }
        viewModelScope.launch {
            container.settings.themeSettings.collect { settings ->
                themeSettings.value = settings
            }
        }

        viewModelScope.launch {
            playback.appliedAudioConfig.collect { applied ->
                if (applied != null && applied == audioConfig.value) {
                    container.settings.setAudioConfig(applied)
                }
            }
        }
        viewModelScope.launch {
            playbackState.map { it.currentMediaItem?.mediaId }
                .distinctUntilChanged()
                .collectLatest { mediaId ->
                    val track = mediaId?.let(::trackForLyrics)
                    currentLyricsTrack = track
                    if (track == null) _lyricsState.value = LyricsUiState.NotFound
                    else resolveLyrics(track)
                }
        }
        viewModelScope.launch {
            playbackState.collect { state ->
                lyricsClock.update(state.positionMs, state.isPlaying, audioConfig.value.speed)
            }
        }
        viewModelScope.launch {
            _onlineSearchQuery
                .debounce(200)
                .map { it.trim() }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        _isSearchingOnline.value = false
                        _onlineProviderFailures.value = 0
                        _providersPending.value = 0
                        flowOf(dev.auralis.studio.online.SearchSnapshot.EMPTY)
                    } else {
                        _isSearchingOnline.value = true
                        container.unifiedSearch.search(query)
                    }
                }
                .collectLatest { snapshot ->
                    _onlineResults.value = snapshot.recordings
                    _onlineProviderFailures.value = snapshot.failures.size
                    _providersPending.value = snapshot.providersPending
                    _isSearchingOnline.value = !snapshot.isComplete
                }
        }
        refreshLibrary()
        viewModelScope.launch { refreshStemModelState() }
        viewModelScope.launch {
            downloadsDao.observeByStatus(DownloadStatus.COMPLETED.name)
                .distinctUntilChanged { old, new -> old.map { it.id } == new.map { it.id } }
                .collect { list ->
                    if (list.isNotEmpty()) {
                        delay(350)
                        refreshLibrary()
                    }
                }
        }
    }

    val hasAudioPermission: Boolean get() = container.library.hasPermission

    fun refreshLibrary() {
        viewModelScope.launch {
            isScanning.value = true
            val minimum = container.settings.minimumTrackDurationMs.first()
            val mediaResult = if (container.library.hasPermission) container.library.refresh(minimum) else Result.success(emptyList())
            val safResult = runCatching {
                container.safLibrary.scan(container.settings.musicFolderUris.first(), minimum)
            }
            safResult.onSuccess(container.library::updateSafTracks)
            mediaResult.exceptionOrNull()?.let { libraryError.value = it.localizedMessage }
                ?: safResult.exceptionOrNull()?.let { libraryError.value = it.localizedMessage }
                ?: run { libraryError.value = null }
            isScanning.value = false
        }
    }

    fun searchOnline(query: String) {
        _onlineSearchQuery.value = query
    }

    fun commitSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            addRecentSearch(trimmed)
        }
    }

    fun addRecentSearch(query: String) {
        viewModelScope.launch {
            container.settings.addRecentSearch(query)
        }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            container.settings.removeRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            container.settings.clearRecentSearches()
        }
    }

    private fun isUriReadable(uriString: String): Boolean {
        if (uriString.isBlank()) return false
        val uri = Uri.parse(uriString)
        return try {
            when (uri.scheme?.lowercase()) {
                "file" -> uri.path?.let { File(it).canRead() } ?: false
                "content" -> appContext.contentResolver.openInputStream(uri)?.use { true } ?: false
                else -> File(uriString).canRead()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun playOnlineTrack(unifiedTrack: CanonicalRecording) {
        viewModelScope.launch {
            // Check completed download in local database first
            val derivedId = DownloadIngestionRequest.deriveRecordingIdentity(unifiedTrack.artist, unifiedTrack.title)
            val downloadedUri: String? = withContext(Dispatchers.IO) {
                (downloadsDao.findCompletedByRecordingIdentity(unifiedTrack.recordingIdentity)
                    ?: downloadsDao.findCompletedByRecordingIdentity(derivedId))
                    ?.takeIf { it.status == DownloadStatus.COMPLETED.name && it.trackUri.isNotBlank() }
                    ?.trackUri
            }

            if (downloadedUri != null) {
                val isReadable = withContext(Dispatchers.IO) { isUriReadable(downloadedUri) }
                if (isReadable) {
                    val localTrack = container.library.track(downloadedUri)?.let {
                        if (it.customArtworkUri.isNullOrBlank() && unifiedTrack.artworkUrl.isNotBlank()) {
                            it.copy(customArtworkUri = unifiedTrack.artworkUrl)
                        } else it
                    } ?: Track(
                        id = -1L,
                        uri = downloadedUri,
                        title = unifiedTrack.title,
                        artist = unifiedTrack.artist,
                        album = unifiedTrack.album,
                        durationMs = unifiedTrack.durationMs,
                        customArtworkUri = unifiedTrack.artworkUrl.takeIf { it.isNotBlank() },
                    )
                    playback.play(localTrack, listOf(localTrack))
                    return@launch
                }
            }

            // Deterministic playback candidate failover
            val isLocalReadable: (String) -> Boolean = { uri -> isUriReadable(uri) }
            val candidates = PlaybackSourceSelector.rank(unifiedTrack.sources, isLocalReadable)
            for (candidate in candidates) {
                when (candidate) {
                    is LocalRecordingSource -> {
                        val localTrack = candidate.originalLocalTrack ?: container.library.track(candidate.contentUri)
                        if (localTrack != null) {
                            val trackWithArt = if (localTrack.customArtworkUri.isNullOrBlank() && unifiedTrack.artworkUrl.isNotBlank()) {
                                localTrack.copy(customArtworkUri = unifiedTrack.artworkUrl)
                            } else localTrack
                            playback.play(trackWithArt, listOf(trackWithArt))
                            return@launch
                        }
                    }
                    is OnlineRecordingSource -> {
                        val onlineTrack = candidate.originalTrack
                        if (onlineTrack != null) {
                            if (onlineTrack.provider.equals("youtube", true)) {
                                val watchUrl = onlineTrack.sourceUrl.takeIf { it.isNotBlank() }
                                    ?: "https://www.youtube.com/watch?v=${onlineTrack.id}"
                                val app = getApplication<Application>()
                                Toast.makeText(app, app.getString(R.string.loading_stream), Toast.LENGTH_SHORT).show()
                                val resolvedMedia = withContext(Dispatchers.IO) {
                                    container.mediaResolver.resolve(
                                        dev.auralis.studio.resolver.api.ResolverRequest(
                                            sourceUrl = watchUrl,
                                            sourceId = onlineTrack.id,
                                            intent = dev.auralis.studio.resolver.api.ResolveIntent.PLAYBACK,
                                        )
                                    )
                                }
                                if (resolvedMedia is dev.auralis.studio.resolver.api.ResolverResult.Success) {
                                    val streamUrl = resolvedMedia.lease?.url
                                        ?: resolvedMedia.media.rawFormats.firstOrNull { it.isAudioOnly && it.url.isNotBlank() }?.url
                                        ?: resolvedMedia.media.rawFormats.firstOrNull { it.url.isNotBlank() }?.url
                                    if (!streamUrl.isNullOrBlank()) {
                                        val trackToPlay = Track(
                                            id = -1L,
                                            uri = streamUrl,
                                            title = unifiedTrack.title,
                                            artist = unifiedTrack.artist,
                                            album = unifiedTrack.album,
                                            durationMs = resolvedMedia.media.durationMs ?: unifiedTrack.durationMs,
                                            customArtworkUri = unifiedTrack.artworkUrl.takeIf { it.isNotBlank() },
                                        )
                                        currentPlayingOnlineTrack.value = trackToPlay
                                        playback.play(trackToPlay, listOf(trackToPlay))
                                        return@launch
                                    }
                                } else if (resolvedMedia is dev.auralis.studio.resolver.api.ResolverResult.Failure) {
                                    Toast.makeText(app, resolvedMedia.error.safeUserMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                            val resolved = providerRegistry.resolveStream(onlineTrack)
                            if (resolved is ProviderResult.Success) {
                                val trackToPlay = onlineTrack.copy(
                                    streamUrl = resolved.data.url.takeIf { resolved.data.kind == dev.auralis.studio.online.MediaUrlKind.STREAM },
                                    previewUrl = resolved.data.url.takeIf { resolved.data.kind == dev.auralis.studio.online.MediaUrlKind.PREVIEW },
                                    streamAllowed = resolved.data.kind == dev.auralis.studio.online.MediaUrlKind.STREAM,
                                ).toStreamableTrack()
                                if (trackToPlay != null) {
                                    currentPlayingOnlineTrack.value = trackToPlay
                                    playback.play(trackToPlay, listOf(trackToPlay))
                                    return@launch
                                }
                            } else if (!onlineTrack.bestStreamUrl.isNullOrBlank()) {
                                val trackToPlay = onlineTrack.toStreamableTrack()
                                if (trackToPlay != null) {
                                    currentPlayingOnlineTrack.value = trackToPlay
                                    playback.play(trackToPlay, listOf(trackToPlay))
                                    return@launch
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** One tap → at most one download. Re-entry for an active DownloadKey is ignored. */
    private val inFlightDownloads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun downloadOnlineTrack(unifiedTrack: CanonicalRecording) {
        val app = getApplication<Application>()
        val selectedSource = unifiedTrack.bestDownloadSource(downloadQuality.value) as? OnlineRecordingSource
        val source = selectedSource?.originalTrack
            ?: unifiedTrack.sources.firstOrNull { it.provider.equals("youtube", true) }?.let { (it as? OnlineRecordingSource)?.originalTrack }

        if (source == null) {
            Toast.makeText(app, app.getString(R.string.preview_only_no_download), Toast.LENGTH_SHORT).show()
            return
        }
        // Process-safe unique work: repeated taps reuse/ignore existing work (KEEP).
        // The inFlightDownloads guard only suppresses duplicate toasts.
        if (!inFlightDownloads.add(unifiedTrack.recordingIdentity)) return
        viewModelScope.launch {
            try {
                val derivedId = DownloadIngestionRequest.deriveRecordingIdentity(unifiedTrack.artist, unifiedTrack.title)
                val existing = withContext(Dispatchers.IO) {
                    downloadsDao.findOwnedByRecordingIdentity(unifiedTrack.recordingIdentity)
                        ?: downloadsDao.findOwnedByRecordingIdentity(derivedId)
                }
                if (existing != null) {
                    val fileExists = existing.trackUri.isNotBlank() && isUriReadable(existing.trackUri)
                    if (existing.status == DownloadStatus.COMPLETED.name && fileExists) {
                        Toast.makeText(app, app.getString(R.string.downloaded), Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    if (existing.status != DownloadStatus.DOWNLOADING.name || !fileExists) {
                        withContext(Dispatchers.IO) { downloadsDao.deleteById(existing.id) }
                    } else {
                        Toast.makeText(app, app.getString(R.string.downloading), Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }

                if (source.provider.equals("youtube", true)) {
                    val canonicalUrl = source.sourceUrl.takeIf { it.isNotBlank() }
                        ?: "https://www.youtube.com/watch?v=${source.id}"
                    val descriptor = dev.auralis.studio.resolver.api.ResolverDownloadDescriptor(
                        source = "youtube",
                        sourceId = source.id,
                        canonicalUrl = canonicalUrl,
                        title = unifiedTrack.title,
                        artist = unifiedTrack.artist,
                        album = unifiedTrack.album,
                        artworkUrl = unifiedTrack.artworkUrl,
                        expectedDurationMs = unifiedTrack.durationMs,
                        qualityPreference = when (downloadQuality.value) {
                            DownloadQuality.DATA_SAVER -> dev.auralis.studio.resolver.api.AudioQualityPreference.LOW
                            DownloadQuality.HIGH -> dev.auralis.studio.resolver.api.AudioQualityPreference.HIGH
                            DownloadQuality.BEST_AVAILABLE -> dev.auralis.studio.resolver.api.AudioQualityPreference.BEST
                        },
                        downloadCapability = dev.auralis.studio.resolver.api.DownloadCapability.PERMITTED,
                    )
                    enqueueResolverDownload(descriptor)
                    Toast.makeText(app, app.getString(R.string.downloading_location, downloadFolderLocation.value), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val resolved = providerRegistry.resolveDownload(source, downloadQuality.value)
                if (resolved !is ProviderResult.Success) {
                    Toast.makeText(app, app.getString(R.string.preview_only_no_download), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val verified = source.copy(
                    title = unifiedTrack.title,
                    artist = unifiedTrack.artist,
                    album = unifiedTrack.album,
                    albumArtist = unifiedTrack.albumArtist,
                    durationMs = unifiedTrack.durationMs,
                    artworkUrl = unifiedTrack.artworkUrl,
                    isrc = unifiedTrack.isrc,
                    downloadUrl = resolved.data.url,
                    downloadAllowed = true,
                    audioFormat = resolved.data.format,
                    qualityLabel = resolved.data.qualityLabel,
                )
                dev.auralis.studio.library.enqueueDownload(app, verified)
                Toast.makeText(app, app.getString(R.string.downloading_location, downloadFolderLocation.value), Toast.LENGTH_SHORT).show()
            } finally {
                inFlightDownloads.remove(unifiedTrack.recordingIdentity)
            }
        }
    }

    fun cancelDownload(onlineTrack: OnlineTrack) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            dev.auralis.studio.library.cancelDownload(app, onlineTrack.provider, onlineTrack.id)
            downloadsDao.findByKey(onlineTrack.provider, onlineTrack.id)
                ?.takeIf { it.status == DownloadStatus.DOWNLOADING.name || it.status == DownloadStatus.QUEUED.name }
                ?.let { downloadsDao.updateStatus(it.id, DownloadStatus.CANCELLED.name, "") }
        }
    }

    fun enqueueResolverDownload(descriptor: dev.auralis.studio.resolver.api.ResolverDownloadDescriptor) {
        if (descriptor.downloadCapability != dev.auralis.studio.resolver.api.DownloadCapability.PERMITTED) return
        val app = getApplication<Application>()
        dev.auralis.studio.library.enqueueDownload(app, descriptor)
        Toast.makeText(app, app.getString(R.string.downloading_location, downloadFolderLocation.value), Toast.LENGTH_SHORT).show()
    }

    fun setDownloadQuality(quality: DownloadQuality) {
        viewModelScope.launch {
            container.settings.setDownloadQuality(quality)
            container.unifiedSearch.invalidate()
        }
    }

    // ── Review broken downloads ─────────────────────────────────────────────

    val brokenDownloads = MutableStateFlow<List<dev.auralis.studio.library.DownloadRepairManager.BrokenDownload>>(emptyList())
    val isScanningBrokenDownloads = MutableStateFlow(false)
    val metadataRepairMessage = MutableStateFlow<String?>(null)

    fun scanForBrokenDownloads() {
        if (isScanningBrokenDownloads.value) return
        viewModelScope.launch {
            isScanningBrokenDownloads.value = true
            try {
                brokenDownloads.value = withContext(Dispatchers.IO) {
                    container.downloadRepair.findBrokenDownloads()
                        // Skip rows the user already chose to keep.
                        .filterNot { it.entity.errorMessage == "user_kept" }
                }
            } finally {
                isScanningBrokenDownloads.value = false
            }
        }
    }

    fun removeBrokenDownload(item: dev.auralis.studio.library.DownloadRepairManager.BrokenDownload, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { container.downloadRepair.removeBrokenDownload(item) }
            if (removed) {
                brokenDownloads.value = brokenDownloads.value.filterNot { it.entity.id == item.entity.id }
            }
            onDone(removed)
        }
    }

    fun keepSuspiciousFile(entityId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            container.downloadRepair.keepFile(entityId)
            brokenDownloads.value = brokenDownloads.value.filterNot { it.entity.id == entityId }
        }
    }

    fun repairPlaceholderMetadata(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { container.downloadRepair.repairPlaceholderMetadata() }
            metadataRepairMessage.value = appContext.getString(R.string.metadata_repaired_count, count)
            refreshLibrary()
            onDone(count)
        }
    }

    fun addMusicFolder(uri: String) {
        viewModelScope.launch {
            container.settings.addMusicFolder(uri)
            refreshLibrary()
        }
    }

    fun removeMusicFolder(uri: String) {
        viewModelScope.launch {
            container.settings.removeMusicFolder(uri)
            refreshLibrary()
        }
    }

    fun toggleFavorite(uri: String) {
        viewModelScope.launch(Dispatchers.IO) { dao.toggleFavorite(uri) }
    }

    fun updateAudio(config: AudioConfig) {
        audioConfig.value = config
        playback.applyAudioConfig(config)
        lyricsClock.onSpeedChanged(config.speed, playbackState.value.positionMs)
        audioSaveJob?.cancel()
        audioSaveJob = viewModelScope.launch {
            delay(120)
            container.settings.setAudioConfig(config)
        }
    }

    fun downloadStemModel() {
        if (stemJob?.isActive == true) return
        stemJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val connection = (URL(VocFtModel.DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }
                try {
                    connection.connect()
                    if (connection.responseCode !in 200..299) error("Model download failed: HTTP ${connection.responseCode}")
                    val availability = stemModelManager.install(
                        VocFtModel.descriptor,
                        connection.inputStream.buffered(),
                    ) { progress -> _stemState.value = StemUiState.DownloadingModel(progress) }
                    _stemState.value = when (availability) {
                        is StemModelAvailability.Installed -> StemUiState.ReadyForTrack
                        is StemModelAvailability.Invalid -> StemUiState.Error(StemErrorCode.MODEL_INTEGRITY, false)
                        else -> StemUiState.Error(StemErrorCode.DOWNLOAD_FAILED, false)
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _stemState.value = StemUiState.ModelRequired(VocFtModel.descriptor.modelBytes)
                throw cancelled
            } catch (error: Throwable) {
                Log.e("StemSeparation", "Voc_FT model download failed", error)
                _stemState.value = StemUiState.Error(StemErrorCode.DOWNLOAD_FAILED, false)
            }
        }
    }

    fun deleteStemModel() {
        cancelStemWork()
        stemModelManager.delete(VocFtModel.descriptor)
        _stemState.value = StemUiState.ModelRequired(VocFtModel.descriptor.modelBytes)
    }

    fun prepareStems(track: Track) {
        if (stemJob?.isActive == true) return
        // "Prepare again" may be pressed while one generated WAV is playing. Always resolve that
        // back to the original source instead of recursively separating an already separated stem.
        val sourceTrack = generatedStemSources[track.uri] ?: track
        val sourceIdentity = stemSourceIdentity(sourceTrack)
        stemJob = viewModelScope.launch(Dispatchers.IO) {
            val workDirectory = File(appContext.cacheDir, "stem_sources").apply { mkdirs() }
            val materialized = File(workDirectory, "${sha256(sourceIdentity)}.source")
            try {
                val estimatedFrames = (sourceTrack.durationMs.coerceAtLeast(60_000L) * 44_100L) / 1_000L
                val requiredBytes = sourceTrack.sizeBytes.coerceAtLeast(0L) + estimatedFrames * 32L
                if (appContext.filesDir.usableSpace < requiredBytes) {
                    _stemState.value = StemUiState.Error(StemErrorCode.NOT_ENOUGH_STORAGE, true)
                    return@launch
                }
                _stemState.value = StemUiState.Processing(StemProcessingStage.COPYING_SOURCE, null, sourceIdentity)
                materializeTrack(sourceTrack, materialized) { progress ->
                    _stemState.value = StemUiState.Processing(StemProcessingStage.COPYING_SOURCE, progress, sourceIdentity)
                }
                val result = stemEngine.separate(
                    StemSeparationRequest(
                        sourceIdentity = sourceIdentity,
                        sourceFile = materialized,
                        outputDirectory = File(appContext.filesDir, "stem_cache"),
                    ),
                ) { engineProgress ->
                    _stemState.value = when {
                        engineProgress <= 20 -> StemUiState.Processing(
                            StemProcessingStage.DECODING,
                            (engineProgress * 5).coerceIn(0, 100),
                            sourceIdentity,
                        )
                        engineProgress <= 90 -> StemUiState.Processing(
                            StemProcessingStage.SEPARATING,
                            (((engineProgress - 20) * 100) / 70).coerceIn(0, 100),
                            sourceIdentity,
                        )
                        else -> StemUiState.Processing(
                            StemProcessingStage.SAVING,
                            (((engineProgress - 90) * 100) / 10).coerceIn(0, 100),
                            sourceIdentity,
                        )
                    }
                }
                registerStemTracks(sourceTrack, result)
                _stemState.value = StemUiState.Complete(result, sourceIdentity)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _stemState.value = StemUiState.ReadyForTrack
                throw cancelled
            } catch (error: Throwable) {
                Log.e("StemSeparation", "Voc_FT separation failed for ${sourceTrack.uri}", error)
                _stemState.value = StemUiState.Error(StemErrorCode.SEPARATION_FAILED, true)
            } finally {
                materialized.delete()
            }
        }
    }

    fun cancelStemWork() {
        stemEngine.cancel()
        stemJob?.cancel()
        stemJob = null
    }

    val stemGains = MutableStateFlow(dev.auralis.studio.audio.stems.StemMixerController.gains.get())

    fun setVocalsGain(gain: Float) {
        dev.auralis.studio.audio.stems.StemMixerController.setVocalsGain(gain)
        stemGains.value = dev.auralis.studio.audio.stems.StemMixerController.gains.get()
    }

    fun setInstrumentalGain(gain: Float) {
        dev.auralis.studio.audio.stems.StemMixerController.setInstrumentalGain(gain)
        stemGains.value = dev.auralis.studio.audio.stems.StemMixerController.gains.get()
    }

    fun setBassGain(gain: Float) {
        dev.auralis.studio.audio.stems.StemMixerController.setBassGain(gain)
        stemGains.value = dev.auralis.studio.audio.stems.StemMixerController.gains.get()
    }

    fun playStemMix() {
        val result = (_stemState.value as? StemUiState.Complete)?.result ?: return
        val vocalsFile = result.files[StemKind.VOCALS] ?: return
        val dir = vocalsFile.parentFile ?: return
        val stemUri = "lyrune-stem://${dir.absolutePath}"
        val sourceTrack = generatedStemSources[vocalsFile.toURI().toString()]
            ?: tracks.value.firstOrNull()
            ?: return

        val mixTrack = sourceTrack.copy(
            id = -999L,
            uri = stemUri,
            title = "${sourceTrack.title} (Stem Mix)",
            displayName = "stem_mix.wav",
            mimeType = "audio/wav",
        )
        generatedStemTracks[stemUri] = mixTrack
        generatedStemSources[stemUri] = sourceTrack
        playback.play(mixTrack, listOf(mixTrack))
    }

    fun playStem(kind: StemKind) {
        val result = (_stemState.value as? StemUiState.Complete)?.result ?: return
        val uri = result.files[kind]?.toURI()?.toString() ?: return
        val track = generatedStemTracks[uri] ?: return
        playback.play(track, listOf(track))
    }

    fun generatedTrack(uri: String): Track? = generatedStemTracks[uri]


    private suspend fun refreshStemModelState() {
        _stemState.value = when (val availability = stemEngine.availability()) {
            is StemModelAvailability.Installed -> StemUiState.ReadyForTrack
            is StemModelAvailability.DownloadRequired -> StemUiState.ModelRequired(availability.bytes)
            is StemModelAvailability.Invalid -> StemUiState.Error(StemErrorCode.MODEL_INTEGRITY, false)
            StemModelAvailability.NotOffered -> StemUiState.Error(StemErrorCode.MODEL_UNAVAILABLE, false)
        }
    }

    private fun registerStemTracks(source: Track, result: dev.auralis.studio.audio.stems.StemSeparationResult) {
        result.files.forEach { (kind, file) ->
            val uri = file.toURI().toString()
            generatedStemTracks[uri] = source.copy(
                id = -(kind.ordinal + 1L),
                uri = uri,
                title = appContext.getString(
                    if (kind == StemKind.VOCALS) R.string.stem_track_title_vocals else R.string.stem_track_title_instrumental,
                    source.title,
                ),
                album = source.album,
                displayName = file.name,
                mimeType = "audio/wav",
                sizeBytes = file.length(),
            )
            generatedStemSources[uri] = source
        }
    }

    private fun stemSourceIdentity(track: Track): String =
        "${track.uri}|${track.sizeBytes}|${track.durationMs}|${track.dateAddedSeconds}"

    private fun materializeTrack(track: Track, destination: File, onProgress: (Int?) -> Unit) {
        val uri = Uri.parse(track.uri)
        val directFile = when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let(::File)
            null -> File(track.uri)
            else -> null
        }
        if (directFile?.isFile == true) {
            directFile.inputStream().buffered().use { input -> copyMeasured(input, destination, directFile.length(), onProgress) }
            return
        }
        val input = when (uri.scheme?.lowercase()) {
            "http", "https" -> URL(track.uri).openStream()
            else -> appContext.contentResolver.openInputStream(uri)
        } ?: error("Unable to open source audio")
        input.buffered().use { copyMeasured(it, destination, track.sizeBytes.takeIf { size -> size > 0L }, onProgress) }
    }

    private fun copyMeasured(input: java.io.InputStream, destination: File, totalBytes: Long?, onProgress: (Int?) -> Unit) {
        destination.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var copied = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                copied += count
                onProgress(totalBytes?.let { ((copied * 100L) / it.coerceAtLeast(1L)).toInt().coerceIn(0, 100) })
            }
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }

    fun updateTheme(settings: ThemeSettings) {
        themeSettings.value = settings
        visualizerAnalyzer.setQuality(settings.visualQuality)
        themeSaveJob?.cancel()
        themeSaveJob = viewModelScope.launch {
            delay(120)
            container.settings.setTheme(settings)
        }
    }

    fun setVisualizerActive(active: Boolean) {
        if (active) visualizerAnalyzer.start(themeSettings.value.visualQuality)
        else visualizerAnalyzer.stop()
    }

    fun retryLyrics() {
        currentLyricsTrack?.let { track -> viewModelScope.launch { resolveLyrics(track, forceRefresh = true) } }
    }

    fun updateLyricsOffset(offsetMs: Long) {
        val track = currentLyricsTrack ?: return
        val found = _lyricsState.value as? LyricsUiState.Found ?: return
        val safeOffset = offsetMs.coerceIn(-5_000L, 5_000L)
        _lyricsState.value = LyricsUiState.Found(found.lyrics.copy(offsetMs = safeOffset))
        viewModelScope.launch { container.lyrics.updateOffset(track, safeOffset) }
    }

    fun importLyrics(lrcContent: String) {
        val track = currentLyricsTrack ?: return
        viewModelScope.launch {
            runCatching { container.lyrics.importLrc(track, lrcContent) }
                .onSuccess { _lyricsState.value = LyricsUiState.Found(container.lyrics.resolve(track)) }
                .onFailure { _lyricsState.value = LyricsUiState.Error(it.localizedMessage ?: appContext.getString(R.string.lyrics_import_failed)) }
        }
    }

    fun seekTo(positionMs: Long) {
        playback.seekTo(positionMs)
        lyricsClock.onSeek(positionMs)
    }

    fun loadLyricsCandidates() {
        val track = currentLyricsTrack ?: return
        viewModelScope.launch {
            _lyricsCandidates.value = emptyList()
            _isSearchingLyricsCandidates.value = true
            try {
                _lyricsCandidates.value = container.lyrics.searchCandidates(track)
            } finally {
                _isSearchingLyricsCandidates.value = false
            }
        }
    }

    fun selectLyricsCandidate(candidate: LrclibTrack) {
        val track = currentLyricsTrack ?: return
        viewModelScope.launch {
            _lyricsState.value = LyricsUiState.Loading
            val selected = container.lyrics.selectCandidate(track, candidate)
            _lyricsState.value = LyricsUiState.Found(selected)
            _lyricsCandidates.value = emptyList()
        }
    }

    fun searchLyricsManually(title: String) {
        val track = currentLyricsTrack ?: return
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        viewModelScope.launch {
            val norm = dev.auralis.studio.lyrics.LyricsMetadataNormalizer.normalize(cleanTitle, track.artist)
            resolveLyrics(track.copy(title = norm.title, artist = norm.artist), forceRefresh = true)
        }
    }

    private suspend fun resolveLyrics(track: Track, forceRefresh: Boolean = false) {
        _lyricsState.value = LyricsUiState.Loading
        try {
            val resolved = container.lyrics.resolve(track, forceRefresh)
            _lyricsState.value = when {
                resolved.lines.isNotEmpty() -> LyricsUiState.Found(resolved)
                !container.lyrics.isNetworkAvailable() -> LyricsUiState.Offline
                container.lyrics.lastLookupWasRateLimited -> LyricsUiState.Error(appContext.getString(R.string.lyrics_rate_limited))
                container.lyrics.lastLookupError != null -> LyricsUiState.Error(container.lyrics.lastLookupError ?: appContext.getString(R.string.lyrics_search_failed))
                else -> LyricsUiState.NotFound
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _lyricsState.value = LyricsUiState.Error(error.localizedMessage ?: appContext.getString(R.string.lyrics_search_failed))
        }
    }

    private fun trackForLyrics(mediaId: String): Track? {
        val downloadedMetadata = downloads.value.firstOrNull {
            it.trackUri == mediaId && it.status == DownloadStatus.COMPLETED.name
        }
        tracks.value.firstOrNull { it.uri == mediaId }?.let { local ->
            return if (downloadedMetadata == null) local else local.copy(
                title = downloadedMetadata.title.takeIf(String::isNotBlank) ?: local.title,
                artist = downloadedMetadata.artist.takeIf { it.isNotBlank() && !it.equals("Music", true) && !it.equals("Lyrune", true) }
                    ?: local.artist,
                album = downloadedMetadata.album.takeIf(String::isNotBlank) ?: local.album,
                durationMs = downloadedMetadata.durationMs.takeIf { it > 0 } ?: local.durationMs,
            )
        }
        val item = playbackState.value.currentMediaItem ?: return null
        return Track(
            id = mediaId.hashCode().toLong(),
            uri = mediaId,
            title = downloadedMetadata?.title?.takeIf(String::isNotBlank) ?: item.mediaMetadata.title?.toString().orEmpty(),
            artist = downloadedMetadata?.artist?.takeIf(String::isNotBlank) ?: item.mediaMetadata.artist?.toString().orEmpty(),
            album = downloadedMetadata?.album?.takeIf(String::isNotBlank) ?: item.mediaMetadata.albumTitle?.toString().orEmpty(),
            albumId = null,
            durationMs = downloadedMetadata?.durationMs?.takeIf { it > 0 } ?: playbackState.value.durationMs,
            dateAddedSeconds = 0,
            displayName = item.mediaMetadata.title?.toString().orEmpty(),
            mimeType = null,
            sizeBytes = 0,
            trackNumber = null,
            year = null,
        )
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { dao.createPlaylist(PlaylistEntity(name = name.trim())) }
        }
    }

    fun addToPlaylist(playlistId: Long, trackUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPlaylistTrack(
                PlaylistTrackEntity(playlistId, trackUri, dao.nextPlaylistPosition(playlistId)),
            )
        }
    }

    fun playlistTrackUris(playlistId: Long): Flow<List<String>> = dao.observePlaylistTracks(playlistId)

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch(Dispatchers.IO) { dao.deletePlaylist(playlist) }
    }

    fun playNext(track: Track) = playback.playNext(track)
    fun addToQueue(track: Track) = playback.addToQueue(track)

    fun shuffleAll(tracksList: List<Track>) {
        if (tracksList.isEmpty()) return
        val shuffled = tracksList.shuffled()
        playback.play(shuffled.first(), shuffled)
    }

    fun playAll(tracksList: List<Track>) {
        if (tracksList.isEmpty()) return
        playback.play(tracksList.first(), tracksList)
    }

    fun setMinimumTrackDuration(durationMs: Long) {
        viewModelScope.launch {
            container.settings.setMinimumTrackDuration(durationMs)
            refreshLibrary()
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val totalMs = minutes * 60_000L
        sleepTimerRemainingMs.value = totalMs
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalMs
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1_000
                sleepTimerRemainingMs.value = remaining
            }
            if (playbackState.value.isPlaying) playback.playPause()
            sleepTimerRemainingMs.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerRemainingMs.value = null
    }

    override fun onCleared() {
        visualizerAnalyzer.stop()
        playback.release()
        super.onCleared()
    }
}
