package dev.auralis.desktop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.desktop.online.DesktopSearchCoordinator
import dev.auralis.studio.online.OnlineTrack

@Composable
fun OnlineScreen(
    searchCoordinator: DesktopSearchCoordinator,
    onPlayTrack: (OnlineTrack) -> Unit,
    history: List<String>,
    onRememberSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
    onQueue: (OnlineTrack) -> Unit,
    onFavorite: (OnlineTrack) -> Unit,
    favorites: Set<String>,
    selectedUrl: String?,
    isBuffering: Boolean,
    focusVersion: Int,
    accentColor: Color,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier,
    onPlayNext: (OnlineTrack) -> Unit = onQueue,
    onPlaylist: (OnlineTrack) -> Unit = {},
) {
    val strings = LocalDesktopStrings.current
    val state by searchCoordinator.state.collectAsState()
    var query by remember { mutableStateOf(state.query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusVersion) { if(focusVersion > 0) focus.requestFocus() }
    fun search(value: String) { query = value; if(value.isNotBlank()) { onRememberSearch(value.trim()); searchCoordinator.search(value) } }
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(uiText("FIND YOUR NEXT FAVORITE"), color = accentColor, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text(uiText("Search YouTube"), color = textPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                placeholder = { Text(uiText("Songs, artists, or a mood…")) }, leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { IconButton({ search(query) }) { Icon(Icons.Default.ArrowForward, "Search") } },
                shape = RoundedCornerShape(18.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search(query) }))
        }
        if (history.isNotEmpty() && state.results.isEmpty()) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Recent searches"), color = textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClearHistory) { Text(uiText("Clear")) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                history.take(10).forEach { term -> SuggestionChip(onClick = { search(term) }, label = { Text(term) }) }
            }
        }
        if (state.query.isBlank()) item {
            Text(uiText("Set the mood"), color = textPrimary, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Arabic", "Rock", "Lo-Fi", "Electronic", "Pop").forEachIndexed { index, mood ->
                    Box(Modifier.size(138.dp, 100.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(listOf(Color(0xFF27554C), Color(0xFF493957), Color(0xFF344C62))[index % 3], cardBg)))
                        .clickable { search(mood) }.padding(16.dp), contentAlignment = Alignment.BottomStart) {
                        Text(uiText(mood), color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
        if (state.isSearching) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = accentColor) }
        state.errorMessage?.let { error -> item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(uiText(error), color = textSecondary, modifier = Modifier.weight(1f)); TextButton({ search(query) }) { Text(uiText("Retry")) }
        } } }
        val top = state.results.firstOrNull()
        if (top != null) item {
            Text(uiText("Top result"), color = textPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(accentColor.copy(alpha = .10f), Color.Transparent))).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TrackArtwork(top.artworkUrl, Modifier.size(124.dp))
                Column(Modifier.weight(1f)) {
                    Text(top.title, color = textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(top.artist + "  ·  " + searchDuration(top.durationMs), color = textSecondary, modifier = Modifier.padding(vertical = 8.dp))
                    Button({ onPlayTrack(top) }, enabled = !(selectedUrl == top.sourceUrl && isBuffering)) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(uiText(if(selectedUrl == top.sourceUrl && isBuffering) "Loading…" else "Play"))
                    }
                }
            }
        }
        if (state.results.isNotEmpty()) item { Text(uiText("Songs & videos"), color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
        items(state.results, key = { it.id }) { track ->
            ContextMenuArea(items = { listOf(ContextMenuItem(strings.text("Play")) { onPlayTrack(track) }, ContextMenuItem(strings.text("Play next")) { onPlayNext(track) }, ContextMenuItem(strings.text("Add to queue")) { onQueue(track) }, ContextMenuItem(strings.text("Favorite")) { onFavorite(track) }, ContextMenuItem(strings.text("Add to playlist")) { onPlaylist(track) }) }) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if(selectedUrl == track.sourceUrl) accentColor.copy(alpha = .08f) else Color.Transparent)
                    .clickable { onPlayTrack(track) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TrackArtwork(track.artworkUrl, Modifier.size(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(track.artist, color = textSecondary, maxLines = 1, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                    Text(searchDuration(track.durationMs), color = textSecondary, fontSize = 12.sp)
                    IconButton({ onFavorite(track) }) { Icon(if(track.sourceUrl in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", tint = if(track.sourceUrl in favorites) accentColor else textSecondary) }
                    if(selectedUrl == track.sourceUrl && isBuffering) CircularProgressIndicator(Modifier.size(22.dp), color = accentColor, strokeWidth = 2.dp)
                    else IconButton({ onPlayTrack(track) }) { Icon(Icons.Default.PlayArrow, "Play", tint = accentColor) }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Default.MoreHoriz, "More", tint = textSecondary) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(uiText("Play next")) }, onClick = { menu = false; onPlayNext(track) })
                            DropdownMenuItem(text = { Text(uiText("Add to queue")) }, onClick = { menu = false; onQueue(track) })
                            DropdownMenuItem(text = { Text(uiText("Favorite")) }, onClick = { menu = false; onFavorite(track) })
                            DropdownMenuItem(text = { Text(uiText("Add to playlist")) }, onClick = { menu = false; onPlaylist(track) })
                        }
                    }
                }
            }
        }
        if(!state.isSearching && state.query.isNotBlank() && state.results.isEmpty() && state.errorMessage == null) item {
            Text(uiText("No songs found. Try a song title or artist name."), color = textSecondary, modifier = Modifier.padding(vertical = 28.dp))
        }
    }
}

private fun searchDuration(ms: Long): String = if(ms <= 0) "—" else "${ms / 60000}:${((ms / 1000) % 60).toString().padStart(2, '0')}"
