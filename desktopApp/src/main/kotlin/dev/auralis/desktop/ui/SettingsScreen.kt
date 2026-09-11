package dev.auralis.desktop.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.desktop.storage.DesktopPaths
import dev.auralis.desktop.windows.smtc.SystemMediaController
import dev.auralis.studio.designsystem.AccentPalette
import kotlinx.coroutines.launch

/**
 * Settings Screen for desktop appearance, music folders management, language, and storage diagnostics.
 */
@Composable
fun SettingsScreen(
    isAmoled: Boolean,
    onAmoledChange: (Boolean) -> Unit,
    selectedAccent: Color,
    onAccentSelected: (Color) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    folders: List<String>,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRefreshFolder: (String) -> Unit,
    accentColor: Color,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column {
            Text("Settings & Customization", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Text("Manage music library directories, appearance, and audio engine metrics", fontSize = 12.sp, color = textSecondary)
        }

        // ── 1. Music Folders Management Card ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Indexed Music Folders", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Lyrune scans audio files inside these directories", color = textSecondary, fontSize = 11.sp)
                }

                Button(
                    onClick = onAddFolder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Add Folder", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Folder", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (folders.isEmpty()) {
                Text(
                    text = "No folders added. Click 'Add Folder' to select your music directory.",
                    color = textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                folders.forEach { folderPath ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1221))
                            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = folderPath,
                                color = textPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onRefreshFolder(folderPath) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = textSecondary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onRemoveFolder(folderPath) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── 2. Appearance & Theme Card ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                .padding(18.dp),
        ) {
            Text("Theme & Visual Style", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("True AMOLED Black", color = textPrimary, fontSize = 14.sp)
                    Text("Pure #000000 black surfaces for OLED displays", color = textSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = isAmoled,
                    onCheckedChange = onAmoledChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text("Accent Color Palette", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentPalette.PRESETS.forEach { color ->
                    val isSelected = color == selectedAccent
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onAccentSelected(color) }
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // ── 3. Localization & Language Card ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                .padding(18.dp),
        ) {
            Text("Language & Region", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("EN" to "English (LTR)", "AR" to "العربية (RTL)").forEach { (code, label) ->
                    val isSelected = language == code
                    Button(
                        onClick = { onLanguageChange(code) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) accentColor else Color(0xFF0F1221),
                            contentColor = if (isSelected) Color.Black else textPrimary,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp),
                    ) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── 4. Engine Architecture & Storage Location Card ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                .padding(18.dp),
        ) {
            Text("Desktop Engine & Storage", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))

            val smtcStatus = if (SystemMediaController.isAvailable()) "Available (In-Process WinRT)" else "Unavailable"
            val targets = listOf(
                "Persistent Storage" to DesktopPaths.appDataDir.absolutePath,
                "Windows Media (SMTC)" to smtcStatus,
                "Idle Working Set Target" to "<= 250 MiB",
                "Normal Playback Target" to "<= 350 MiB",
                "Playback + Visualizer + DSP" to "<= 400 MiB",
                "Artwork Memory Cache" to "Bounded LRU (Max 150 items)",
                "PCM Buffer Size" to "32 KiB (Strictly Bounded)",
            )

            targets.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, color = textSecondary, fontSize = 11.sp, modifier = Modifier.width(170.dp))
                    Text(
                        value,
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}
