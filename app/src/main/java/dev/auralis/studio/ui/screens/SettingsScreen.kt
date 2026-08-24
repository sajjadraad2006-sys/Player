package dev.auralis.studio.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auralis.studio.BuildConfig
import dev.auralis.studio.R
import dev.auralis.studio.core.model.AppearanceMode
import dev.auralis.studio.core.model.CornerStyle
import dev.auralis.studio.core.model.ThemeSettings
import dev.auralis.studio.core.model.ThemeStyle
import dev.auralis.studio.core.model.VisualQuality
import dev.auralis.studio.designsystem.AccentPalette
import dev.auralis.studio.designsystem.isColorDark
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.SectionHeader
import kotlin.math.roundToInt

// ─── Sub-screen navigation ────────────────────────────────────────────────────
private enum class SettingsSection { ROOT, APPEARANCE, LIBRARY, PROVIDERS, PRIVACY, ABOUT }

@Composable
fun SettingsScreen(
    theme: ThemeSettings,
    folders: Set<String>,
    minimumDurationMs: Long,
    notificationPermissionGranted: Boolean,
    downloadLocation: String,
    onThemeChange: (ThemeSettings) -> Unit,
    onMinimumDuration: (Long) -> Unit,
    onChooseFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRequestNotifications: () -> Unit,
    onChooseDownloadFolder: () -> Unit,
    onResetDownloadFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var section by remember { mutableStateOf(SettingsSection.ROOT) }

    when (section) {
        SettingsSection.ROOT -> SettingsRoot(
            modifier = modifier,
            onNavigate = { section = it },
            notificationNeeded = Build.VERSION.SDK_INT >= 33 && !notificationPermissionGranted,
        )
        SettingsSection.APPEARANCE -> AppearanceSection(
            theme = theme,
            onThemeChange = onThemeChange,
            onBack = { section = SettingsSection.ROOT },
            modifier = modifier,
        )
        SettingsSection.LIBRARY -> LibrarySection(
            folders = folders,
            minimumDurationMs = minimumDurationMs,
            downloadLocation = downloadLocation,
            onMinimumDuration = onMinimumDuration,
            onChooseFolder = onChooseFolder,
            onRemoveFolder = onRemoveFolder,
            notificationPermissionGranted = notificationPermissionGranted,
            onRequestNotifications = onRequestNotifications,
            onChooseDownloadFolder = onChooseDownloadFolder,
            onResetDownloadFolder = onResetDownloadFolder,
            onBack = { section = SettingsSection.ROOT },
            modifier = modifier,
        )
        SettingsSection.PROVIDERS -> ProvidersSection(
            onBack = { section = SettingsSection.ROOT },
            modifier = modifier,
        )
        SettingsSection.PRIVACY -> PrivacySection(
            onBack = { section = SettingsSection.ROOT },
            modifier = modifier,
        )
        SettingsSection.ABOUT -> AboutSection(
            onBack = { section = SettingsSection.ROOT },
            modifier = modifier,
        )
    }
}

// ─── Root page ────────────────────────────────────────────────────────────────
@Composable
private fun SettingsRoot(
    onNavigate: (SettingsSection) -> Unit,
    notificationNeeded: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    NavRow(Icons.Default.Palette, stringResource(R.string.appearance), "Themes, colours, corner style") { onNavigate(SettingsSection.APPEARANCE) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
                    NavRow(Icons.Default.Storage, stringResource(R.string.library_settings), "Folders, scan filters") { onNavigate(SettingsSection.LIBRARY) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
                    NavRow(Icons.Default.Tune, "Online Providers", "Lyrics, Jamendo status") { onNavigate(SettingsSection.PROVIDERS) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
                    NavRow(Icons.Default.PrivacyTip, stringResource(R.string.privacy), "Permissions & data") { onNavigate(SettingsSection.PRIVACY) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
                    NavRow(Icons.Default.Info, stringResource(R.string.about), "Version, licenses") { onNavigate(SettingsSection.ABOUT) }
                }
            }
        }
        if (notificationNeeded) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.playback_notifications), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(stringResource(R.string.notification_permission_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.72f))
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), modifier = Modifier.size(18.dp))
    }
}

