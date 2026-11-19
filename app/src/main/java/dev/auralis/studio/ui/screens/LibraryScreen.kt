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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import dev.auralis.studio.R
import dev.auralis.studio.core.database.DownloadEntity
import dev.auralis.studio.core.database.DownloadStatus
import dev.auralis.studio.core.database.PlaylistEntity
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.TrackRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private enum class LibraryTab { SONGS, DOWNLOADS, ALBUMS, ARTISTS, FAVORITES, PLAYLISTS }

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
    completedDownloads: List<DownloadEntity> = emptyList(),
    onDeleteDownload: ((Track) -> Unit)? = null,
    playbackQueue: List<MediaItem> = emptyList(),
    currentQueueIndex: Int = 0,
    onMoveQueueItem: ((Int, Int) -> Unit)? = null,
    onQueueIndex: ((Int) -> Unit)? = null,
    onRemoveQueueItem: ((Int) -> Unit)? = null,
    currentTrack: Track? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedGroup by remember { mutableStateOf<List<Track>?>(null) }
    var selectedGroupName by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<Track?>(null) }
    val tabs = LibraryTab.entries
    val colors = MaterialTheme.colorScheme
    val unknownAlbum = stringResource(R.string.unknown_album)
    val unknownArtist = stringResource(R.string.unknown_artist)

    val selectedUrisFlow = remember(selectedPlaylist?.id) {
        selectedPlaylist?.let { playlistTrackUris(it.id) } ?: flowOf(emptyList())
    }
    val selectedPlaylistUris by selectedUrisFlow.collectAsState(initial = emptyList())

    BackHandler(enabled = selectedGroup != null || selectedPlaylist != null) {
        selectedGroup = null
        selectedGroupName = null
        selectedPlaylist = null
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (onBack != null) 4.dp else 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = colors.onBackground,
                    )
                }
            }
            Text(
                stringResource(R.string.library),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.onBackground,
            )
        }

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
                currentTrack = currentTrack,
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
                currentTrack = currentTrack,
                onBack = null,
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylist = onAddToPlaylist,
            )
            LibraryTab.DOWNLOADS -> {
                val downloadedUris = remember(completedDownloads) {
                    completedDownloads
                        .filter { it.status == DownloadStatus.COMPLETED.name }
                        .flatMap { listOf(it.trackUri, it.destinationPath) }
                        .filter { it.isNotBlank() }
                        .toSet()
                }
                val downloadedTracks = remember(tracks, downloadedUris) {
                    tracks.filter { it.uri in downloadedUris }
                }
                TrackList(
                    title = null,
                    tracks = downloadedTracks,
                    favorites = favorites,
                    playlists = playlists,
                    currentTrack = currentTrack,
                    onBack = null,
                    onPlay = onPlay,
                    onToggleFavorite = onToggleFavorite,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onDeleteTrack = if (onDeleteDownload != null) { track -> trackToDelete = track } else null,
                    headerContent = if (playbackQueue.isNotEmpty() && onMoveQueueItem != null) {
                        {
                            DownloadsQueueSection(
                                queue = playbackQueue,
                                currentIndex = currentQueueIndex,
                                tracks = tracks,
                                onMoveQueueItem = onMoveQueueItem,
                                onQueueIndex = onQueueIndex,
                                onRemoveQueueItem = onRemoveQueueItem,
                            )
                        }
                    } else null,
                )
            }
            LibraryTab.FAVORITES -> TrackList(
                title = null,
                tracks = tracks.filter { it.uri in favorites },
                favorites = favorites,
                playlists = playlists,
                currentTrack = currentTrack,
                onBack = null,
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylist = onAddToPlaylist,
            )
            LibraryTab.ALBUMS -> GroupList(
                icon = Icons.Default.Album,
                groups = tracks.groupBy { it.album.ifBlank { unknownAlbum } },
                onOpen = { name, groupTracks ->
                    selectedGroupName = name
                    selectedGroup = groupTracks
                },
            )
            LibraryTab.ARTISTS -> GroupList(
                icon = Icons.Default.Person,
                groups = tracks.groupBy { it.artist.ifBlank { unknownArtist } },
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

    if (trackToDelete != null) {
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.remove_download_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        trackToDelete?.let { track ->
                            onDeleteDownload?.invoke(track)
                        }
                        trackToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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
    currentTrack: Track? = null,
    onDeleteTrack: ((Track) -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (headerContent != null) {
            item(key = "tracklist_header_content") {
                headerContent()
            }
        }
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
                            TextButton(onClick = onBack) { Text(stringResource(R.string.close)) }
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
                                Text(stringResource(R.string.play_all), style = MaterialTheme.typography.labelMedium)
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
                                Text(stringResource(R.string.shuffle), style = MaterialTheme.typography.labelMedium)
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
                        stringResource(R.string.no_music_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onBackground.copy(alpha = 0.5f),
                    )
                }
            }
        } else {
            items(tracks, key = Track::uri) { track ->
                TrackRow(
                    track = track,
                    isPlaying = currentTrack?.uri == track.uri,
                    isFavorite = track.uri in favorites,
                    playlists = playlists,
                    onPlay = { onPlay(track, tracks) },
                    onToggleFavorite = { onToggleFavorite(track.uri) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylist(it, track.uri) },
                    onRemove = onDeleteTrack?.let { deleteFn -> { deleteFn(track) } },
                    removeLabel = if (onDeleteTrack != null) stringResource(R.string.delete) else null,
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
                        contentDescription = stringResource(R.string.open),
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
        LibraryTab.DOWNLOADS -> R.string.downloads
        LibraryTab.ALBUMS -> R.string.albums
        LibraryTab.ARTISTS -> R.string.artists
        LibraryTab.FAVORITES -> R.string.favorites
        LibraryTab.PLAYLISTS -> R.string.playlists
    },
)

@Composable
private fun DownloadsQueueSection(
    queue: List<MediaItem>,
    currentIndex: Int,
    tracks: List<Track>,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onQueueIndex: ((Int) -> Unit)?,
    onRemoveQueueItem: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(true) }

    val upcomingItems = remember(queue, currentIndex) {
        queue.mapIndexed { index, item -> index to item }
            .filter { (index, _) -> index >= currentIndex }
    }

    if (upcomingItems.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.upcoming_queue) + " (${upcomingItems.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }

        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                upcomingItems.forEach { (index, item) ->
                    val isCurrent = index == currentIndex
                    val matchingTrack = tracks.firstOrNull { it.uri == item.mediaId }
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onQueueIndex?.invoke(index) },
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
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) colors.primary else colors.onSurfaceVariant,
                                modifier = Modifier.width(24.dp),
                            )
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                                Text(
                                    item.mediaMetadata.title?.toString() ?: matchingTrack?.title.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isCurrent) colors.primary else colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    item.mediaMetadata.artist?.toString() ?: matchingTrack?.artist.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            // Reordering controls
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onMoveQueueItem(index, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.move_up),
                                        tint = if (index > 0) colors.onSurface else colors.onSurface.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveQueueItem(index, index + 1) },
                                    enabled = index < queue.size - 1,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.move_down),
                                        tint = if (index < queue.size - 1) colors.onSurface else colors.onSurface.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                if (onRemoveQueueItem != null) {
                                    IconButton(
                                        onClick = { onRemoveQueueItem(index) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.remove_from_queue),
                                            tint = colors.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp),
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
