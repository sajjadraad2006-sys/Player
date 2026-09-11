package dev.auralis.desktop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import dev.auralis.desktop.storage.DesktopSettings
import dev.auralis.studio.core.model.*

@Composable fun ProductSettings(settings: DesktopSettings, theme: ThemeSettings, folders: List<String>,
    onChange: (DesktopSettings) -> Unit, onTheme: (ThemeSettings) -> Unit, onAdd: () -> Unit, onRemove: (String) -> Unit, onRefresh: () -> Unit) {
    var section by remember { mutableStateOf("Appearance") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(uiText("Make Lyrune yours"), fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Appearance", "Home layout", "Language", "Library", "Search & privacy", "Audio", "About", "Diagnostics").forEach { name ->
                FilterChip(section == name, { section = name }, label = { Text(uiText(name)) })
            }
        } }
        when(section) {
            "Appearance" -> {
                item { SettingChoices("Appearance", AppearanceMode.entries, theme.appearance) { onTheme(theme.copy(appearance = it)) } }
                item { SettingChoices("Theme", ThemeStyle.entries, theme.style) { style ->
                    val colors = when(style) {
                        ThemeStyle.NEON -> 0xFF67F7CA to 0xFFDC6FFF
                        ThemeStyle.AURORA -> 0xFF9BA5FF to 0xFF66DCCC
                        ThemeStyle.SKY_BREEZE -> 0xFF65BFFF to 0xFFACE8DB
                        ThemeStyle.RETRO_SYNTH -> 0xFFE998EE to 0xFF6FB8FF
                        ThemeStyle.MINIMAL -> 0xFFBCD5CC to 0xFF9DBBB1
                        else -> 0xFF65DFBA to 0xFF8395FF
                    }
                    onTheme(theme.copy(style = style, accentArgb = colors.first, secondaryArgb = colors.second))
                } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ColorSetting("Primary accent", theme.accentArgb) { onTheme(theme.copy(accentArgb = it)) }
                    ColorSetting("Secondary accent", theme.secondaryArgb) { onTheme(theme.copy(secondaryArgb = it)) }
                } }
                item { SettingChoices("Corners", CornerStyle.entries, theme.cornerStyle) { onTheme(theme.copy(cornerStyle = it)) } }
                item { SettingChoices("Visual quality", VisualQuality.entries, theme.visualQuality) { onTheme(theme.copy(visualQuality = it)) } }
                item { SettingChoices("Motion", listOf(AnimationStyle.SMOOTH, AnimationStyle.SPRING, AnimationStyle.REDUCED), theme.animationStyle) { onTheme(theme.copy(animationStyle = it)) } }
                item { SettingSwitch("Artwork background", "A subtle glow from the current song’s artwork", theme.backgroundArtwork) { onTheme(theme.copy(backgroundArtwork = it)) } }
                item { SettingSlider("Glow softness", theme.blurStrength, 0f..1f) { onTheme(theme.copy(blurStrength = it)) } }
                item { SettingSlider("Background dim", theme.backgroundDim, 0f..1f) { onTheme(theme.copy(backgroundDim = it)) } }
            }
            "Home layout" -> {
                item { SettingSwitch("Quick categories", "Explore moods from Home", settings.homeLayout.showQuickCategories) { onChange(settings.copy(homeLayout = settings.homeLayout.copy(showQuickCategories = it))) } }
                item { SettingChoices("Layout", listOf(HomeLayoutStyle.GRID_CARDS, HomeLayoutStyle.MINIMAL_STREAM), settings.homeLayout.style) { onChange(settings.copy(homeLayout = settings.homeLayout.copy(style = it))) } }
                item { SettingSwitch("Continue listening", "Your recent songs", settings.homeLayout.showRecentlyPlayed) { onChange(settings.copy(homeLayout = settings.homeLayout.copy(showRecentlyPlayed = it))) } }
                item { SettingSwitch("Recently added", "New additions to your music folders", settings.homeLayout.showRecentTracks) { onChange(settings.copy(homeLayout = settings.homeLayout.copy(showRecentTracks = it))) } }
                item { SettingSwitch("Playlists", "Collections you created", settings.homeLayout.showPlaylists) { onChange(settings.copy(homeLayout = settings.homeLayout.copy(showPlaylists = it))) } }
                item { SettingSwitch("Shuffle shortcut", "Start a mix from your library", settings.homeLayout.showFloatingShuffle) { onChange(settings.copy(homeLayout = settings.homeLayout.copy(showFloatingShuffle = it))) } }
            }
            "Language" -> item { SettingChoices("Language & reading direction", listOf("EN", "AR"), settings.language) { onChange(settings.copy(language = it)) }; Text(uiText("English / العربية"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            "Library" -> {
                item { Row { Button(onAdd) { Text(uiText("Add music folder")) }; TextButton(onRefresh) { Text(uiText("Refresh library")) } } }
                folders.forEach { path -> item { ListItem(headlineContent = { Text(path) }, trailingContent = { TextButton({ onRemove(path) }) { Text(uiText("Remove")) } }) } }
                item { Text(uiText("Removing a folder only removes it from Lyrune. Your music files stay on disk."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            "Search & privacy" -> {
                item { SettingSwitch("Search history", "Save recent searches on this PC", settings.rememberSearches) { onChange(settings.copy(rememberSearches = it)) } }
                item { TextButton({ onChange(settings.copy(searchHistory = emptyList())) }) { Text(uiText("Clear search history")) } }
                item { SettingSwitch("Online lyrics", "Look up missing lyrics using LRCLIB", settings.onlineLyrics) { onChange(settings.copy(onlineLyrics = it)) } }
                item { TextButton({ onChange(settings.copy(recentTracks = emptyList())) }) { Text(uiText("Clear listening history")) } }
                item { Text(uiText("Online search uses YouTube. Local files and imported lyrics stay on this PC."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            "Audio" -> {
                item { SettingSlider("Volume", settings.volume, 0f..1f) { onChange(settings.copy(volume = it)) } }
                item { Text(uiText("Audio follows your Windows default output device. Shape the sound with presets, pitch, tempo, EQ and spatial controls in Audio Lab.")) }
            }
            "About" -> item { Text("LYRUNE", fontSize = 36.sp, fontWeight = FontWeight.Bold); Text(uiText("Your music. Your atmosphere."), modifier = Modifier.padding(vertical = 10.dp)); Text(uiText("Kotlin Multiplatform · Windows\nYouTube discovery · LRCLIB lyrics\nStreaming audio with FFmpeg, JLayer and libFLAC\nFFmpeg / JavaCPP: LGPL components; see bundled dependency licenses."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            "Diagnostics" -> item { Text("Playback supports MP3, WAV, FLAC, AAC/M4A, Opus/WebM and OGG/Vorbis. Audio streams through bounded buffers into the shared EQ, spatial processor and limiter.\n\nApp data: ${dev.auralis.desktop.storage.DesktopPaths.appDataDir}\nJava: ${System.getProperty("java.version")}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable private fun ColorSetting(label: String, value: Long, onChange: (Long) -> Unit) {
    var hex by remember(value) { mutableStateOf("%06X".format(value and 0xFFFFFF)) }
    Column {
        OutlinedTextField(hex, { hex = it.take(7).removePrefix("#") }, label = { Text(uiText(label)) }, singleLine = true, modifier = Modifier.width(170.dp))
        TextButton({ hex.toLongOrNull(16)?.let { onChange(0xFF000000 or it) } }, enabled = hex.length == 6 && hex.toLongOrNull(16) != null) { Text(uiText("Apply")) }
    }
}
@Composable fun <T> SettingChoices(label: String, values: List<T>, selected: T, onChange: (T) -> Unit) {
    Column {
        Text(uiText(label), fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            values.forEach { value -> FilterChip(value == selected, { onChange(value) }, label = { Text(when(value.toString()) { "EN" -> "English"; "AR" -> "العربية"; else -> uiText(value.toString().lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }) }) }
        }
    }
}
@Composable fun SettingSwitch(label: String, description: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(uiText(label), fontWeight = FontWeight.Medium); if(description.isNotBlank()) Text(uiText(description), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        Switch(value, onChange)
    }
}
@Composable fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column { Row { Text(uiText(label), modifier = Modifier.weight(1f)); Text(uiText("%.2f").format(value), color = MaterialTheme.colorScheme.primary) }; Slider(value, onChange, valueRange = range) }
}
