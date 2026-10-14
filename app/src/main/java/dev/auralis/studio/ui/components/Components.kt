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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
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
import coil.compose.SubcomposeAsyncImage
import dev.auralis.studio.R
import dev.auralis.studio.core.database.PlaylistEntity
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.designsystem.LocalAuralisTokens
import dev.auralis.studio.designsystem.LocalThemeSettings
import dev.auralis.studio.designsystem.SurfaceTreatment
import dev.auralis.studio.designsystem.isColorDark

// ─────────────────────────────────────────────────────────────────────────────
// GlassCard — primary card surface with proper glass/frosted appearance
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    surfaceAlpha: Float? = null,
    elevation: Dp? = null,
    content: @Composable () -> Unit,
) {
    val tokens = LocalAuralisTokens.current
    val colors = MaterialTheme.colorScheme
    val shape  = RoundedCornerShape(tokens.cardCorner.dp)
    val accent = colors.primary

    // Build gradient overlay for glass depth
    val requestedAlpha = surfaceAlpha ?: tokens.surfaceAlpha
    val containerColor = when (tokens.surfaceTreatment) {
        SurfaceTreatment.BORDERLESS -> Color.Transparent
        SurfaceTreatment.MATERIAL_TONAL -> colors.surfaceVariant
        SurfaceTreatment.CLASSIC_OUTLINE, SurfaceTreatment.RETRO_ANALOG -> colors.surface
        SurfaceTreatment.NEON_EDGE -> Color(0xFF090A16).copy(alpha = requestedAlpha)
        SurfaceTreatment.SKY_BREEZE -> colors.surface.copy(alpha = requestedAlpha)
        SurfaceTreatment.ARTWORK, SurfaceTreatment.GLASS, SurfaceTreatment.AURORA_FROST, SurfaceTreatment.NEBULA_GLOW -> colors.surface.copy(alpha = requestedAlpha)
    }

    val cardBorder = when (tokens.surfaceTreatment) {
        SurfaceTreatment.NEON_EDGE -> BorderStroke(
            tokens.cardBorderWidth,
            Brush.linearGradient(listOf(accent, colors.secondary)),
        )
        SurfaceTreatment.SKY_BREEZE -> BorderStroke(
            tokens.cardBorderWidth,
            colors.primary.copy(alpha = 0.35f),
        )
        else -> if (tokens.cardBorderWidth > 0.dp) {
            val borderColor = if (tokens.accentGlowAlpha > 0f) {
                accent.copy(alpha = tokens.cardBorderAlpha)
            } else {
                colors.outline.copy(alpha = tokens.cardBorderAlpha)
            }
            BorderStroke(tokens.cardBorderWidth, borderColor)
        } else null
    }

    val cardModifier = if (onClick != null) modifier.clip(shape).clickable(onClick = onClick) else modifier

    Card(
        modifier  = cardModifier,
        shape     = shape,
        colors    = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor   = colors.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation ?: tokens.cardElevation),
        border    = cardBorder,
    ) {
        if (tokens.gradientOverlay) {
            // Subtle gradient overlay inside the card for glass depth
            Box(
                Modifier.background(
                    Brush.verticalGradient(
                        when (tokens.surfaceTreatment) {
                            SurfaceTreatment.NEON_EDGE -> listOf(accent.copy(alpha = 0.22f), Color.Transparent, colors.secondary.copy(alpha = 0.14f))
                            SurfaceTreatment.SKY_BREEZE -> listOf(Color(0xFF38BDF8).copy(alpha = 0.12f), Color.Transparent, colors.primary.copy(alpha = 0.06f))
                            SurfaceTreatment.ARTWORK -> listOf(colors.secondary.copy(alpha = 0.12f), Color.Transparent, accent.copy(alpha = 0.08f))
                            else -> listOf(Color.White.copy(alpha = 0.07f), Color.Transparent, accent.copy(alpha = 0.035f))
                        },
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
    val scrim = if (isColorDark(MaterialTheme.colorScheme.background)) Color.Black else Color.White
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    scrim.copy(alpha = tokens.glassContrastScrimAlpha * 0.4f),
                    scrim.copy(alpha = tokens.glassContrastScrimAlpha * 0.8f),
                    scrim.copy(alpha = tokens.glassContrastScrimAlpha),
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
    val shape  = remember(tokens.artworkCorner) { RoundedCornerShape(tokens.artworkCorner.dp) }
    val rawUri = track?.artworkUri
    val model: Any? = remember(rawUri) {
        when {
            rawUri.isNullOrBlank() -> null
            rawUri.startsWith("content://") -> android.net.Uri.parse(rawUri)
            rawUri.startsWith("file://") -> {
                val path = android.net.Uri.parse(rawUri).path
                if (path != null) java.io.File(path) else android.net.Uri.parse(rawUri)
            }
            rawUri.startsWith("/") -> java.io.File(rawUri)
            else -> rawUri
        }
    }
    Box(modifier = modifier.clip(shape), contentAlignment = Alignment.Center) {
        if (model == null) {
            DefaultArtworkPlaceholder(shape, Modifier.fillMaxSize())
        } else {
            val context = androidx.compose.ui.platform.LocalContext.current
            val request = remember(model) {
                coil.request.ImageRequest.Builder(context)
                    .data(model)
                    .size(coil.size.Size(160, 160))
                    .precision(coil.size.Precision.INEXACT)
                    .allowRgb565(true)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = track?.album,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun DefaultArtworkPlaceholder(shape: RoundedCornerShape, modifier: Modifier = Modifier) {
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
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
fun MiniEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "eq_bars")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.95f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "h1",
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 0.25f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "h2",
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(520, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "h3",
    )

    Row(
        modifier = modifier.size(width = 15.dp, height = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val bar1Height = if (isPlaying) h1 else 0.35f
        val bar2Height = if (isPlaying) h2 else 0.65f
        val bar3Height = if (isPlaying) h3 else 0.45f

        Box(Modifier.width(2.5.dp).fillMaxHeight(bar1Height).clip(RoundedCornerShape(1.dp)).background(color))
        Box(Modifier.width(2.5.dp).fillMaxHeight(bar2Height).clip(RoundedCornerShape(1.dp)).background(color))
        Box(Modifier.width(2.5.dp).fillMaxHeight(bar3Height).clip(RoundedCornerShape(1.dp)).background(color))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TrackRow — compact list item with high performance rendering & active playing state
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
    isPlaying: Boolean = false,
    maxTitleLines: Int = 2,
    onRemove: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val activeBg = if (isPlaying) colors.primary.copy(alpha = 0.08f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(activeBg)
            .clickable(onClick = onPlay)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            TrackArtwork(track, Modifier.size(52.dp))
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                ) {
                    MiniEqualizerBars(isPlaying = true, color = colors.primary)
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines  = maxTitleLines,
                overflow  = TextOverflow.Ellipsis,
                style     = MaterialTheme.typography.titleMedium,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                color     = if (isPlaying) colors.primary else colors.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOf(track.artist, track.album).filter(String::isNotBlank).joinToString(" · "),
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                style     = MaterialTheme.typography.bodySmall,
                color     = colors.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(6.dp))

        Text(
            DateUtils.formatElapsedTime(track.durationMs / 1000),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.8f),
        )

        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = stringResource(if (isFavorite) R.string.remove_favorite else R.string.favorite),
                tint = if (isFavorite) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(19.dp),
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_actions),
                    tint = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(19.dp),
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
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remove_from_playlist)) },
                            onClick = { menuExpanded = false; onRemove() },
                        )
                    }
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

// ─────────────────────────────────────────────────────────────────────────────
// Universal bounce click modifier for interactive tactile feedback
// ─────────────────────────────────────────────────────────────────────────────
fun Modifier.bounceClick(
    interactionSource: MutableInteractionSource? = null,
    scaleDown: Float = 0.96f,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bounce_click_scale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
