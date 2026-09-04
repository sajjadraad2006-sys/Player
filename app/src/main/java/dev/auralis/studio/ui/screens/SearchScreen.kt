package dev.auralis.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import coil.compose.AsyncImage
import dev.auralis.studio.R
import dev.auralis.studio.core.database.DownloadEntity
import dev.auralis.studio.core.database.DownloadStatus
import dev.auralis.studio.core.database.PlaylistEntity
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.online.DownloadQuality
import dev.auralis.studio.online.UnifiedTrack
import dev.auralis.studio.library.RecordingDownloadMatcher
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.TrackRow

private val GENERAL_MUSIC_CATEGORIES = listOf(
    "Top Hits" to R.string.genre_top_hits,
    "Pop" to R.string.genre_pop,
    "Arabic Pop" to R.string.genre_arabic_pop,
    "Hip-Hop" to R.string.genre_hip_hop,
    "Rock" to R.string.genre_rock,
    "Acoustic" to R.string.genre_acoustic,
    "Lo-Fi" to R.string.genre_lofi,
    "Electronic" to R.string.genre_edm,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    results: List<Track>,
    favorites: Set<String>,
    playlists: List<PlaylistEntity>,
    onlineResults: List<UnifiedTrack>,
    isSearchingOnline: Boolean,
    providerFailures: Int = 0,
    recentSearches: List<String> = emptyList(),
    downloads: List<DownloadEntity> = emptyList(),
    onQueryChange: (String) -> Unit,
    onSearchOnline: (String) -> Unit,
    onCommitSearch: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    onRemoveRecentSearch: (String) -> Unit = {},
    onPlay: (Track, List<Track>) -> Unit,
    onPlayOnline: (UnifiedTrack) -> Unit,
    onDownloadOnline: (UnifiedTrack) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Long, String) -> Unit,
    mediaResolver: dev.auralis.studio.resolver.api.MediaResolver? = null,
    onEnqueueResolverDownload: (dev.auralis.studio.resolver.api.ResolverDownloadDescriptor) -> Unit = {},
    youtubeApiKey: String = "",
    onSaveYoutubeApiKey: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var sourceSheetTrack by remember { mutableStateOf<UnifiedTrack?>(null) }
    var inspectingSource by remember { mutableStateOf<dev.auralis.studio.online.RecordingSource?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showApiKeyGuideDialog by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    if (showApiKeyGuideDialog) {
        YouTubeApiKeyGuideDialog(
            currentApiKey = youtubeApiKey,
            onSaveApiKey = onSaveYoutubeApiKey,
            onDismiss = { showApiKeyGuideDialog = false },
        )
    }

    if (showImportDialog && mediaResolver != null) {
        dev.auralis.studio.ui.components.ImportFromLinkDialog(
            resolver = mediaResolver,
            onDismiss = { showImportDialog = false },
            onEnqueueDownload = onEnqueueResolverDownload,
        )
    }

    if (inspectingSource != null && mediaResolver != null) {
        dev.auralis.studio.ui.components.SourceInspectionBottomSheet(
            source = inspectingSource!!,
            resolver = mediaResolver,
            onDismiss = { inspectingSource = null },
            onEnqueueDownload = onEnqueueResolverDownload,
        )
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.search),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showApiKeyGuideDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "YouTube API Key & Guide",
                        tint = if (youtubeApiKey.isNotBlank()) colors.primary else colors.onSurfaceVariant,
                    )
                }
                if (mediaResolver != null) {
                    IconButton(
                        onClick = { showImportDialog = true },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = stringResource(R.string.import_from_link),
                            tint = colors.primary,
                        )
                    }
                }
            }
        }

        // Clean Natural Search Input Bar
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                if (selectedTab == 1 && it.length >= 2) {
                    onSearchOnline(it)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = {
                Text(
                    if (selectedTab == 0) stringResource(R.string.search_hint)
                    else stringResource(R.string.search_online_hint)
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = colors.primary)
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear), tint = colors.onSurfaceVariant)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onCommitSearch(query)
                    if (query.isNotBlank()) onSearchOnline(query)
                }
            ),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outline.copy(alpha = 0.4f),
                focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
            ),
        )

        Spacer(Modifier.height(6.dp))

        // High-Contrast Segmented Tabs: Local Library vs Free Online Search & Download
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Local Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selectedTab == 0) colors.primary else colors.surfaceVariant.copy(alpha = 0.85f))
                    .clickable { selectedTab = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.on_device_count, results.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                    color = if (selectedTab == 0) colors.onPrimary else colors.onSurface,
                )
            }

            // Online Search & Download Tab
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selectedTab == 1) colors.primary else colors.surfaceVariant.copy(alpha = 0.85f))
                    .clickable {
                        selectedTab = 1
                        if (query.isNotBlank()) onSearchOnline(query)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (selectedTab == 1) colors.onPrimary else colors.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.search_download_online),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == 1) colors.onPrimary else colors.onSurface,
                    )
                }
            }
        }

        // Clean Music Genres / Trending Tags
        if (selectedTab == 1) {
            Spacer(Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(GENERAL_MUSIC_CATEGORIES) { (searchKeyword, labelResource) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceVariant.copy(alpha = 0.8f))
                            .clickable {
                                onQueryChange(searchKeyword)
                                onSearchOnline(searchKeyword)
                                onCommitSearch(searchKeyword)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(labelResource),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Tab Content
        if (selectedTab == 0) {
            // Local Library Search
            if (query.isBlank()) {
                // Empty state: Show Recent Searches if available
                if (recentSearches.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.recent_searches),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface,
                                )
                            }
                            TextButton(onClick = onClearRecentSearches) {
                                Text(
                                    stringResource(R.string.clear_all),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.primary,
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            recentSearches.forEach { searchItem ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceVariant.copy(alpha = 0.65f))
                                        .clickable {
                                            onQueryChange(searchItem)
                                            onCommitSearch(searchItem)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(searchItem, style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.delete),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { onRemoveRecentSearch(searchItem) },
                                            tint = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val completedDownloads = downloads.filter { it.status == dev.auralis.studio.core.database.DownloadStatus.COMPLETED.name }
                if (completedDownloads.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.recent_downloads),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        completedDownloads.take(5).forEach { dl ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceVariant.copy(alpha = 0.4f))
                                    .clickable {
                                        if (dl.trackUri.isNotBlank()) {
                                            val dummyTrack = Track(
                                                id = dl.id,
                                                uri = dl.trackUri,
                                                title = dl.title,
                                                artist = dl.artist,
                                                album = dl.album,
                                                durationMs = dl.durationMs,
                                                displayName = dl.fileName,
                                                sizeBytes = dl.bytesDownloaded,
                                                dateAddedSeconds = dl.completedAt,
                                            )
                                            onPlay(dummyTrack, listOf(dummyTrack))
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            dl.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            dl.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = stringResource(R.string.play),
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                if (recentSearches.isEmpty() && completedDownloads.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.search_empty),
                            color = colors.onBackground.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            } else if (results.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.no_results),
                        color = colors.onBackground.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            selectedTab = 1
                            onSearchOnline(query)
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.search_online_for, query))
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(results, key = Track::uri) { track ->
                        TrackRow(
                            track = track,
                            isFavorite = track.uri in favorites,
                            playlists = playlists,
                            onPlay = { onPlay(track, results) },
                            onToggleFavorite = { onToggleFavorite(track.uri) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onAddToPlaylist(it, track.uri) },
                        )
                    }
                }
            }
        } else {
            // Online General Music Search & Download
            if (isSearchingOnline && onlineResults.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.searching),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            } else if (onlineResults.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (query.isBlank()) stringResource(R.string.online_search_empty)
                            else stringResource(R.string.online_no_results, query),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onBackground.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (isSearchingOnline) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .height(2.dp),
                            color = colors.primary,
                            trackColor = colors.surfaceVariant,
                        )
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(onlineResults, key = { "online:${it.uiKey}" }) { onlineTrack ->
                            val downloadStatus = RecordingDownloadMatcher.status(onlineTrack, downloads)
                            OnlineTrackCard(
                                track = onlineTrack,
                                downloadStatus = downloadStatus,
                                onPlay = {
                                    if (query.isNotBlank()) onCommitSearch(query)
                                    onPlayOnline(onlineTrack)
                                },
                                onDownload = {
                                    if (query.isNotBlank()) onCommitSearch(query)
                                    onDownloadOnline(onlineTrack)
                                },
                                onSources = { sourceSheetTrack = onlineTrack },
                            )
                        }
                    }
                }
            }
        }
    }

    sourceSheetTrack?.let { unified ->
        ModalBottomSheet(onDismissRequest = { sourceSheetTrack = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.available_sources), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                unified.sources.forEach { source ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                inspectingSource = source
                                sourceSheetTrack = null
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(source.provider.replaceFirstChar(Char::uppercase), fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    source.isLocal -> stringResource(R.string.on_device_count, 1)
                                    source.isDownloadableFullTrack -> stringResource(R.string.full_download)
                                    source.playbackCapability == dev.auralis.studio.online.PlaybackCapability.FULL_STREAM -> stringResource(R.string.full_stream)
                                    source.playbackCapability == dev.auralis.studio.online.PlaybackCapability.PREVIEW -> stringResource(R.string.preview_available)
                                    else -> "Inspect source with LRR"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        source.qualityLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun OnlineTrackCard(
    track: UnifiedTrack,
    downloadStatus: String? = null,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onSources: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    GlassCard(Modifier.fillMaxWidth(), onClick = onPlay) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Artwork
            if (track.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = colors.primary)
                }
            }

            Spacer(Modifier.width(12.dp))

            // Metadata
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(track.artist, track.album, formatDuration(track.durationMs)).filter(String::isNotBlank).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        downloadStatus == DownloadStatus.COMPLETED.name -> stringResource(R.string.downloaded)
                        downloadStatus == DownloadStatus.DOWNLOADING.name -> stringResource(R.string.downloading)
                        track.isLocal -> stringResource(R.string.on_device_count, 1)
                        else -> stringResource(R.string.youtube_download_available)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (downloadStatus == DownloadStatus.COMPLETED.name) colors.primary else colors.primary.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Quick Play Button
            FilledIconButton(
                onClick = onPlay,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play), modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(6.dp))

            // Download Status / Action Button
            when (downloadStatus) {
                DownloadStatus.COMPLETED.name -> {
                    FilledIconButton(
                        onClick = onPlay,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.primary.copy(alpha = 0.15f),
                            contentColor = colors.primary,
                        ),
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.downloaded),
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                DownloadStatus.DOWNLOADING.name -> {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colors.primary,
                            strokeWidth = 2.dp,
                        )
                    }
                }
                DownloadStatus.FAILED.name -> {
                    FilledIconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.errorContainer,
                            contentColor = colors.onErrorContainer,
                        ),
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = stringResource(R.string.download_failed),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                else -> {
                    FilledIconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.surfaceVariant,
                            contentColor = colors.onSurface,
                        ),
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.download),
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val seconds = durationMs / 1_000L
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
fun YouTubeApiKeyGuideDialog(
    currentApiKey: String,
    onSaveApiKey: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var keyText by remember { mutableStateOf(currentApiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.yt_api_guide_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Info banner: Works without API key
                Surface(
                    color = colors.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.yt_api_guide_no_key_needed),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.yt_api_guide_steps_header),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )

                Text(
                    text = stringResource(R.string.yt_api_guide_steps_content),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    lineHeight = 18.sp,
                )

                // Open Google Cloud Console button
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.cloud.google.com/apis/credentials"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.yt_api_guide_open_console))
                }

                Spacer(Modifier.height(4.dp))

                // Input Field for API Key
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text(stringResource(R.string.yt_api_guide_input_label)) },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (keyText.isNotBlank()) {
                            IconButton(onClick = { keyText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveApiKey(keyText.trim())
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
