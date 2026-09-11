package dev.auralis.desktop.ui
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.auralis.desktop.artwork.DesktopArtworkManager
import dev.auralis.desktop.library.*
import dev.auralis.desktop.lyrics.*
import dev.auralis.desktop.online.*
import dev.auralis.desktop.playback.DesktopPlaybackEngine
import dev.auralis.desktop.resolver.DesktopMediaResolver
import dev.auralis.desktop.storage.*
import dev.auralis.desktop.windows.smtc.DesktopSmtcCoordinator
import dev.auralis.studio.audio.visualizer.VisualizerAnalyzer
import dev.auralis.studio.core.model.*
import dev.auralis.studio.designsystem.AccentPalette
import dev.auralis.studio.lyrics.LyricsClock
import dev.auralis.studio.online.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private fun OnlineTrack.desktop(favorite: Boolean = false) = DesktopTrack(id.hashCode().toLong(), sourceUrl, title, artist, album, durationMs, artworkKey = artworkUrl, isFavorite = favorite)
private fun DesktopTrack.youtube() = OnlineTrack("youtube", path.substringAfter("v="), title, artist, album, durationMs = durationMs, artworkUrl = artworkKey.orEmpty(), sourceUrl = path)
@Composable fun DesktopShell(engine: DesktopPlaybackEngine, repository: DesktopMusicRepository, settingsRepository: DesktopSettingsRepository,
    onOpenFolderPicker: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val shellFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { shellFocus.requestFocus() }
    val settings by settingsRepository.settings.collectAsState()
    val tracks by repository.tracks.collectAsState()
    val folders by repository.folders.collectAsState()
    val scanning by repository.isScanning.collectAsState()
    val youtube = remember { YouTubeCatalogProvider() }
    val search = remember { DesktopSearchCoordinator(listOf(youtube), scope) }
    val resolver = remember { DesktopMediaResolver() }
    val online = remember { YouTubePlaybackCoordinator(resolver, engine, scope) }
    val lyricsRepo = remember { DesktopLyricsRepository() }
    val clock = remember { LyricsClock() }
    val visualizer = remember { VisualizerAnalyzer(tap = engine.audioTap) }
    val metadata by remember(engine) { engine.state.map { state -> state.metadata?.let {
        DesktopTrack(0, state.currentUri.orEmpty(), it.title, it.artist, it.album, state.durationMs, artworkKey = it.artwork)
    } }.distinctUntilChanged() }.collectAsState(null)
    val playing by remember(engine) { engine.state.map { it.isPlaying }.distinctUntilChanged() }.collectAsState(false)
    val buffering by remember(engine) { engine.state.map { it.isBuffering }.distinctUntilChanged() }.collectAsState(false)
    var currentTab by remember { mutableStateOf(DesktopNavTab.HOME) }
    var backStack by remember { mutableStateOf<List<DesktopNavTab>>(emptyList()) }
    var forwardStack by remember { mutableStateOf<List<DesktopNavTab>>(emptyList()) }
    var panel by remember { mutableStateOf("Lyrics") }
    var panelVisible by remember { mutableStateOf(false) }
    var focusVersion by remember { mutableStateOf(0) }
    var playlists by remember { mutableStateOf<List<DesktopPlaylist>>(emptyList()) }
    var selectedPlaylist by remember { mutableStateOf<Long?>(null) }
    var playlistTrack by remember { mutableStateOf<DesktopTrack?>(null) }
    var queue by remember { mutableStateOf<List<DesktopTrack>>(emptyList()) }
    var resumePoint by remember { mutableStateOf<Pair<String?, Long>>(null to 0L) }
    var played by remember { mutableStateOf<List<DesktopTrack>>(emptyList()) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(0) }
    var lyrics by remember { mutableStateOf<LyricsUiState>(LyricsUiState.NotFound) }
    var lyricsRefresh by remember(metadata?.path) { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var audioConfig by remember { mutableStateOf(settings.dspConfig) }
    val legacyAccent = AccentPalette.PRESETS.find { it.value.toString() == settings.themeAccent } ?: AccentPalette.MINT
    val theme = settings.theme ?: ThemeSettings(appearance = if(settings.isAmoled) AppearanceMode.AMOLED else AppearanceMode.DARK, accentArgb = legacyAccent.toArgb().toLong() and 0xFFFFFFFF)
    val dark = ThemeModeResolver.isDark(theme.appearance, isSystemInDarkTheme())
    val bg = if(theme.appearance == AppearanceMode.AMOLED) Color.Black else if(dark) Color(0xFF0B1018) else Color(0xFFF3F6F7)
    val surface = if(dark) Color(0xFF131B25) else Color.White
    val primary = Color(theme.accentArgb.toInt())
    val secondary = Color(theme.secondaryArgb.toInt())
    val fg = if(dark) Color(0xFFF1F5F7) else Color(0xFF17232D)
    val muted = if(dark) Color(0xFF9AA9B6) else Color(0xFF566773)
    val scheme = if(dark) darkColorScheme(primary = primary, secondary = secondary, background = bg, surface = surface, onSurface = fg, onBackground = fg, onSurfaceVariant = muted, onPrimary = Color(0xFF09241E), secondaryContainer = Color(0xFF233A3B), onSecondaryContainer = fg, surfaceVariant = Color(0xFF25313C))
        else lightColorScheme(primary = primary.copy(red = primary.red * .48f, green = primary.green * .48f, blue = primary.blue * .48f), secondary = secondary, background = bg, surface = surface, onSurface = fg, onSurfaceVariant = muted)
    var glow by remember { mutableStateOf(primary) }
    LaunchedEffect(metadata?.artworkKey, theme.backgroundArtwork, theme.visualQuality, primary) {
        glow = primary
        if(theme.backgroundArtwork && theme.visualQuality != VisualQuality.REDUCED) metadata?.artworkKey?.let { key ->
            DesktopArtworkManager.getArtwork(key)?.let { bitmap ->
                val pixels = bitmap.toPixelMap()
                val samples = (1..6).flatMap { x -> (1..6).map { y -> pixels[x * pixels.width / 7, y * pixels.height / 7] } }
                glow = Color(samples.map { it.red }.average().toFloat(), samples.map { it.green }.average().toFloat(), samples.map { it.blue }.average().toFloat())
            }
        }
    }
    fun saveSettings(change: (DesktopSettings) -> DesktopSettings) { scope.launch { settingsRepository.update(transform = change) } }
    fun navigate(tab: DesktopNavTab) {
        if(currentTab != tab) { backStack = backStack + currentTab; forwardStack = emptyList(); currentTab = tab }
        saveSettings { it.copy(lastTab = tab.name) }
    }
    fun play(track: DesktopTrack) { if(track.path.startsWith("https://www.youtube.com/")) online.play(track.youtube()) else engine.playTrack(track) }
    fun resumeTrack(track: DesktopTrack, position: Long) {
        if(engine.state.value.currentUri == track.path && engine.canResume) { engine.resume(); return }
        queue = emptyList()
        if(track.path.startsWith("https://www.youtube.com/")) online.play(track.youtube(), position) else engine.playTrack(track, position)
    }
    fun playCollection(track: DesktopTrack, collection: List<DesktopTrack>) {
        queue = if(shuffle) collection.filterNot { it.path == track.path }.shuffled() else collection.dropWhile { it.path != track.path }.drop(1)
        play(track)
    }
    fun next() {
        if(queue.isNotEmpty()) { val song = queue.first(); queue = queue.drop(1); play(song) }
        else if(repeatMode == 1 && played.isNotEmpty()) { val list = played.asReversed(); playCollection(list.first(), list) }
    }
    fun previous() { if(engine.state.value.currentPositionMs > 3000) engine.seekTo(0) else played.drop(1).firstOrNull()?.let { old -> metadata?.let { queue = listOf(it) + queue }; play(old) } }
    fun toggle() { if(engine.state.value.isPlaying) engine.pause() else if(engine.canResume) engine.resume() else metadata?.let(::play) }
    fun favorite(track: DesktopTrack) {
        if(track.path.startsWith("http")) saveSettings { old -> old.copy(onlineFavorites = if(old.onlineFavorites.any { it.sourceUrl == track.path }) old.onlineFavorites.filterNot { it.sourceUrl == track.path } else old.onlineFavorites + track.youtube()) }
        else scope.launch { repository.toggleFavorite(track) }
    }
    fun addQueue(track: DesktopTrack, first: Boolean = false) { queue = if(first) listOf(track) + queue else queue + track; message = if(first) "Playing next" else "Added to queue" }
    fun savePlaylist(item: DesktopPlaylist) { scope.launch { repository.database.savePlaylist(item); playlists = repository.database.getPlaylists() } }
    fun openPanel(name: String) { panel = name; panelVisible = true; saveSettings { it.copy(nowPlayingVisible = true) } }
    fun updateAudio(next: AudioConfig) { audioConfig = next; engine.updateAudioConfig(next); saveSettings { it.copy(dspSettings = DesktopDspSettings.from(next)) } }
    LaunchedEffect(Unit) { repository.initialize(); resumePoint = repository.database.getLastPlaybackState(); playlists = repository.database.getPlaylists(); engine.setVolume(settings.volume); engine.updateAudioConfig(audioConfig); repository.scanFolders() }
    LaunchedEffect(settings.onlineLyrics) { lyricsRepo.allowOnline = settings.onlineLyrics }
    LaunchedEffect(metadata?.path, metadata?.durationMs, lyricsRefresh) {
        val track = metadata
        lyrics = if(track == null) LyricsUiState.NotFound else LyricsUiState.Loading
        if(track != null) try {
            val result = lyricsRepo.pipeline.resolve(track.lyricsTrack(), lyricsRefresh > 0)
            lyrics = if(result.lines.isEmpty()) LyricsUiState.NotFound else LyricsUiState.Found(result)
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { lyrics = LyricsUiState.Error("Lyrics could not be loaded. Try again.") }
    }
    LaunchedEffect(engine) {
        var lastError: String? = null
        var lastHistory: String? = null
        engine.state.collect { state ->
            clock.update(state.currentPositionMs, state.isPlaying && !state.isBuffering, state.speed)
            if(state.errorMessage != null && state.errorMessage != lastError) message = state.errorMessage
            lastError = state.errorMessage
            val info = state.metadata
            val uri = state.currentUri
            if(!state.isBuffering && uri != null && info != null) {
                repository.database.updatePlaybackPosition(uri, state.currentPositionMs)
                if(uri != resumePoint.first || kotlin.math.abs(state.currentPositionMs - resumePoint.second) >= 2000 || !state.isPlaying) resumePoint = uri to state.currentPositionMs
                if(state.isPlaying && lastHistory != uri) {
                    lastHistory = uri
                    val track = DesktopTrack(0, uri, info.title, info.artist, info.album, state.durationMs, artworkKey = info.artwork)
                    played = (listOf(track) + played.filterNot { it.path == track.path }).take(100)
                    settingsRepository.update { it.copy(recentTracks = (listOf(track) + it.recentTracks.filterNot { old -> old.path == track.path }).take(50)) }
                }
            }
        }
    }
    val finishAction by rememberUpdatedState<() -> Unit> { if(repeatMode == 2) metadata?.let(::play) else next() }
    val nextAction by rememberUpdatedState<() -> Unit> { next() }
    val previousAction by rememberUpdatedState<() -> Unit> { previous() }
    val toggleAction by rememberUpdatedState<() -> Unit> { toggle() }
    LaunchedEffect(engine) { engine.state.map { it.completedSessions }.distinctUntilChanged().drop(1).collect { finishAction() } }
    val smtc = remember { DesktopSmtcCoordinator(playbackEngine = engine, onNextAction = { scope.launch { nextAction() } }, onPreviousAction = { scope.launch { previousAction() } }, onPlayAction = { scope.launch { if(!engine.state.value.isPlaying) toggleAction() } }) }
    LaunchedEffect(metadata, queue.size) { smtc.onTrackChanged(metadata, queue.isNotEmpty(), played.size > 1) }
    DisposableEffect(Unit) { onDispose { online.close(); search.clear(); youtube.close(); resolver.shutdownWorker(); lyricsRepo.close(); visualizer.stop(); smtc.release() } }
    LaunchedEffect(message) { if(message != null) { delay(5000); message = null } }
    val onlineFavorites = settings.onlineFavorites.map { it.desktop(true) }
    val favorites = tracks.filter { it.isFavorite } + onlineFavorites
    val rightContent: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Now playing"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton({ panelVisible = false; saveSettings { it.copy(nowPlayingVisible = false) } }) { Icon(Icons.Default.Close, "Hide Now Playing") }
            }
            if(metadata != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TrackArtwork(metadata?.artworkKey, Modifier.size(74.dp))
                Column(Modifier.weight(1f)) { Text(metadata!!.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(metadata!!.artist, color = muted, fontSize = 12.sp, maxLines = 1) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Lyrics", "Queue", "Visualizer").forEach { name -> FilterChip(panel == name, { panel = name }, label = { Text(uiText(name), fontSize = 12.sp) }) }
            }
            Box(Modifier.weight(1f)) {
                when(panel) {
                    "Lyrics" -> key(metadata?.path) { LyricsScreen(metadata, clock, lyrics, lyricsRepo, { lyrics = it }, { lyricsRefresh++ }, engine::seekTo, primary, fg, muted, surface) }
                    "Queue" -> Column {
                        Text(uiText("Queue") + " · ${queue.size}", fontWeight = FontWeight.SemiBold)
                        if(queue.isEmpty()) Text(uiText("Add songs from search or your library."), color = muted, modifier = Modifier.padding(top = 14.dp))
                        LazyColumn { itemsIndexed(queue) { index, track -> Row(Modifier.fillMaxWidth().clickable { queue = queue.drop(index + 1); play(track) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            TrackArtwork(track.artworkKey ?: track.path, Modifier.size(40.dp)); Text(track.title, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 2, fontSize = 12.sp)
                            IconButton({ queue = queue.filterIndexed { i, _ -> i != index } }) { Icon(Icons.Default.Close, "Remove from queue", modifier = Modifier.size(16.dp)) }
                        } } }
                    }
                    else -> VisualizerScreen(visualizer, playing, primary, fg, muted)
                }
            }
        }
    }
    CompositionLocalProvider(LocalDesktopStrings provides remember(settings.language) { DesktopStrings(settings.language == "AR") }, LocalContentColor provides fg, LocalLayoutDirection provides if(settings.language == "AR") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = scheme, shapes = Shapes(medium = RoundedCornerShape(theme.effectiveCornerRadius.dp), large = RoundedCornerShape(theme.effectiveCornerRadius.dp))) {
            BoxWithConstraints(modifier.fillMaxSize().background(bg).onPreviewKeyEvent { e ->
                if(e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.F) { navigate(DesktopNavTab.ONLINE); panelVisible = false; focusVersion++; true }
                else if(e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.O) { onOpenFolderPicker(); true } else false
            }.onKeyEvent { e ->
                if(e.type != KeyEventType.KeyDown) false else when(e.key) {
                    Key.Spacebar, Key.MediaPlayPause -> { toggle(); true }; Key.MediaNext -> { next(); true }; Key.MediaPrevious -> { previous(); true }
                    Key.DirectionLeft -> { engine.seekTo(engine.state.value.currentPositionMs - 5000); true }
                    Key.DirectionRight -> { engine.seekTo(engine.state.value.currentPositionMs + 5000); true }
                    Key.DirectionUp -> { engine.setVolume(engine.state.value.volume + .05f); true }
                    Key.DirectionDown -> { engine.setVolume(engine.state.value.volume - .05f); true }; else -> false
                }
            }.focusRequester(shellFocus).focusable()) {
                val wide = maxWidth >= 1160.dp
                val sidebarWidth = if(maxWidth < 1000.dp) 156.dp else 188.dp
                LaunchedEffect(wide, metadata?.path) {
                    if(!wide || metadata == null) panelVisible = false
                    else if(settings.nowPlayingVisible) panelVisible = true
                }
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).animateContentSize(animationSpec = if(theme.animationStyle == AnimationStyle.SPRING) spring() else tween(if(theme.animationStyle == AnimationStyle.REDUCED) 0 else 180))) {
                        Column(Modifier.width(sidebarWidth).fillMaxHeight().padding(horizontal = 14.dp, vertical = 22.dp)) {
                            Text("LYRUNE", fontSize = 22.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Black, color = fg, modifier = Modifier.padding(start = 10.dp, bottom = 28.dp))
                            listOf(DesktopNavTab.HOME, DesktopNavTab.LIBRARY, DesktopNavTab.ONLINE, DesktopNavTab.AUDIO_LAB, DesktopNavTab.SETTINGS).forEach { tab ->
                                val chosen = currentTab == tab
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(theme.effectiveCornerRadius.dp))
                                    .background(if(chosen) primary.copy(alpha = .12f) else Color.Transparent).clickable { selectedPlaylist = null; navigate(tab); if(!wide) panelVisible = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(tab.icon, tab.title, tint = if(chosen) primary else muted, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                                    Text(if(settings.language == "AR") when(tab) { DesktopNavTab.HOME -> "الرئيسية"; DesktopNavTab.LIBRARY -> "المكتبة"; DesktopNavTab.ONLINE -> "بحث"; DesktopNavTab.AUDIO_LAB -> "مختبر الصوت"; else -> "الإعدادات" } else tab.title,
                                        color = if(chosen) fg else muted, fontSize = 13.sp, maxLines = 1)
                                }
                            }
                            Text(uiText("PLAYLISTS"), fontSize = 10.sp, letterSpacing = 1.sp, color = muted, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp, start = 12.dp))
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { playlists.forEach { item ->
                                Text(item.name, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clickable { selectedPlaylist = item.id; navigate(DesktopNavTab.LIBRARY) }.padding(12.dp))
                            } }
                            Text(uiText("Songs") + " · ${tracks.size}", color = muted, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                        }
                        Column(Modifier.weight(1f).fillMaxHeight().drawBehind {
                            if(theme.visualQuality != VisualQuality.REDUCED && theme.style != ThemeStyle.MINIMAL) drawRect(Brush.radialGradient(
                                listOf((if(theme.backgroundArtwork) glow else primary).copy(alpha = (1f - theme.backgroundDim) * .2f), secondary.copy(alpha = .025f), Color.Transparent),
                                center = Offset(size.width * .7f, 0f), radius = size.width * (.6f + theme.blurStrength)))
                        }) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton({ if(backStack.isNotEmpty()) { forwardStack = forwardStack + currentTab; currentTab = backStack.last(); backStack = backStack.dropLast(1) } }, enabled = backStack.isNotEmpty()) { Icon(Icons.Default.ChevronLeft, "Back") }
                                IconButton({ if(forwardStack.isNotEmpty()) { backStack = backStack + currentTab; currentTab = forwardStack.last(); forwardStack = forwardStack.dropLast(1) } }, enabled = forwardStack.isNotEmpty()) { Icon(Icons.Default.ChevronRight, "Forward") }
                                TextButton({ navigate(DesktopNavTab.ONLINE); panelVisible = false; focusVersion++ }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(uiText("Search YouTube"), fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text(uiText("Ctrl F"), color = muted, fontSize = 10.sp) }
                                IconButton({ panelVisible = !panelVisible; saveSettings { it.copy(nowPlayingVisible = panelVisible) } }) { Icon(Icons.Default.ViewSidebar, "Toggle Now Playing") }
                            }
                            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
                                if(panelVisible && !wide) rightContent() else when(currentTab) {
                                    DesktopNavTab.HOME -> MusicHome(tracks, settings.recentTracks, favorites, playlists, settings.homeLayout, ::playCollection, { navigate(DesktopNavTab.ONLINE) }, onOpenFolderPicker, { selectedPlaylist = it; navigate(DesktopNavTab.LIBRARY) }, { category -> search.search(category); navigate(DesktopNavTab.ONLINE) }, resumePoint, ::resumeTrack, metadata?.path.takeIf { playing })
                                    DesktopNavTab.LIBRARY -> MusicLibrary(tracks, onlineFavorites, playlists, selectedPlaylist, metadata?.path, scanning,
                                        ::playCollection, ::favorite, { addQueue(it, true) }, { addQueue(it) }, { playlistTrack = it }, ::savePlaylist,
                                        { id -> scope.launch { repository.database.deletePlaylist(id); playlists = repository.database.getPlaylists() } }, onOpenFolderPicker, { scope.launch { repository.scanFolders() } }, savedOnline = settings.savedOnlineTracks)
                                    DesktopNavTab.ONLINE -> OnlineScreen(search, { play(it.desktop()) }, settings.searchHistory,
                                        { term -> if(settings.rememberSearches) saveSettings { it.copy(searchHistory = (listOf(term) + it.searchHistory.filterNot { old -> old.equals(term, true) }).take(12)) } },
                                        { saveSettings { it.copy(searchHistory = emptyList()) } }, { addQueue(it.desktop()) }, { favorite(it.desktop()) }, settings.onlineFavorites.map { it.sourceUrl }.toSet(), metadata?.path, buffering, focusVersion, primary, surface, surface, fg, muted,
                                        onPlayNext = { addQueue(it.desktop(), true) }, onPlaylist = { song -> val track = song.desktop(); playlistTrack = track; saveSettings { it.copy(savedOnlineTracks = (it.savedOnlineTracks + track).distinctBy { song -> song.path }) } })
                                    DesktopNavTab.AUDIO_LAB -> AudioLabScreen(audioConfig, ::updateAudio, primary, surface, surface, fg, muted)
                                    DesktopNavTab.SETTINGS -> ProductSettings(settings, theme, folders, { updated -> saveSettings { updated }; engine.setVolume(updated.volume) },
                                        { updated -> saveSettings { it.copy(theme = updated) } }, onOpenFolderPicker, { path -> scope.launch { repository.removeFolder(path) } }, { scope.launch { repository.scanFolders() } })
                                    else -> rightContent()
                                }
                            }
                        }
                        if(panelVisible && wide) Box(Modifier.width(350.dp).fillMaxHeight().background(surface.copy(alpha = .48f))) { rightContent() }
                    }
                    ModernPlayerBar(engine.state, favorites.any { it.path == metadata?.path }, shuffle, repeatMode, { metadata?.let(::favorite) }, { shuffle = !shuffle; if(shuffle) queue = queue.shuffled() },
                        { repeatMode = (repeatMode + 1) % 3 }, ::toggle, ::previous, ::next, engine::seekTo,
                        { volume -> engine.setVolume(volume); saveSettings { it.copy(volume = volume) } }, { openPanel("Lyrics") }, { openPanel("Queue") }, { navigate(DesktopNavTab.AUDIO_LAB) })
                }
                message?.let { text -> Snackbar(Modifier.align(Alignment.BottomCenter).padding(bottom = 115.dp).widthIn(max = 560.dp), action = { TextButton({ message = null }) { Text(uiText("Dismiss")) } }) { Text(uiText(text)) } }
            }
            playlistTrack?.let { track ->
                AlertDialog(
                    onDismissRequest = { playlistTrack = null },
                    title = { Text(uiText("Add to playlist")) },
                    text = {
                        Column {
                            if(playlists.isEmpty()) Text(uiText("Create a playlist in Library first."))
                            playlists.forEach { item ->
                                TextButton({
                                    savePlaylist(item.copy(trackPaths = (item.trackPaths + track.path).distinct()))
                                    playlistTrack = null
                                    message = "Added to ${item.name}"
                                }) { Text(item.name) }
                            }
                        }
                    },
                    confirmButton = { TextButton({ playlistTrack = null }) { Text(uiText("Done")) } },
                )
            }
        }
    }
}
