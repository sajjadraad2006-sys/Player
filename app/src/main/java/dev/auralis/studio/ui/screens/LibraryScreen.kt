package dev.auralis.studio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import dev.auralis.studio.R
import dev.auralis.studio.core.database.PlaylistEntity
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.TrackRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private enum class LibraryTab { SONGS, ALBUMS, ARTISTS, FAVORITES, PLAYLISTS }

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    favorites: Set<String>,
    playlists: List<PlaylistEntity>,
    playlistTrackUris: (Long) -> Flow<List<String>>,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedGroup by remember { mutableStateOf<List<Track>?>(null) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    val tabs = LibraryTab.entries
    val unknownAlbum = stringResource(R.string.unknown_album)
    val unknownArtist = stringResource(R.string.unknown_artist)
    val selectedUrisFlow = remember(selectedPlaylist?.id) {
        selectedPlaylist?.let { playlistTrackUris(it.id) } ?: flowOf(emptyList())
    }
    val selectedPlaylistUris by selectedUrisFlow.collectAsState(initial = emptyList())
    Column(modifier) {
        Text(stringResource(R.string.library), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(16.dp))
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = index == selectedTab,
                    onClick = { selectedTab = index; selectedGroup = null; selectedPlaylist = null },
                    text = { Text(tabLabel(tab)) },
                )
            }
        }
        val shownTracks = selectedGroup ?: selectedPlaylist?.let { playlist ->
            selectedPlaylistUris.mapNotNull { uri -> tracks.firstOrNull { it.uri == uri } }
        }
        if (shownTracks != null) {
            TrackList(
                tracks = shownTracks,
                favorites = favorites,
                playlists = playlists,
                onBack = { selectedGroup = null; selectedPlaylist = null },
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylist = onAddToPlaylist,
            )
        } else when (tabs[selectedTab]) {
            LibraryTab.SONGS -> TrackList(tracks, favorites, playlists, null, onPlay, onToggleFavorite, onPlayNext, onAddToQueue, onAddToPlaylist)
            LibraryTab.FAVORITES -> TrackList(tracks.filter { it.uri in favorites }, favorites, playlists, null, onPlay, onToggleFavorite, onPlayNext, onAddToQueue, onAddToPlaylist)
            LibraryTab.ALBUMS -> GroupList(tracks.groupBy { it.album.ifBlank { unknownAlbum } }) { selectedGroup = it }
            LibraryTab.ARTISTS -> GroupList(tracks.groupBy { it.artist.ifBlank { unknownArtist } }) { selectedGroup = it }
            LibraryTab.PLAYLISTS -> PlaylistList(playlists, { selectedPlaylist = it }, { createDialog = true }, onDeletePlaylist)
        }
    }
    if (createDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text(stringResource(R.string.create_playlist)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.playlist_name)) }, singleLine = true) },
            confirmButton = { Button(onClick = { onCreatePlaylist(name); createDialog = false }, enabled = name.isNotBlank()) { Text(stringResource(R.string.create)) } },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    favorites: Set<String>,
    playlists: List<PlaylistEntity>,
    onBack: (() -> Unit)?,
    onPlay: (Track, List<Track>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Long, String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(pluralStringResource(R.plurals.track_count, tracks.size, tracks.size), style = MaterialTheme.typography.labelLarge)
                onBack?.let { TextButton(onClick = it) { Text(stringResource(R.string.close)) } }
            }
        }
        items(tracks, key = Track::uri) { track ->
            TrackRow(
                track,
                track.uri in favorites,
                playlists,
                { onPlay(track, tracks) },
                { onToggleFavorite(track.uri) },
                { onPlayNext(track) },
                { onAddToQueue(track) },
                { onAddToPlaylist(it, track.uri) },
            )
        }
    }
}

@Composable
private fun GroupList(groups: Map<String, List<Track>>, onOpen: (List<Track>) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(groups.entries.toList(), key = { it.key }) { (name, groupTracks) ->
            GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(groupTracks) }) {
                Column(Modifier.padding(18.dp)) {
                    Text(name, style = MaterialTheme.typography.titleLarge)
                    Text(pluralStringResource(R.plurals.track_count, groupTracks.size, groupTracks.size), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<PlaylistEntity>,
    onOpen: (PlaylistEntity) -> Unit,
    onCreate: () -> Unit,
    onDelete: (PlaylistEntity) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.create_playlist)) } }
        if (playlists.isEmpty()) item { Text(stringResource(R.string.no_playlists), modifier = Modifier.padding(18.dp)) }
        items(playlists, key = PlaylistEntity::id) { playlist ->
            GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(playlist) }) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(playlist.name, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { onDelete(playlist) }) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}

@Composable
private fun tabLabel(tab: LibraryTab): String = stringResource(
    when (tab) {
        LibraryTab.SONGS -> R.string.songs
        LibraryTab.ALBUMS -> R.string.albums
        LibraryTab.ARTISTS -> R.string.artists
        LibraryTab.FAVORITES -> R.string.favorites
        LibraryTab.PLAYLISTS -> R.string.playlists
    },
)