// ─── Appearance section ───────────────────────────────────────────────────────
@Composable
private fun AppearanceSection(
    theme: ThemeSettings,
    onThemeChange: (ThemeSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Back") }
                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
            }
        }

        // Theme Style
        item {
            SettingsCard(stringResource(R.string.theme_style)) {
                Text("Each style changes the visual language — not just the colour.", style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.65f))
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ThemeStyle.entries) { style ->
                        FilterChip(
                            selected = theme.style == style,
                            onClick  = { onThemeChange(theme.copy(style = style)) },
                            label    = { Text(themeLabel(style)) },
                        )
                    }
                }
            }
        }

        // Appearance Mode (separate from theme style)
        item {
            SettingsCard(stringResource(R.string.appearance_mode)) {
                Text("Light/Dark mode is separate from the theme visual style.", style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.65f))
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppearanceMode.entries) { mode ->
                        FilterChip(
                            selected = theme.appearance == mode,
                            onClick  = { onThemeChange(theme.copy(appearance = mode)) },
                            label    = { Text(appearanceLabel(mode)) },
                        )
                    }
                }
            }
        }

        // Accent colours
        item {
            SettingsCard(stringResource(R.string.accent_color)) {
                val accents = listOf(
                    AccentPalette.MINT, AccentPalette.PURPLE, AccentPalette.PINK,
                    AccentPalette.BLUE, AccentPalette.ORANGE, AccentPalette.TEAL,
                    AccentPalette.GOLD, AccentPalette.CORAL, AccentPalette.LIME,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(accents) { color ->
                        val isSelected = theme.accentArgb == color.value.toLong()
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, colors.onSurface, CircleShape)
                                    else Modifier,
                                )
                                .clickable { onThemeChange(theme.copy(accentArgb = color.value.toLong())) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint     = if (isColorDark(color)) Color.White else Color(0xFF111111),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Corner style
        item {
            SettingsCard(stringResource(R.string.corner_radius)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(dev.auralis.studio.core.model.CornerStyle.entries) { style ->
                        FilterChip(
                            selected = theme.cornerStyle == style,
                            onClick  = { onThemeChange(theme.copy(cornerStyle = style)) },
                            label    = { Text(style.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        }

        // Visual quality
        item {
            SettingsCard(stringResource(R.string.visual_quality)) {
                Text("Controls visualizer FPS and glass blur quality.", style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.65f))
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(VisualQuality.entries) { quality ->
                        FilterChip(
                            selected = theme.visualQuality == quality,
                            onClick  = { onThemeChange(theme.copy(visualQuality = quality)) },
                            label    = { Text(qualityLabel(quality)) },
                        )
                    }
                }
            }
        }

        // Background artwork toggle
        item {
            SettingsCard("Background artwork") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Album art background", style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                        Text("Blurred artwork fills the Now Playing background.", style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.6f))
                    }
                    Switch(
                        checked         = theme.backgroundArtwork,
                        onCheckedChange = { onThemeChange(theme.copy(backgroundArtwork = it)) },
                    )
                }
                if (theme.backgroundArtwork) {
                    Spacer(Modifier.height(4.dp))
                    Text("Background dim · ${(theme.backgroundDim * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.onSurface.copy(0.7f))
                    Slider(theme.backgroundDim, { onThemeChange(theme.copy(backgroundDim = it)) }, valueRange = 0.25f..0.90f)
                }
            }
        }

        // Haptics
        item {
            SettingsCard("Haptic feedback") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibrate on interactive elements", style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                    Switch(
                        checked         = theme.hapticFeedback,
                        onCheckedChange = { onThemeChange(theme.copy(hapticFeedback = it)) },
                    )
                }
            }
        }
    }
}

// ─── Library section ──────────────────────────────────────────────────────────
@Composable
private fun LibrarySection(
    folders: Set<String>,
    minimumDurationMs: Long,
    downloadLocation: String,
    onMinimumDuration: (Long) -> Unit,
    onChooseFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    notificationPermissionGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onChooseDownloadFolder: () -> Unit,
    onResetDownloadFolder: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Back") }
                Text(stringResource(R.string.library_settings), style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
            }
        }

        // ── Download Folder Location Card ──
        item {
            SettingsCard("مسار تنزيل الموسيقى (Download Location)") {
                Text(
                    "المجلد الحالي: $downloadLocation",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onChooseDownloadFolder,
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("تغيير المجلد")
                    }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onResetDownloadFolder,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("الافتراضي")
                    }
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.ignore_shorter_than)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(0, 15, 30, 60)) { seconds ->
                        FilterChip(
                            selected = minimumDurationMs == seconds * 1_000L,
                            onClick  = { onMinimumDuration(seconds * 1_000L) },
                            label    = { Text(pluralStringResource(R.plurals.seconds_value, seconds, seconds)) },
                        )
                    }
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.included_folders)) {
                Button(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.choose_folder)) }
                if (folders.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    folders.forEach { folder ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(folder.substringAfterLast('/').take(32), modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                            TextButton(onClick = { onRemoveFolder(folder) }) { Text(stringResource(R.string.remove_folder)) }
                        }
                    }
                } else {
                    Text("No extra folders selected. Auralis will use MediaStore if you granted music access.", style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.55f))
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && !notificationPermissionGranted) {
            item {
                SettingsCard(stringResource(R.string.playback_notifications)) {
                    Text(stringResource(R.string.notification_permission_description), color = colors.onSurface.copy(0.72f))
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.allow_notifications)) }
                }
            }
        }
    }
}

