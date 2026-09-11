package dev.auralis.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.desktop.artwork.DesktopArtworkManager
import dev.auralis.desktop.library.DesktopTrack
import dev.auralis.desktop.library.TrackSortOption

/**
 * Desktop Music Library screen with search filtering, fast scrolling table, bounded thumbnail artwork,
 * favorite toggles, and sorting options.
 */
@Composable
fun LibraryScreen(
    tracks: List<DesktopTrack>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isScanning: Boolean,
    onRescan: () -> Unit,
    currentTrack: DesktopTrack?,
    isPlaying: Boolean,
    onTrackSelected: (DesktopTrack, Int) -> Unit,
    onToggleFavorite: (DesktopTrack) -> Unit,
    sortOption: TrackSortOption,
    onSortChange: (TrackSortOption) -> Unit,
    accentColor: Color,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier,
) {
    val filteredTracks = remember(tracks, searchQuery, sortOption) {
        val list = if (searchQuery.isBlank()) tracks else {
            tracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true)
            }
        }
        when (sortOption) {
            TrackSortOption.TITLE -> list.sortedBy { it.title.lowercase() }
            TrackSortOption.ARTIST -> list.sortedBy { it.artist.lowercase() }
            TrackSortOption.ALBUM -> list.sortedBy { it.album.lowercase() }
            TrackSortOption.DURATION -> list.sortedBy { it.durationMs }
            TrackSortOption.RECENTLY_ADDED -> list.sortedByDescending { it.lastModified }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Music Library",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                )
                Text(
                    text = "${filteredTracks.size} available tracks",
                    fontSize = 12.sp,
                    color = textSecondary,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Search field
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search songs, artists, albums...", color = textSecondary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = textSecondary) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg,
                        focusedIndicatorColor = accentColor,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.width(280.dp).height(46.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Sort cycle button
                Button(
                    onClick = {
                        val next = when (sortOption) {
                            TrackSortOption.TITLE -> TrackSortOption.ARTIST
                            TrackSortOption.ARTIST -> TrackSortOption.ALBUM
                            TrackSortOption.ALBUM -> TrackSortOption.DURATION
                            TrackSortOption.DURATION -> TrackSortOption.RECENTLY_ADDED
                            TrackSortOption.RECENTLY_ADDED -> TrackSortOption.TITLE
                        }
                        onSortChange(next)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cardBg,
                        contentColor = textPrimary,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(46.dp).border(1.dp, borderColor, RoundedCornerShape(10.dp)),
                ) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort", tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sort: ${sortOption.name.replace('_', ' ')}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Rescan button
                Button(
                    onClick = onRescan,
                    enabled = !isScanning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(46.dp),
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Folders", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Track List Table
        if (filteredTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicOff, contentDescription = null, tint = textSecondary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No tracks found in library", color = textPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Click 'Scan Folders' or drag & drop music files to discover tracks", color = textSecondary, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardBg.copy(alpha = 0.6f))
                    .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
            ) {
                // Table header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("#", color = textSecondary, fontSize = 12.sp, modifier = Modifier.width(36.dp))
                        Text("COVER", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                        Text("TITLE", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                        Text("ARTIST", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
                        Text("ALBUM", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                        Text("FORMAT", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(76.dp))
                        Text("DURATION", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(66.dp), textAlign = TextAlign.End)
                        Text("FAV", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                    }
                    HorizontalDivider(color = borderColor, thickness = 1.dp)
                }

                itemsIndexed(filteredTracks, key = { _, track -> track.path }) { index, track ->
                    val isCurrent = currentTrack?.path == track.path
                    val rowBg = if (isCurrent) accentColor.copy(alpha = 0.12f) else Color.Transparent

                    var artworkThumbnail by remember(track.path) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(track.path) {
                        artworkThumbnail = DesktopArtworkManager.getArtwork(track.path)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .clickable { onTrackSelected(track, index) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Track index or playing indicator
                        Box(modifier = Modifier.width(36.dp)) {
                            if (isCurrent && isPlaying) {
                                Icon(Icons.Default.GraphicEq, contentDescription = "Playing", tint = accentColor, modifier = Modifier.size(16.dp))
                            } else {
                                Text("${index + 1}", color = textSecondary, fontSize = 12.sp)
                            }
                        }

                        // Artwork Thumbnail
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF222842)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val bitmap = artworkThumbnail
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = track.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = accentColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title
                        Text(
                            text = track.title,
                            color = if (isCurrent) accentColor else textPrimary,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(2f),
                        )

                        // Artist
                        Text(
                            text = track.artist.ifBlank { "Unknown Artist" },
                            color = textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.4f),
                        )

                        // Album
                        Text(
                            text = track.album.ifBlank { "—" },
                            color = textSecondary.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.2f),
                        )

                        // Format Badge
                        val isSupported = track.mimeType == "audio/mpeg" || track.mimeType == "audio/wav" || track.mimeType == "audio/x-wav"
                        val badgeBg = if (isSupported) Color(0xFF232840) else Color(0xFF3B1E22)
                        val badgeColor = if (isSupported) accentColor else Color(0xFFFF6B6B)
                        Box(
                            modifier = Modifier
                                .width(76.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = track.mimeType.substringAfter("/").uppercase(),
                                color = badgeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        // Duration
                        val durationSec = track.durationMs / 1000
                        val min = durationSec / 60
                        val sec = durationSec % 60
                        Text(
                            text = "%02d:%02d".format(min, sec),
                            color = textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.width(66.dp),
                            textAlign = TextAlign.End,
                        )

                        // Favorite toggle
                        IconButton(
                            onClick = { onToggleFavorite(track) },
                            modifier = Modifier.width(44.dp).size(28.dp),
                        ) {
                            Icon(
                                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (track.isFavorite) Color(0xFFFF4B72) else textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
