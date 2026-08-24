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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.auralis.studio.R
import dev.auralis.studio.core.database.PlaylistEntity
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.online.OnlineTrack
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.TrackRow

private val GENERAL_MUSIC_CATEGORIES = listOf(
    "Top Hits" to "Top Hits",
    "Pop" to "Pop",
    "Arabic Pop" to "Arabic Pop",
    "Hip-Hop" to "Hip-Hop",
    "Rock" to "Rock",
    "Acoustic" to "Acoustic",
    "Lo-Fi" to "Lo-Fi",
    "Electronic" to "EDM",
)

@Composable
fun SearchScreen(
    query: String,
    results: List<Track>,
    favorites: Set<String>,
    playlists: List<PlaylistEntity>,
    onlineResults: List<OnlineTrack>,
    isSearchingOnline: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchOnline: (String) -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onPlayOnline: (OnlineTrack) -> Unit,
    onDownloadOnline: (OnlineTrack) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val colors = MaterialTheme.colorScheme

    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.search),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )

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
                    else "ابحث عن أي أغنية، فنان، ألبوم، أو موسيقى..."
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = colors.primary)
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = colors.onSurfaceVariant)
                    }
                }
            },
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
                    text = "على الجهاز (${results.size})",
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
                        text = "بحث وتحميل أونلاين",
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
                items(GENERAL_MUSIC_CATEGORIES) { (searchKeyword, label) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceVariant.copy(alpha = 0.8f))
                            .clickable {
                                onQueryChange(searchKeyword)
                                onSearchOnline(searchKeyword)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
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
            if (query.isBlank() || results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (query.isBlank()) R.string.search_empty else R.string.no_results),
                        color = colors.onBackground.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
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
            if (isSearchingOnline) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "جاري البحث...",
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
                            if (query.isBlank()) "ابحث عن أي أغنية أو فنان أو موسيقى للاستماع والتحميل مجاناً"
                            else "لم يتم العثور على نتائج لـ \"$query\"، جرب كلمة أخرى",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onBackground.copy(alpha = 0.75f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(onlineResults, key = { "online:${it.provider}:${it.id}" }) { onlineTrack ->
                        OnlineTrackCard(
                            track = onlineTrack,
                            onPlay = { onPlayOnline(onlineTrack) },
                            onDownload = { onDownloadOnline(onlineTrack) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineTrackCard(
    track: OnlineTrack,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
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
                    "${track.artist} · ${track.album}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "استماع وتحميل مباشر مجاني",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
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
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(6.dp))

            // Quick Download Button
            FilledIconButton(
                onClick = onDownload,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.surfaceVariant,
                    contentColor = colors.onSurface,
                ),
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download", tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
