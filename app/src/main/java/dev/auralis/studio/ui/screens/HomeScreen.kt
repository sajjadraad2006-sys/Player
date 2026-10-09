package dev.auralis.studio.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import java.util.Calendar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import dev.auralis.studio.core.model.HomeLayoutStyle
import dev.auralis.studio.core.model.HomeLayoutSettings
import dev.auralis.studio.R
import dev.auralis.studio.core.database.PlaylistEntity
import dev.auralis.studio.core.model.AudioPreset
import dev.auralis.studio.core.model.Track
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.SectionHeader
import dev.auralis.studio.ui.components.TrackArtwork
import dev.auralis.studio.ui.components.TrackRow

enum class HomeCategory(val titleRes: Int) {
    SONGS(R.string.filter_songs),
    PLAYLISTS(R.string.filter_playlists),
    FAVORITES(R.string.filter_favorites),
    RECENT(R.string.filter_recent),
    FOLDERS(R.string.filter_folders),
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
    onDeletePlaylist: (PlaylistEntity) -> Unit = {},
    onRemovePlaylistTrack: (Long, String) -> Unit = { _, _ -> },
    playlistTrackUris: (Long) -> Flow<List<String>> = { flowOf(emptyList()) },
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    layoutSettings: HomeLayoutSettings = HomeLayoutSettings(),
    onUpdateLayoutSettings: (HomeLayoutSettings) -> Unit = {},
    recommendations: List<Track> = emptyList(),
) {
    val colors = MaterialTheme.colorScheme
    var selectedCategory by remember { mutableStateOf<HomeCategory?>(null) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showCustomizeDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }

    val selectedPlaylistUrisFlow = remember(selectedPlaylist?.id) {
        selectedPlaylist?.let { playlistTrackUris(it.id) } ?: flowOf(emptyList())
    }
    val selectedPlaylistUris by selectedPlaylistUrisFlow.collectAsState(initial = emptyList())
    val selectedPlaylistTracks = remember(selectedPlaylistUris, tracks) {
        selectedPlaylistUris.mapNotNull { uri -> tracks.firstOrNull { it.uri == uri } }
    }

    val greetingRes = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> R.string.good_morning
            in 12..17 -> R.string.good_afternoon
            else -> R.string.good_evening
        }
    }

    // Intercept system Back when viewing a playlist or filtered category
    BackHandler(enabled = selectedPlaylist != null || selectedCategory != null) {
        if (selectedPlaylist != null) {
            selectedPlaylist = null
        } else {
            selectedCategory = null
        }
    }

    // Derived category track lists memoized for performance
    val favoriteTracks = remember(tracks, favorites) {
        tracks.filter { it.uri in favorites }
    }
    val historyTracks = remember(tracks, historyUris) {
        historyUris.mapNotNull { uri -> tracks.firstOrNull { it.uri == uri } }
    }
    val recentlyAddedTracks = remember(tracks) {
        tracks.sortedByDescending { it.dateAddedSeconds }.take(100)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (selectedPlaylist != null) {
            PlaylistDetailView(
                playlist = selectedPlaylist!!,
                tracks = selectedPlaylistTracks,
                favorites = favorites,
                playlists = playlists,
                onBack = { selectedPlaylist = null },
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylist = onAddToPlaylist,
                onRemoveFromPlaylist = { uri -> onRemovePlaylistTrack(selectedPlaylist!!.id, uri) },
                onDeletePlaylist = {
                    val pl = selectedPlaylist!!
                    selectedPlaylist = null
                    onDeletePlaylist(pl)
                },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                // ── Top Action Bar (Branding + Instant Search + Direct Settings) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (selectedCategory != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedCategory = null }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = colors.onBackground,
                            )
                        }
                        Text(
                            text = stringResource(selectedCategory!!.titleRes),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground,
                        )
                    }
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = colors.onBackground,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(colors.primary, colors.secondary),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = colors.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.onBackground,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.search),
                                tint = colors.onBackground,
                            )
                        }
                        IconButton(onClick = onNavigateToLibrary) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = stringResource(R.string.library),
                                tint = colors.onBackground,
                            )
                        }
                        IconButton(onClick = { showCustomizeDialog = true }) {
                            Icon(
                                Icons.Default.Dashboard,
                                contentDescription = stringResource(R.string.customize_home),
                                tint = colors.onBackground,
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = colors.onBackground,
                            )
                        }
                    }
                }
            }

            // ── Fast Navigation Filter Chips ────────────────────────────────
            if (layoutSettings.showFilterChips) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text(stringResource(R.string.home)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == HomeCategory.SONGS,
                            onClick = { selectedCategory = HomeCategory.SONGS },
                            label = { Text("${stringResource(R.string.all_songs)} (${tracks.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == HomeCategory.FAVORITES,
                            onClick = { selectedCategory = HomeCategory.FAVORITES },
                            label = { Text("${stringResource(R.string.favorites)} (${favoriteTracks.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == HomeCategory.PLAYLISTS,
                            onClick = { selectedCategory = HomeCategory.PLAYLISTS },
                            label = { Text("${stringResource(R.string.filter_playlists)} (${playlists.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == HomeCategory.RECENT,
                            onClick = { selectedCategory = HomeCategory.RECENT },
                            label = { Text(stringResource(R.string.filter_recent)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == HomeCategory.FOLDERS,
                            onClick = { selectedCategory = HomeCategory.FOLDERS },
                            label = { Text(stringResource(R.string.filter_folders)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Main Content Area ──────────────────────────────────────────
            if (selectedCategory == null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ── Dynamic Greeting Header Banner ──
                    if (layoutSettings.showGreetingHeader) {
                        item(key = "greeting_banner") {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(greetingRes),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.onSurface,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "${tracks.size} ${stringResource(R.string.all_songs)} · ${favoriteTracks.size} ${stringResource(R.string.favorites)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }

                                    if (tracks.isNotEmpty()) {
                                        FilledIconButton(
                                            onClick = {
                                                val shuffled = tracks.shuffled()
                                                shuffled.firstOrNull()?.let { onPlay(it, shuffled) }
                                            },
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = colors.primary,
                                                contentColor = colors.onPrimary,
                                            ),
                                        ) {
                                            Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.shuffle_all))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    when (layoutSettings.style) {
                        HomeLayoutStyle.GRID_CARDS -> {
                            // ── Classic 6-Card Category Grid (User Requested Screenshot Style) ──
                            if (layoutSettings.showQuickCategories) {
                                item(key = "classic_six_grid") {
                                    ClassicSixCardGrid(
                                        tracksCount = tracks.size,
                                        foldersCount = folders.size.coerceAtLeast(1),
                                        favoritesCount = favoriteTracks.size,
                                        recentPlayCount = historyTracks.size,
                                        recentAddCount = recentlyAddedTracks.size,
                                        mostPlayCount = historyTracks.size.coerceAtLeast(tracks.size.coerceAtMost(25)),
                                        onSelectCategory = { selectedCategory = it },
                                    )
                                }
                            }
                        }
                        HomeLayoutStyle.STUDIO_HUB -> {
                            // ── Modern Studio Hub Header Card ──
                            if (layoutSettings.showQuickCategories) {
                                item(key = "hub_card") {
                                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(
                                                            colors.primary.copy(alpha = 0.15f),
                                                            colors.secondary.copy(alpha = 0.10f),
                                                        ),
                                                    ),
                                                )
                                                .padding(16.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        text = stringResource(R.string.app_name),
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.onSurface,
                                                    )
                                                    Text(
                                                        text = "${tracks.size} ${stringResource(R.string.all_songs)} · ${playlists.size} ${stringResource(R.string.filter_playlists)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.onSurfaceVariant,
                                                    )
                                                }

                                                if (tracks.isNotEmpty()) {
                                                    Button(
                                                        onClick = {
                                                            val shuffled = tracks.shuffled()
                                                            shuffled.firstOrNull()?.let { onPlay(it, shuffled) }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = colors.primary,
                                                            contentColor = colors.onPrimary,
                                                        ),
                                                        shape = RoundedCornerShape(16.dp),
                                                    ) {
                                                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(stringResource(R.string.shuffle_all))
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = onChooseFolder,
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = colors.primary,
                                                            contentColor = colors.onPrimary,
                                                        ),
                                                        shape = RoundedCornerShape(16.dp),
                                                    ) {
                                                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(stringResource(R.string.choose_folder))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HomeLayoutStyle.VINYL_TURNTABLE -> {
                            item(key = "vinyl_turntable_deck") {
                                VinylTurntableDeck(
                                    currentTrack = currentTrack ?: tracks.firstOrNull(),
                                    tracks = tracks,
                                    onPlay = onPlay,
                                    onChooseFolder = onChooseFolder,
                                )
                            }
                        }
                        HomeLayoutStyle.IMMERSIVE_CAROUSEL -> {
                            item(key = "immersive_carousel_deck") {
                                ImmersiveCarouselDeck(
                                    tracks = if (favoriteTracks.isNotEmpty()) favoriteTracks else tracks,
                                    allTracks = tracks,
                                    onPlay = onPlay,
                                )
                            }
                        }
                        HomeLayoutStyle.MINIMAL_STREAM -> {
                            // Direct tracks list below
                        }
                    }

                    // ── Playlists Section ─────────────────────────────────────
                    if (layoutSettings.showPlaylists && layoutSettings.style != HomeLayoutStyle.MINIMAL_STREAM) {
                        item(key = "playlists_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${stringResource(R.string.filter_playlists)} (${playlists.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onBackground,
                                )
                                IconButton(onClick = { showCreatePlaylistDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "Create Playlist", tint = colors.primary)
                                }
                            }
                        }

                        item(key = "playlists_carousel") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 130.dp, height = 90.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(colors.surfaceVariant.copy(alpha = 0.4f))
                                            .border(1.dp, colors.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                            .clickable { showCreatePlaylistDialog = true },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                                            Spacer(Modifier.height(4.dp))
                                            Text(stringResource(R.string.create_playlist), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                        }
                                    }
                                }

                                items(playlists, key = { it.id }) { pl ->
                                    Box(
                                        modifier = Modifier
                                            .size(width = 130.dp, height = 90.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(colors.surfaceVariant.copy(alpha = 0.6f))
                                            .border(1.dp, colors.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                            .clickable { selectedPlaylist = pl },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(8.dp),
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                                            Spacer(Modifier.height(4.dp))
                                            Text(pl.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Recently Played Section ──────────────────────────────
                    if (layoutSettings.showRecentlyPlayed && historyTracks.isNotEmpty() && layoutSettings.style != HomeLayoutStyle.MINIMAL_STREAM) {
                        item(key = "recently_played_header") {
                            Text(
                                text = stringResource(R.string.recently_played),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onBackground,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }

                        item(key = "recently_played_carousel") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(historyTracks.take(8), key = { "rec_${it.id}_${it.uri}" }) { trk ->
                                    Column(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onPlay(trk, historyTracks) },
                                    ) {
                                        TrackArtwork(trk, Modifier.size(100.dp))
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            trk.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = colors.onSurface,
                                        )
                                        Text(
                                            trk.artist,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = colors.onSurfaceVariant,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Smart Recommendations Section ─────────────────────────
                    if (layoutSettings.showRecommendations && recommendations.isNotEmpty() && layoutSettings.style != HomeLayoutStyle.MINIMAL_STREAM) {
                        item(key = "recommendations_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.recommended_for_you),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onBackground,
                                    )
                                    currentTrack?.let { ct ->
                                        Text(
                                            text = stringResource(R.string.based_on_track, ct.title),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                TextButton(onClick = {
                                    recommendations.firstOrNull()?.let { onPlay(it, recommendations) }
                                }) {
                                    Text(stringResource(R.string.play_all), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        item(key = "recommendations_carousel") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(recommendations, key = { "recom_${it.id}_${it.uri}" }) { trk ->
                                    Column(
                                        modifier = Modifier
                                            .width(115.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.surfaceVariant.copy(alpha = 0.35f))
                                            .clickable { onPlay(trk, recommendations) }
                                            .padding(8.dp),
                                    ) {
                                        TrackArtwork(trk, Modifier.size(99.dp).clip(RoundedCornerShape(10.dp)))
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            trk.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = colors.onSurface,
                                        )
                                        Text(
                                            trk.artist,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = colors.onSurfaceVariant,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Songs List Section ────────────────────────────────────
                    if (layoutSettings.showRecentTracks) {
                        item(key = "recently_added_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (layoutSettings.style == HomeLayoutStyle.MINIMAL_STREAM) stringResource(R.string.all_songs) else stringResource(R.string.recently_added),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onBackground,
                                )
                                TextButton(onClick = { selectedCategory = HomeCategory.SONGS }) {
                                    Text(stringResource(R.string.all_songs), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        if (recentlyAddedTracks.isEmpty()) {
                            item(key = "empty_tracks") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            stringResource(R.string.library_empty_permission),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Button(onClick = onChooseFolder) {
                                            Text(stringResource(R.string.choose_folder))
                                        }
                                    }
                                }
                            }
                        } else {
                            val displayList = if (layoutSettings.style == HomeLayoutStyle.MINIMAL_STREAM) tracks else recentlyAddedTracks.take(40)
                            items(
                                items = displayList,
                                key = { "home_${it.id}_${it.uri}" },
                                contentType = { "track_row" },
                            ) { track ->
                                TrackRow(
                                    track = track,
                                    isFavorite = track.uri in favorites,
                                    playlists = playlists,
                                    onPlay = { onPlay(track, displayList) },
                                    onToggleFavorite = { onToggleFavorite(track.uri) },
                                    onPlayNext = { onPlayNext(track) },
                                    onAddToQueue = { onAddToQueue(track) },
                                    onAddToPlaylist = { pId -> onAddToPlaylist(pId, track.uri) },
                                )
                            }
                        }
                    }
                }
            } else if (selectedCategory == HomeCategory.PLAYLISTS) {
                PlaylistsListView(
                    playlists = playlists,
                    onCreatePlaylist = { showCreatePlaylistDialog = true },
                    onSelectPlaylist = { selectedPlaylist = it },
                    onDeletePlaylist = onDeletePlaylist,
                )
            } else {
                // ── Filtered Category View (Songs, Favorites, Playlists, Folders, Recent) ──
                val categoryTracks = when (selectedCategory) {
                    HomeCategory.SONGS -> tracks
                    HomeCategory.FAVORITES -> favoriteTracks
                    HomeCategory.RECENT -> historyTracks
                    HomeCategory.FOLDERS -> tracks
                    else -> emptyList()
                }

                if (selectedCategory == HomeCategory.FOLDERS) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Button(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.choose_folder))
                            }
                        }
                        if (folders.isEmpty()) {
                            item {
                                Text(stringResource(R.string.no_extra_folders), color = colors.onSurfaceVariant)
                            }
                        } else {
                            items(folders.toList(), key = { it }) { fld ->
                                GlassCard(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = colors.primary)
                                        Spacer(Modifier.width(10.dp))
                                        Text(fld.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurface)
                                    }
                                }
                            }
                        }
                    }
                } else if (categoryTracks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.search_empty),
                            color = colors.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(categoryTracks, key = { "cat_${it.uri}" }) { track ->
                            TrackRow(
                                track = track,
                                isFavorite = track.uri in favorites,
                                playlists = playlists,
                                onPlay = { onPlay(track, categoryTracks) },
                                onToggleFavorite = { onToggleFavorite(track.uri) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                onAddToPlaylist = { pId -> onAddToPlaylist(pId, track.uri) },
                            )
                        }
                    }
                }
            }
        }

            // Floating Shuffle Action (matching user screenshot with vibrant yellow FAB)
            if (selectedPlaylist == null && selectedCategory == null && tracks.size > 1 && layoutSettings.showFloatingShuffle) {
                FloatingActionButton(
                    onClick = {
                        val shuffled = tracks.shuffled()
                        shuffled.firstOrNull()?.let { onPlay(it, shuffled) }
                    },
                    containerColor = Color(0xFFFACC15),
                    contentColor = Color(0xFF1E293B),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 90.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.shuffle_all))
                }
            }
        }
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(stringResource(R.string.create_playlist)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPlaylistTitle.trim()
                        if (trimmed.isNotBlank()) {
                            onCreatePlaylist(trimmed)
                            newPlaylistTitle = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    enabled = newPlaylistTitle.isNotBlank(),
                ) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Customize Home Dialog
    if (showCustomizeDialog) {
        CustomizeHomeDialog(
            settings = layoutSettings,
            onUpdate = onUpdateLayoutSettings,
            onDismiss = { showCustomizeDialog = false },
        )
    }
}

@Composable
private fun CustomizeHomeDialog(
    settings: HomeLayoutSettings,
    onUpdate: (HomeLayoutSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.customize_home),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.home_layout),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HomeLayoutStyle.entries.forEach { style ->
                            val selected = settings.style == style
                            val title = when (style) {
                                HomeLayoutStyle.STUDIO_HUB -> stringResource(R.string.layout_studio_hub)
                                HomeLayoutStyle.GRID_CARDS -> stringResource(R.string.layout_grid_cards)
                                HomeLayoutStyle.MINIMAL_STREAM -> stringResource(R.string.layout_minimal_stream)
                                HomeLayoutStyle.VINYL_TURNTABLE -> stringResource(R.string.layout_vinyl_turntable)
                                HomeLayoutStyle.IMMERSIVE_CAROUSEL -> stringResource(R.string.layout_immersive_carousel)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (selected) colors.primary else colors.outline.copy(alpha = 0.2f),
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { onUpdate(settings.copy(style = style)) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) colors.primary else colors.onSurface,
                                )
                            }
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                item {
                    Text(
                        stringResource(R.string.home_layout_desc),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                }
                item {
                    HomeToggleRow(
                        title = stringResource(R.string.show_quick_categories),
                        checked = settings.showQuickCategories,
                        onCheckedChange = { onUpdate(settings.copy(showQuickCategories = it)) },
                    )
                }
                item {
                    HomeToggleRow(
                        title = stringResource(R.string.show_playlists),
                        checked = settings.showPlaylists,
                        onCheckedChange = { onUpdate(settings.copy(showPlaylists = it)) },
                    )
                }
                item {
                    HomeToggleRow(
                        title = stringResource(R.string.show_recently_played),
                        checked = settings.showRecentlyPlayed,
                        onCheckedChange = { onUpdate(settings.copy(showRecentlyPlayed = it)) },
                    )
                }
                item {
                    HomeToggleRow(
                        title = stringResource(R.string.show_recent_tracks),
                        checked = settings.showRecentTracks,
                        onCheckedChange = { onUpdate(settings.copy(showRecentTracks = it)) },
                    )
                }
                item {
                    HomeToggleRow(
                        title = stringResource(R.string.show_filter_chips),
                        checked = settings.showFilterChips,
                        onCheckedChange = { onUpdate(settings.copy(showFilterChips = it)) },
                    )
                }
                item {
                    HomeToggleRow(
                        title = stringResource(R.string.show_floating_shuffle),
                        checked = settings.showFloatingShuffle,
                        onCheckedChange = { onUpdate(settings.copy(showFloatingShuffle = it)) },
                    )
                }
                item {
                    HomeToggleRow(
                        title = stringResource(R.string.show_recommendations),
                        checked = settings.showRecommendations,
                        onCheckedChange = { onUpdate(settings.copy(showRecommendations = it)) },
                    )
                }
                item {
                    HomeToggleRow(
                        title = stringResource(R.string.show_greeting_header),
                        checked = settings.showGreetingHeader,
                        onCheckedChange = { onUpdate(settings.copy(showGreetingHeader = it)) },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_save))
            }
        },
    )
}

@Composable
private fun HomeToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ClassicSixCardGrid(
    tracksCount: Int,
    foldersCount: Int,
    favoritesCount: Int,
    recentPlayCount: Int,
    recentAddCount: Int,
    mostPlayCount: Int,
    onSelectCategory: (HomeCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Row 1: Library, Folder, Favorite
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryTile(
                title = stringResource(R.string.category_library),
                count = tracksCount,
                icon = Icons.Default.MusicNote,
                backgroundColor = Color(0xFF3B82F6),
                onClick = { onSelectCategory(HomeCategory.SONGS) },
                modifier = Modifier.weight(1f),
            )
            CategoryTile(
                title = stringResource(R.string.category_folder),
                count = foldersCount,
                icon = Icons.Default.Folder,
                backgroundColor = Color(0xFFF97316),
                onClick = { onSelectCategory(HomeCategory.FOLDERS) },
                modifier = Modifier.weight(1f),
            )
            CategoryTile(
                title = stringResource(R.string.category_favorite),
                count = favoritesCount,
                icon = Icons.Default.Favorite,
                backgroundColor = Color(0xFFEC4899),
                onClick = { onSelectCategory(HomeCategory.FAVORITES) },
                modifier = Modifier.weight(1f),
            )
        }

        // Row 2: Recent Play, Recent Add, Most Play
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryTile(
                title = stringResource(R.string.category_recent_play),
                count = recentPlayCount,
                icon = Icons.Default.Schedule,
                backgroundColor = Color(0xFF06B6D4),
                onClick = { onSelectCategory(HomeCategory.RECENT) },
                modifier = Modifier.weight(1f),
            )
            CategoryTile(
                title = stringResource(R.string.category_recent_add),
                count = recentAddCount,
                icon = Icons.Default.History,
                backgroundColor = Color(0xFF10B981),
                onClick = { onSelectCategory(HomeCategory.SONGS) },
                modifier = Modifier.weight(1f),
            )
            CategoryTile(
                title = stringResource(R.string.category_most_play),
                count = mostPlayCount,
                icon = Icons.Default.Equalizer,
                backgroundColor = Color(0xFFA855F7),
                onClick = { onSelectCategory(HomeCategory.RECENT) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryTile(
    title: String,
    count: Int,
    icon: ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(115.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // Count badge in top right
        Text(
            text = "$count",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd),
        )

        // Centered large icon
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier
                .size(38.dp)
                .align(Alignment.Center),
        )

        // Bottom title uppercase
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun VinylTurntableDeck(
    currentTrack: Track?,
    tracks: List<Track>,
    onPlay: (Track, List<Track>) -> Unit,
    onChooseFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "home_turntable_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
        ),
        label = "home_turntable_rot",
    )

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.vinyl_record),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "33 1/3 RPM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Vinyl Record Disc
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .graphicsLayer { rotationZ = if (currentTrack != null) rotation else 0f },
                contentAlignment = Alignment.Center,
            ) {
                // Outer Black Disc with Micro Grooves Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    drawCircle(
                        color = Color(0xFF141417),
                        radius = radius,
                    )
                    // Concentric Groove Rings
                    for (i in 1..8) {
                        drawCircle(
                            color = Color(0xFF28282C).copy(alpha = 0.45f),
                            radius = radius * (0.35f + (i * 0.075f)),
                            style = Stroke(width = 1.2f),
                        )
                    }
                }

                // Inner Artwork Label
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(2.dp, colors.primary.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (currentTrack != null) {
                        TrackArtwork(currentTrack, Modifier.fillMaxSize())
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    // Center Spindle Hole
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF252528))
                            .border(1.5.dp, Color(0xFFE2E8F0), CircleShape),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (currentTrack != null) {
                Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = currentTrack.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onPlay(currentTrack, tracks) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.play))
                }
            } else {
                Button(onClick = onChooseFolder) {
                    Text(stringResource(R.string.choose_folder))
                }
            }
        }
    }
}

@Composable
private fun ImmersiveCarouselDeck(
    tracks: List<Track>,
    allTracks: List<Track>,
    onPlay: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) return
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.category_favorite),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(tracks.take(10), key = { "carousel_${it.id}_${it.uri}" }) { trk ->
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onPlay(trk, allTracks) },
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(20.dp)),
                        ) {
                            TrackArtwork(trk, Modifier.fillMaxSize())
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.7f),
                                            ),
                                        ),
                                    ),
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = colors.onPrimary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = trk.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.onSurface,
                        )
                        Text(
                            text = trk.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsListView(
    playlists: List<PlaylistEntity>,
    onCreatePlaylist: () -> Unit,
    onSelectPlaylist: (PlaylistEntity) -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_playlist_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        playlistToDelete?.let(onDeletePlaylist)
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Button(
                onClick = onCreatePlaylist,
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
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_playlists),
                        color = colors.onBackground.copy(alpha = 0.55f),
                    )
                }
            }
        } else {
            items(playlists, key = { it.id }) { pl ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPlaylist(pl) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                pl.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                            )
                        }
                        IconButton(onClick = { playlistToDelete = pl }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.delete),
                                tint = colors.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.open),
                            tint = colors.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: PlaylistEntity,
    tracks: List<Track>,
    favorites: Set<String>,
    playlists: List<PlaylistEntity>,
    onBack: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Long, String) -> Unit,
    onRemoveFromPlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_playlist_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePlaylist()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = colors.onSurface,
                )
            }
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                color = colors.onSurface,
            )
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.delete),
                    tint = colors.error.copy(alpha = 0.85f),
                )
            }
        }

        // Playlist Hero Banner Card
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(colors.primary, colors.secondary),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pluralStringResource(R.plurals.track_count, tracks.size, tracks.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { if (tracks.isNotEmpty()) onPlay(tracks.first(), tracks) },
                            enabled = tracks.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.play), fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = { if (tracks.isNotEmpty()) onPlay(tracks.shuffled().first(), tracks.shuffled()) },
                            enabled = tracks.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.shuffle), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Tracks List
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.no_tracks_in_playlist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(tracks, key = { "pl_${playlist.id}_${it.uri}" }) { track ->
                    TrackRow(
                        track = track,
                        isFavorite = track.uri in favorites,
                        playlists = playlists,
                        onPlay = { onPlay(track, tracks) },
                        onToggleFavorite = { onToggleFavorite(track.uri) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onAddToPlaylist = { pId -> onAddToPlaylist(pId, track.uri) },
                        onRemove = { onRemoveFromPlaylist(track.uri) },
                    )
                }
            }
        }
    }
}



