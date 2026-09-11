package dev.auralis.desktop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.desktop.library.DesktopTrack
import dev.auralis.desktop.storage.DesktopPlaylist
import dev.auralis.studio.core.model.HomeLayoutSettings
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable fun MusicRow(track: DesktopTrack, selected: Boolean = false, onPlay: () -> Unit,
    onFavorite: () -> Unit, onNext: () -> Unit, onQueue: () -> Unit, onPlaylist: () -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalDesktopStrings.current
    val colors = MaterialTheme.colorScheme
    var properties by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val actions = listOf(
        ContextMenuItem("Play", onPlay), ContextMenuItem("Play next", onNext), ContextMenuItem("Add to queue", onQueue),
        ContextMenuItem(if(track.isFavorite) "Remove favorite" else "Favorite", onFavorite), ContextMenuItem("Add to playlist", onPlaylist),
    ) + (if(!track.path.startsWith("http")) listOf(ContextMenuItem("Open file location") { ProcessBuilder("explorer.exe", "/select,", File(track.path).absolutePath).start(); Unit }) else emptyList()) +
        ContextMenuItem("Properties") { properties = true }
    ContextMenuArea(items = { actions.map { ContextMenuItem(strings.text(it.label), it.onClick) } }) {
        Row(modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(12.dp))
            .background(if(selected) colors.primary.copy(alpha = .1f) else colors.surface.copy(alpha = .35f))
            .combinedClickable(onClick = {}, onDoubleClick = onPlay).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TrackArtwork(track.artworkKey ?: track.path, Modifier.size(46.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(track.artist.ifBlank { "Unknown artist" }, color = colors.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            }
            Text(musicTime(track.durationMs), color = colors.onSurfaceVariant, fontSize = 12.sp)
            IconButton(onFavorite) { Icon(if(track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", tint = if(track.isFavorite) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(19.dp)) }
            IconButton(onPlay) { Icon(Icons.Default.PlayArrow, "Play", tint = colors.primary) }
            Box {
                IconButton({ more = true }) { Icon(Icons.Default.MoreHoriz, "More") }
                DropdownMenu(more, { more = false }) { actions.forEach { action -> DropdownMenuItem(text = { Text(uiText(action.label)) }, onClick = { more = false; action.onClick() }) } }
            }
        }
    }
    if(properties) AlertDialog(onDismissRequest = { properties = false }, title = { Text(track.title) },
        text = { Text("Artist: ${track.artist}\nAlbum: ${track.album}\nDuration: ${musicTime(track.durationMs)}\nFormat: ${track.mimeType}\n${track.path}") },
        confirmButton = { TextButton({ properties = false }) { Text(uiText("Done")) } })
}

fun musicTime(ms: Long) = if(ms <= 0) "—" else "${ms / 60000}:${(ms / 1000 % 60).toString().padStart(2, '0')}"

@Composable fun MusicLibrary(
    tracks: List<DesktopTrack>, onlineFavorites: List<DesktopTrack>, playlists: List<DesktopPlaylist>,
    selectedPlaylistId: Long?, currentPath: String?, scanning: Boolean,
    onPlay: (DesktopTrack, List<DesktopTrack>) -> Unit, onFavorite: (DesktopTrack) -> Unit,
    onNext: (DesktopTrack) -> Unit, onQueue: (DesktopTrack) -> Unit, onAddPlaylist: (DesktopTrack) -> Unit,
    onSavePlaylist: (DesktopPlaylist) -> Unit, onDeletePlaylist: (Long) -> Unit,
    onAddFolder: () -> Unit, onRescan: () -> Unit, savedOnline: List<DesktopTrack> = emptyList(),
) {
    var tab by remember { mutableStateOf("Songs") }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("Title") }
    var sortOpen by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf<String?>(null) }
    var playlistId by remember { mutableStateOf<Long?>(null) }
    var create by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<DesktopPlaylist?>(null) }
    LaunchedEffect(selectedPlaylistId) { if(selectedPlaylistId != null) { tab = "Playlists"; playlistId = selectedPlaylistId; group = null } }
    val playlist = playlists.firstOrNull { it.id == playlistId }
    val all = (tracks + onlineFavorites + savedOnline).distinctBy { it.path }
    var collection = when(tab) {
        "Favorites" -> (tracks.filter { it.isFavorite } + onlineFavorites).distinctBy { it.path }
        "Albums" -> if(group == null) tracks else tracks.filter { it.album.ifBlank { "Singles" } == group }
        "Artists" -> if(group == null) tracks else tracks.filter { it.artist.ifBlank { "Unknown artist" } == group }
        "Playlists" -> playlist?.trackPaths?.mapNotNull { path -> all.find { it.path == path } }.orEmpty()
        else -> tracks
    }
    collection = collection.filter { "${it.title} ${it.artist} ${it.album}".contains(query, true) }.let { list -> when(sort) {
        "Artist" -> list.sortedBy { it.artist.lowercase() }; "Album" -> list.sortedBy { it.album.lowercase() }
        "Duration" -> list.sortedBy { it.durationMs }; "Recently added" -> list.sortedByDescending { it.lastModified }
        else -> list.sortedBy { it.title.lowercase() }
    } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(group ?: playlist?.name ?: uiText("Your library"), fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onAddFolder) { Icon(Icons.Default.Add, null); Text(uiText("Add music")) }
            IconButton(onRescan, enabled = !scanning) { Icon(Icons.Default.Refresh, "Refresh library") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Songs", "Albums", "Artists", "Favorites", "Playlists").forEach { name -> FilterChip(tab == name, { tab = name; group = null; playlistId = null }, label = { Text(uiText(name)) }) }
        }
        if(group != null || playlist != null) Row {
            TextButton({ group = null; playlistId = null }) { Text(uiText("Back") + " · " + uiText(tab)) }
            if(playlist != null) { TextButton({ edit = playlist }) { Text(uiText("Rename")) }; TextButton({ onDeletePlaylist(playlist.id); playlistId = null }) { Text(uiText("Delete playlist")) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(uiText("Filter your music")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
            Box { TextButton({ sortOpen = true }) { Text(uiText(sort)); Icon(Icons.Default.ExpandMore, null) }
                DropdownMenu(sortOpen, { sortOpen = false }) { listOf("Title", "Artist", "Album", "Duration", "Recently added").forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { sort = option; sortOpen = false }) } }
            }
        }
        if(scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            tab == "Playlists" && playlist == null -> {
                Button({ create = true }) { Icon(Icons.Default.Add, null); Text(uiText("New playlist")) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(playlists) { item ->
                    ListItem(headlineContent = { Text(item.name) }, supportingContent = { Text("${item.trackPaths.size} songs") },
                        leadingContent = { Icon(Icons.Default.LibraryMusic, null) }, modifier = Modifier.clickable { playlistId = item.id })
                } }
            }
            tab in listOf("Albums", "Artists") && group == null -> {
                val groups = collection.groupBy { if(tab == "Albums") it.album.ifBlank { "Singles" } else it.artist.ifBlank { "Unknown artist" } }
                LazyVerticalGrid(GridCells.Adaptive(145.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    items(groups.keys.toList()) { name -> val songs = groups.getValue(name)
                        Column(Modifier.clickable { group = name }) {
                            TrackArtwork(songs.first().artworkKey ?: songs.first().path, Modifier.fillMaxWidth().aspectRatio(1f))
                            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                            Text("${songs.size} songs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            else -> {
                if(collection.isEmpty()) Text(uiText("No songs here yet. Add music or explore YouTube."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) { items(collection, key = { it.path }) { track ->
                    Column {
                        MusicRow(track, currentPath == track.path, { onPlay(track, collection) }, { onFavorite(track) }, { onNext(track) }, { onQueue(track) }, { onAddPlaylist(track) })
                        if(playlist != null) TextButton({ onSavePlaylist(playlist.copy(trackPaths = playlist.trackPaths - track.path)) }, modifier = Modifier.align(Alignment.End)) { Text(uiText("Remove from playlist"), fontSize = 11.sp) }
                    }
                } }
            }
        }
    }
    if(create || edit != null) {
        var name by remember { mutableStateOf(edit?.name.orEmpty()) }
        AlertDialog(onDismissRequest = { create = false; edit = null }, title = { Text(if(edit == null) "New playlist" else "Rename playlist") },
            text = { OutlinedTextField(name, { name = it }, label = { Text(uiText("Name")) }, singleLine = true) },
            confirmButton = { TextButton({ onSavePlaylist(edit?.copy(name = name.trim()) ?: DesktopPlaylist(System.currentTimeMillis(), name.trim())); create = false; edit = null }, enabled = name.isNotBlank()) { Text(uiText("Save")) } },
            dismissButton = { TextButton({ create = false; edit = null }) { Text(uiText("Cancel")) } })
    }
}

@Composable fun MusicHome(tracks: List<DesktopTrack>, recent: List<DesktopTrack>, favorites: List<DesktopTrack>, playlists: List<DesktopPlaylist>,
    layout: HomeLayoutSettings, onPlay: (DesktopTrack, List<DesktopTrack>) -> Unit, onSearch: () -> Unit, onAdd: () -> Unit, onPlaylist: (Long) -> Unit, onCategory: (String) -> Unit, resumePoint: Pair<String?, Long> = null to 0L, onResume: (DesktopTrack, Long) -> Unit = { track, _ -> onPlay(track, listOf(track)) }, playingPath: String? = null) {
    val strings = LocalDesktopStrings.current
    val colors = MaterialTheme.colorScheme
    LazyColumn(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        item {
            Text(uiText("MADE FOR YOUR MUSIC"), color = colors.primary, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(uiText("Make yourself at home."), fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(uiText("Your favorites, your sound, your next discovery."), color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onSearch) { Icon(Icons.Default.Search, null); Text(uiText("Explore YouTube")) }
                if(tracks.isNotEmpty() && layout.showFloatingShuffle) FilledTonalButton({ val shuffled = tracks.shuffled(); onPlay(shuffled.first(), shuffled) }) { Icon(Icons.Default.Shuffle, null); Text(uiText("Shuffle all")) }
                TextButton(onAdd) { Text(uiText("Add music")) }
            }
        }
        val resume = (recent + tracks).firstOrNull { it.path == resumePoint.first }
        if(layout.showRecentlyPlayed && resume != null && resume.path != playingPath && resumePoint.second >= 3000 && resume.durationMs > resumePoint.second + 5000) item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.primary.copy(alpha = .08f)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TrackArtwork(resume.artworkKey ?: resume.path, Modifier.size(68.dp))
                Column(Modifier.weight(1f)) {
                    Text(uiText("Pick up where you left off"), color = colors.primary, fontSize = 12.sp)
                    Text(resume.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(resume.artist, color = colors.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                }
                Button({ onResume(resume, resumePoint.second) }) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text(uiText("Resume") + " · " + musicTime(resumePoint.second))
                }
            }
        }
        if(layout.showQuickCategories) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Arabic", "Rock", "Lo-Fi", "Electronic", "Pop").forEach { mood ->
                    SuggestionChip(onClick = { onCategory(mood) }, label = { Text(uiText(mood)) })
                }
            }
        }
        val sections = listOf(
            "Recently played" to if(layout.showRecentlyPlayed) recent.take(12) else emptyList(),
            "Recently added" to if(layout.showRecentTracks) tracks.sortedByDescending { it.lastModified }.take(16) else emptyList(),
            "Your favorites" to favorites.take(16),
        )
        sections.filter { it.second.isNotEmpty() }.forEach { (title, songs) -> item {
            Text(uiText(title), fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 14.dp)) { items(songs) { track ->
                Column(Modifier.width(if(layout.style == dev.auralis.studio.core.model.HomeLayoutStyle.MINIMAL_STREAM) 116.dp else 156.dp).clickable { onPlay(track, songs) }) {
                    TrackArtwork(track.artworkKey ?: track.path, Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                    Text(track.artist, color = colors.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } }
        } }
        if(layout.showPlaylists && playlists.isNotEmpty()) item {
            Text(uiText("Made by you"), fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(playlists) { playlist ->
                FilledTonalButton({ onPlaylist(playlist.id) }) { Icon(Icons.Default.LibraryMusic, null); Spacer(Modifier.width(8.dp)); Text(playlist.name) }
            } }
        }
    }
}
