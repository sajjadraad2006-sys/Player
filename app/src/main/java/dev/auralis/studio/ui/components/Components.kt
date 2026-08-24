package dev.auralis.studio.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.auralis.studio.R
import dev.auralis.studio.core.database.PlaylistEntity
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.designsystem.LocalAuralisTokens
import dev.auralis.studio.designsystem.LocalThemeSettings

// ─────────────────────────────────────────────────────────────────────────────
// GlassCard — primary card surface with proper glass/frosted appearance
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = LocalAuralisTokens.current
    val colors = MaterialTheme.colorScheme
    val shape  = RoundedCornerShape(tokens.cardCorner.dp)
    val accent = colors.primary

    // Build gradient overlay for glass depth
    val containerColor = if (tokens.gradientOverlay) {
        colors.surface.copy(alpha = tokens.surfaceAlpha)
    } else {
        colors.surface.copy(alpha = tokens.surfaceAlpha)
    }

    // Border color — use accent tint for glass/neon, neutral for others
    val borderColor = if (tokens.accentGlowAlpha > 0f) {
        accent.copy(alpha = tokens.cardBorderAlpha)
    } else {
        colors.outline.copy(alpha = tokens.cardBorderAlpha)
    }

    val cardModifier = if (onClick != null) modifier.clip(shape).clickable(onClick = onClick) else modifier

    Card(
        modifier  = cardModifier,
        shape     = shape,
        colors    = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor   = colors.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = tokens.cardElevation),
        border    = if (tokens.cardBorderWidth > 0.dp) {
            BorderStroke(tokens.cardBorderWidth, borderColor)
        } else null,
    ) {
        if (tokens.gradientOverlay) {
            // Subtle gradient overlay inside the card for glass depth
            Box(
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.03f),
                            Color.Transparent,
                            colors.secondary.copy(alpha = 0.02f),
                        ),
                    ),
                ),
            ) { content() }
        } else {
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GlassSurface — flat surface (no elevation, for full-screen layering)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    alpha: Float = LocalAuralisTokens.current.surfaceAlpha,
    content: @Composable () -> Unit,
) {
    val tokens = LocalAuralisTokens.current
    val color  = MaterialTheme.colorScheme.surface.copy(alpha = alpha)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(tokens.cardCorner.dp))
            .background(color),
    ) { content() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Contrast scrim used under glass surfaces over artwork backgrounds
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ArtworkContrastScrim(modifier: Modifier = Modifier) {
    val tokens = LocalAuralisTokens.current
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = tokens.glassContrastScrimAlpha * 0.4f),
                    Color.Black.copy(alpha = tokens.glassContrastScrimAlpha * 0.8f),
                    Color.Black.copy(alpha = tokens.glassContrastScrimAlpha),
                ),
            ),
        ),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// TrackArtwork — album art with theme-dependent corner
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TrackArtwork(track: Track?, modifier: Modifier = Modifier) {
    val tokens = LocalAuralisTokens.current
    val shape  = RoundedCornerShape(tokens.cardCorner.dp)
    if (track?.artworkUri != null) {
        AsyncImage(
            model           = track.artworkUri,
            contentDescription = track.album,
            contentScale    = ContentScale.Crop,
            modifier        = modifier.clip(shape),
        )
    } else {
        Box(
            modifier         = modifier
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TrackRow — compact list item with improved visuals
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TrackRow(
    track: Track,
    isFavorite: Boolean,
    playlists: List<PlaylistEntity>,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(track, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                style     = MaterialTheme.typography.titleMedium,
                color     = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOf(track.artist, track.album).filter(String::isNotBlank).joinToString(" · "),
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            DateUtils.formatElapsedTime(track.durationMs / 1000),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedContent(
            targetState = isFavorite,
            transitionSpec = {
                (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith fadeOut()
            },
            label = "favorite_icon",
        ) { fav ->
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(if (fav) R.string.remove_favorite else R.string.favorite),
                    tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_actions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.play_next)) },
                    onClick = { menuExpanded = false; onPlayNext() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_to_queue)) },
                    onClick = { menuExpanded = false; onAddToQueue() },
                )
                playlists.forEach { playlist ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_to_playlist) + ": " + playlist.name) },
                        onClick = { menuExpanded = false; onAddToPlaylist(playlist.id) },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SectionHeader
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        action?.invoke()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small dot page indicator for the 3-page Now Playing pager
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PagerDotIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    dotSize: Dp = 6.dp,
    selectedSize: Dp = 8.dp,
) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val size       = if (isSelected) selectedSize else dotSize
            val color      = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
            }
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
