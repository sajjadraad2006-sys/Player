package dev.auralis.studio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.studio.R
import dev.auralis.studio.core.database.PlaylistEntity
import dev.auralis.studio.core.model.AudioPreset
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.ui.components.TrackRow

enum class HomeCategory(val titleRes: Int, val defaultLabel: String) {
    LIBRARY(R.string.library, "LIBRARY"),
    FOLDER(R.string.choose_folder, "FOLDER"),
    FAVORITE(R.string.favorites, "FAVORITE"),
    RECENT_PLAY(R.string.recently_played, "RECENT PLAY"),
    RECENT_ADD(R.string.recently_added, "RECENT ADD"),
    MOST_PLAY(R.string.songs, "MOST PLAY"),
}

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
    onPreset: (AudioPreset) -> Unit = {},
    modifier: Modifier = Modifier,
    folders: Set<String> = emptySet(),
    onCreatePlaylist: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    var selectedCategory by remember { mutableStateOf<HomeCategory?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }

    // Derived category track lists
    val favoriteTracks = remember(tracks, favorites) {
        tracks.filter { it.uri in favorites }
    }
    val historyTracks = remember(tracks, historyUris) {
        historyUris.mapNotNull { uri -> tracks.firstOrNull { it.uri == uri } }
    }
    val recentlyAddedTracks = remember(tracks) {
        tracks.sortedByDescending { it.dateAddedSeconds }.take(100)
    }
    val mostPlayedTracks = remember(tracks, historyTracks) {
        if (historyTracks.isNotEmpty()) historyTracks else tracks.take(100)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ── Top Header Bar ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (selectedCategory != null) {
                    IconButton(onClick = { selectedCategory = null }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.onBackground,
                        )
                    }
                    Text(
                        text = selectedCategory?.defaultLabel ?: "Music Player",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground,
                    )
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.onBackground,
                        )
                    }
                } else {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Settings",
                            tint = colors.onBackground,
                        )
                    }
                    Text(
                        text = "Music Player",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground,
                    )
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.onBackground,
                        )
                    }
                }
            }

            // ── Main Content Area ──────────────────────────────────────────
            if (selectedCategory == null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. Six Colorful Category Cards (2 Rows x 3 Columns)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Row 1: LIBRARY, FOLDER, FAVORITE
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CategoryGridCard(
                                    title = "LIBRARY",
                                    count = tracks.size,
                                    icon = Icons.Default.MusicNote,
                                    gradient = Brush.linearGradient(
                                        listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
                                    ),
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedCategory = HomeCategory.LIBRARY },
                                )
                                CategoryGridCard(
                                    title = "FOLDER",
                                    count = if (folders.isNotEmpty()) folders.size else 1,
                                    icon = Icons.Default.Folder,
                                    gradient = Brush.linearGradient(
                                        listOf(Color(0xFFF97316), Color(0xFFC2410C)),
                                    ),
                                    modifier = Modifier.weight(1f),
                                    onClick = { onChooseFolder() },
                                )
                                CategoryGridCard(
                                    title = "FAVORITE",
                                    count = favorites.size,
                                    icon = Icons.Default.Favorite,
                                    gradient = Brush.linearGradient(
                                        listOf(Color(0xFFF43F5E), Color(0xFFBE123C)),
                                    ),
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedCategory = HomeCategory.FAVORITE },
                                )
                            }

                            // Row 2: RECENT PLAY, RECENT ADD, MOST PLAY
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CategoryGridCard(
                                    title = "RECENT PLAY",
                                    count = historyTracks.size,
                                    icon = Icons.Default.History,
                                    gradient = Brush.linearGradient(
                                        listOf(Color(0xFF0EA5E9), Color(0xFF0369A1)),
                                    ),
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedCategory = HomeCategory.RECENT_PLAY },
                                )
                                CategoryGridCard(
                                    title = "RECENT ADD",
                                    count = recentlyAddedTracks.size,
                                    icon = Icons.Default.Update,
                                    gradient = Brush.linearGradient(
                                        listOf(Color(0xFF10B981), Color(0xFF047857)),
                                    ),
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedCategory = HomeCategory.RECENT_ADD },
                                )
                                CategoryGridCard(
                                    title = "MOST PLAY",
                                    count = mostPlayedTracks.size,
                                    icon = Icons.Default.GraphicEq,
                                    gradient = Brush.linearGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
                                    ),
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedCategory = HomeCategory.MOST_PLAY },
                                )
                            }
                        }
                    }

                    // 2. PLAYLIST Section
                    item {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "PLAYLIST (${playlists.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onBackground,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { showCreatePlaylistDialog = true },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "New Playlist",
                                            tint = colors.primary,
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { selectedCategory = HomeCategory.LIBRARY },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = "View all",
                                            tint = colors.onBackground.copy(alpha = 0.6f),
                                        )
                                    }
                                }
                            }

                            if (playlists.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.surfaceVariant.copy(alpha = 0.45f))
                                        .clickable { showCreatePlaylistDialog = true }
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.QueueMusic,
                                            contentDescription = null,
                                            tint = colors.primary,
                                            modifier = Modifier.size(36.dp),
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.create_playlist),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp),
                                ) {
                                    items(playlists) { playlist ->
                                        PlaylistCard(
                                            playlist = playlist,
                                            onClick = {
                                                selectedCategory = HomeCategory.LIBRARY
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Quick Suggestions / Recent section
                    if (tracks.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.recently_added),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onBackground,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(recentlyAddedTracks.take(6), key = { "recent_${it.id}" }) { track ->
                            TrackRow(
                                track = track,
                                isFavorite = favorites.contains(track.uri),
                                onPlay = { onPlay(track, recentlyAddedTracks) },
                                onToggleFavorite = { onToggleFavorite(track.uri) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                playlists = playlists,
                                onAddToPlaylist = { pId -> onAddToPlaylist(pId, track.uri) },
                            )
                        }
                    }
                }
            } else {
                // Category Filtered List View
                val activeTracks = when (selectedCategory) {
                    HomeCategory.LIBRARY -> tracks
                    HomeCategory.FOLDER -> tracks
                    HomeCategory.FAVORITE -> favoriteTracks
                    HomeCategory.RECENT_PLAY -> historyTracks
                    HomeCategory.RECENT_ADD -> recentlyAddedTracks
                    HomeCategory.MOST_PLAY -> mostPlayedTracks
                    null -> emptyList()
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${activeTracks.size} ${stringResource(R.string.songs)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onBackground.copy(alpha = 0.7f),
                            )
                            if (activeTracks.isNotEmpty()) {
                                TextButton(onClick = { onPlay(activeTracks.shuffled().first(), activeTracks.shuffled()) }) {
                                    Icon(
                                        Icons.Default.Shuffle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.shuffle_all))
                                }
                            }
                        }
                    }

                    if (activeTracks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.no_music_found),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onBackground.copy(alpha = 0.5f),
                                )
                            }
                        }
                    } else {
                        items(activeTracks, key = { "cat_${selectedCategory?.name}_${it.id}" }) { track ->
                            TrackRow(
                                track = track,
                                isFavorite = favorites.contains(track.uri),
                                onPlay = { onPlay(track, activeTracks) },
                                onToggleFavorite = { onToggleFavorite(track.uri) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                playlists = playlists,
                                onAddToPlaylist = { pId -> onAddToPlaylist(pId, track.uri) },
                            )
                        }
                    }
                }
            }
        }

        // ── Floating Action Button (Shuffle All) ────────────────────────────
        if (tracks.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    val shuffled = tracks.shuffled()
                    if (shuffled.isNotEmpty()) {
                        onPlay(shuffled.first(), shuffled)
                    }
                },
                containerColor = Color(0xFFFBBF24), // Vibrant gold from Screenshot 2
                contentColor = Color(0xFF1E1B4B),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
                    .size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = stringResource(R.string.shuffle_all),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }

    // ── Create Playlist Dialog ─────────────────────────────────────────────
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreatePlaylistDialog = false
                newPlaylistTitle = ""
            },
            title = { Text(stringResource(R.string.create_playlist)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPlaylistTitle.trim()
                        if (name.isNotBlank()) {
                            onCreatePlaylist(name)
                        }
                        showCreatePlaylistDialog = false
                        newPlaylistTitle = ""
                    },
                ) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreatePlaylistDialog = false
                        newPlaylistTitle = ""
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * Clean Category Card matching Screenshot 2:
 * Rounded card with gradient, count badge in top-right, centered big icon, and title at bottom.
 */
@Composable
private fun CategoryGridCard(
    title: String,
    count: Int,
    icon: ImageVector,
    gradient: Brush,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(108.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        // Count badge at top-right
        Text(
            text = count.toString(),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopEnd),
        )

        // Centered Big Icon
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier
                .size(36.dp)
                .align(Alignment.Center),
        )

        // Label at bottom
        Text(
            text = title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}

/**
 * Playlist Card matching Screenshot 2:
 * Square rounded card with music lines icon, count badge in top right, and name at the bottom.
 */
@Composable
private fun PlaylistCard(
    playlist: PlaylistEntity,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        // Top-right indicator
        Text(
            text = "•",
            color = colors.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd),
        )

        // Center Playlist Icon
        Icon(
            imageVector = Icons.Default.QueueMusic,
            contentDescription = playlist.name,
            tint = colors.primary,
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.Center),
        )

        // Bottom Title
        Text(
            text = playlist.name,
            color = colors.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}
