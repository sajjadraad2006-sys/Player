package dev.auralis.studio.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.studio.R
import dev.auralis.studio.core.model.Track

enum class MoodVibe(
    val titleRes: Int,
    val icon: ImageVector,
    val gradient: List<Color>,
    val keywords: List<String>,
) {
    ENERGIZE(
        titleRes = R.string.mood_energize,
        icon = Icons.Default.Bolt,
        gradient = listOf(Color(0xFFFF5722), Color(0xFFFF9800)),
        keywords = listOf("dance", "remix", "beat", "upbeat", "energy", "party", "club"),
    ),
    CHILL(
        titleRes = R.string.mood_chill,
        icon = Icons.Default.Bedtime,
        gradient = listOf(Color(0xFF3F51B5), Color(0xFF00BCD4)),
        keywords = listOf("chill", "relax", "ambient", "slow", "peace", "calm", "lofi"),
    ),
    FOCUS(
        titleRes = R.string.mood_focus,
        icon = Icons.Default.Headphones,
        gradient = listOf(Color(0xFF009688), Color(0xFF4CAF50)),
        keywords = listOf("study", "piano", "instrumental", "focus", "work", "synth"),
    ),
    WORKOUT(
        titleRes = R.string.mood_workout,
        icon = Icons.Default.FitnessCenter,
        gradient = listOf(Color(0xFFE91E63), Color(0xFFFF1744)),
        keywords = listOf("workout", "gym", "bass", "trap", "hard", "rock", "fast"),
    ),
    ACOUSTIC(
        titleRes = R.string.mood_acoustic,
        icon = Icons.Default.MusicNote,
        gradient = listOf(Color(0xFF8D6E63), Color(0xFFFFB300)),
        keywords = listOf("acoustic", "guitar", "unplugged", "folk", "live", "warm"),
    ),
    LATE_NIGHT(
        titleRes = R.string.mood_late_night,
        icon = Icons.Default.NightsStay,
        gradient = listOf(Color(0xFF673AB7), Color(0xFF283593)),
        keywords = listOf("night", "dark", "rnb", "midnight", "vibes", "soul"),
    );

    fun matchesTrack(track: Track): Boolean {
        val title = track.title.lowercase()
        val artist = track.artist.lowercase()
        val album = track.album.lowercase()
        return keywords.any { kw -> title.contains(kw) || artist.contains(kw) || album.contains(kw) }
    }
}

/**
 * Interactive Mood Mosaic Discovery Hub inspired by LastWave.
 * Tapping a mood generates and launches an instant curated radio mix.
 */
@Composable
fun MoodMosaicSection(
    onPlayMoodMix: (MoodVibe) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.mood_discovery_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.mood_discovery_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onBackground.copy(alpha = 0.65f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(MoodVibe.entries) { mood ->
                Surface(
                    onClick = { onPlayMoodMix(mood) },
                    modifier = Modifier
                        .width(148.dp)
                        .height(96.dp)
                        .bounceClick(),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(mood.gradient))
                            .padding(12.dp),
                    ) {
                        // Top row with vector icon badge and mini play icon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = mood.icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        // Bottom Title
                        Text(
                            text = stringResource(mood.titleRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
            }
        }
    }
}
