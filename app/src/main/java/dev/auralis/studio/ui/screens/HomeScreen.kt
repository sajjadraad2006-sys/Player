package dev.auralis.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auralis.studio.R
import dev.auralis.studio.core.database.PlaylistEntity
import dev.auralis.studio.core.model.AudioPreset
import dev.auralis.studio.core.model.BuiltInPresets
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.SectionHeader
import dev.auralis.studio.ui.components.TrackArtwork
import dev.auralis.studio.ui.components.TrackRow

@Composable
fun HomeScreen(
    tracks: List<Track>,
    favorites: Set<String>,
    historyUris: List<String>,
    playlists: List<PlaylistEntity>,
    currentTrack: Track?,
    isScanning: Boolean,
    onRefresh: () -> Unit,
    onChooseFolder: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Long, String) -> Unit,
    onPreset: (AudioPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recent = historyUris.mapNotNull { uri -> tracks.firstOrNull { it.uri == uri } }.take(8)
    val recentlyAdded = tracks.sortedByDescending(Track::dateAddedSeconds).take(8)
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // App Title / Welcome
        item {
            Column {
                Text(
                    stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                )
                Text(
                    stringResource(R.string.welcome_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onBackground.copy(alpha = 0.70f),
                )
            }
        }

        // Quick Presets Carousel
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.quick_presets),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BuiltInPresets.all) { preset ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(colors.surfaceVariant.copy(alpha = 0.85f))
                                .clickable { onPreset(preset) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = presetLabel(preset.name),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // Action Bar when music is present: Play All / Shuffle
        if (tracks.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { onPlay(tracks.first(), tracks) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.play_all_count, tracks.size), fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            val shuffled = tracks.shuffled()
                            onPlay(shuffled.first(), shuffled)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colors.surfaceVariant,
                            contentColor = colors.onSurface,
                        ),
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = colors.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.shuffle_all), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Empty State
        if (tracks.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (isScanning) stringResource(R.string.scanning) else stringResource(R.string.no_music_found),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        Text(
                            stringResource(R.string.no_music_body),
                            color = colors.onSurface.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onRefresh, enabled = !isScanning) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.scan_again))
                            }
                            TextButton(onClick = onChooseFolder) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.choose_folder))
                            }
                        }
                    }
                }
            }
        }

        // Continue Listening Card
        currentTrack?.let { track ->
            item {
                SectionHeader(stringResource(R.string.continue_listening))
                Spacer(Modifier.height(4.dp))
                GlassCard(onClick = { onPlay(track, tracks) }) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrackArtwork(track, Modifier.size(72.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                color = colors.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface.copy(alpha = 0.65f),
                                maxLines = 1,
                            )
                        }
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            tint = colors.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }

        // Recently Played
        if (recent.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.recently_played)) }
            items(recent, key = { "recent:${it.uri}" }) { track ->
                TrackRow(
                    track = track,
                    isFavorite = track.uri in favorites,
                    playlists = playlists,
                    onPlay = { onPlay(track, recent) },
                    onToggleFavorite = { onToggleFavorite(track.uri) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylist(it, track.uri) },
                )
            }
        }

        // Recently Added
        if (recentlyAdded.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.recently_added)) }
            items(recentlyAdded, key = { "added:${it.uri}" }) { track ->
                TrackRow(
                    track = track,
                    isFavorite = track.uri in favorites,
                    playlists = playlists,
                    onPlay = { onPlay(track, recentlyAdded) },
                    onToggleFavorite = { onToggleFavorite(track.uri) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylist(it, track.uri) },
                )
            }
        }
    }
}

@Composable
fun presetLabel(name: String): String = stringResource(
    when (name) {
        "Original" -> R.string.preset_original
        "Slowed" -> R.string.preset_slowed
        "Slowed + Reverb" -> R.string.preset_slowed_reverb
        "Nightcore" -> R.string.preset_nightcore
        "8D" -> R.string.preset_8d
        "Slowed 8D" -> R.string.preset_slowed_8d
        "Deep Bass" -> R.string.preset_deep_bass
        else -> R.string.preset_dream
    },
)
