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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.auralis.studio.R
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.designsystem.LocalAuralisTokens

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,          // 0.0..1.0
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAuralisTokens.current
    val colors = MaterialTheme.colorScheme
    val shape  = RoundedCornerShape(tokens.miniPlayerAlpha.dp.coerceAtLeast(12.dp))

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column {
            // Track info row
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(start = 8.dp, top = 8.dp, bottom = 4.dp, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArtwork(track, Modifier.size(48.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        style      = MaterialTheme.typography.titleSmall,
                        color      = colors.onSurface,
                    )
                    Text(
                        track.artist.ifBlank { track.album }.ifBlank { "Unknown" },
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        style      = MaterialTheme.typography.bodySmall,
                        color      = colors.onSurfaceVariant,
                    )
                }
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
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.next),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
            // Playback progress bar
            LinearProgressIndicator(
                progress        = { progress.coerceIn(0f, 1f) },
                modifier        = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(3.dp),
                color           = colors.primary,
                trackColor      = colors.outline.copy(alpha = 0.25f),
                strokeCap       = StrokeCap.Round,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
