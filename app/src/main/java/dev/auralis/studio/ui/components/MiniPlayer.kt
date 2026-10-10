package dev.auralis.studio.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.auralis.studio.R
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.designsystem.LocalAuralisTokens
import dev.auralis.studio.designsystem.LocalAuralisControlColors

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
    val tokens = LocalAuralisTokens.current
    val colors = MaterialTheme.colorScheme
    val controls = LocalAuralisControlColors.current

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        surfaceAlpha = tokens.miniPlayerAlpha,
        elevation = tokens.miniPlayerElevation,
    ) {
        Column {
            // Playback progress bar at top
            LinearProgressIndicator(
                progress        = { progress.coerceIn(0f, 1f) },
                modifier        = Modifier.fillMaxWidth().height(2.5.dp),
                color           = colors.primary,
                trackColor      = controls.sliderInactive,
                strokeCap       = StrokeCap.Round,
            )
            // Track info row
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)))
                            .togetherWith(fadeOut(tween(160)))
                    },
                    modifier = Modifier.weight(1f),
                    label = "mini_track_info",
                ) { currentTrack ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackArtwork(currentTrack, Modifier.size(46.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                currentTrack.title,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = colors.onSurface,
                            )
                            Text(
                                currentTrack.artist.ifBlank { currentTrack.album }.ifBlank { stringResource(R.string.unknown_artist) },
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                style      = MaterialTheme.typography.bodySmall,
                                color      = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Previous button
                IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.previous),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                // Play/Pause
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = {
                        (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh)) + fadeIn()) togetherWith
                                fadeOut(tween(80))
                    },
                    label = "mini_play_pause",
                ) { playing ->
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                            tint = colors.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                // Next button
                IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.next),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
