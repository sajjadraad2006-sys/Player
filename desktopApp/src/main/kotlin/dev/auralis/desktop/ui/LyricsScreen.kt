package dev.auralis.desktop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.desktop.library.DesktopTrack
import dev.auralis.desktop.lyrics.*
import dev.auralis.studio.core.model.*
import dev.auralis.studio.lyrics.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun LyricsScreen(
    currentTrack: DesktopTrack?, clock: LyricsClock, state: LyricsUiState,
    repository: DesktopLyricsRepository, onStateChange: (LyricsUiState) -> Unit,
    onRetry: () -> Unit, onSeek: (Long) -> Unit,
    accentColor: Color, textPrimary: Color, textSecondary: Color, cardBg: Color,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var time by remember { mutableStateOf(0L) }
    var following by remember(currentTrack?.path) { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showTiming by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val lyrics = (state as? LyricsUiState.Found)?.lyrics
    val active = lyrics?.activeIndex(time) ?: -1
    LaunchedEffect(clock) { while(isActive) { time = clock.tick(); delay(50) } }
    LaunchedEffect(active, following, lyrics) {
        if(following && active >= 0) list.animateScrollToItem(active, -80)
    }
    val manualScroll = remember { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if(source == NestedScrollSource.UserInput) following = false
            return Offset.Zero
        }
    } }
    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(uiText("Lyrics"), color = textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(currentTrack?.title ?: uiText("Play a song to see its lyrics"), color = textSecondary, fontSize = 12.sp, maxLines = 2)
            }
            if(!following && lyrics?.synced == true) TextButton({ following = true }) { Text(uiText("Follow")) }
            Box {
                IconButton({ menu = true }, enabled = currentTrack != null) { Icon(Icons.Default.MoreHoriz, "Lyrics options", tint = textSecondary) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(uiText("Find another version")) }, onClick = { menu = false; showSearch = true })
                    DropdownMenuItem(text = { Text(uiText("Retry lyrics")) }, onClick = { menu = false; onRetry() })
                    DropdownMenuItem(text = { Text(uiText("Timing offset")) }, onClick = { menu = false; showTiming = !showTiming })
                    DropdownMenuItem(text = { Text(uiText("Import LRC file")) }, onClick = {
                        menu = false
                        val track = currentTrack ?: return@DropdownMenuItem
                        scope.launch {
                            val file = withContext(Dispatchers.Swing) {
                                val dialog = FileDialog(null as Frame?, "Import lyrics", FileDialog.LOAD)
                                dialog.file = "*.lrc"; dialog.isVisible = true
                                val selected = dialog.file?.let { File(dialog.directory, it) }; dialog.dispose(); selected
                            }
                            if(file != null && file.length() <= 1_048_576) {
                                val content = withContext(Dispatchers.IO) { file.readText(Charsets.UTF_8) }
                                repository.pipeline.import(track.lyricsTrack(), content)
                                onRetry()
                            }
                        }
                    })
                }
            }
        }
        if(showTiming && lyrics != null && currentTrack != null) {
            Text("Timing: ${lyrics.offsetMs} ms", color = textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(-1000L, -500, -100, 0, 100, 500, 1000).forEach { change ->
                    TextButton({
                        val offset = (if(change == 0L) 0 else lyrics.offsetMs + change).coerceIn(-30_000, 30_000)
                        onStateChange(LyricsUiState.Found(lyrics.copy(offsetMs = offset)))
                        scope.launch { repository.pipeline.updateOffset(currentTrack.lyricsTrack(), offset) }
                    }, contentPadding = PaddingValues(6.dp)) { Text(if(change == 0L) "Reset" else if(change > 0) "+$change" else "$change", fontSize = 11.sp) }
                }
            }
        }
        when(state) {
            LyricsUiState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accentColor) }
            is LyricsUiState.Found -> LazyColumn(Modifier.weight(1f).fillMaxWidth().nestedScroll(manualScroll), state = list,
                contentPadding = PaddingValues(top = 36.dp, bottom = 160.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                itemsIndexed(state.lyrics.lines) { index, line ->
                    val current = index == active
                    val lineColor = if(!state.lyrics.synced || current) textPrimary else textSecondary.copy(alpha = .6f)
                    val styled = buildAnnotatedString {
                        if(current && line.hasWordTiming) line.words!!.forEach { word ->
                            withStyle(SpanStyle(color = if(time - state.lyrics.offsetMs >= word.startTimeMs) accentColor else lineColor)) { append(word.text); append(" ") }
                        } else append(line.text)
                    }
                    Text(styled, color = if(current) accentColor else lineColor, fontSize = 25.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = state.lyrics.synced) { onSeek(state.lyrics.seekPositionFor(line)); following = true })
                }
            }
            else -> Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FormatQuote, null, tint = accentColor, modifier = Modifier.size(42.dp))
                Text(uiText(if(state is LyricsUiState.Error) state.message else if(currentTrack == null) "Your music, word for word." else "No lyrics found for this version."), color = textSecondary, modifier = Modifier.padding(14.dp))
                if(currentTrack != null) Row { TextButton(onRetry) { Text(uiText("Retry")) }; TextButton({ showSearch = true }) { Text(uiText("Search lyrics")) } }
            }
        }
    }
    if(showSearch && currentTrack != null) {
        val track = currentTrack
        val normalized = remember(track.path) { LyricsMetadataNormalizer.normalize(track.title, track.artist) }
        var title by remember { mutableStateOf(normalized.title) }
        var artist by remember { mutableStateOf(normalized.artist) }
        var candidates by remember { mutableStateOf<List<LrclibTrack>>(emptyList()) }
        var searching by remember { mutableStateOf(false) }
        var searched by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { showSearch = false }, title = { Text(uiText("Find lyrics")) },
            text = { Column(Modifier.width(440.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(uiText("Song")) }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text(uiText("Artist")) }, singleLine = true)
                Button({ scope.launch {
                    searching = true
                    try { candidates = repository.pipeline.candidates(track.lyricsTrack().copy(title = title, artist = artist)); searched = true }
                    finally { searching = false }
                } }, enabled = !searching) { Text(uiText(if(searching) "Searching…" else "Search versions")) }
                if(searched && candidates.isEmpty()) Text(uiText("No matching lyrics. Try a shorter title."))
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    itemsIndexed(candidates) { _, candidate ->
                        TextButton({ scope.launch {
                            val selected = repository.pipeline.select(track.lyricsTrack(), candidate)
                            onStateChange(LyricsUiState.Found(selected)); showSearch = false
                        } }) { Column(Modifier.fillMaxWidth()) {
                            Text(candidate.trackName, fontWeight = FontWeight.Bold)
                            Text("${candidate.artistName} · ${candidate.duration.toInt()}s · ${if(!candidate.syncedLyrics.isNullOrBlank()) "Synced" else "Plain"}", fontSize = 12.sp)
                        } }
                    }
                }
            } }, confirmButton = { TextButton({ showSearch = false }) { Text(uiText("Done")) } })
    }
}
