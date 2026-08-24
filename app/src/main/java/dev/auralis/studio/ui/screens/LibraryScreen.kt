package dev.auralis.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    var selectedGroupName by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    val tabs = LibraryTab.entries
    val colors = MaterialTheme.colorScheme

    val selectedUrisFlow = remember(selectedPlaylist?.id) {
        selectedPlaylist?.let { playlistTrackUris(it.id) } ?: flowOf(emptyList())
    }
    val selectedPlaylistUris by selectedUrisFlow.collectAsState(initial = emptyList())

    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.library),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onBackground,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )

        // High-Contrast Segmented Tab Pills
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tabs.size) { index ->
                val tab = tabs[index]
                val isSelected = index == selectedTab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) colors.primary else colors.surfaceVariant.copy(alpha = 0.8f)
                        )
                        .clickable {
                            selectedTab = index
                            selectedGroup = null
                            selectedGroupName = null
                            selectedPlaylist = null
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tabLabel(tab),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) colors.onPrimary else colors.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        val shownTracks = selectedGroup ?: selectedPlaylist?.let { playlist ->
            selectedPlaylistUris.mapNotNull { uri -> tracks.firstOrNull { it.uri == uri } }
        }

        if (shownTracks != null) {
            // Detailed drill-down view
            TrackList(
                title = selectedGroupName ?: selectedPlaylist?.name ?: "",
                tracks = shownTracks,
                favorites = favorites,
                playlists = playlists,
                onBack = {
                    selectedGroup = null
                    selectedGroupName = null
                    selectedPlaylist = null
                },
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylist = onAddToPlaylist,
            )
        } else when (tabs[selectedTab]) {
            LibraryTab.SONGS -> TrackList(
                title = null,
                tracks = tracks,
                favorites = favorites,
                playlists = playlists,
                onBack = null,
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylist = onAddToPlaylist,
            )
            LibraryTab.FAVORITES -> TrackList(
                title = null,
                tracks = tracks.filter { it.uri in favorites },
                favorites = favorites,
                playlists = playlists,
                onBack = null,
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylist = onAddToPlaylist,
            )
            LibraryTab.ALBUMS -> GroupList(
                icon = Icons.Default.Album,
                groups = tracks.groupBy { it.album.ifBlank { "Unknown Album" } },
                onOpen = { name, groupTracks ->
                    selectedGroupName = name
                    selectedGroup = groupTracks
                },
            )
            LibraryTab.ARTISTS -> GroupList(
                icon = Icons.Default.Person,
                groups = tracks.groupBy { it.artist.ifBlank { "Unknown Artist" } },
                onOpen = { name, groupTracks ->
                    selectedGroupName = name
                    selectedGroup = groupTracks
                },
            )
            LibraryTab.PLAYLISTS -> PlaylistList(
                playlists = playlists,
                onOpen = { selectedPlaylist = it },
                onCreate = { createDialog = true },
                onDelete = onDeletePlaylist,
            )
        }
    }

    if (createDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text(stringResource(R.string.create_playlist)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = { onCreatePlaylist(name); createDialog = false },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { createDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun TrackList(
    title: String?,
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
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                if (title != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground,
                        )
                        if (onBack != null) {
                            TextButton(onClick = onBack) { Text("← " + stringResource(R.string.close)) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // Action Bar: Count + Play All + Shuffle All
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        pluralStringResource(R.plurals.track_count, tracks.size, tracks.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )

                    if (tracks.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Play All
                            FilledTonalButton(
                                onClick = { onPlay(tracks.first(), tracks) },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Play All", style = MaterialTheme.typography.labelMedium)
                            }

                            // Shuffle All
                            Button(
                                onClick = {
                                    val shuffled = tracks.shuffled()
                                    onPlay(shuffled.first(), shuffled)
                                },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Shuffle", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No tracks found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onBackground.copy(alpha = 0.5f),
                    )
                }
            }
        } else {
            items(tracks, key = Track::uri) { track ->
                TrackRow(
                    track = track,
                    isFavorite = track.uri in favorites,
                    playlists = playlists,
                    onPlay = { onPlay(track, tracks) },
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
private fun GroupList(
    icon: ImageVector,
    groups: Map<String, List<Track>>,
    onOpen: (String, List<Track>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(groups.entries.toList(), key = { it.key }) { (name, groupTracks) ->
            GlassCard(
                Modifier.fillMaxWidth(),
                onClick = { onOpen(name, groupTracks) },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            pluralStringResource(R.plurals.track_count, groupTracks.size, groupTracks.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Open",
                        tint = colors.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
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
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Button(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.create_playlist))
            }
        }

        if (playlists.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_playlists),
                        color = colors.onBackground.copy(alpha = 0.55f),
                    )
                }
            }
        }

        items(playlists, key = PlaylistEntity::id) { playlist ->
            GlassCard(
                Modifier.fillMaxWidth(),
                onClick = { onOpen(playlist) },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                        )
                    }
                    IconButton(onClick = { onDelete(playlist) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = colors.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
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
