package dev.auralis.studio.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.auralis.studio.R
import dev.auralis.studio.core.model.LyricsLine
import dev.auralis.studio.core.model.SyncedLyrics
import dev.auralis.studio.core.model.Track

/**
 * Visual Lyric Share card generator allowing listeners to select lyric lines
 * and generate a styled quote card to share on social platforms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLyricsDialog(
    track: Track?,
    lyrics: SyncedLyrics,
    initialLineIndex: Int = -1,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val selectedIndices = remember {
        mutableStateListOf<Int>().apply {
            if (initialLineIndex in lyrics.lines.indices) {
                add(initialLineIndex)
            } else if (lyrics.lines.isNotEmpty()) {
                add(0)
            }
        }
    }

    var cardThemeIndex by remember { mutableIntStateOf(0) }
    val cardGradients = listOf(
        listOf(Color(0xFF2C1654), Color(0xFF130926)), // Deep Violet Aurora
        listOf(Color(0xFF0F2B48), Color(0xFF081423)), // Midnight Ocean
        listOf(Color(0xFF4A192C), Color(0xFF1F0912)), // Rose Velvet
        listOf(Color(0xFF1B382B), Color(0xFF091710)), // Emerald Forest
        listOf(Color(0xFF1E202B), Color(0xFF0C0D12)), // Sleek Graphite
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.lyrics_share_card_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            Spacer(Modifier.height(12.dp))

            // The Rendered Lyric Share Card
            val activeGradient = cardGradients[cardThemeIndex % cardGradients.size]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.2.dp, colors.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(activeGradient))
                        .padding(22.dp),
                ) {
                    Column {
                        // Header with quote icon and song info
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (!track?.artworkUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = track?.artworkUri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track?.title ?: "Unknown Track",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track?.artist ?: "Unknown Artist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.70f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.Default.FormatQuote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.45f),
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        // Selected Lyric Lines
                        val sortedIndices = selectedIndices.sorted()
                        if (sortedIndices.isEmpty()) {
                            Text(
                                text = stringResource(R.string.lyrics_share_select_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.60f),
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        } else {
                            sortedIndices.forEach { idx ->
                                val line = lyrics.lines.getOrNull(idx)
                                if (line != null) {
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 19.sp,
                                            lineHeight = 28.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = Color.White,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Footer branding
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text(
                                text = "Lyrune Studio",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.40f),
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Card Theme Palette Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cardGradients.forEachIndexed { idx, gradient ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(if (cardThemeIndex == idx) 32.dp else 26.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(gradient))
                            .clickable { cardThemeIndex = idx }
                            .then(
                                if (cardThemeIndex == idx) Modifier.clip(CircleShape)
                                else Modifier
                            ),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Lyric line selector list
            Text(
                text = stringResource(R.string.lyrics_share_tap_to_toggle),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 170.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp),
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val isSelected = index in selectedIndices
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) colors.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable {
                                if (isSelected) {
                                    if (selectedIndices.size > 1) selectedIndices.remove(index)
                                } else {
                                    if (selectedIndices.size < 5) selectedIndices.add(index)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.primary else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Share & Copy Actions
            val sharePayload = buildString {
                val sorted = selectedIndices.sorted()
                sorted.forEach { idx ->
                    lyrics.lines.getOrNull(idx)?.let { appendLine("\"${it.text}\"") }
                }
                appendLine()
                appendLine("— ${track?.title.orEmpty()} · ${track?.artist.orEmpty()}")
                appendLine("Shared via Lyrune")
            }.trim()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Lyrics Quote", sharePayload))
                        Toast.makeText(context, context.getString(R.string.lyrics_copied), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.copy))
                }

                Button(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, sharePayload)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share)))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share))
                }
            }
        }
    }
}