// ─── Providers section ────────────────────────────────────────────────────────
@Composable
private fun ProvidersSection(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val jamendoConfigured = dev.auralis.studio.BuildConfig.JAMENDO_CLIENT_ID.isNotBlank()
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Back") }
                Text("Online Providers", style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
            }
        }
        item {
            SettingsCard("Provider Status") {
                Text("These providers are optional. Local playback and DSP never require network access.", style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.65f))
                Spacer(Modifier.height(8.dp))
                ProviderRow("LRCLIB", "Lyrics provider", ready = true, note = "No API key required · LRCLIB.net")
                HorizontalDivider(color = colors.outline.copy(0.10f), modifier = Modifier.padding(vertical = 4.dp))
                ProviderRow(
                    name  = "Jamendo",
                    role  = "Online music (search + stream + download)",
                    ready = jamendoConfigured,
                    note  = if (jamendoConfigured) "client_id configured" else "Set JAMENDO_CLIENT_ID in local.properties",
                )
                HorizontalDivider(color = colors.outline.copy(0.10f), modifier = Modifier.padding(vertical = 4.dp))
                ProviderRow("MusicBrainz", "Metadata lookup", ready = true, note = "No API key required · musicbrainz.org")
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("How to enable Jamendo", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1. Register at developer.jamendo.com\n2. Obtain a client_id\n3. Add JAMENDO_CLIENT_ID=your_key to local.properties\n4. Rebuild the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface.copy(0.70f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Your client_id is never committed to source control.", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(name: String, role: String, ready: Boolean, note: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(role, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(note, style = MaterialTheme.typography.labelSmall, color = if (ready) colors.primary else colors.error)
        }
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(if (ready) Color(0xFF4CAF50) else colors.error),
        )
    }
}

// ─── Privacy section ──────────────────────────────────────────────────────────
@Composable
private fun PrivacySection(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Back") }
                Text(stringResource(R.string.privacy), style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
            }
        }
        item {
            SettingsCard(stringResource(R.string.privacy)) {
                Text(stringResource(R.string.privacy_description), color = colors.onSurface.copy(0.72f))
                Spacer(Modifier.height(4.dp))
                listOf(
                    "No user account required",
                    "No analytics or telemetry",
                    "No cloud sync",
                    "Local playback works with no internet",
                    "Online features (lyrics, Jamendo) are user-initiated only",
                    "No audio uploaded or streamed without your action",
                ).forEach { point ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                        Text("·  ", color = colors.primary, style = MaterialTheme.typography.bodyMedium)
                        Text(point, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface.copy(0.80f))
                    }
                }
            }
        }
    }
}

// ─── About section ────────────────────────────────────────────────────────────
@Composable
private fun AboutSection(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Back") }
                Text(stringResource(R.string.about), style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
            }
        }
        item {
            SettingsCard(stringResource(R.string.about)) {
                Text(stringResource(R.string.version_value, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                Text("Package: ${BuildConfig.APPLICATION_ID}", style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.60f))
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.android_version, Build.VERSION.RELEASE, Build.VERSION.SDK_INT), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        item {
            SettingsCard(stringResource(R.string.diagnostics)) {
                Text(stringResource(R.string.dsp_backend), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(stringResource(R.string.dsp_backend_value), style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.70f))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.audio_chain), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(stringResource(R.string.audio_chain_value), style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.70f))
            }
        }
        item {
            SettingsCard(stringResource(R.string.open_source_licenses)) {
                Text(stringResource(R.string.licenses_summary), color = colors.onSurface.copy(0.72f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                listOf(
                    "AndroidX libraries — Apache 2.0",
                    "Kotlin — Apache 2.0",
                    "Media3 (ExoPlayer) — Apache 2.0",
                    "Coil — Apache 2.0",
                    "Ktor — Apache 2.0",
                    "Guava — Apache 2.0",
                    "LRCLIB — Public API, attribution appreciated",
                    "Jamendo — Developer API, CC-licensed content",
                ).forEach { license ->
                    Text("• $license", style = MaterialTheme.typography.labelMedium, color = colors.onSurface.copy(0.65f), modifier = Modifier.padding(vertical = 1.dp))
                }
            }
        }
    }
}

// ─── Shared composables ───────────────────────────────────────────────────────
@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(title)
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun themeLabel(style: ThemeStyle): String = stringResource(
    when (style) {
        ThemeStyle.GLASS       -> R.string.glass
        ThemeStyle.AMOLED      -> R.string.amoled
        ThemeStyle.MINIMAL     -> R.string.minimal
        ThemeStyle.MATERIAL    -> R.string.material
        ThemeStyle.NEON        -> R.string.neon
        ThemeStyle.DYNAMIC_ART -> R.string.dynamic_art
        ThemeStyle.CLASSIC     -> R.string.classic
    },
)

@Composable
private fun appearanceLabel(mode: AppearanceMode): String = stringResource(
    when (mode) {
        AppearanceMode.SYSTEM -> R.string.system
        AppearanceMode.LIGHT  -> R.string.light
        AppearanceMode.DARK   -> R.string.dark
        AppearanceMode.AMOLED -> R.string.amoled
    },
)

@Composable
private fun qualityLabel(quality: VisualQuality): String = stringResource(
    when (quality) {
        VisualQuality.FULL     -> R.string.full
        VisualQuality.BALANCED -> R.string.balanced
        VisualQuality.REDUCED  -> R.string.reduced
    },
)
