package dev.auralis.studio.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import dev.auralis.studio.core.model.Track
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.auralis.studio.core.model.AnimationStyle
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.auralis.studio.R
import dev.auralis.studio.core.model.LyricsUiState
import dev.auralis.studio.designsystem.AuralisBackground
import dev.auralis.studio.designsystem.AuralisTheme
import dev.auralis.studio.designsystem.LocalAuralisTokens
import dev.auralis.studio.designsystem.LocalAuralisControlColors
import dev.auralis.studio.designsystem.rememberArtworkPalette
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.MiniPlayer
import dev.auralis.studio.ui.screens.AudioLabScreen
import dev.auralis.studio.ui.screens.HomeScreen
import dev.auralis.studio.ui.screens.LibraryScreen
import dev.auralis.studio.ui.screens.PlayerScreen
import dev.auralis.studio.ui.screens.SearchScreen
import dev.auralis.studio.ui.screens.SettingsScreen
import dev.auralis.studio.ui.sheets.AudioEffectsSheet
import dev.auralis.studio.ui.sheets.RecommendationsSheet

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
    val onlineResults by viewModel.onlineResults.collectAsStateWithLifecycle()
    val isSearchingOnline by viewModel.isSearchingOnline.collectAsStateWithLifecycle()
    val onlineProviderFailures by viewModel.onlineProviderFailures.collectAsStateWithLifecycle()
    val folders by viewModel.musicFolders.collectAsStateWithLifecycle()
    val minimumDuration by viewModel.minimumTrackDuration.collectAsStateWithLifecycle()
    val sleepRemaining by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    val libraryError by viewModel.libraryError.collectAsStateWithLifecycle()
    val downloadLocation by viewModel.downloadFolderLocation.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val stemState by viewModel.stemState.collectAsStateWithLifecycle()
    val stemGains by viewModel.stemGains.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val lyricsCandidates by viewModel.lyricsCandidates.collectAsStateWithLifecycle()
    val isSearchingLyricsCandidates by viewModel.isSearchingLyricsCandidates.collectAsStateWithLifecycle()
    val youtubeApiKey by viewModel.youtubeApiKey.collectAsStateWithLifecycle()
    val homeLayout by viewModel.homeLayoutSettings.collectAsStateWithLifecycle()
    val offlineRecommendations by viewModel.offlineRecommendations.collectAsStateWithLifecycle()


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
    var recommendationsVisible by rememberSaveable { mutableStateOf(false) }
    var welcomeDismissed by rememberSaveable { mutableStateOf(false) }
    val destination = runCatching { MainDestination.valueOf(destinationName) }.getOrDefault(MainDestination.HOME)
    val currentOnlineTrack by viewModel.currentPlayingOnlineTrack.collectAsStateWithLifecycle()
    val currentMediaMetadata = playback.currentMediaItem?.mediaMetadata
    val currentTrack = playback.currentMediaItem?.mediaId?.let { uri ->
        val found = tracks.firstOrNull { it.uri == uri }
            ?: (if (currentOnlineTrack?.uri == uri) currentOnlineTrack else null)
            ?: viewModel.generatedTrack(uri)
        if (found != null && found.artworkUri == null && currentMediaMetadata?.artworkUri != null) {
            found.copy(customArtworkUri = currentMediaMetadata.artworkUri.toString())
        } else {
            found ?: currentMediaMetadata?.let { meta ->
                Track(
                    id = -1L,
                    uri = uri,
                    title = meta.title?.toString() ?: currentOnlineTrack?.title.orEmpty(),
                    artist = meta.artist?.toString() ?: currentOnlineTrack?.artist.orEmpty(),
                    album = meta.albumTitle?.toString() ?: currentOnlineTrack?.album.orEmpty(),
                    customArtworkUri = meta.artworkUri?.toString() ?: currentOnlineTrack?.customArtworkUri,
                )
            }
        }
    }
    val artworkPalette = rememberArtworkPalette(currentTrack?.artworkUri)
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
    val downloadFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val folderName = it.path?.substringAfterLast(':')?.ifBlank { null } ?: it.lastPathSegment ?: it.toString()
            viewModel.setDownloadLocation(it, folderName)
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionGranted = granted
    }
    val lyricsImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() } }
                .getOrNull()?.takeIf(String::isNotBlank)?.let(viewModel::importLyrics)
        }
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

    LaunchedEffect(playerVisible, theme.visualQuality) {
        viewModel.setVisualizerActive(playerVisible)
    }

    AuralisTheme(theme, artworkPalette) {
        AuralisBackground(Modifier.fillMaxSize()) {
            if (!audioPermissionGranted && folders.isEmpty() && tracks.isEmpty() && !welcomeDismissed) {
                FirstLaunchWelcomeDialog(
                    onGrant = {
                        requestMusicPermission()
                        if (Build.VERSION.SDK_INT >= 33 && !notificationPermissionGranted) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onChooseFolder = chooseFolder,
                    onDismiss = { welcomeDismissed = true },
                )
            }

            Box(Modifier.fillMaxSize()) {
                BackHandler(enabled = destination == MainDestination.SETTINGS) {
                    destinationName = MainDestination.HOME.name
                }
                BackHandler(enabled = destination == MainDestination.SEARCH) {
                    destinationName = MainDestination.HOME.name
                }
                BackHandler(enabled = destination == MainDestination.LIBRARY) {
                    destinationName = MainDestination.HOME.name
                }
                BackHandler(enabled = destination == MainDestination.AUDIO_LAB) {
                    destinationName = MainDestination.HOME.name
                }

                Scaffold(
                    containerColor      = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost        = { SnackbarHost(snackbar) },
                    bottomBar = {
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
                                onPrevious  = viewModel.playback::previous,
                                modifier    = Modifier
                                    .navigationBarsPadding()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    },
                ) { padding ->
                    val commonModifier = Modifier.fillMaxSize().padding(padding).graphicsLayer { clip = true }
                    AnimatedContent(
                        targetState = destination,
                        transitionSpec = {
                            when (theme.animationStyle) {
                                AnimationStyle.SMOOTH -> {
                                    (fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = tween(300, easing = FastOutSlowInEasing)))
                                        .togetherWith(
                                            fadeOut(animationSpec = tween(180)) +
                                                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = tween(180, easing = FastOutSlowInEasing))
                                        )
                                }
                                AnimationStyle.SPRING -> {
                                    (fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) +
                                            scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)))
                                        .togetherWith(
                                            fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 1.04f)
                                        )
                                }
                                AnimationStyle.FADE_ZOOM -> {
                                    (fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                                            scaleIn(initialScale = 0.88f, animationSpec = tween(260, easing = FastOutSlowInEasing)))
                                        .togetherWith(
                                            fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 1.05f)
                                        )
                                }
                                AnimationStyle.FAST -> {
                                    (fadeIn(animationSpec = tween(140)) +
                                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(150)))
                                        .togetherWith(
                                            fadeOut(animationSpec = tween(110))
                                        )
                                }
                                AnimationStyle.EXPRESSIVE -> {
                                    (fadeIn(animationSpec = tween(340)) +
                                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow)))
                                        .togetherWith(
                                            fadeOut(animationSpec = tween(200))
                                        )
                                }
                                AnimationStyle.REDUCED -> {
                                    fadeIn(animationSpec = tween(90)).togetherWith(fadeOut(animationSpec = tween(90)))
                                }
                            }
                        },
                        label = "screen_destination",
                    ) { targetDestination ->
                        when (targetDestination) {
                            MainDestination.HOME -> HomeScreen(
                                tracks = tracks,
                                favorites = favorites,
                                historyUris = history,
                                playlists = playlists,
                                currentTrack = currentTrack,
                                isScanning = scanning,
                                onRefresh = viewModel::refreshLibrary,
                                onChooseFolder = chooseFolder,
                                onPlay = viewModel.playback::play,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::addToQueue,
                                onAddToPlaylist = viewModel::addToPlaylist,
                                onPreset = { viewModel.updateAudio(it.config) },
                                modifier = commonModifier,
                                folders = folders,
                                onCreatePlaylist = viewModel::createPlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onRemovePlaylistTrack = viewModel::removePlaylistTrack,
                                playlistTrackUris = viewModel::playlistTrackUris,
                                onNavigateToSearch = { destinationName = MainDestination.SEARCH.name },
                                onNavigateToSettings = { destinationName = MainDestination.SETTINGS.name },
                                onNavigateToLibrary = { destinationName = MainDestination.LIBRARY.name },
                                layoutSettings = homeLayout,
                                onUpdateLayoutSettings = viewModel::updateHomeLayout,
                                recommendations = offlineRecommendations,
                            )
                            MainDestination.LIBRARY -> LibraryScreen(
                                tracks = tracks,
                                favorites = favorites,
                                playlists = playlists,
                                playlistTrackUris = viewModel::playlistTrackUris,
                                onCreatePlaylist = viewModel::createPlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onPlay = viewModel.playback::play,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::addToQueue,
                                onAddToPlaylist = viewModel::addToPlaylist,
                                onBack = { destinationName = MainDestination.HOME.name },
                                modifier = commonModifier,
                            )
                            MainDestination.SEARCH -> SearchScreen(
                                query                 = query,
                                results               = results,
                                favorites             = favorites,
                                playlists             = playlists,
                                onlineResults         = onlineResults,
                                isSearchingOnline     = isSearchingOnline,
                                providerFailures      = onlineProviderFailures,
                                recentSearches        = recentSearches,
                                downloads             = downloads,
                                onQueryChange         = { viewModel.searchQuery.value = it },
                                onSearchOnline        = viewModel::searchOnline,
                                onCommitSearch        = viewModel::commitSearch,
                                onClearRecentSearches = viewModel::clearRecentSearches,
                                onRemoveRecentSearch  = viewModel::removeRecentSearch,
                                onPlay                = viewModel.playback::play,
                                onPlayOnline          = viewModel::playOnlineTrack,
                                onDownloadOnline      = viewModel::downloadOnlineTrack,
                                onToggleFavorite      = viewModel::toggleFavorite,
                                onPlayNext            = viewModel::playNext,
                                onAddToQueue          = viewModel::addToQueue,
                                onAddToPlaylist       = viewModel::addToPlaylist,
                                mediaResolver         = viewModel.mediaResolver,
                                onEnqueueResolverDownload = viewModel::enqueueResolverDownload,
                                youtubeApiKey         = youtubeApiKey,
                                onSaveYoutubeApiKey   = viewModel::setYoutubeApiKey,
                                onBack                = { destinationName = MainDestination.HOME.name },
                                modifier              = commonModifier,
                            )
                            MainDestination.SETTINGS -> SettingsScreen(
                                theme = theme,
                                folders = folders,
                                minimumDurationMs = minimumDuration,
                                notificationPermissionGranted = notificationPermissionGranted,
                                audioPermissionGranted = audioPermissionGranted,
                                downloadLocation = downloadLocation,
                                downloadQuality = downloadQuality,
                                onThemeChange = viewModel::updateTheme,
                                onMinimumDuration = viewModel::setMinimumTrackDuration,
                                onChooseFolder = chooseFolder,
                                onRemoveFolder = viewModel::removeMusicFolder,
                                onRequestNotifications = { if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                onRequestAudioPermission = requestMusicPermission,
                                onNavigateHome = { destinationName = MainDestination.HOME.name },
                                onChooseDownloadFolder = { downloadFolderLauncher.launch(null) },
                                onResetDownloadFolder = viewModel::resetDownloadLocation,
                                onDownloadQualityChange = viewModel::setDownloadQuality,
                                brokenDownloads = viewModel.brokenDownloads.collectAsStateWithLifecycle().value,
                                isScanningBrokenDownloads = viewModel.isScanningBrokenDownloads.collectAsStateWithLifecycle().value,
                                onScanBrokenDownloads = viewModel::scanForBrokenDownloads,
                                onRemoveBrokenDownload = viewModel::removeBrokenDownload,
                                onKeepSuspiciousFile = viewModel::keepSuspiciousFile,
                                onRepairDownloadMetadata = viewModel::repairPlaceholderMetadata,
                                metadataRepairMessage = viewModel.metadataRepairMessage.collectAsStateWithLifecycle().value,
                                homeLayout = homeLayout,
                                onHomeLayoutChange = viewModel::updateHomeLayout,
                                modifier = commonModifier,
                            )
                            MainDestination.AUDIO_LAB -> AudioLabScreen(
                                config = config,
                                onChange = viewModel::updateAudio,
                                stemState = stemState,
                                stemGains = stemGains,
                                currentTrack = currentTrack,
                                onDownloadStemModel = viewModel::downloadStemModel,
                                onDeleteStemModel = viewModel::deleteStemModel,
                                onPrepareStems = viewModel::prepareStems,
                                onCancelStemWork = viewModel::cancelStemWork,
                                onPlayStem = viewModel::playStem,
                                onVocalsGainChange = viewModel::setVocalsGain,
                                onInstrumentalGainChange = viewModel::setInstrumentalGain,
                                onBassGainChange = viewModel::setBassGain,
                                onPlayStemMix = viewModel::playStemMix,
                                onBack = { destinationName = MainDestination.HOME.name },
                                modifier = commonModifier,
                            )
                        }
                    }
                }

                // Fullscreen Player with slide up/down animation
                AnimatedVisibility(
                    visible = playerVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = when (theme.animationStyle) {
                            AnimationStyle.SPRING -> spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                            AnimationStyle.FAST -> tween(200, easing = FastOutLinearInEasing)
                            AnimationStyle.REDUCED -> tween(100)
                            else -> tween(340, easing = FastOutSlowInEasing)
                        }
                    ) + fadeIn(animationSpec = tween(240)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = when (theme.animationStyle) {
                            AnimationStyle.SPRING -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            AnimationStyle.FAST -> tween(180, easing = FastOutLinearInEasing)
                            AnimationStyle.REDUCED -> tween(100)
                            else -> tween(300, easing = FastOutSlowInEasing)
                        }
                    ) + fadeOut(animationSpec = tween(200)),
                ) {
                    BackHandler { playerVisible = false }

                    val visualizerBands by viewModel.visualizerBands.collectAsStateWithLifecycle()
                    val visualizerWaveform by viewModel.visualizerWaveform.collectAsStateWithLifecycle()

                    PlayerScreen(
                        state               = playback,
                        track               = currentTrack,
                        tracks              = tracks,
                        isFavorite          = currentTrack?.uri in favorites,
                        sleepRemainingMs    = sleepRemaining,
                        lyricsState         = lyricsState,
                        visualizerBands     = visualizerBands,
                        visualizerWaveform  = visualizerWaveform,
                        onBack              = { playerVisible = false },
                        onPlayPause         = viewModel.playback::playPause,
                        onNext              = viewModel.playback::next,
                        onPrevious          = viewModel.playback::previous,
                        onSeek              = viewModel::seekTo,
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
                        onRetryLyrics       = viewModel::retryLyrics,
                        onImportLyrics      = { lyricsImportLauncher.launch("*/*") },
                        onManualLyricsSearch = viewModel::searchLyricsManually,
                        onLyricsOffsetChange = viewModel::updateLyricsOffset,
                        lyricsClock         = viewModel.lyricsClock,
                        lyricsCandidates    = lyricsCandidates,
                        isSearchingLyricsCandidates = isSearchingLyricsCandidates,
                        onLoadLyricsCandidates = viewModel::loadLyricsCandidates,
                        onSelectLyricsCandidate = viewModel::selectLyricsCandidate,
                        onOpenRecommendations = { recommendationsVisible = true },
                    )
                    // Audio effects sheet shown over the player
                    if (quickAudioVisible) {
                        AudioEffectsSheet(
                            config         = config,
                            onConfigChange = { viewModel.updateAudio(it) },
                            onDismiss      = { quickAudioVisible = false },
                        )
                    }
                    // Recommendations sheet shown over the player
                    if (recommendationsVisible) {
                        RecommendationsSheet(
                            currentTrack = currentTrack,
                            offlineRecommendations = offlineRecommendations,
                            onlineRecommendations = viewModel.onlineRecommendations.collectAsStateWithLifecycle().value,
                            isSearchingOnline = viewModel.isSearchingRecommendations.collectAsStateWithLifecycle().value,
                            downloads = downloads,
                            onPlayOffline = { track, list ->
                                viewModel.playback.play(track, list)
                                recommendationsVisible = false
                            },
                            onPlayOnline = { onlineTrack ->
                                viewModel.playOnlineTrack(onlineTrack)
                                recommendationsVisible = false
                            },
                            onDownloadOnline = { onlineTrack ->
                                viewModel.downloadOnlineTrack(onlineTrack)
                            },
                            onAddToQueue = viewModel::addToQueue,
                            onPlayNext = viewModel::playNext,
                            onDismiss = { recommendationsVisible = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstLaunchWelcomeDialog(
    onGrant: () -> Unit,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.welcome_to_lyrune),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.first_launch_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface.copy(alpha = 0.8f),
                )
                Text(
                    stringResource(R.string.permission_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onGrant()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.grant_music_access))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.TextButton(onClick = onChooseFolder) {
                    Text(stringResource(R.string.choose_folder))
                }
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.continue_to_app))
                }
            }
        },
    )
}
