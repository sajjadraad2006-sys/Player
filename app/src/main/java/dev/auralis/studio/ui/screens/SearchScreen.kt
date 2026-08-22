package dev.auralis.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.auralis.studio.R
import dev.auralis.studio.core.database.PlaylistEntity
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.ui.components.TrackRow

@Composable
fun SearchScreen(
    query: String,
    results: List<Track>,
    favorites: Set<String>,
    playlists: List<PlaylistEntity>,
    onQueryChange: (String) -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Text(stringResource(R.string.search), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
        )
        if (query.isBlank() || results.isEmpty()) {
            Text(
                stringResource(if (query.isBlank()) R.string.search_empty else R.string.no_results),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(results, key = Track::uri) { track ->
                    TrackRow(
                        track,
                        track.uri in favorites,
                        playlists,
                        { onPlay(track, results) },
                        { onToggleFavorite(track.uri) },
                        { onPlayNext(track) },
                        { onAddToQueue(track) },
                        { onAddToPlaylist(it, track.uri) },
                    )
                }
            }
        }
    }
}
