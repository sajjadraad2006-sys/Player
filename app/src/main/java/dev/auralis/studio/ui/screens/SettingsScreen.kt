package dev.auralis.studio.ui.screens

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import dev.auralis.studio.BuildConfig
import dev.auralis.studio.R
import dev.auralis.studio.core.model.AppearanceMode
import dev.auralis.studio.core.model.ThemeSettings
import dev.auralis.studio.core.model.ThemeStyle
import dev.auralis.studio.core.model.VisualQuality
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.SectionHeader
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    theme: ThemeSettings,
    folders: Set<String>,
    minimumDurationMs: Long,
    notificationPermissionGranted: Boolean,
    onThemeChange: (ThemeSettings) -> Unit,
    onMinimumDuration: (Long) -> Unit,
    onChooseFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRequestNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge) }
        item {
            SettingsCard(stringResource(R.string.appearance)) {
                Text(stringResource(R.string.theme_style), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ThemeStyle.entries) { style ->
                        FilterChip(
                            selected = theme.style == style,
                            onClick = { onThemeChange(theme.copy(style = style)) },
                            label = { Text(themeLabel(style)) },
                        )
                    }
                }
                Text(stringResource(R.string.appearance_mode), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppearanceMode.entries) { appearance ->
                        FilterChip(
                            selected = theme.appearance == appearance,
                            onClick = { onThemeChange(theme.copy(appearance = appearance)) },
                            label = { Text(appearanceLabel(appearance)) },
                        )
                    }
                }
                Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf(0xFF70E1C1, 0xFF9F91FF, 0xFFFF6FAE, 0xFF57B7FF, 0xFFFFB85C).forEach { color ->
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .clickable { onThemeChange(theme.copy(accentArgb = color)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (theme.accentArgb == color) Text("✓", color = Color.Black)
                        }
                    }
                }
                Text(stringResource(R.string.glass_transparency) + " · " + stringResource(R.string.percent_value, (theme.glassAlpha * 100).roundToInt()))
                Slider(theme.glassAlpha, { onThemeChange(theme.copy(glassAlpha = it)) }, valueRange = 0.06f..0.38f)
                Text(stringResource(R.string.corner_radius) + " · " + stringResource(R.string.dp_value, theme.cornerRadius.roundToInt()))
                Slider(theme.cornerRadius, { onThemeChange(theme.copy(cornerRadius = it)) }, valueRange = 8f..32f)
                Text(stringResource(R.string.visual_quality), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(VisualQuality.entries) { quality ->
                        FilterChip(
                            selected = theme.visualQuality == quality,
                            onClick = { onThemeChange(theme.copy(visualQuality = quality)) },
                            label = { Text(qualityLabel(quality)) },
                        )
                    }
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.library_settings)) {
                Text(stringResource(R.string.ignore_shorter_than), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(0, 15, 30, 60)) { seconds ->
                        FilterChip(
                            selected = minimumDurationMs == seconds * 1_000L,
                            onClick = { onMinimumDuration(seconds * 1_000L) },
                            label = { Text(pluralStringResource(R.plurals.seconds_value, seconds, seconds)) },
                        )
                    }
                }
                Button(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.choose_folder)) }
                if (folders.isNotEmpty()) Text(stringResource(R.string.included_folders), style = MaterialTheme.typography.titleMedium)
                folders.forEach { folder ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(folder.substringAfterLast('/').take(28), modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = { onRemoveFolder(folder) }) { Text(stringResource(R.string.remove_folder)) }
                    }
                }
            }
        }
        item {
            if (Build.VERSION.SDK_INT >= 33 && !notificationPermissionGranted) {
                SettingsCard(stringResource(R.string.playback_notifications)) {
                    Text(stringResource(R.string.notification_permission_description), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                    Button(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.allow_notifications)) }
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.privacy)) {
                Text(stringResource(R.string.privacy_description), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
            }
        }
        item {
            SettingsCard(stringResource(R.string.diagnostics)) {
                Text(stringResource(R.string.android_version, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
                Text(stringResource(R.string.dsp_backend), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.dsp_backend_value), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
            }
        }
        item {
            SettingsCard(stringResource(R.string.about)) {
                Text(stringResource(R.string.version_value, BuildConfig.VERSION_NAME))
                Text(stringResource(R.string.open_source_licenses), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.licenses_summary), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
            }
        }
    }
}

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
        ThemeStyle.GLASS -> R.string.glass
        ThemeStyle.AMOLED -> R.string.amoled
        ThemeStyle.MINIMAL -> R.string.minimal
        ThemeStyle.MATERIAL -> R.string.material
        ThemeStyle.NEON -> R.string.neon
        ThemeStyle.DYNAMIC_ART -> R.string.dynamic_art
        ThemeStyle.CLASSIC -> R.string.classic
    },
)

@Composable
private fun appearanceLabel(mode: AppearanceMode): String = stringResource(
    when (mode) {
        AppearanceMode.SYSTEM -> R.string.system
        AppearanceMode.LIGHT -> R.string.light
        AppearanceMode.DARK -> R.string.dark
        AppearanceMode.AMOLED -> R.string.amoled
    },
)

@Composable
private fun qualityLabel(quality: VisualQuality): String = stringResource(
    when (quality) {
        VisualQuality.FULL -> R.string.full
        VisualQuality.BALANCED -> R.string.balanced
        VisualQuality.REDUCED -> R.string.reduced
    },
)
