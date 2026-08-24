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
import dev.auralis.studio.online.ArchiveOrgMusicProvider
import dev.auralis.studio.online.FreeMusicDiscoveryProvider
import dev.auralis.studio.online.OnlineTrack
import dev.auralis.studio.online.ProviderResult
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
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

@OptIn(FlowPreview::class)
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
    private val stemModelManager = StemModelManager(File(application.filesDir, "stem_models"))
    private val stemEngine = VocFtLiteRtEngine(stemModelManager)
    private val _stemState = MutableStateFlow<StemUiState>(StemUiState.CheckingModel)
    val stemState: StateFlow<StemUiState> = _stemState.asStateFlow()
    private var stemJob: Job? = null
    private val generatedStemTracks = ConcurrentHashMap<String, Track>()
    private val generatedStemSources = ConcurrentHashMap<String, Track>()
    private var currentLyricsTrack: Track? = null
    val tracks: StateFlow<List<Track>> = container.library.tracks
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

    private val onlineProvider = FreeMusicDiscoveryProvider()
    private val _onlineResults = MutableStateFlow<List<OnlineTrack>>(emptyList())
    val onlineResults: StateFlow<List<OnlineTrack>> = _onlineResults.asStateFlow()
    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()
    val downloads: StateFlow<List<DownloadEntity>> = downloadsDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchResults: StateFlow<List<Track>> = combine(
        searchQuery.debounce(180),
        tracks,
    ) { query, allTracks ->
        val normalized = query.trim()
        if (normalized.isEmpty()) emptyList() else allTracks.filter {
            it.title.contains(normalized, ignoreCase = true) ||
                it.artist.contains(normalized, ignoreCase = true) ||
                it.album.contains(normalized, ignoreCase = true)
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            audioConfig.value = container.settings.audioConfig.first()
            playback.applyAudioConfig(audioConfig.value)
            themeSettings.value = container.settings.themeSettings.first()
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
        refreshLibrary()
        viewModelScope.launch { refreshStemModelState() }
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
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _onlineResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearchingOnline.value = true
            when (val res = onlineProvider.search(trimmed)) {
                is ProviderResult.Success<*> -> {
                    _onlineResults.value = (res.data as? List<*>)?.filterIsInstance<OnlineTrack>() ?: emptyList()
                }
                else -> _onlineResults.value = emptyList()
            }
            _isSearchingOnline.value = false
        }
    }

    fun playOnlineTrack(onlineTrack: OnlineTrack) {
        viewModelScope.launch {
            // Resolve direct audio stream if needed
            val trackToPlay = if (onlineTrack.durationMs <= 0) {
                when (val detailed = onlineProvider.getTrack(onlineTrack.id)) {
                    is ProviderResult.Success<*> -> (detailed.data as? OnlineTrack)?.toStreamableTrack() ?: onlineTrack.toStreamableTrack()
                    else -> onlineTrack.toStreamableTrack()
                }
            } else {
                onlineTrack.toStreamableTrack()
            }
            trackToPlay?.let { playback.play(it, listOf(it)) }
        }
    }

    fun downloadOnlineTrack(onlineTrack: OnlineTrack) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val resolved = if (onlineTrack.streamUrl?.contains(".mp3") == true) onlineTrack else {
                when (val d = onlineProvider.getTrack(onlineTrack.id)) {
                    is ProviderResult.Success<*> -> (d.data as? OnlineTrack) ?: onlineTrack
                    else -> onlineTrack
                }
            }

            val downloadUrl = resolved.downloadUrl ?: resolved.streamUrl ?: return@launch
            val safeTitle = resolved.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(50)
            val fileName = "$safeTitle.mp3"

            val downloadRecord = DownloadEntity(
                provider         = resolved.provider,
                providerTrackId  = resolved.id,
                remoteUrl        = downloadUrl,
                fileName         = fileName,
                title            = resolved.title,
                artist           = resolved.artist,
                album            = resolved.album,
                artworkUrl       = resolved.artworkUrl,
                durationMs       = resolved.durationMs,
                status           = DownloadStatus.DOWNLOADING.name,
            )
            val rowId = downloadsDao.insert(downloadRecord)

            val customUriStr = downloadFolderUri.value
            if (customUriStr != null) {
                try {
                    val treeUri = Uri.parse(customUriStr)
                    val docTree = androidx.documentfile.provider.DocumentFile.fromTreeUri(app, treeUri)
                    val newFile = docTree?.createFile("audio/mpeg", fileName)
                    if (newFile != null) {
                        val inputUrl = java.net.URL(downloadUrl)
                        val conn = inputUrl.openConnection()
                        conn.connect()
                        val input = conn.getInputStream()
                        val output = app.contentResolver.openOutputStream(newFile.uri)
                        if (output != null) {
                            input.use { i -> output.use { o -> i.copyTo(o) } }
                            downloadsDao.updateStatus(rowId, DownloadStatus.COMPLETED.name, "")
                            launch(Dispatchers.Main) {
                                Toast.makeText(app, app.getString(R.string.download_complete_location, downloadFolderLocation.value), Toast.LENGTH_LONG).show()
                            }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Custom download failed, falling back to standard DownloadManager", e)
                }
            }

            try {
                if (appContext.filesDir.usableSpace < VocFtModel.descriptor.modelBytes * 2L) {
                    _stemState.value = StemUiState.Error(StemErrorCode.NOT_ENOUGH_STORAGE, false)
                    return@launch
                }
                val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val uri = Uri.parse(downloadUrl)
                val request = DownloadManager.Request(uri).apply {
                    setTitle(resolved.title)
                    setDescription(app.getString(R.string.downloading_via_lyrune))
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Lyrune/$fileName")
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }
                dm.enqueue(request)

                launch(Dispatchers.Main) {
                    Toast.makeText(app, app.getString(R.string.downloading_location, downloadFolderLocation.value), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                downloadsDao.updateStatus(rowId, DownloadStatus.FAILED.name, e.localizedMessage ?: app.getString(R.string.download_failed))
            }
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

    fun searchLyricsManually(title: String) {
        val track = currentLyricsTrack ?: return
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        viewModelScope.launch {
            resolveLyrics(track.copy(title = cleanTitle, artist = ""), forceRefresh = true)
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
        tracks.value.firstOrNull { it.uri == mediaId }?.let { return it }
        val item = playbackState.value.currentMediaItem ?: return null
        return Track(
            id = mediaId.hashCode().toLong(),
            uri = mediaId,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            artist = item.mediaMetadata.artist?.toString().orEmpty(),
            album = item.mediaMetadata.albumTitle?.toString().orEmpty(),
            albumId = null,
            durationMs = playbackState.value.durationMs,
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
