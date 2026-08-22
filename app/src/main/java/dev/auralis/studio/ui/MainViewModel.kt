package dev.auralis.studio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.auralis.studio.AuralisApplication
import dev.auralis.studio.core.database.PlaylistEntity
import dev.auralis.studio.core.database.PlaylistTrackEntity
import dev.auralis.studio.core.model.AudioConfig
import dev.auralis.studio.core.model.ThemeSettings
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.playback.PlaybackConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AuralisApplication).container
    private val dao = container.database.dao()
    val playback = PlaybackConnection(application, viewModelScope)
    val playbackState = playback.state
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
        refreshLibrary()
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

    fun updateTheme(settings: ThemeSettings) {
        themeSettings.value = settings
        themeSaveJob?.cancel()
        themeSaveJob = viewModelScope.launch {
            delay(120)
            container.settings.setTheme(settings)
        }
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
        playback.release()
        super.onCleared()
    }
}
