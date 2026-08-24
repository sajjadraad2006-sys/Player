package dev.auralis.studio.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

    Box(modifier.fillMaxSize()) {
        // Background artwork blur when theme uses artwork backgrounds
        if (tokens.backgroundUsesArtwork && track?.artworkUri != null) {
            AsyncImage(
                model        = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier     = Modifier
                    .fillMaxSize()
                    .scale(if (tokens.glassBlur) 1.10f else 1f)
                    .blur(if (tokens.glassBlur) (12f + 20f * themeSettings.blurStrength).dp else 0.dp),
            )
            // Contrast scrim
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                artworkScrim.copy(alpha = tokens.glassContrastScrimAlpha * 0.55f),
                                artworkScrim.copy(alpha = tokens.glassContrastScrimAlpha * 0.82f),
                                artworkScrim.copy(alpha = tokens.glassContrastScrimAlpha),
                            ),
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
                        state     = lyricsState,
                        positionMs = state.positionMs,
                        onSeek    = onSeek,
                        onRetry   = onRetryLyrics,
                        onImport  = onImportLyrics,
                        onManualSearch = onManualLyricsSearch,
                        onOffsetChange = onLyricsOffsetChange,
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
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
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
                TrackArtwork(
                    track,
                    Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f),
                )
            }
        }

        // Track info
        item {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    track?.title ?: stringResource(R.string.nothing_playing),
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
                        track?.artist?.ifBlank { null },
                        track?.album?.ifBlank { null },
                    ).joinToString(" · "),
                    style   = MaterialTheme.typography.bodyLarge,
                    color   = colors.onBackground.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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

        // Repeat mode label — small text below controls showing current state
        item {
            val repeatLabel = when (state.repeatMode) {
                Player.REPEAT_MODE_ALL -> "🔁 Repeat All"
                Player.REPEAT_MODE_ONE -> "🔂 Repeat One"
                else -> null
            }
            if (repeatLabel != null) {
                Text(
                    repeatLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Secondary action row: Quick Audio | Queue | Audio Lab
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Quick Audio Sheet
                GlassCard(
                    modifier = Modifier.weight(1f),
                    onClick  = onOpenQuickAudio,
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint     = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.audio), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    }
                }
                // Queue toggle
                GlassCard(
                    modifier = Modifier.weight(1f),
                    onClick  = onQueueToggle,
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint     = if (queueVisible) colors.primary else colors.onSurface.copy(0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.queue), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    }
                }
                // Audio Lab (full DSP)
                GlassCard(
                    modifier = Modifier.weight(1f),
                    onClick  = onOpenAudioLab,
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint     = colors.onSurface.copy(0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.lab), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
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
@Composable
private fun LyricsPage(
    state: LyricsUiState,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    onImport: () -> Unit,
    onManualSearch: (String) -> Unit,
    onOffsetChange: (Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val lyrics = (state as? LyricsUiState.Found)?.lyrics ?: SyncedLyrics.EMPTY
    var manualQuery by remember { mutableStateOf("") }
    val activeIndex = lyrics.activeIndex(positionMs)
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.lyrics_offset_value, lyrics.offsetMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = { onOffsetChange(lyrics.offsetMs - 250L) }) { Text(stringResource(R.string.offset_negative_step)) }
                                TextButton(onClick = { onOffsetChange(0L) }) { Text(stringResource(R.string.reset)) }
                                TextButton(onClick = { onOffsetChange(lyrics.offsetMs + 250L) }) { Text(stringResource(R.string.offset_positive_step)) }
                            }
                        }
                    }
                }
                itemsIndexed(lyrics.lines, key = { i, line -> "$i:${line.timeMs}" }) { index, line ->
                    val isActive = index == activeIndex
                    val scale by animateFloatAsState(if (isActive) 1.035f else 1f, tween(220), label = "lyric_scale")
                    val lineColor by animateColorAsState(
                        when {
                            isActive -> colors.primary
                            index < activeIndex -> colors.onSurface.copy(alpha = 0.44f)
                            else -> colors.onSurface.copy(alpha = 0.72f)
                        },
                        tween(220),
                        label = "lyric_color",
                    )
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
    val displayedBands = rememberInterpolatedSpectrum(bands)
    val lyricWindow = (lyricsState as? LyricsUiState.Found)?.lyrics?.takeIf { it.synced }?.windowAt(positionMs)
    var discRotation by rememberSaveable { mutableFloatStateOf(0f) }

    // One elapsed-time clock, active only while Media3 reports actual playback.
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var previousFrame = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previousFrame != 0L) {
                    val deltaSeconds = ((now - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    discRotation = (discRotation + deltaSeconds * 48f) % 360f // calm 8 RPM visual rotation
                }
                previousFrame = now
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Rotating record bound to actual playback state.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(202.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    colors.primary.copy(alpha = if (isPlaying) 0.35f else 0.12f),
                                    colors.secondary.copy(alpha = if (isPlaying) 0.18f else 0.05f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .size(172.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF080910)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        repeat(6) { ring ->
                            drawCircle(
                                color = Color.White.copy(alpha = 0.025f + ring * 0.006f),
                                radius = size.minDimension * (0.49f - ring * 0.045f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                            )
                        }
                    }
                    Crossfade(targetState = track, animationSpec = tween(420), label = "disc_artwork") { discTrack ->
                        TrackArtwork(
                            discTrack,
                            Modifier
                                .size(148.dp)
                                .graphicsLayer { rotationZ = discRotation }
                                .clip(CircleShape),
                        )
                    }
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF090A12))
                            .border(2.dp, colors.primary.copy(alpha = 0.72f), CircleShape),
                    )
                    Box(Modifier.size(4.dp).clip(CircleShape).background(colors.onSurface.copy(alpha = 0.8f)))
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                track?.title ?: stringResource(R.string.visualizer),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(if (isPlaying) R.string.live_post_dsp_spectrum else R.string.paused_spectrum_settling),
                style = MaterialTheme.typography.bodySmall,
                color = if (isPlaying) colors.primary else colors.onSurfaceVariant,
            )

            lyricWindow?.current?.text?.takeIf(String::isNotBlank)?.let { currentLine ->
                Spacer(Modifier.height(10.dp))
                AnimatedContent(currentLine, label = "visualizer_lyric") { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        color = colors.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }
                lyricWindow.next?.text?.takeIf(String::isNotBlank)?.let { nextLine ->
                    Text(
                        nextLine,
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                        color = colors.onSurfaceVariant.copy(alpha = 0.58f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }

        val spectrumShape = RoundedCornerShape(tokens.cardCorner.dp)
        val spectrumContainer = when (theme.style) {
            dev.auralis.studio.core.model.ThemeStyle.MINIMAL -> Color.Transparent
            dev.auralis.studio.core.model.ThemeStyle.NEON -> colors.surface.copy(alpha = 0.42f)
            dev.auralis.studio.core.model.ThemeStyle.CLASSIC -> colors.surface
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                val barCount = if (displayedBands.isEmpty()) 32 else displayedBands.size
                val barWidth = (size.width / (barCount * 1.4f)).coerceAtLeast(4f)
                val gap = barWidth * 0.4f
                val totalWidth = barCount * (barWidth + gap)
                val startX = (size.width - totalWidth) / 2f
                val maxHeight = size.height - 12f

                for (i in 0 until barCount) {
                    val x = startX + i * (barWidth + gap)
                    val rawMag = displayedBands.getOrElse(i) { 0f }.coerceIn(0f, 1f)

                    val barHeight = if (rawMag < 0.008f) 0f else (rawMag * maxHeight).coerceAtLeast(2.5f)
                    val y = size.height - barHeight

                    if (barHeight > 0f && tokens.accentGlowAlpha > 0f) {
                        drawRoundRect(
                            color = colors.primary.copy(alpha = tokens.accentGlowAlpha * rawMag),
                            topLeft = androidx.compose.ui.geometry.Offset(x - barWidth * 0.45f, y - 3f),
                            size = androidx.compose.ui.geometry.Size(barWidth * 1.9f, barHeight + 6f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth),
                        )
                    }

                    if (barHeight > 0f) drawRoundRect(
                        brush = Brush.verticalGradient(
                            when (theme.style) {
                                dev.auralis.studio.core.model.ThemeStyle.CLASSIC -> listOf(colors.primary, colors.primary.copy(alpha = 0.62f))
                                dev.auralis.studio.core.model.ThemeStyle.NEON -> listOf(colors.secondary, colors.primary, Color.White.copy(alpha = 0.55f))
                                dev.auralis.studio.core.model.ThemeStyle.DYNAMIC_ART -> listOf(semantic.artworkAccent, colors.primary, semantic.visualizerBass.copy(alpha = 0.72f))
                                else -> listOf(semantic.visualizerTreble, colors.primary, semantic.visualizerBass.copy(alpha = 0.72f))
                            },
                            startY = y,
                            endY = size.height,
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2),
                    )
                }
            }

            if (waveform.isNotEmpty()) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                ) {
                    val path = androidx.compose.ui.graphics.Path()
                    waveform.forEachIndexed { index, sample ->
                        val x = index * size.width / (waveform.size - 1).coerceAtLeast(1)
                        val y = size.height * (0.5f - sample.coerceIn(-1f, 1f) * 0.42f)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = colors.primary.copy(alpha = 0.75f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Frequency spectrum labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.sub), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(stringResource(R.string.bass), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(stringResource(R.string.mid), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(stringResource(R.string.treble), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(stringResource(R.string.air), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * A single frame-clock interpolator follows real FFT targets. It never generates
 * spectrum energy: zero input converges to zero and no oscillator/random source exists.
 */
@Composable
private fun rememberInterpolatedSpectrum(target: FloatArray): FloatArray {
    val latestTarget by rememberUpdatedState(target)
    val rendered = remember(target.size) { FloatArray(target.size) }
    var frameVersion by remember(target.size) { mutableIntStateOf(0) }
    LaunchedEffect(target.size) {
        var previousFrame = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previousFrame != 0L) {
                    val delta = ((now - previousFrame) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    for (index in rendered.indices) {
                        val destination = latestTarget.getOrElse(index) { 0f }.coerceIn(0f, 1f)
                        val timeConstant = if (destination > rendered[index]) 0.050f else 0.165f
                        val coefficient = 1f - exp(-delta / timeConstant)
                        rendered[index] += (destination - rendered[index]) * coefficient
                        if (rendered[index] < 0.001f && destination == 0f) rendered[index] = 0f
                    }
                    frameVersion++
                }
                previousFrame = now
            }
        }
    }
    frameVersion // Snapshot read invalidates the Canvas once per display frame.
    return rendered
}
