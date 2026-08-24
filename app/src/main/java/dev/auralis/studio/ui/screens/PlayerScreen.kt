package dev.auralis.studio.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import dev.auralis.studio.R
import dev.auralis.studio.core.model.SyncedLyrics
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.designsystem.LocalAuralisTokens
import dev.auralis.studio.playback.PlaybackUiState
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.PagerDotIndicator
import dev.auralis.studio.ui.components.TrackArtwork
import kotlinx.coroutines.launch

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
    lyrics: SyncedLyrics,
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
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val tokens = LocalAuralisTokens.current
    val colors = MaterialTheme.colorScheme
    val scope  = rememberCoroutineScope()

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
                modifier     = Modifier.fillMaxSize(),
            )
            // Contrast scrim
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = tokens.glassContrastScrimAlpha * 0.3f),
                                Color.Black.copy(alpha = tokens.glassContrastScrimAlpha * 0.7f),
                                Color.Black.copy(alpha = tokens.glassContrastScrimAlpha),
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
                    text = when (pagerState.currentPage) {
                        PAGE_LYRICS     -> "Lyrics"
                        PAGE_VISUALIZER -> "Visualizer"
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
                        lyrics    = lyrics,
                        positionMs = state.positionMs,
                        onSeek    = onSeek,
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
                    )
                }
            }

            // ── Pager dot indicator & page shortcuts ─────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(if (isLtr) PAGE_LYRICS else PAGE_COUNT - 1 - PAGE_LYRICS) } }) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = "Lyrics",
                        tint = if (pagerState.currentPage == PAGE_LYRICS) colors.primary else colors.onSurface.copy(0.4f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                PagerDotIndicator(
                    pageCount   = PAGE_COUNT,
                    currentPage = pagerState.currentPage,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(if (isLtr) PAGE_VISUALIZER else PAGE_COUNT - 1 - PAGE_VISUALIZER) } }) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = "Visualizer",
                        tint = if (pagerState.currentPage == PAGE_VISUALIZER) colors.primary else colors.onSurface.copy(0.4f),
                        modifier = Modifier.size(20.dp),
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
                Slider(
                    value         = state.positionMs.coerceAtMost(state.durationMs).toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange    = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
                    enabled       = state.durationMs > 0,
                    colors        = SliderDefaults.colors(
                        thumbColor                 = colors.primary,
                        activeTrackColor           = colors.primary,
                        inactiveTrackColor         = colors.surfaceVariant,
                        disabledThumbColor         = colors.primary,
                        disabledActiveTrackColor   = colors.primary.copy(alpha = 0.6f),
                        disabledInactiveTrackColor = colors.surfaceVariant.copy(alpha = 0.6f),
                    ),
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
                            Player.REPEAT_MODE_OFF -> "Repeat off"
                            Player.REPEAT_MODE_ALL -> "Repeat all"
                            Player.REPEAT_MODE_ONE -> "Repeat one"
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
                        Text("Audio", style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
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
                        Text("Lab", style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
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
    lyrics: SyncedLyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val activeIndex = lyrics.activeIndex(positionMs)

    // Auto-scroll to active line
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem(index = (activeIndex - 2).coerceAtLeast(0))
        }
    }

    if (lyrics.lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = colors.onSurface.copy(0.3f), modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("No lyrics available", color = colors.onSurface.copy(0.45f), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text("Embedded, local .lrc, or LRCLIB lyrics will appear here.", color = colors.onSurface.copy(0.30f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
            }
        }
    } else {
        LazyColumn(
            state           = listState,
            contentPadding  = PaddingValues(horizontal = 24.dp, vertical = 64.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(lyrics.lines, key = { i, _ -> i }) { index, line ->
                val isActive = index == activeIndex
                Text(
                    text      = line.text,
                    style     = MaterialTheme.typography.bodyLarge.copy(
                        fontSize   = if (isActive) 20.sp else 17.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        lineHeight = if (isActive) 28.sp else 24.sp,
                    ),
                    color     = when {
                        isActive            -> colors.primary
                        index < activeIndex -> colors.onSurface.copy(alpha = 0.45f)
                        else                -> colors.onSurface.copy(alpha = 0.75f)
                    },
                    textAlign = TextAlign.Center,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .then(
                            if (lyrics.synced && line.timeMs >= 0) {
                                Modifier.pointerInput(line.timeMs) {
                                    detectTapGestures { onSeek(line.timeMs) }
                                }
                            } else Modifier,
                        ),
                )
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
) {
    val colors = MaterialTheme.colorScheme
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "visualizer_anim")
    val phaseState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "phase",
    )
    val curPhase = phaseState.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Track Header with glowing artwork aura
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // Outer pulsating glow
                Box(
                    modifier = Modifier
                        .size(170.dp)
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
                // Center Album Art
                TrackArtwork(
                    track,
                    Modifier
                        .size(130.dp)
                        .clip(CircleShape),
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                track?.title ?: "Studio Visualizer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (isPlaying) "Live Real-Time DSP Audio Processing" else "Paused · Tap Play to animate",
                style = MaterialTheme.typography.bodySmall,
                color = if (isPlaying) colors.primary else colors.onSurfaceVariant,
            )
        }

        // Live Spectrum Equalizer Visualizer Bars
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface.copy(alpha = 0.55f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                val barCount = 32
                val barWidth = (size.width / (barCount * 1.4f)).coerceAtLeast(4f)
                val gap = barWidth * 0.4f
                val totalWidth = barCount * (barWidth + gap)
                val startX = (size.width - totalWidth) / 2f
                val maxHeight = size.height - 12f

                for (i in 0 until barCount) {
                    val x = startX + i * (barWidth + gap)
                    val iFloat = i.toFloat()
                    val freqFactor: Float = if (i < 8) 0.85f + (0.15f * iFloat / 8f) else 1.0f - (0.4f * (iFloat - 8f) / 24f)
                    val waveAngle = (curPhase + (iFloat * 0.45f)).toDouble()
                    val waveAngle2 = ((curPhase * 1.5f) + (iFloat * 0.25f)).toDouble()
                    val waveValue = kotlin.math.sin(waveAngle).toFloat()
                    val waveValue2 = kotlin.math.cos(waveAngle2).toFloat()

                    val rawMag: Float = if (bands.isNotEmpty() && i < bands.size) {
                        bands[i]
                    } else if (isPlaying) {
                        ((waveValue * 0.4f + waveValue2 * 0.35f + 0.45f) * freqFactor).coerceIn(0.12f, 1f)
                    } else {
                        0.06f
                    }

                    val barHeight = (rawMag * maxHeight).coerceAtLeast(6f)
                    val y = size.height - barHeight

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                colors.secondary,
                                colors.primary,
                                colors.primary.copy(alpha = 0.6f),
                            ),
                            startY = y,
                            endY = size.height,
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Frequency spectrum labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Sub", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text("Bass", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text("Mid", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text("Treble", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text("Air", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
