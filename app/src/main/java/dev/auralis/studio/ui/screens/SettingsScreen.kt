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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dev.auralis.studio.BuildConfig
import dev.auralis.studio.R
import dev.auralis.studio.core.model.AppearanceMode
import dev.auralis.studio.core.model.AppLanguage
import dev.auralis.studio.core.model.CornerStyle
import dev.auralis.studio.core.model.ThemeSettings
import dev.auralis.studio.core.model.ThemeStyle
import dev.auralis.studio.core.model.VisualQuality
import dev.auralis.studio.designsystem.AccentPalette
import dev.auralis.studio.designsystem.HexColorParser
import dev.auralis.studio.designsystem.AuralisFilterChip as FilterChip
import dev.auralis.studio.designsystem.AuralisSlider as Slider
import dev.auralis.studio.designsystem.AuralisSwitch as Switch
import dev.auralis.studio.designsystem.isColorDark
import dev.auralis.studio.online.DownloadQuality
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.SectionHeader
import kotlin.math.roundToInt

// ─── Sub-screen navigation ────────────────────────────────────────────────────
private enum class SettingsSection { ROOT, APPEARANCE, LANGUAGE, LIBRARY, PROVIDERS, PRIVACY, ABOUT }

@Composable
fun SettingsScreen(
    theme: ThemeSettings,
    folders: Set<String>,
    minimumDurationMs: Long,
    notificationPermissionGranted: Boolean,
    downloadLocation: String,
    downloadQuality: DownloadQuality,
    onThemeChange: (ThemeSettings) -> Unit,
    onMinimumDuration: (Long) -> Unit,
    onChooseFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRequestNotifications: () -> Unit,
    onChooseDownloadFolder: () -> Unit,
    onResetDownloadFolder: () -> Unit,
    onDownloadQualityChange: (DownloadQuality) -> Unit,
    brokenDownloads: List<dev.auralis.studio.library.DownloadRepairManager.BrokenDownload> = emptyList(),
    isScanningBrokenDownloads: Boolean = false,
    onScanBrokenDownloads: () -> Unit = {},
    onRemoveBrokenDownload: (dev.auralis.studio.library.DownloadRepairManager.BrokenDownload) -> Unit = {},
    onKeepSuspiciousFile: (Long) -> Unit = {},
    onRepairDownloadMetadata: () -> Unit = {},
    metadataRepairMessage: String? = null,
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
        SettingsSection.LANGUAGE -> LanguageSection(
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
            brokenDownloads = brokenDownloads,
            isScanningBroken = isScanningBrokenDownloads,
            onScanBrokenDownloads = onScanBrokenDownloads,
            onRemoveBrokenDownload = onRemoveBrokenDownload,
            onKeepSuspiciousFile = onKeepSuspiciousFile,
            onRepairMetadata = onRepairDownloadMetadata,
            metadataRepairMessage = metadataRepairMessage,
            onBack = { section = SettingsSection.ROOT },
            modifier = modifier,
        )
        SettingsSection.PROVIDERS -> ProvidersSection(
            downloadQuality = downloadQuality,
            onDownloadQualityChange = onDownloadQualityChange,
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
                    NavRow(Icons.Default.Palette, stringResource(R.string.appearance), stringResource(R.string.appearance_summary)) { onNavigate(SettingsSection.APPEARANCE) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.42f))
                    NavRow(Icons.Default.Language, stringResource(R.string.language), stringResource(R.string.language_summary)) { onNavigate(SettingsSection.LANGUAGE) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.42f))
                    NavRow(Icons.Default.Storage, stringResource(R.string.library_settings), stringResource(R.string.library_summary)) { onNavigate(SettingsSection.LIBRARY) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.42f))
                    NavRow(Icons.Default.Tune, stringResource(R.string.online_providers), stringResource(R.string.online_providers_summary)) { onNavigate(SettingsSection.PROVIDERS) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.42f))
                    NavRow(Icons.Default.PrivacyTip, stringResource(R.string.privacy), stringResource(R.string.privacy_summary)) { onNavigate(SettingsSection.PRIVACY) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.42f))
                    NavRow(Icons.Default.Info, stringResource(R.string.about), stringResource(R.string.about_summary)) { onNavigate(SettingsSection.ABOUT) }
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
private fun LanguageSection(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val selected = AppLanguage.fromLanguageTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.language), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        item {
            SettingsCard(stringResource(R.string.app_language)) {
                Text(stringResource(R.string.language_change_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = selected == language,
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(language.languageTag),
                            )
                        },
                        label = { Text(stringResource(language.labelResource())) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun AppLanguage.labelResource(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.system_default
    AppLanguage.ENGLISH -> R.string.english
    AppLanguage.ARABIC -> R.string.arabic
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
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
            }
        }

        // Theme Style
        item {
            SettingsCard(stringResource(R.string.theme_style)) {
                Text(stringResource(R.string.theme_style_description), style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.65f))
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
                Text(stringResource(R.string.appearance_mode_description), style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.65f))
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
            AccentColorSettingCard(theme = theme, onThemeChange = onThemeChange)
        }



        // Corner style
        item {
            SettingsCard(stringResource(R.string.corner_radius)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(dev.auralis.studio.core.model.CornerStyle.entries) { style ->
                        FilterChip(
                            selected = theme.cornerStyle == style,
                            onClick  = { onThemeChange(theme.copy(cornerStyle = style)) },
                            label    = { Text(cornerLabel(style)) },
                        )
                    }
                }
            }
        }

        // Visual quality
        item {
            SettingsCard(stringResource(R.string.visual_quality)) {
                Text(stringResource(R.string.visual_quality_description), style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.65f))
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
            SettingsCard(stringResource(R.string.background_artwork)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.album_art_background), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                        Text(stringResource(R.string.album_art_background_description), style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.6f))
                    }
                    Switch(
                        checked         = theme.backgroundArtwork,
                        onCheckedChange = { onThemeChange(theme.copy(backgroundArtwork = it)) },
                    )
                }
                if (theme.backgroundArtwork) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.background_dim_value, (theme.backgroundDim * 100).roundToInt()), style = MaterialTheme.typography.labelMedium, color = colors.onSurface.copy(0.7f))
                    Slider(theme.backgroundDim.coerceAtLeast(0.50f), { onThemeChange(theme.copy(backgroundDim = it)) }, valueRange = 0.50f..0.90f)
                }
            }
        }

        // Haptics
        item {
            SettingsCard(stringResource(R.string.haptic_feedback)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.haptic_feedback_description), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
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
    brokenDownloads: List<dev.auralis.studio.library.DownloadRepairManager.BrokenDownload>,
    isScanningBroken: Boolean,
    onScanBrokenDownloads: () -> Unit,
    onRemoveBrokenDownload: (dev.auralis.studio.library.DownloadRepairManager.BrokenDownload) -> Unit,
    onKeepSuspiciousFile: (Long) -> Unit,
    onRepairMetadata: () -> Unit,
    metadataRepairMessage: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.library_settings), style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
            }
        }

        // ── Download Folder Location Card ──
        item {
            SettingsCard(stringResource(R.string.download_location)) {
                Text(
                    stringResource(R.string.current_download_folder, downloadLocation),
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
                        Text(stringResource(R.string.change_folder))
                    }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onResetDownloadFolder,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.use_default))
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
                    Text(stringResource(R.string.no_extra_folders), style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.55f))
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

        // ── Review broken downloads (Lyrune-owned preview pollution cleanup) ──
        item {
            SettingsCard(stringResource(R.string.review_broken_downloads)) {
                Text(stringResource(R.string.review_broken_downloads_summary), style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.65f))
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onScanBrokenDownloads,
                    enabled = !isScanningBroken,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isScanningBroken) stringResource(R.string.scanning) else stringResource(R.string.scan_for_broken_downloads))
                }
                if (!isScanningBroken && brokenDownloads.isEmpty()) {
                    Text(stringResource(R.string.no_broken_downloads), style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.55f))
                }
            }
        }
        items(brokenDownloads, key = { it.entity.id }) { broken ->
            BrokenDownloadCard(
                item = broken,
                onRemove = onRemoveBrokenDownload,
                onKeep = onKeepSuspiciousFile,
            )
        }

        // ── Repair download metadata ──
        item {
            SettingsCard(stringResource(R.string.repair_download_metadata)) {
                Text(stringResource(R.string.repair_download_metadata_summary), style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.65f))
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onRepairMetadata, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.repair_download_metadata))
                }
                metadataRepairMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.primary)
                }
            }
        }
    }
}

