package dev.auralis.studio.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auralis.studio.R
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.designsystem.LocalAuralisControlColors
import dev.auralis.studio.designsystem.LocalAuralisTokens
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MiniPlayer(
    track: Track,
    playbackStateFlow: kotlinx.coroutines.flow.StateFlow<dev.auralis.studio.playback.PlaybackUiState>,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback by playbackStateFlow.collectAsStateWithLifecycle()
    val progressFraction = if (playback.durationMs > 0)
        playback.positionMs.toFloat() / playback.durationMs.toFloat()
    else 0f

    MiniPlayer(
        track = track,
        isPlaying = playback.isPlaying,
        progress = progressFraction,
        onOpen = onOpen,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onPrevious = onPrevious,
        modifier = modifier,
    )
}

/**
 * World-class MiniPlayer floating pill dock inspired by LastWave and BitChord:
 * - Concentric geometry: 52dp artwork nesting seamlessly in the capsule curve
 * - Dynamic animated artwork corner rounding (circle when playing, rounded squircle when paused)
 * - Gesture swipe interactions: swipe up to expand full player, swipe left/right to skip tracks
 * - Elastic spring physics with non-recomposing graphics layer translation
 * - Sleek micro-progress indicator with rounded stroke
 */
@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,          // 0.0..1.0
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val controls = LocalAuralisControlColors.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Smooth drag offsets inside graphicsLayer to avoid recomposition
    val dragX = remember(track.id, track.uri) { Animatable(0f) }
    val dragY = remember(track.id, track.uri) { Animatable(0f) }
    val thresholdPx = 110f

    fun settleDrag() {
        scope.launch { dragX.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)) }
        scope.launch { dragY.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)) }
    }

    // Concentric Artwork Corner: 50% (circle) when playing, 26% when paused
    val artworkCornerPercent by animateIntAsState(
        targetValue = if (isPlaying) 50 else 26,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "mini_art_corner",
    )

    val capsuleShape = RoundedCornerShape(32.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = dragX.value
                translationY = dragY.value.coerceAtMost(30f)
                val dragDistance = abs(dragX.value) + abs(dragY.value)
                alpha = (1f - (dragDistance / 600f)).coerceIn(0.65f, 1f)
            }
            .pointerInput(track.id, track.uri) {
                detectDragGestures(
                    onDragCancel = { settleDrag() },
                    onDragEnd = {
                        when {
                            dragY.value < -45f -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpen()
                            }
                            dragX.value < -thresholdPx -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNext()
                            }
                            dragX.value > thresholdPx -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPrevious()
                            }
                        }
                        settleDrag()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val newX = dragX.value + dragAmount.x
                    val newY = dragY.value + dragAmount.y
                    scope.launch {
                        dragX.snapTo(newX)
                        dragY.snapTo(newY)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = capsuleShape,
                    ambientColor = Color.Black.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.35f),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpen()
                    },
                ),
            shape = capsuleShape,
            color = colors.surfaceContainerHigh.copy(alpha = 0.94f),
            tonalElevation = 6.dp,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.26f),
                        Color.White.copy(alpha = 0.04f),
                    ),
                ),
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Concentric Left Geometry: 50.dp artwork nesting inside capsule
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(percent = artworkCornerPercent))
                            .background(colors.surfaceContainerHighest)
                            .border(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(colors.primary.copy(alpha = 0.45f), colors.secondary.copy(alpha = 0.25f)),
                                ),
                                RoundedCornerShape(percent = artworkCornerPercent),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        TrackArtwork(
                            track = track,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Typography Hierarchy: 14.5sp Bold Title + 12.5sp Medium Artist
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = track.title,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = track.artist.ifBlank { track.album }.ifBlank { stringResource(R.string.unknown_artist) },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurfaceVariant.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Previous Action Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPrevious()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.previous),
                            tint = colors.onSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Spacer(Modifier.width(2.dp))

                    // Concentric Solid Play/Pause Button
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayPause()
                        },
                        shape = CircleShape,
                        color = colors.primary,
                        contentColor = colors.onPrimary,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AnimatedContent(
                                targetState = isPlaying,
                                transitionSpec = {
                                    (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh)) + fadeIn(tween(140))) togetherWith
                                            (scaleOut(tween(100)) + fadeOut(tween(100)))
                                },
                                label = "mini_play_pause",
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                                    modifier = Modifier.size(24.dp),
                                    tint = colors.onPrimary,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(2.dp))

                    // Next Action Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNext()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.next),
                            tint = colors.onSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // Micro-progress line with smooth rounded corners at the bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(colors.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(colors.primary, colors.secondary),
                                ),
                            ),
                    )
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}
