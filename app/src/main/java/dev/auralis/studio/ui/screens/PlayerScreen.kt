package dev.auralis.studio.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.auralis.studio.R
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.playback.PlaybackUiState
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.TrackArtwork
import kotlin.math.abs

@Composable
fun PlayerScreen(
    state: PlaybackUiState,
    track: Track?,
    tracks: List<Track>,
    isFavorite: Boolean,
    sleepRemainingMs: Long?,
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
    onSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
) {
    val seekDescription = stringResource(R.string.seek)
    val previousDescription = stringResource(R.string.previous)
    val nextDescription = stringResource(R.string.next)
    val pauseDescription = stringResource(R.string.pause)
    BackHandler(onBack = onBack)
    var queueVisible by remember { mutableStateOf(false) }
    var pointA by remember(state.currentMediaItem?.mediaId) { mutableLongStateOf(-1L) }
    var pointB by remember(state.currentMediaItem?.mediaId) { mutableLongStateOf(-1L) }
    LaunchedEffect(state.positionMs, pointA, pointB) {
        if (pointA >= 0 && pointB > pointA && state.positionMs >= pointB) onSeek(pointA)
    }
    LazyColumn(
        contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close)) }
                Text(stringResource(R.string.now_playing), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onToggleFavorite, enabled = track != null) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(if (isFavorite) R.string.remove_favorite else R.string.favorite),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        item {
            var dragTotal by remember { mutableFloatStateOf(0f) }
            TrackArtwork(
                track,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onHorizontalDrag = { _, amount -> dragTotal += amount },
                            onDragEnd = {
                                if (abs(dragTotal) > 100f) {
                                    if (dragTotal < 0) onNext() else onPrevious()
                                }
                            },
                        )
                    },
            )
        }
        item {
            Text(track?.title ?: stringResource(R.string.nothing_playing), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track?.artist.orEmpty(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.66f))
        }
        item {
            Slider(
                value = state.positionMs.coerceAtMost(state.durationMs).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
                modifier = Modifier.semantics { contentDescription = seekDescription },
                enabled = state.durationMs > 0,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(DateUtils.formatElapsedTime(state.positionMs / 1_000), style = MaterialTheme.typography.labelMedium)
                Text("−" + DateUtils.formatElapsedTime((state.durationMs - state.positionMs).coerceAtLeast(0) / 1_000), style = MaterialTheme.typography.labelMedium)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onShuffle) { Text(if (state.shuffleEnabled) "●" else "○"); Text("  " + stringResource(R.string.shuffle)) }
                Button(onClick = onPrevious, modifier = Modifier.semantics { contentDescription = previousDescription }) { Text("⏮") }
                FilledIconButton(onClick = onPlayPause) {
                    if (state.isPlaying) Text("Ⅱ", modifier = Modifier.semantics { contentDescription = pauseDescription })
                    else Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play))
                }
                Button(onClick = onNext, modifier = Modifier.semantics { contentDescription = nextDescription }) { Text("⏭") }
                TextButton(onClick = onRepeat) { Text(repeatGlyph(state.repeatMode)); Text("  " + repeatLabel(state.repeatMode)) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenAudioLab, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.audio_lab)) }
                Button(onClick = { queueVisible = !queueVisible }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.queue)) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ab_repeat), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pointA = state.positionMs }) { Text(stringResource(R.string.set_a)) }
                        TextButton(onClick = { if (pointA >= 0 && state.positionMs > pointA) pointB = state.positionMs }) { Text(stringResource(R.string.set_b)) }
                        TextButton(onClick = { pointA = -1; pointB = -1 }) { Text(stringResource(R.string.clear)) }
                    }
                    if (pointA >= 0 && pointB > pointA) Text(stringResource(R.string.loop_active, DateUtils.formatElapsedTime(pointA / 1000), DateUtils.formatElapsedTime(pointB / 1000)))
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sleep_timer), style = MaterialTheme.typography.titleMedium)
                    if (sleepRemainingMs == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15, 30, 45, 60).forEach { minutes ->
                                TextButton(onClick = { onSleepTimer(minutes) }) { Text(stringResource(R.string.minutes_value, minutes)) }
                            }
                        }
                    } else {
                        Text(stringResource(R.string.timer_remaining, DateUtils.formatElapsedTime(sleepRemainingMs / 1_000)))
                        TextButton(onClick = onCancelSleepTimer) { Text(stringResource(R.string.stop_timer)) }
                    }
                }
            }
        }
        if (queueVisible) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.queue), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClearQueue) { Text(stringResource(R.string.clear_queue)) }
                }
            }
            itemsIndexed(state.queue, key = { index, item -> "$index:${item.mediaId}" }) { index, item ->
                val queuedTrack = tracks.firstOrNull { it.uri == item.mediaId }
                GlassCard(Modifier.fillMaxWidth(), onClick = { onQueueIndex(index) }) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text((index + 1).toString(), modifier = Modifier.padding(horizontal = 8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.mediaMetadata.title?.toString().orEmpty(), maxLines = 1)
                            Text(queuedTrack?.artist.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onRemoveQueue(index) }) { Text(stringResource(R.string.remove_from_queue)) }
                    }
                }
            }
        }
        track?.let { current ->
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(R.string.track_information), style = MaterialTheme.typography.titleMedium)
                        Text(current.displayName)
                        current.mimeType?.let { Text(it) }
                        Text(stringResource(R.string.file_size, formatBytes(current.sizeBytes)))
                    }
                }
            }
        }
    }
}

@Composable
private fun repeatLabel(mode: Int): String = stringResource(
    when (mode) {
        Player.REPEAT_MODE_ALL -> R.string.repeat_all
        Player.REPEAT_MODE_ONE -> R.string.repeat_one
        else -> R.string.repeat_off
    },
)

private fun repeatGlyph(mode: Int): String = if (mode == Player.REPEAT_MODE_ONE) "↻¹" else "↻"

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%.0f KB".format(bytes / 1_024.0)
}
