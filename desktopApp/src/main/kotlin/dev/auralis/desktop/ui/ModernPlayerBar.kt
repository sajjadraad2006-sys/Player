package dev.auralis.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.studio.playback.PlaybackState
import kotlinx.coroutines.flow.StateFlow

@Composable fun ModernPlayerBar(stateFlow: StateFlow<PlaybackState>, favorite: Boolean, shuffle: Boolean, repeatMode: Int,
    onFavorite: () -> Unit, onShuffle: () -> Unit, onRepeat: () -> Unit, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onSeek: (Long) -> Unit, onVolume: (Float) -> Unit, onLyrics: () -> Unit, onQueue: () -> Unit, onAudioLab: () -> Unit) {
    val state by stateFlow.collectAsState()
    val colors = MaterialTheme.colorScheme
    var audibleVolume by remember { mutableStateOf(state.volume.takeIf { it > 0f } ?: .7f) }
    LaunchedEffect(state.volume) { if(state.volume > 0f) audibleVolume = state.volume }
    var dragging by remember(state.currentUri) { mutableStateOf(false) }
    var seek by remember(state.currentUri) { mutableStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 20.dp, vertical = 10.dp)) {
        val wide = maxWidth > 1100.dp
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.width(if(wide) 280.dp else 235.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(state.metadata?.artwork, Modifier.size(if(wide) 54.dp else 46.dp))
                PlayerTooltip(state.metadata?.let { it.title + " · " + it.artist }.orEmpty(), Modifier.weight(1f)) {
                Column(Modifier.padding(start = 12.dp)) {
                    Text(state.metadata?.title ?: uiText("Your next song awaits"), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                    Text(if(state.isBuffering) uiText("Loading your song…") else state.metadata?.artist.orEmpty(), color = colors.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                }
                PlayerButton(if(favorite) "Remove favorite" else "Favorite", onFavorite, enabled = state.metadata != null) { Icon(if(favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", tint = if(favorite) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerButton(if(shuffle) "Shuffle on" else "Shuffle off", onShuffle) { Icon(Icons.Default.Shuffle, "Shuffle", tint = if(shuffle) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(19.dp)) }
                    PlayerButton("Previous", onPrevious, enabled = state.metadata != null) { Icon(Icons.Default.SkipPrevious, "Previous") }
                    PlayerTooltip(uiText(if(state.isPlaying) "Pause" else "Play") + " · Space") {
                    FilledIconButton(onToggle, enabled = state.metadata != null, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                        if(state.isBuffering) CircularProgressIndicator(Modifier.size(22.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                        else Icon(if(state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if(state.isPlaying) "Pause" else "Play")
                    }
                    }
                    PlayerButton("Next", onNext) { Icon(Icons.Default.SkipNext, "Next") }
                    PlayerButton(when(repeatMode) { 1 -> "Repeat queue"; 2 -> "Repeat track"; else -> "Repeat off" }, onRepeat) { Icon(if(repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repeat", tint = if(repeatMode > 0) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(19.dp)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if(state.metadata == null) "0:00" else musicTime(if(dragging) seek.toLong() else state.currentPositionMs).replace("—", "0:00"), fontSize = 11.sp, color = colors.onSurfaceVariant)
                    Slider(value = if(dragging) seek else state.currentPositionMs.toFloat().coerceIn(0f, state.durationMs.coerceAtLeast(1).toFloat()),
                        onValueChange = { dragging = true; seek = it }, onValueChangeFinished = { onSeek(seek.toLong()); dragging = false },
                        enabled = state.durationMs > 0 && !state.isBuffering,
                        valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(), modifier = Modifier.weight(1f).height(24.dp))
                    Text(musicTime(state.durationMs), fontSize = 11.sp, color = colors.onSurfaceVariant)
                }
            }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerButton("Lyrics", onLyrics) { Icon(Icons.Default.FormatQuote, "Lyrics", modifier = Modifier.size(20.dp)) }
                PlayerButton("Queue", onQueue) { Icon(Icons.Default.LibraryMusic, "Queue", modifier = Modifier.size(20.dp)) }
                if(wide) PlayerButton("Audio Lab", onAudioLab) { Icon(Icons.Default.Tune, "Audio Lab", modifier = Modifier.size(20.dp)) }
                PlayerButton(if(state.volume == 0f) "Unmute" else "Mute", { onVolume(if(state.volume == 0f) audibleVolume else 0f) }) {
                    Icon(if(state.volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp, null, modifier = Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                }
                Slider(state.volume, onVolume, modifier = Modifier.width(if(wide) 105.dp else 72.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PlayerTooltip(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { if(label.isNotBlank()) PlainTooltip { Text(label) } },
        state = rememberTooltipState(), modifier = modifier, content = content)
}

@Composable private fun PlayerButton(label: String, onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    PlayerTooltip(uiText(label)) {
        IconButton(onClick, enabled = enabled, modifier = Modifier.size(40.dp)) { content() }
    }
}
