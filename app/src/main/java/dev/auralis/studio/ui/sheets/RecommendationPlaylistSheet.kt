package dev.auralis.studio.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.studio.R
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.TrackArtwork

@Composable
fun RecommendationPlaylistSheet(
    visible: Boolean,
    seedTrack: Track?,
    recommendations: List<Track>,
    onDismiss: () -> Unit,
    onPlayImmediately: (Track, List<Track>) -> Unit,
    onQueueAsNext: (List<Track>) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayNextTrack: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (visible && seedTrack != null) {
        BackHandler(onBack = onDismiss)
    }

    AnimatedVisibility(
        visible = visible && seedTrack != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(260, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        ) + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(140, easing = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)),
        ) + fadeOut(animationSpec = tween(120)),
        modifier = modifier.fillMaxSize(),
    ) {
        val colors = MaterialTheme.colorScheme
        val currentSeed = seedTrack ?: return@AnimatedVisibility

        // Full screen scrim overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.92f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* consume click inside sheet */ },
                    ),
                color = colors.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    // Drag handle & Close header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp, start = 16.dp, end = 16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(width = 40.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.onSurfaceVariant.copy(alpha = 0.4f)),
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.CenterEnd).size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Mix Header
                        item(key = "mix_header") {
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TrackArtwork(
                                            track = currentSeed,
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(16.dp)),
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = colors.primary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    stringResource(R.string.smart_mix),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.primary,
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                currentSeed.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = colors.onSurface,
                                            )
                                            Text(
                                                currentSeed.artist,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(colors.primaryContainer.copy(alpha = 0.5f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                ) {
                                                    Text(
                                                        pluralStringResource(
                                                            R.plurals.track_count,
                                                            recommendations.size,
                                                            recommendations.size,
                                                        ),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = colors.onPrimaryContainer,
                                                    )
                                                }
                                                if (currentSeed.album.isNotBlank()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(colors.surfaceVariant)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    ) {
                                                        Text(
                                                            currentSeed.album,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = colors.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    // Action Buttons Row: Play Immediately & Queue as Next
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        val playNowSource = remember { MutableInteractionSource() }
                                        val isPlayNowPressed by playNowSource.collectIsPressedAsState()
                                        val playNowScale by animateFloatAsState(
                                            targetValue = if (isPlayNowPressed) 0.94f else 1f,
                                            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                                            label = "play_now_squash",
                                        )

                                        Button(
                                            onClick = {
                                                onPlayImmediately(currentSeed, recommendations)
                                                onDismiss()
                                            },
                                            interactionSource = playNowSource,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .scale(playNowScale),
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                stringResource(R.string.play_immediately),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                            )
                                        }

                                        val queueNextSource = remember { MutableInteractionSource() }
                                        val isQueueNextPressed by queueNextSource.collectIsPressedAsState()
                                        val queueNextScale by animateFloatAsState(
                                            targetValue = if (isQueueNextPressed) 0.94f else 1f,
                                            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                                            label = "queue_next_squash",
                                        )

                                        FilledTonalButton(
                                            onClick = {
                                                onQueueAsNext(recommendations)
                                                onDismiss()
                                            },
                                            interactionSource = queueNextSource,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .scale(queueNextScale),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.QueueMusic,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                stringResource(R.string.queue_as_next),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Tracks Section Header
                        item(key = "track_list_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.similar_tracks),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onBackground,
                                )
                                Text(
                                    pluralStringResource(
                                        R.plurals.track_count,
                                        recommendations.size,
                                        recommendations.size,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }

                        // Individual Recommended Tracks
                        itemsIndexed(recommendations, key = { index, track -> "rec_pl_${index}_${track.uri}" }) { index, track ->
                            val staggerDelay = (index * 35).coerceAtMost(280)
                            val itemAlpha by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 180, delayMillis = staggerDelay),
                                label = "rec_item_alpha_$index",
                            )

                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { alpha = itemAlpha },
                                onClick = {
                                    onPlayTrack(track)
                                    onDismiss()
                                },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        (index + 1).toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.width(24.dp),
                                    )
                                    TrackArtwork(
                                        track = track,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            track.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = colors.onSurface,
                                        )
                                        Text(
                                            track.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }

                                    // Quick action buttons: Play Now, Queue Next, Add to Queue (Fitts's law: 40dp minimum)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                onPlayTrack(track)
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(40.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = stringResource(R.string.play_now),
                                                tint = colors.primary,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { onPlayNextTrack(track) },
                                            modifier = Modifier.size(40.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.QueueMusic,
                                                contentDescription = stringResource(R.string.play_next),
                                                tint = colors.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { onAddToQueue(track) },
                                            modifier = Modifier.size(40.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                                contentDescription = stringResource(R.string.add_to_queue),
                                                tint = colors.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
