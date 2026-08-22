package dev.auralis.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            )
        }
        item {
            SectionHeader(stringResource(R.string.quick_presets))
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BuiltInPresets.all) { preset ->
                    TextButton(onClick = { onPreset(preset) }) { Text(presetLabel(preset.name)) }
                }
            }
        }
        if (tracks.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (isScanning) stringResource(R.string.scanning) else stringResource(R.string.no_music_found),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(stringResource(R.string.no_music_body), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Row {
                            Button(onClick = onRefresh, enabled = !isScanning) { Text(stringResource(R.string.scan_again)) }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = onChooseFolder) { Text(stringResource(R.string.choose_folder)) }
                        }
                    }
                }
            }
        }
        currentTrack?.let { track ->
            item {
                SectionHeader(stringResource(R.string.continue_listening))
                Spacer(Modifier.height(8.dp))
                GlassCard(onClick = { onPlay(track, tracks) }) {
                    Row(Modifier.padding(14.dp)) {
                        TrackArtwork(track, Modifier.width(82.dp).height(82.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                            Text(track.artist, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        }
                    }
                }
            }
        }
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
