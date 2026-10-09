package dev.auralis.studio.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Favorite
import dev.auralis.studio.ui.components.bounceClick
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import dev.auralis.studio.R
import dev.auralis.studio.core.model.SyncedLyrics
import dev.auralis.studio.core.model.LyricsUiState
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.designsystem.LocalAuralisTokens
import dev.auralis.studio.designsystem.AuralisSlider
import dev.auralis.studio.designsystem.AuralisFilterChip
import dev.auralis.studio.designsystem.isColorDark
import dev.auralis.studio.designsystem.LocalThemeSettings
import dev.auralis.studio.designsystem.LocalLyruneSemanticColors
import dev.auralis.studio.playback.PlaybackUiState
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.PagerDotIndicator
import dev.auralis.studio.ui.components.TrackArtwork
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlin.math.exp

/** 3-page Now Playing experience: Lyrics (0) · Player (1) · Visualizer (2) */
private const val PAGE_LYRICS    = 0
private const val PAGE_PLAYER    = 1
private const val PAGE_VISUALIZER = 2
private const val PAGE_COUNT     = 3

private fun Modifier.swipeUpToTrigger(onTrigger: () -> Unit): Modifier = this.pointerInput(Unit) {
    var accumulatedDrag = 0f
    detectVerticalDragGestures(
        onDragStart = { accumulatedDrag = 0f },
        onDragEnd = {
            if (accumulatedDrag < -35f) {
                onTrigger()
            }
            accumulatedDrag = 0f
        },
        onDragCancel = { accumulatedDrag = 0f },
        onVerticalDrag = { change, dragAmount ->
            accumulatedDrag += dragAmount
            if (accumulatedDrag < -55f) {
                change.consume()
                onTrigger()
                accumulatedDrag = 0f
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlaybackUiState,
    track: Track?,
    tracks: List<Track>,
    isFavorite: Boolean,
    sleepRemainingMs: Long?,
    lyricsState: LyricsUiState,
    visualizerBands: FloatArray,
    visualizerWaveform: FloatArray,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onQueueIndex: (Int) -> Unit,
    onRemoveQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onOpenAudioLab: () -> Unit,
    onOpenQuickAudio: () -> Unit,
    onSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onRetryLyrics: () -> Unit,
    onImportLyrics: () -> Unit,
    onManualLyricsSearch: (String) -> Unit,
    onLyricsOffsetChange: (Long) -> Unit,
    lyricsClock: dev.auralis.studio.lyrics.LyricsClock? = null,
    lyricsCandidates: List<dev.auralis.studio.lyrics.LrclibTrack> = emptyList(),
    isSearchingLyricsCandidates: Boolean = false,
    onLoadLyricsCandidates: () -> Unit = {},
    onSelectLyricsCandidate: (dev.auralis.studio.lyrics.LrclibTrack) -> Unit = {},
    onOpenRecommendations: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val tokens = LocalAuralisTokens.current
    val colors = MaterialTheme.colorScheme
    val scope  = rememberCoroutineScope()
    val artworkScrim = if (isColorDark(colors.background)) Color.Black else Color.White
    val themeSettings = LocalThemeSettings.current

    val pagerState = rememberPagerState(initialPage = PAGE_PLAYER) { PAGE_COUNT }
    val isLtr = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Ltr

    var queueVisible by remember { mutableStateOf(false) }
    var pointA by remember(state.currentMediaItem?.mediaId) { mutableLongStateOf(-1L) }
    var pointB by remember(state.currentMediaItem?.mediaId) { mutableLongStateOf(-1L) }

    // A-B repeat enforcement
    LaunchedEffect(state.positionMs, pointA, pointB) {
        if (pointA >= 0 && pointB > pointA && state.positionMs >= pointB) onSeek(pointA)
    }

    Box(modifier.fillMaxSize().background(colors.background)) {
        // Dynamic, luminous ambient background (Never flat black)
        val rawArtUri = track?.artworkUri
        val bgModel: Any? = remember(rawArtUri) {
            if (rawArtUri.isNullOrBlank()) null
            else if (rawArtUri.startsWith("/") && !rawArtUri.startsWith("content://") && !rawArtUri.startsWith("file://")) {
                java.io.File(rawArtUri)
            } else {
                rawArtUri
            }
        }

        if (bgModel != null) {
            // 1. Softly blurred, scaled ambient artwork
            AsyncImage(
                model = bgModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.25f)
                    .blur(48.dp),
            )
            // 2. Luminous color wash & gentle vignette (keeps colors alive and text readable)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.22f),
                                Color.Black.copy(alpha = 0.42f),
                                Color.Black.copy(alpha = 0.68f),
                            ),
                        ),
                    ),
            )
        } else {
            // 3. Ambient dynamic glowing gradient when artwork is loading or absent
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.primary.copy(alpha = 0.35f),
                                colors.secondary.copy(alpha = 0.20f),
                                colors.surfaceVariant.copy(alpha = 0.15f),
                                colors.background,
                            ),
                            radius = 1200f,
                        ),
                    ),
            )
        }

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // ── Top app bar ──────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.close),
                        tint = colors.onBackground,
                    )
                }
                // Page label
                Text(
                    text = when (if (isLtr) pagerState.currentPage else PAGE_COUNT - 1 - pagerState.currentPage) {
                        PAGE_LYRICS     -> stringResource(R.string.lyrics)
                        PAGE_VISUALIZER -> stringResource(R.string.visualizer)
                        else            -> stringResource(R.string.now_playing)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onBackground,
                )
                IconButton(onClick = onToggleFavorite, enabled = track != null) {
                    AnimatedContent(
                        targetState = isFavorite,
                        transitionSpec = {
                            (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith fadeOut()
                        },
                        label = "fav",
                    ) { fav ->
                        Icon(
                            if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(if (fav) R.string.remove_favorite else R.string.favorite),
                            tint = if (fav) colors.primary else colors.onBackground,
                        )
                    }
                }
            }

            // ── 3-page pager ─────────────────────────────────────────────────
            HorizontalPager(
                state   = pagerState,
                modifier = Modifier.weight(1f),
                reverseLayout = !isLtr,
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                ),
            ) { rawPage ->
                val page = if (isLtr) rawPage else PAGE_COUNT - 1 - rawPage
                when (page) {
                    PAGE_LYRICS     -> LyricsPage(
                        state                  = lyricsState,
                        positionMs             = state.positionMs,
                        lyricsClock            = lyricsClock,
                        lyricsCandidates       = lyricsCandidates,
                        isSearchingCandidates  = isSearchingLyricsCandidates,
                        onSeek                 = onSeek,
                        onRetry                = onRetryLyrics,
                        onImport               = onImportLyrics,
                        onManualSearch         = onManualLyricsSearch,
                        onOffsetChange         = onLyricsOffsetChange,
                        onLoadCandidates       = onLoadLyricsCandidates,
                        onSelectCandidate      = onSelectLyricsCandidate,
                    )
                    PAGE_PLAYER     -> PlayerPage(
                        state               = state,
                        track               = track,
                        tracks              = tracks,
                        queueVisible        = queueVisible,
                        pointA              = pointA,
                        pointB              = pointB,
                        sleepRemainingMs    = sleepRemainingMs,
                        onPlayPause         = onPlayPause,
                        onNext              = onNext,
                        onPrevious          = onPrevious,
                        onSeek              = onSeek,
                        onShuffle           = onShuffle,
                        onRepeat            = onRepeat,
                        onQueueIndex        = onQueueIndex,
                        onRemoveQueue       = onRemoveQueue,
                        onClearQueue        = onClearQueue,
                        onOpenAudioLab      = onOpenAudioLab,
                        onOpenQuickAudio    = onOpenQuickAudio,
                        onSleepTimer        = onSleepTimer,
                        onCancelSleepTimer  = onCancelSleepTimer,
                        onQueueToggle       = { queueVisible = !queueVisible },
                        onSetPointA         = { pointA = state.positionMs },
                        onSetPointB         = { if (pointA >= 0 && state.positionMs > pointA) pointB = state.positionMs },
                        onClearAB           = { pointA = -1; pointB = -1 },
                        onOpenRecommendations = onOpenRecommendations,
                    )
                    PAGE_VISUALIZER -> VisualizerPage(
                        bands     = visualizerBands,
                        waveform  = visualizerWaveform,
                        isPlaying = state.isPlaying,
                        track     = track,
                        lyricsState = lyricsState,
                        positionMs = state.positionMs,
                    )
                }
            }

            // ── Explicit view selector: no ambiguous dots/icons ──────────────
            val logicalPage = if (isLtr) pagerState.currentPage else PAGE_COUNT - 1 - pagerState.currentPage
            Row(
                Modifier
                    .fillMaxWidth()
                    .swipeUpToTrigger { onOpenRecommendations() }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                listOf(
                    PAGE_LYRICS to stringResource(R.string.lyrics),
                    PAGE_PLAYER to stringResource(R.string.now_playing),
                    PAGE_VISUALIZER to stringResource(R.string.spectrum),
                ).forEach { (page, label) ->
                    AuralisFilterChip(
                        selected = logicalPage == page,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(if (isLtr) page else PAGE_COUNT - 1 - page)
                            }
                        },
                        label = label,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            // Pull-down / swipe recommendations grabber bar (supports tap AND swipe up gesture)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .swipeUpToTrigger { onOpenRecommendations() }
                    .clickable { onOpenRecommendations() }
                    .padding(bottom = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.smart_recommendations),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Player Page (center)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PlayerPage(
    state: PlaybackUiState,
    track: Track?,
    tracks: List<Track>,
    queueVisible: Boolean,
    pointA: Long,
    pointB: Long,
    sleepRemainingMs: Long?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onQueueIndex: (Int) -> Unit,
    onRemoveQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onOpenAudioLab: () -> Unit,
    onOpenQuickAudio: () -> Unit,
    onSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onQueueToggle: () -> Unit,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onClearAB: () -> Unit,
    onOpenRecommendations: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val tokens = LocalAuralisTokens.current

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Artwork — large, 82% width
        item {
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        (fadeIn(tween(260)) + scaleIn(initialScale = 0.94f, animationSpec = tween(260)))
                            .togetherWith(fadeOut(tween(180)))
                    },
                    label = "player_art_change",
                ) { currentTrack ->
                    TrackArtwork(
                        currentTrack,
                        Modifier
                            .fillMaxWidth(0.82f)
                            .aspectRatio(1f),
                    )
                }
            }
        }

        // Track info
        item {
            AnimatedContent(
                targetState = track,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(animationSpec = tween(240)) { it / 4 })
                        .togetherWith(fadeOut(tween(160)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = "player_title_change",
            ) { currentTrack ->
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        currentTrack?.title ?: stringResource(R.string.nothing_playing),
                        style    = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color    = colors.onBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(
                            currentTrack?.artist?.ifBlank { null },
                            currentTrack?.album?.ifBlank { null },
                        ).joinToString(" · "),
                        style   = MaterialTheme.typography.bodyLarge,
                        color   = colors.onBackground.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Progress slider
        item {
            Column {
                AuralisSlider(
                    value         = state.positionMs.coerceAtMost(state.durationMs).toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange    = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
                    enabled       = state.durationMs > 0,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        DateUtils.formatElapsedTime(state.positionMs / 1_000),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.onBackground,
                    )
                    Text(
                        "−" + DateUtils.formatElapsedTime((state.durationMs - state.positionMs).coerceAtLeast(0) / 1_000),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.onBackground.copy(alpha = 0.75f),
                    )
                }
            }
        }

        // Main controls row: Shuffle | Previous | Play/Pause | Next | Repeat
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // Shuffle
                IconButton(onClick = onShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        tint = if (state.shuffleEnabled) colors.primary else colors.onBackground.copy(alpha = 0.45f),
                        modifier = Modifier.size(24.dp),
                    )
                }
                // Previous
                IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.previous),
                        tint   = colors.onBackground,
                        modifier = Modifier.size(36.dp),
                    )
                }
                // Play/Pause — large primary button
                FilledIconButton(
                    onClick  = onPlayPause,
                    modifier = Modifier.size(68.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.primary,
                        contentColor   = colors.onPrimary,
                    ),
                ) {
                    AnimatedContent(
                        targetState = state.isPlaying,
                        transitionSpec = {
                            (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh)) + fadeIn()) togetherWith fadeOut()
                        },
                        label = "play_pause",
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                // Next
                IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.next),
                        tint   = colors.onBackground,
                        modifier = Modifier.size(36.dp),
                    )
                }
                // Repeat — cycles Off → Repeat All → Repeat One → Off
                IconButton(onClick = onRepeat) {
                    val repeatIcon = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    val repeatTint = when (state.repeatMode) {
                        Player.REPEAT_MODE_OFF -> colors.onBackground.copy(alpha = 0.45f)
                        else -> colors.primary  // Both ALL and ONE are active (highlighted)
                    }
                    Icon(
                        repeatIcon,
                        contentDescription = when (state.repeatMode) {
                            Player.REPEAT_MODE_OFF -> stringResource(R.string.repeat_off)
                            Player.REPEAT_MODE_ALL -> stringResource(R.string.repeat_all)
                            Player.REPEAT_MODE_ONE -> stringResource(R.string.repeat_one)
                            else -> null
                        },
                        tint = repeatTint,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // Secondary actions: Audio Effects | Queue | Smart Recommendations
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Audio effects button
                GlassCard(
                    modifier = Modifier.weight(1f).bounceClick(),
                    onClick  = onOpenQuickAudio,
                ) {
                    Row(
                        Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint     = colors.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.audio_effects),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                        )
                    }
                }
                // Queue toggle
                GlassCard(
                    modifier = Modifier.weight(1f).bounceClick(),
                    onClick  = onQueueToggle,
                ) {
                    Row(
                        Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint     = if (queueVisible) colors.primary else colors.onSurface.copy(0.7f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.queue),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                        )
                    }
                }
                // Recommendations 3rd Button
                GlassCard(
                    modifier = Modifier.weight(1f).bounceClick(),
                    onClick  = onOpenRecommendations,
                ) {
                    Row(
                        Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint     = colors.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.smart_recommendations),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // A-B repeat (collapsed by default inside queue card)
        if (pointA >= 0 || pointB > 0 || queueVisible) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ab_repeat), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = onSetPointA) { Text(stringResource(R.string.set_a)) }
                            TextButton(onClick = onSetPointB, enabled = pointA >= 0) { Text(stringResource(R.string.set_b)) }
                            TextButton(onClick = onClearAB, enabled = pointA >= 0) { Text(stringResource(R.string.clear)) }
                        }
                        if (pointA >= 0 && pointB > pointA) {
                            Text(
                                stringResource(R.string.loop_active, DateUtils.formatElapsedTime(pointA / 1000), DateUtils.formatElapsedTime(pointB / 1000)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.primary,
                            )
                        }
                    }
                }
            }
        }

        // Sleep timer
        if (sleepRemainingMs != null || queueVisible) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sleep_timer), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                        if (sleepRemainingMs == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(15, 30, 45, 60).forEach { minutes ->
                                    TextButton(onClick = { onSleepTimer(minutes) }) {
                                        Text(stringResource(R.string.minutes_value, minutes), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.timer_remaining, DateUtils.formatElapsedTime(sleepRemainingMs / 1_000)), color = colors.primary)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = onCancelSleepTimer) { Text(stringResource(R.string.stop_timer)) }
                            }
                        }
                    }
                }
            }
        }

        // Queue list
        if (queueVisible) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.queue), style = MaterialTheme.typography.titleMedium, color = colors.onBackground)
                    TextButton(onClick = onClearQueue) { Text(stringResource(R.string.clear_queue)) }
                }
            }
            itemsIndexed(state.queue, key = { i, item -> "$i:${item.mediaId}" }) { index, item ->
                val queuedTrack = tracks.firstOrNull { it.uri == item.mediaId }
                val isCurrent   = index == state.currentIndex
                GlassCard(
                    Modifier.fillMaxWidth(),
                    onClick = { onQueueIndex(index) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (index + 1).toString(),
                            modifier = Modifier.widthIn(min = 28.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isCurrent) colors.primary else colors.onSurfaceVariant,
                        )
                        Column(Modifier.weight(1f).padding(start = 6.dp)) {
                            Text(
                                item.mediaMetadata.title?.toString().orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) colors.primary else colors.onSurface,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                queuedTrack?.artist.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onRemoveQueue(index) }) {
                            Text(stringResource(R.string.remove_from_queue), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lyrics Page (left)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsPage(
    state: LyricsUiState,
    positionMs: Long,
    lyricsClock: dev.auralis.studio.lyrics.LyricsClock?,
    lyricsCandidates: List<dev.auralis.studio.lyrics.LrclibTrack>,
    isSearchingCandidates: Boolean = false,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    onImport: () -> Unit,
    onManualSearch: (String) -> Unit,
    onOffsetChange: (Long) -> Unit,
    onLoadCandidates: () -> Unit,
    onSelectCandidate: (dev.auralis.studio.lyrics.LrclibTrack) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val lyrics = (state as? LyricsUiState.Found)?.lyrics ?: SyncedLyrics.EMPTY
    var manualQuery by remember { mutableStateOf("") }
    var showCandidatesSheet by remember { mutableStateOf(false) }

    var smoothPositionMs by remember(positionMs) { mutableLongStateOf(positionMs) }
    LaunchedEffect(state, lyricsClock) {
        if (state is LyricsUiState.Found) {
            while (isActive) {
                smoothPositionMs = lyricsClock?.tick() ?: positionMs
                delay(16)
            }
        }
    }

    val activeIndex = lyrics.activeIndex(smoothPositionMs)
    var autoFollow by remember(lyrics.lines) { mutableStateOf(true) }
    var programmaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !programmaticScroll) autoFollow = false
        }
    }

    LaunchedEffect(activeIndex, autoFollow) {
        if (activeIndex >= 0 && autoFollow) {
            programmaticScroll = true
            try {
                listState.animateScrollToItem(index = activeIndex, scrollOffset = -120)
            } finally {
                programmaticScroll = false
            }
        }
    }

    if (showCandidatesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCandidatesSheet = false },
            containerColor = colors.surface,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    stringResource(R.string.choose_lyrics_version),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(12.dp))

                if (isSearchingCandidates) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = colors.primary)
                    }
                } else if (lyricsCandidates.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.lyrics_not_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface.copy(alpha = 0.75f),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(lyricsCandidates) { candidate ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable {
                                        onSelectCandidate(candidate)
                                        showCandidatesSheet = false
                                    }
                                    .padding(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            candidate.trackName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${candidate.artistName} • ${candidate.albumName.ifBlank { "Single" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        val durSec = candidate.duration.toInt()
                                        val durMin = durSec / 60
                                        val durRem = durSec % 60
                                        Text(
                                            "%02d:%02d".format(durMin, durRem),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (!candidate.syncedLyrics.isNullOrBlank()) colors.primary.copy(alpha = 0.2f)
                                                    else colors.surfaceVariant
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                if (!candidate.syncedLyrics.isNullOrBlank()) stringResource(R.string.synced_badge)
                                                else stringResource(R.string.plain_badge),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (!candidate.syncedLyrics.isNullOrBlank()) colors.primary else colors.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (state !is LyricsUiState.Found) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = colors.onSurface.copy(0.3f), modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    when (state) {
                        LyricsUiState.Loading -> stringResource(R.string.lyrics_searching)
                        LyricsUiState.NotFound -> stringResource(R.string.lyrics_not_found)
                        LyricsUiState.Offline -> stringResource(R.string.lyrics_offline)
                        is LyricsUiState.Error -> stringResource(R.string.lyrics_error)
                        is LyricsUiState.Found -> ""
                    },
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when (state) {
                        LyricsUiState.Loading -> stringResource(R.string.lyrics_searching_detail)
                        LyricsUiState.NotFound -> stringResource(R.string.lyrics_not_found_detail)
                        LyricsUiState.Offline -> stringResource(R.string.lyrics_offline_detail)
                        is LyricsUiState.Error -> state.message
                        is LyricsUiState.Found -> ""
                    },
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                if (state != LyricsUiState.Loading) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_search)) }
                        TextButton(onClick = onImport) { Text(stringResource(R.string.import_lrc)) }
                        TextButton(onClick = {
                            onLoadCandidates()
                            showCandidatesSheet = true
                        }) { Text(stringResource(R.string.choose_lyrics_version)) }
                    }
                    if (state == LyricsUiState.NotFound || state is LyricsUiState.Error) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualQuery,
                            onValueChange = { manualQuery = it },
                            label = { Text(stringResource(R.string.song_title)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = { onManualSearch(manualQuery) },
                            enabled = manualQuery.isNotBlank(),
                        ) { Text(stringResource(R.string.search_manually)) }
                    }
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (lyrics.synced) {
                    item(key = "offset") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceVariant.copy(alpha = 0.35f))
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                IconButton(
                                    onClick = { onOffsetChange(lyrics.offsetMs - 500L) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Text("-", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = colors.primary)
                                }

                                Spacer(Modifier.width(10.dp))

                                Text(
                                    text = stringResource(R.string.lyrics_offset_value, lyrics.offsetMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface,
                                )

                                Spacer(Modifier.width(10.dp))

                                IconButton(
                                    onClick = { onOffsetChange(lyrics.offsetMs + 500L) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Text("+", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = colors.primary)
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { onOffsetChange(lyrics.offsetMs - 1000L) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                    Text("-1s", fontSize = 11.sp)
                                }
                                TextButton(onClick = { onOffsetChange(lyrics.offsetMs - 500L) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                    Text("-500ms", fontSize = 11.sp)
                                }
                                TextButton(onClick = { onOffsetChange(lyrics.offsetMs - 100L) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                    Text("-100ms", fontSize = 11.sp)
                                }
                                TextButton(onClick = { onOffsetChange(0L) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text(stringResource(R.string.reset), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { onOffsetChange(lyrics.offsetMs + 100L) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                    Text("+100ms", fontSize = 11.sp)
                                }
                                TextButton(onClick = { onOffsetChange(lyrics.offsetMs + 500L) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                    Text("+500ms", fontSize = 11.sp)
                                }
                                TextButton(onClick = { onOffsetChange(lyrics.offsetMs + 1000L) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                    Text("+1s", fontSize = 11.sp)
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            TextButton(
                                onClick = {
                                    onLoadCandidates()
                                    showCandidatesSheet = true
                                },
                            ) {
                                Text(stringResource(R.string.choose_lyrics_version), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                itemsIndexed(lyrics.lines, key = { i, line -> "$i:${line.timeMs}" }) { index, line ->
                    val isActive = index == activeIndex
                    val scale by animateFloatAsState(if (isActive) 1.035f else 1f, tween(80), label = "lyric_scale")
                    val lineColor by animateColorAsState(
                        when {
                            isActive -> colors.primary
                            index < activeIndex -> colors.onSurface.copy(alpha = 0.44f)
                            else -> colors.onSurface.copy(alpha = 0.72f)
                        },
                        tween(80),
                        label = "lyric_color",
                    )

                    val adjustedPos = smoothPositionMs - lyrics.offsetMs
                    val annotatedText = if (isActive && line.hasWordTiming) {
                        androidx.compose.ui.text.buildAnnotatedString {
                            val words = line.words!!
                            for (wIdx in words.indices) {
                                val word = words[wIdx]
                                val nextWordStart = words.getOrNull(wIdx + 1)?.startTimeMs
                                    ?: if (word.endTimeMs > 0) word.endTimeMs else (word.startTimeMs + 2000L)
                                val isWordCurrent = adjustedPos >= word.startTimeMs && adjustedPos < nextWordStart
                                val isWordPassed = adjustedPos >= nextWordStart
                                val wordColor = when {
                                    isWordCurrent -> colors.primary
                                    isWordPassed -> colors.primary.copy(alpha = 0.85f)
                                    else -> colors.onSurface.copy(alpha = 0.40f)
                                }
                                val wordWeight = if (isWordCurrent) FontWeight.Bold else FontWeight.SemiBold
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = wordColor, fontWeight = wordWeight))
                                append(word.text)
                                pop()
                            }
                        }
                    } else null

                    if (annotatedText != null) {
                        Text(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 20.sp,
                                lineHeight = 29.sp,
                                textDirection = TextDirection.Content,
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(scale)
                                .then(
                                    if (lyrics.synced && line.timeMs >= 0) {
                                        Modifier.pointerInput(line.timeMs, lyrics.offsetMs) {
                                             detectTapGestures { onSeek(lyrics.seekPositionFor(line)) }
                                        }
                                    } else Modifier,
                                )
                                .padding(vertical = 6.dp),
                        )
                    } else {
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = if (isActive) 20.sp else 17.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                lineHeight = if (isActive) 29.sp else 25.sp,
                                textDirection = TextDirection.Content,
                            ),
                            color = lineColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(scale)
                                .then(
                                    if (lyrics.synced && line.timeMs >= 0) {
                                        Modifier.pointerInput(line.timeMs, lyrics.offsetMs) {
                                            detectTapGestures { onSeek(lyrics.seekPositionFor(line)) }
                                        }
                                    } else Modifier,
                                )
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = lyrics.synced && !autoFollow && activeIndex >= 0,
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
            ) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = { autoFollow = true },
                ) { Text(stringResource(R.string.return_to_current_line)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Visualizer Page (right) — Dynamic Real-Time Animated Studio Spectrum
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VisualizerPage(
    bands: FloatArray,
    waveform: FloatArray,
    isPlaying: Boolean,
    track: Track?,
    lyricsState: LyricsUiState,
    positionMs: Long,
) {
    val colors = MaterialTheme.colorScheme
    val tokens = LocalAuralisTokens.current
    val theme = LocalThemeSettings.current
    val semantic = LocalLyruneSemanticColors.current
    val lyricWindow = (lyricsState as? LyricsUiState.Found)?.lyrics?.takeIf { it.synced }?.windowAt(positionMs)

    // Butter-smooth vinyl rotation running directly on GPU RenderNode without recomposing the page
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_disc")
    val discRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    // Reactive bass energy for glowing pulse aura
    val bassEnergy = remember(bands) {
        if (bands.isEmpty()) 0f
        else (bands.take(5).average().toFloat() * 1.5f).coerceIn(0f, 1f)
    }

    val spectrumRenderer = remember { SpectrumRenderer(44) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── Rotating Vinyl Record with Reactive Audio Aura ────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Reactive Ambient Bass Glow Halo
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .graphicsLayer {
                            val pulse = if (isPlaying) 1f + bassEnergy * 0.16f else 1f
                            scaleX = pulse
                            scaleY = pulse
                            alpha = if (isPlaying) 0.45f + bassEnergy * 0.45f else 0.15f
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    colors.primary.copy(alpha = 0.65f),
                                    colors.secondary.copy(alpha = 0.35f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )

                // Vinyl Disc Body
                Box(
                    Modifier
                        .size(184.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF07080E))
                        .border(2.dp, colors.outline.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // Concentric Vinyl Micro-Groove Texture
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        repeat(10) { ring ->
                            drawCircle(
                                color = Color.White.copy(alpha = 0.025f + ring * 0.005f),
                                radius = size.minDimension * (0.48f - ring * 0.034f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                            )
                        }
                    }

                    // Album Artwork Label Spinning on Vinyl Disc
                    TrackArtwork(
                        track,
                        Modifier
                            .size(126.dp)
                            .graphicsLayer { rotationZ = if (isPlaying) discRotation else 0f }
                            .clip(CircleShape),
                    )

                    // Spindle Hole with Metallic Inner Ring
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D0E18))
                            .border(2.5.dp, colors.primary.copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.8f))
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                track?.title ?: stringResource(R.string.visualizer),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(if (isPlaying) R.string.live_post_dsp_spectrum else R.string.paused_spectrum_settling),
                style = MaterialTheme.typography.bodySmall,
                color = if (isPlaying) colors.primary else colors.onSurfaceVariant,
            )

            // Frosted Glass Synced Lyrics Bubble
            lyricWindow?.current?.text?.takeIf(String::isNotBlank)?.let { currentLine ->
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, colors.primary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedContent(currentLine, label = "visualizer_lyric") { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                                color = colors.primary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        lyricWindow.next?.text?.takeIf(String::isNotBlank)?.let { nextLine ->
                            Text(
                                nextLine,
                                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                                color = colors.onSurfaceVariant.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // ── 44-Band Studio Spectrum Visualizer with Falling Peak Caps ─────────
        val spectrumShape = RoundedCornerShape(tokens.cardCorner.dp)
        val spectrumContainer = when (theme.style) {
            dev.auralis.studio.core.model.ThemeStyle.MINIMAL -> Color.Transparent
            dev.auralis.studio.core.model.ThemeStyle.NEON -> Color(0xFF090A16).copy(alpha = 0.75f)
            dev.auralis.studio.core.model.ThemeStyle.MATERIAL -> colors.surface
            else -> colors.surface.copy(alpha = tokens.surfaceAlpha)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(spectrumShape)
                .background(spectrumContainer)
                .then(
                    if (tokens.cardBorderWidth > 0.dp) Modifier.border(
                        tokens.cardBorderWidth,
                        (if (theme.style == dev.auralis.studio.core.model.ThemeStyle.NEON) colors.primary else colors.outline)
                            .copy(alpha = tokens.cardBorderAlpha),
                        spectrumShape,
                    ) else Modifier,
                )
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp),
            ) {
                spectrumRenderer.update(bands, System.nanoTime())
                val count = spectrumRenderer.count
                val barWidth = (size.width / (count * 1.35f)).coerceAtLeast(4f)
                val gap = barWidth * 0.35f
                val totalWidth = count * (barWidth + gap)
                val startX = (size.width - totalWidth) / 2f
                val maxHeight = size.height - 14f

                val gradientColors = when (theme.style) {
                    dev.auralis.studio.core.model.ThemeStyle.MATERIAL -> listOf(colors.primary, colors.primary.copy(alpha = 0.65f))
                    dev.auralis.studio.core.model.ThemeStyle.NEON -> listOf(Color(0xFF38BDF8), colors.secondary, colors.primary)
                    dev.auralis.studio.core.model.ThemeStyle.RETRO_SYNTH -> listOf(Color(0xFFEC4899), Color(0xFF06B6D4), colors.primary)
                    dev.auralis.studio.core.model.ThemeStyle.SKY_BREEZE -> listOf(Color(0xFF38BDF8), Color(0xFF06B6D4), colors.primary)
                    dev.auralis.studio.core.model.ThemeStyle.DYNAMIC_ART -> listOf(semantic.artworkAccent, colors.primary, semantic.visualizerBass.copy(alpha = 0.75f))
                    else -> listOf(semantic.visualizerTreble, colors.primary, semantic.visualizerBass.copy(alpha = 0.75f))
                }

                for (i in 0 until count) {
                    val x = startX + i * (barWidth + gap)
                    val mag = spectrumRenderer.currentMagnitudes[i]
                    val peak = spectrumRenderer.peakCaps[i]

                    val barHeight = if (mag < 0.005f) 0f else (mag * maxHeight).coerceAtLeast(3f)
                    val y = size.height - barHeight

                    // Subtle neon glow under active bars
                    if (barHeight > 0f && tokens.accentGlowAlpha > 0f) {
                        drawRoundRect(
                            color = colors.primary.copy(alpha = (tokens.accentGlowAlpha * mag * 0.45f).coerceIn(0f, 0.45f)),
                            topLeft = androidx.compose.ui.geometry.Offset(x - barWidth * 0.3f, y - 2f),
                            size = androidx.compose.ui.geometry.Size(barWidth * 1.6f, barHeight + 4f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth),
                        )
                    }

                    // Main Spectrum Frequency Bar
                    if (barHeight > 0f) {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = gradientColors,
                                startY = y,
                                endY = size.height,
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
                        )
                    }

                    // Floating Peak Cap Marker (Hi-Fi studio equipment feel)
                    if (peak > 0.02f) {
                        val peakY = (size.height - (peak * maxHeight) - 3f).coerceAtLeast(0f)
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.92f),
                            topLeft = androidx.compose.ui.geometry.Offset(x, peakY),
                            size = androidx.compose.ui.geometry.Size(barWidth, 2.5f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f),
                        )
                    }
                }
            }

            // Smooth Audio Waveform Line
            if (waveform.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                ) {
                    val path = androidx.compose.ui.graphics.Path()
                    waveform.forEachIndexed { index, sample ->
                        val x = index * size.width / (waveform.size - 1).coerceAtLeast(1)
                        val y = size.height * (0.5f - sample.coerceIn(-1f, 1f) * 0.44f)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = colors.primary.copy(alpha = 0.85f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Professional Frequency Spectrum Labels
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.sub), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                Text(stringResource(R.string.bass), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                Text(stringResource(R.string.mid), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                Text(stringResource(R.string.treble), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                Text(stringResource(R.string.air), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * 120Hz Hardware-accelerated spectrum renderer with realistic physics
 * and peak-hold gravity falloff for studio-grade audio visualization.
 */
private class SpectrumRenderer(val count: Int = 44) {
    val currentMagnitudes = FloatArray(count)
    val peakCaps = FloatArray(count)
    val peakVelocities = FloatArray(count)
    var lastNanos: Long = 0L

    fun update(targets: FloatArray, nowNanos: Long) {
        val deltaSec = if (lastNanos == 0L) 0.016f else ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastNanos = nowNanos
        val gravity = 1.6f * deltaSec

        for (i in 0 until count) {
            val target = targets.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val timeConstant = if (target > currentMagnitudes[i]) 0.035f else 0.140f
            val factor = 1f - kotlin.math.exp(-deltaSec / timeConstant)
            currentMagnitudes[i] += (target - currentMagnitudes[i]) * factor

            // Peak cap physics (gravity drop)
            if (currentMagnitudes[i] >= peakCaps[i]) {
                peakCaps[i] = currentMagnitudes[i]
                peakVelocities[i] = 0f
            } else {
                peakVelocities[i] += gravity
                peakCaps[i] = (peakCaps[i] - peakVelocities[i]).coerceAtLeast(currentMagnitudes[i])
            }
        }
    }
}
