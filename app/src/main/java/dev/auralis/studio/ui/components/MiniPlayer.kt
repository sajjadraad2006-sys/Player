package dev.auralis.studio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.auralis.studio.R
import dev.auralis.studio.core.model.Track

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pauseDescription = stringResource(R.string.pause)
    GlassCard(modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(track, Modifier.size(50.dp))
            Spacer(Modifier.width(10.dp))
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onPlayPause) {
                if (isPlaying) Text("Ⅱ", modifier = Modifier.semantics { contentDescription = pauseDescription })
                else Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play))
            }
            IconButton(onClick = onNext) { Text("⏭", style = MaterialTheme.typography.titleLarge) }
        }
    }
}
