package dev.auralis.studio.ui.sheets

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.auralis.studio.R
import dev.auralis.studio.core.database.DownloadEntity
import dev.auralis.studio.core.database.DownloadStatus
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.library.RecordingDownloadMatcher
import dev.auralis.studio.online.UnifiedTrack
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.TrackArtwork
import dev.auralis.studio.ui.components.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsSheet(
    currentTrack: Track?,
    offlineRecommendations: List<Track>,
    onlineRecommendations: List<UnifiedTrack>,
    isSearchingOnline: Boolean,
    downloads: List<DownloadEntity>,
    onPlayOffline: (Track, List<Track>) -> Unit,
    onPlayOnline: (UnifiedTrack) -> Unit,
    onDownloadOnline: (UnifiedTrack) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val colors = MaterialTheme.colorScheme
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Header: Title + Context + Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.smart_recommendations),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        if (currentTrack != null) {
                            Text(
                                text = stringResource(R.string.based_on_track, currentTrack.title),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Segmented Tabs: Offline vs Online
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Offline Tab
                val isOffline = selectedTab == 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isOffline) colors.primary else colors.surfaceVariant.copy(alpha = 0.6f))
                        .clickable { selectedTab = 0 }
                        .bounceClick()
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isOffline) colors.onPrimary else colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${stringResource(R.string.tab_offline)} (${offlineRecommendations.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isOffline) FontWeight.Bold else FontWeight.Medium,
                            color = if (isOffline) colors.onPrimary else colors.onSurface,
                        )
                    }
                }

                // Online Tab
                val isOnline = selectedTab == 1
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isOnline) colors.primary else colors.surfaceVariant.copy(alpha = 0.6f))
                        .clickable { selectedTab = 1 }
                        .bounceClick()
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isOnline) colors.onPrimary else colors.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${stringResource(R.string.tab_online)} (${onlineRecommendations.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isOnline) FontWeight.Bold else FontWeight.Medium,
                            color = if (isOnline) colors.onPrimary else colors.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Tab Content
            if (selectedTab == 0) {
                // Offline Local Recommendations
                if (offlineRecommendations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.no_offline_recommendations),
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
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(offlineRecommendations, key = { "sheet_offline_${it.id}_${it.uri}" }) { track ->
                            OfflineRecommendationRow(
                                track = track,
                                onPlay = { onPlayOffline(track, offlineRecommendations) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                            )
                        }
                    }
                }
            } else {
                // Online YouTube Recommendations
                if (isSearchingOnline && onlineRecommendations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.fetching_online_recommendations),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                } else if (onlineRecommendations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.no_online_recommendations),
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
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(onlineRecommendations, key = { "sheet_online_${it.uiKey}" }) { onlineTrack ->
                            val downloadStatus = RecordingDownloadMatcher.status(onlineTrack, downloads)
                            OnlineRecommendationRow(
                                track = onlineTrack,
                                downloadStatus = downloadStatus,
                                onPlay = { onPlayOnline(onlineTrack) },
                                onDownload = { onDownloadOnline(onlineTrack) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineRecommendationRow(
    track: Track,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var menuExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onPlay,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(track = track, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)))

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOf(track.artist, DateUtils.formatElapsedTime(track.durationMs / 1000))
                        .filter(String::isNotBlank).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(6.dp))

            FilledIconButton(
                onClick = onPlay,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play), modifier = Modifier.size(18.dp))
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_actions),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (menuExpanded) {
                    DropdownMenu(expanded = true, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.play_next)) },
                            onClick = { menuExpanded = false; onPlayNext() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_to_queue)) },
                            onClick = { menuExpanded = false; onAddToQueue() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineRecommendationRow(
    track: UnifiedTrack,
    downloadStatus: String?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onPlay,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Artwork
            if (track.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = colors.primary)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOf(track.artist, formatClockDuration(track.durationMs))
                        .filter(String::isNotBlank).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(6.dp))

            // Play Button
            FilledIconButton(
                onClick = onPlay,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play), modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.width(4.dp))

            // Download Button
            IconButton(
                onClick = onDownload,
                enabled = downloadStatus != DownloadStatus.COMPLETED.name && downloadStatus != DownloadStatus.DOWNLOADING.name,
                modifier = Modifier.size(36.dp),
            ) {
                when (downloadStatus) {
                    DownloadStatus.COMPLETED.name -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.downloaded),
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    DownloadStatus.DOWNLOADING.name -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary,
                    )
                    else -> Icon(
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

private fun formatClockDuration(ms: Long): String {
    if (ms <= 0) return ""
    return DateUtils.formatElapsedTime(ms / 1000)
}