@Composable
private fun BrokenDownloadCard(
    item: dev.auralis.studio.library.DownloadRepairManager.BrokenDownload,
    onRemove: (dev.auralis.studio.library.DownloadRepairManager.BrokenDownload) -> Unit,
    onKeep: (Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var confirmRemove by remember { mutableStateOf(false) }

    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.entity.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.expected_duration_label, formatDuration(item.expectedDurationMs)),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.local_duration_label, formatDuration(item.actualDurationMs)),
                    style = MaterialTheme.typography.bodySmall, color = colors.error,
                )
                Text(item.reason, style = MaterialTheme.typography.labelSmall, color = colors.onSurface.copy(0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { confirmRemove = true }, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.remove_broken_download))
            }
            androidx.compose.material3.FilledTonalButton(onClick = { onKeep(item.entity.id) }, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.keep_file))
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_broken_download)) },
            text = { Text(stringResource(R.string.remove_download_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove(item)
                }) { Text(stringResource(R.string.remove_broken_download), color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.keep_file)) }
            },
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

// ─── Providers section ────────────────────────────────────────────────────────
@Composable
private fun ProvidersSection(
    downloadQuality: DownloadQuality,
    onDownloadQualityChange: (DownloadQuality) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val jamendoConfigured = dev.auralis.studio.BuildConfig.JAMENDO_CLIENT_ID.isNotBlank()
    val audiusKeyConfigured = dev.auralis.studio.BuildConfig.AUDIUS_API_KEY.isNotBlank()
    val youtubeKeyConfigured = dev.auralis.studio.BuildConfig.YOUTUBE_API_KEY.isNotBlank()
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.online_providers), style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
            }
        }
        item {
            SettingsCard(stringResource(R.string.provider_status)) {
                Text(stringResource(R.string.provider_status_description), style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.65f))
                Spacer(Modifier.height(8.dp))
                ProviderRow("LRCLIB", stringResource(R.string.lyrics_provider), ready = true, note = stringResource(R.string.no_api_key_lrclib))
                HorizontalDivider(color = colors.outline.copy(0.42f), modifier = Modifier.padding(vertical = 4.dp))
                ProviderRow(
                    name = "Audius",
                    role = stringResource(R.string.online_music_role),
                    ready = true,
                    note = stringResource(if (audiusKeyConfigured) R.string.api_key_configured else R.string.audius_public_access),
                )
                HorizontalDivider(color = colors.outline.copy(0.42f), modifier = Modifier.padding(vertical = 4.dp))
                ProviderRow(
                    name  = "Jamendo",
                    role  = stringResource(R.string.online_music_role),
                    ready = jamendoConfigured,
                    note  = stringResource(if (jamendoConfigured) R.string.client_id_configured else R.string.client_id_not_configured),
                )
                HorizontalDivider(color = colors.outline.copy(0.42f), modifier = Modifier.padding(vertical = 4.dp))
                ProviderRow("iTunes", stringResource(R.string.metadata_lookup), ready = true, note = stringResource(R.string.catalog_preview_only))
                HorizontalDivider(color = colors.outline.copy(0.42f), modifier = Modifier.padding(vertical = 4.dp))
                ProviderRow("Deezer", stringResource(R.string.metadata_lookup), ready = true, note = stringResource(R.string.catalog_preview_only))
                HorizontalDivider(color = colors.outline.copy(0.42f), modifier = Modifier.padding(vertical = 4.dp))
                ProviderRow(
                    "YouTube",
                    stringResource(R.string.metadata_lookup),
                    ready = false,
                    note = stringResource(if (youtubeKeyConfigured) R.string.youtube_discovery_not_enabled else R.string.youtube_not_configured),
                )
            }
        }
        item {
            SettingsCard(stringResource(R.string.download_quality)) {
                DownloadQuality.entries.forEach { quality ->
                    val label = when (quality) {
                        DownloadQuality.BEST_AVAILABLE -> R.string.quality_best
                        DownloadQuality.HIGH -> R.string.quality_high
                        DownloadQuality.DATA_SAVER -> R.string.quality_data_saver
                    }
                    FilterChip(
                        selected = downloadQuality == quality,
                        onClick = { onDownloadQualityChange(quality) },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.resolver_runtime)) {
                Text(stringResource(R.string.resolver_runtime_description), style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.65f))
                Spacer(Modifier.height(8.dp))
                ProviderRow(
                    name = stringResource(R.string.resolver_service_status),
                    role = "LrrResolverService",
                    ready = true,
                    note = stringResource(R.string.ready_isolated_process),
                )
                HorizontalDivider(color = colors.outline.copy(0.42f), modifier = Modifier.padding(vertical = 4.dp))
                ProviderRow(
                    name = stringResource(R.string.resolver_python_status),
                    role = "CPython / Chaquopy 17",
                    ready = true,
                    note = "3.14.7",
                )
                HorizontalDivider(color = colors.outline.copy(0.42f), modifier = Modifier.padding(vertical = 4.dp))
                ProviderRow(
                    name = stringResource(R.string.resolver_ytdlp_status),
                    role = "yt-dlp Python API",
                    ready = true,
                    note = "2026.08.19",
                )
                HorizontalDivider(color = colors.outline.copy(0.42f), modifier = Modifier.padding(vertical = 4.dp))
                ProviderRow(
                    name = stringResource(R.string.resolver_ejs_status),
                    role = "yt-dlp-ejs",
                    ready = true,
                    note = "0.8.0",
                )
                HorizontalDivider(color = colors.outline.copy(0.42f), modifier = Modifier.padding(vertical = 4.dp))
                ProviderRow(
                    name = stringResource(R.string.resolver_quickjs_status),
                    role = "QuickJS-NG",
                    ready = true,
                    note = "0.15.1",
                )
                HorizontalDivider(color = colors.outline.copy(0.42f), modifier = Modifier.padding(vertical = 4.dp))
                ProviderRow(
                    name = stringResource(R.string.resolver_ffmpeg_status),
                    role = "FFmpeg",
                    ready = false,
                    note = stringResource(R.string.optional_not_installed),
                )
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.enable_jamendo), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.enable_jamendo_steps),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface.copy(0.70f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.client_id_safety), style = MaterialTheme.typography.labelSmall, color = colors.primary)
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
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.privacy), style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
            }
        }
        item {
            SettingsCard(stringResource(R.string.privacy)) {
                Text(stringResource(R.string.privacy_description), color = colors.onSurface.copy(0.72f))
                Spacer(Modifier.height(4.dp))
                listOf(
                    stringResource(R.string.privacy_no_account),
                    stringResource(R.string.privacy_no_analytics),
                    stringResource(R.string.privacy_no_cloud),
                    stringResource(R.string.privacy_offline_playback),
                    stringResource(R.string.privacy_lyrics_network),
                    stringResource(R.string.privacy_no_upload),
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
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.about), style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
            }
        }
        item {
            SettingsCard(stringResource(R.string.about)) {
                Text(stringResource(R.string.version_value, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                Text(stringResource(R.string.package_name, BuildConfig.APPLICATION_ID), style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(0.60f))
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
        ThemeStyle.GLASS           -> R.string.glass
        ThemeStyle.MINIMAL         -> R.string.minimal
        ThemeStyle.MATERIAL        -> R.string.material
        ThemeStyle.NEON            -> R.string.neon
        ThemeStyle.DYNAMIC_ART     -> R.string.dynamic_art
        ThemeStyle.AURORA          -> R.string.aurora
        ThemeStyle.RETRO_VINYL     -> R.string.retro_vinyl
        ThemeStyle.MIDNIGHT_PURPLE -> R.string.midnight_purple
        ThemeStyle.CLASSIC         -> R.string.classic
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

@Composable
private fun cornerLabel(style: CornerStyle): String = stringResource(
    when (style) {
        CornerStyle.COMPACT -> R.string.compact
        CornerStyle.BALANCED -> R.string.balanced
        CornerStyle.ROUND -> R.string.round
    },
)

@Composable
private fun AccentColorSettingCard(
    theme: ThemeSettings,
    onThemeChange: (ThemeSettings) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accents = AccentPalette.PRESETS
    val currentHex = remember(theme.accentArgb) {
        HexColorParser.formatToHex(theme.accentArgb)
    }
    var customHexInput by remember(theme.accentArgb) {
        mutableStateOf(currentHex)
    }
    val parsedCustomColor = remember(customHexInput) {
        HexColorParser.parse(customHexInput)
    }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    SettingsCard(stringResource(R.string.accent_color)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(accents) { color ->
                val colorArgb = HexColorParser.fromComposeColor(color)
                val isSelected = (theme.accentArgb and 0xFFFFFFFFL) == colorArgb
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) Modifier.border(2.5.dp, colors.onSurface, CircleShape)
                            else Modifier,
                        )
                        .clickable {
                            onThemeChange(theme.copy(accentArgb = colorArgb))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isColorDark(color)) Color.White else Color(0xFF111111),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = colors.outline.copy(alpha = 0.25f))
        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.custom_hex_color),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Color swatch preview
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        parsedCustomColor?.let { Color(it.toInt()) } ?: colors.surfaceVariant
                    )
                    .border(1.dp, colors.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (parsedCustomColor == null) {
                    Text("?", color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }

            androidx.compose.material3.OutlinedTextField(
                value = customHexInput,
                onValueChange = { customHexInput = it },
                placeholder = { Text(stringResource(R.string.hex_color_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                isError = parsedCustomColor == null && customHexInput.isNotBlank(),
            )

            TextButton(
                onClick = {
                    val clipText = clipboardManager.getText()?.text
                    if (!clipText.isNullOrBlank()) {
                        customHexInput = clipText
                    }
                }
            ) {
                Text(stringResource(R.string.paste))
            }
        }

        if (parsedCustomColor == null && customHexInput.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.invalid_hex),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    onThemeChange(theme.copy(accentArgb = AccentPalette.DEFAULT_ARGB))
                }
            ) {
                Text(stringResource(R.string.reset_default))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    parsedCustomColor?.let { validArgb ->
                        onThemeChange(theme.copy(accentArgb = validArgb))
                    }
                },
                enabled = parsedCustomColor != null && (theme.accentArgb and 0xFFFFFFFFL) != parsedCustomColor,
            ) {
                Text(stringResource(R.string.apply))
            }
        }
    }
}
