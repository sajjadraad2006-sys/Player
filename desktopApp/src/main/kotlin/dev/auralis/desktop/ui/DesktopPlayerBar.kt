package dev.auralis.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.desktop.artwork.DesktopArtworkManager
import dev.auralis.desktop.library.DesktopTrack
import dev.auralis.studio.playback.PlaybackState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

/**
 * Bottom persistent player bar.
 *
 * Performance Hardening:
 * Scopes high-frequency playback position ticks locally to avoid recomposing the entire Desktop shell or library tables.
 */
@Composable
fun DesktopPlayerBar(
    currentTrack: DesktopTrack?,
    playbackStateFlow: StateFlow<PlaybackState>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenAudioLab: () -> Unit,
    accentColor: Color,
    surfaceColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier,
) {
    val playbackState by playbackStateFlow.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(surfaceColor)
            .border(width = 1.dp, color = borderColor)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── Left: Track Info ──
        Row(
            modifier = Modifier.width(260.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var currentArtwork by remember(currentTrack?.path) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(currentTrack?.path) {
                currentArtwork = currentTrack?.let { DesktopArtworkManager.getArtwork(it.artworkKey ?: it.path) }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222842)),
                contentAlignment = Alignment.Center,
            ) {
                val art = currentArtwork
                if (art != null) {
                    Image(
                        bitmap = art,
                        contentDescription = currentTrack?.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack?.title ?: "Nothing Playing",
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val errorMsg: String? = null
                if (errorMsg != null) {
                    Text(
                        text = errorMsg,
                        color = Color(0xFFFF6B6B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = currentTrack?.artist?.ifBlank { "Select a track to begin" } ?: "Ready to play",
                        color = textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ── Center: Controls & Seek Bar ──
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Media Standard Controls (Enforce LTR direction so rewind is left and next is right)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = textPrimary)
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = textPrimary)
                    }
                }
            }

            // Progress Slider
            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val currentSec = playbackState.currentPositionMs / 1000
                Text(
                    text = "%02d:%02d".format(currentSec / 60, currentSec % 60),
                    color = textSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.width(36.dp),
                )

                Slider(
                    value = playbackState.currentPositionMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..max(1f, playbackState.durationMs.toFloat()),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color(0xFF282E47),
                    ),
                    modifier = Modifier.weight(1f).height(24.dp),
                )

                val durSec = playbackState.durationMs / 1000
                Text(
                    text = "%02d:%02d".format(durSec / 60, durSec % 60),
                    color = textSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.End,
                )
            }
        }

        // ── Right: Mini Controls & Volume ──
        Row(
            modifier = Modifier.width(260.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            // DSP Quick Shortcut
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .clickable { onOpenAudioLab() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("DSP", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Volume Icon & Slider
            Icon(Icons.Default.VolumeUp, contentDescription = "Volume", tint = textSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Slider(
                value = playbackState.volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                modifier = Modifier.width(100.dp).height(24.dp),
            )
        }
    }
}
