package dev.auralis.studio.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.auralis.studio.R
import dev.auralis.studio.core.model.SyncedLyrics
import dev.auralis.studio.designsystem.AuralisBackground
import dev.auralis.studio.designsystem.AuralisTheme
import dev.auralis.studio.designsystem.LocalAuralisTokens
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.MiniPlayer
import dev.auralis.studio.ui.screens.AudioLabScreen
import dev.auralis.studio.ui.screens.HomeScreen
import dev.auralis.studio.ui.screens.LibraryScreen
import dev.auralis.studio.ui.screens.PlayerScreen
import dev.auralis.studio.ui.screens.SearchScreen
import dev.auralis.studio.ui.screens.SettingsScreen
import dev.auralis.studio.ui.sheets.AudioQuickSheet

private enum class MainDestination { HOME, LIBRARY, SEARCH, AUDIO_LAB, SETTINGS }

@Composable
fun AuralisApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val history by viewModel.historyUris.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val config by viewModel.audioConfig.collectAsStateWithLifecycle()
    val theme by viewModel.themeSettings.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val scanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val folders by viewModel.musicFolders.collectAsStateWithLifecycle()
    val minimumDuration by viewModel.minimumTrackDuration.collectAsStateWithLifecycle()
    val sleepRemaining by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    val libraryError by viewModel.libraryError.collectAsStateWithLifecycle()

    var audioPermissionGranted by remember { mutableStateOf(viewModel.hasAudioPermission) }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var destinationName by rememberSaveable { mutableStateOf(MainDestination.HOME.name) }
    var playerVisible by rememberSaveable { mutableStateOf(false) }
    var quickAudioVisible by rememberSaveable { mutableStateOf(false) }
    val destination = runCatching { MainDestination.valueOf(destinationName) }.getOrDefault(MainDestination.HOME)
    val currentTrack = playback.currentMediaItem?.mediaId?.let { uri -> tracks.firstOrNull { it.uri == uri } }
    val snackbar = remember { SnackbarHostState() }
    val unknownError = stringResource(R.string.unknown_error)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        audioPermissionGranted = granted
        if (granted) viewModel.refreshLibrary()
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.addMusicFolder(it.toString())
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionGranted = granted
    }
    val requestMusicPermission = {
        permissionLauncher.launch(
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE,
        )
    }
    val chooseFolder = { folderLauncher.launch(null) }

    LaunchedEffect(libraryError, playback.error) {
        (libraryError ?: playback.error)?.let { snackbar.showSnackbar(it.ifBlank { unknownError }) }
    }

    AuralisTheme(theme) {
        AuralisBackground(Modifier.fillMaxSize()) {
            if (!audioPermissionGranted && folders.isEmpty() && tracks.isEmpty()) {
                PermissionGate(onGrant = requestMusicPermission, onChooseFolder = chooseFolder)
            } else if (playerVisible) {
                val progressFraction = if (playback.durationMs > 0)
                    playback.positionMs.toFloat() / playback.durationMs.toFloat()
                else 0f

                PlayerScreen(
                    state               = playback,
                    track               = currentTrack,
                    tracks              = tracks,
                    isFavorite          = currentTrack?.uri in favorites,
                    sleepRemainingMs    = sleepRemaining,
                    lyrics              = SyncedLyrics.EMPTY,
                    visualizerBands     = FloatArray(0),
                    visualizerWaveform  = FloatArray(0),
                    onBack              = { playerVisible = false },
                    onPlayPause         = viewModel.playback::playPause,
                    onNext              = viewModel.playback::next,
                    onPrevious          = viewModel.playback::previous,
                    onSeek              = viewModel.playback::seekTo,
                    onToggleFavorite    = { currentTrack?.let { viewModel.toggleFavorite(it.uri) } },
                    onShuffle           = viewModel.playback::toggleShuffle,
                    onRepeat            = viewModel.playback::cycleRepeat,
                    onQueueIndex        = viewModel.playback::playQueueIndex,
                    onRemoveQueue       = viewModel.playback::removeQueueItem,
                    onClearQueue        = viewModel.playback::clearQueue,
                    onOpenAudioLab      = { playerVisible = false; destinationName = MainDestination.AUDIO_LAB.name },
                    onOpenQuickAudio    = { quickAudioVisible = true },
                    onSleepTimer        = viewModel::startSleepTimer,
                    onCancelSleepTimer  = viewModel::cancelSleepTimer,
                )
                // Audio quick sheet shown over the player
                if (quickAudioVisible) {
                    AudioQuickSheet(
                        config        = config,
                        onConfigChange = { viewModel.updateAudio(it) },
                        onOpenAudioLab = { quickAudioVisible = false; playerVisible = false; destinationName = MainDestination.AUDIO_LAB.name },
                        onDismiss      = { quickAudioVisible = false },
                    )
                }
            } else {
                Scaffold(
                    containerColor      = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost        = { SnackbarHost(snackbar) },
                    bottomBar = {
                        val tokens = LocalAuralisTokens.current
                        Column {
                            currentTrack?.let {
                                val progressFraction = if (playback.durationMs > 0)
                                    playback.positionMs.toFloat() / playback.durationMs.toFloat()
                                else 0f
                                MiniPlayer(
                                    track       = it,
                                    isPlaying   = playback.isPlaying,
                                    progress    = progressFraction,
                                    onOpen      = { playerVisible = true },
                                    onPlayPause = viewModel.playback::playPause,
                                    onNext      = viewModel.playback::next,
                                    modifier    = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = tokens.navBarContainerAlpha),
                            ) {
                                MainDestination.entries.forEach { item ->
                                    NavigationBarItem(
                                        selected = destination == item,
                                        onClick  = { destinationName = item.name },
                                        icon     = { DestinationIcon(item) },
                                        label    = { Text(destinationLabel(item)) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    val commonModifier = Modifier.fillMaxSize().padding(padding)
                    when (destination) {
                        MainDestination.HOME -> HomeScreen(
                            tracks, favorites, history, playlists, currentTrack, scanning,
                            viewModel::refreshLibrary, chooseFolder,
                            viewModel.playback::play, viewModel::toggleFavorite,
                            viewModel::playNext, viewModel::addToQueue, viewModel::addToPlaylist,
                            { viewModel.updateAudio(it.config) }, commonModifier,
                        )
                        MainDestination.LIBRARY -> LibraryScreen(
                            tracks, favorites, playlists, viewModel::playlistTrackUris,
                            viewModel::createPlaylist, viewModel::deletePlaylist,
                            viewModel.playback::play, viewModel::toggleFavorite,
                            viewModel::playNext, viewModel::addToQueue, viewModel::addToPlaylist,
                            commonModifier,
                        )
                        MainDestination.SEARCH -> SearchScreen(
                            query, results, favorites, playlists,
                            { viewModel.searchQuery.value = it },
                            viewModel.playback::play, viewModel::toggleFavorite,
                            viewModel::playNext, viewModel::addToQueue, viewModel::addToPlaylist,
                            commonModifier,
                        )
                        MainDestination.AUDIO_LAB -> AudioLabScreen(config, viewModel::updateAudio, commonModifier)
                        MainDestination.SETTINGS -> SettingsScreen(
                            theme, folders, minimumDuration, notificationPermissionGranted,
                            viewModel::updateTheme, viewModel::setMinimumTrackDuration,
                            chooseFolder, viewModel::removeMusicFolder,
                            { if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            commonModifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit, onChooseFolder: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
        )
        Text(
            stringResource(R.string.permission_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
        Button(
            onClick  = onGrant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.grant_music_access))
        }
        FilledTonalButton(
            onClick  = onChooseFolder,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.choose_folder))
        }
    }
}

@Composable
private fun DestinationIcon(destination: MainDestination) {
    when (destination) {
        MainDestination.HOME      -> Icon(Icons.Default.Home,         contentDescription = null)
        MainDestination.SEARCH    -> Icon(Icons.Default.Search,       contentDescription = null)
        MainDestination.SETTINGS  -> Icon(Icons.Default.Settings,     contentDescription = null)
        MainDestination.LIBRARY   -> Icon(Icons.Default.LibraryMusic, contentDescription = null)
        MainDestination.AUDIO_LAB -> Icon(Icons.Default.Tune,         contentDescription = null)
    }
}

@Composable
private fun destinationLabel(destination: MainDestination): String = stringResource(
    when (destination) {
        MainDestination.HOME      -> R.string.home
        MainDestination.LIBRARY   -> R.string.library
        MainDestination.SEARCH    -> R.string.search
        MainDestination.AUDIO_LAB -> R.string.audio_lab
        MainDestination.SETTINGS  -> R.string.settings
    },
)
