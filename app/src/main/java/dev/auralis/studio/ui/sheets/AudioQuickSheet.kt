package dev.auralis.studio.ui.sheets

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auralis.studio.R
import dev.auralis.studio.core.model.AudioConfig
import dev.auralis.studio.core.model.PitchTempoMode
import dev.auralis.studio.designsystem.LocalAuralisTokens
import kotlinx.coroutines.launch

/**
 * Audio Quick Sheet — a `ModalBottomSheet` for fast adjustments from the Now Playing screen.
 *
 * Controls exposed: Pitch/Speed mode selector, pitch step-buttons, speed slider,
 * 8D toggle, reverb, safe-limiter, EQ presets quick-access.
 * Links to the full Audio Lab for the comprehensive EQ view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQuickSheet(
    config: AudioConfig,
    onConfigChange: (AudioConfig) -> Unit,
    onOpenAudioLab: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope      = rememberCoroutineScope()
    val colors     = MaterialTheme.colorScheme
    val tokens     = LocalAuralisTokens.current

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { dismiss() },
        containerColor  = colors.surface,
        contentColor    = colors.onSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header ─────────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = colors.primary)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text("Audio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                }
                TextButton(onClick = { dismiss(); onOpenAudioLab() }) {
                    Text(stringResource(R.string.audio_lab), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                }
            }
            HorizontalDivider(color = colors.outline.copy(0.15f))

            // ── Pitch/Speed mode ──────────────────────────────────────────
            Text(stringResource(R.string.pitch_tempo), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            LazyRow(contentPadding = PaddingValues(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PitchTempoMode.entries) { mode ->
                    FilterChip(
                        selected = config.mode == mode,
                        onClick  = { onConfigChange(config.copy(mode = mode)) },
                        label    = { Text(modeLabel(mode)) },
                    )
                }
            }

            // ── Pitch stepper ─────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Pitch · ${if (config.pitchSemitones >= 0) "+" else ""}${"%.2f".format(config.pitchSemitones)} st",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onConfigChange(config.copy(pitchSemitones = (config.pitchSemitones - 1).coerceIn(-12f, 12f))) }) { Text("−1") }
                    TextButton(onClick = { onConfigChange(config.copy(pitchSemitones = (config.pitchSemitones - 0.5f).coerceIn(-12f, 12f))) }) { Text("−½") }
                    TextButton(onClick = { onConfigChange(config.copy(pitchSemitones = (config.pitchSemitones + 0.5f).coerceIn(-12f, 12f))) }) { Text("+½") }
                    TextButton(onClick = { onConfigChange(config.copy(pitchSemitones = (config.pitchSemitones + 1).coerceIn(-12f, 12f))) }) { Text("+1") }
                    TextButton(onClick = { onConfigChange(config.copy(pitchSemitones = 0f)) }) { Text(stringResource(R.string.reset)) }
                }
            }

            // ── Speed slider ──────────────────────────────────────────────
            Text(
                "Speed · ${"%.2f".format(config.speed)}×",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
            Slider(
                value       = config.speed,
                onValueChange = { onConfigChange(config.copy(speed = it)) },
                valueRange  = 0.25f..2.5f,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { spd ->
                    FilterChip(
                        selected = kotlin.math.abs(config.speed - spd) < 0.01f,
                        onClick  = { onConfigChange(config.copy(speed = spd)) },
                        label    = { Text("${spd}×") },
                    )
                }
            }

            HorizontalDivider(color = colors.outline.copy(0.15f))

            // ── 8D + Reverb toggles ───────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.eight_d_audio), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                    Text("Spatial orbit effect", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Switch(
                    checked         = config.eightDEnabled,
                    onCheckedChange = { onConfigChange(config.copy(eightDEnabled = it)) },
                )
            }
            AnimatedVisibility(visible = config.eightDEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Strength · ${(config.eightDStrength * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.onSurface.copy(0.7f))
                    Slider(config.eightDStrength, { onConfigChange(config.copy(eightDStrength = it)) }, valueRange = 0.1f..1f)
                }
            }

            // Reverb
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.reverb), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                    Text("Room reverb wet mix", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Switch(
                    checked         = config.reverbMix > 0.01f,
                    onCheckedChange = { onConfigChange(config.copy(reverbMix = if (it) 0.25f else 0f)) },
                )
            }
            AnimatedVisibility(visible = config.reverbMix > 0.01f, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Wet mix · ${(config.reverbMix * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.onSurface.copy(0.7f))
                    Slider(config.reverbMix, { onConfigChange(config.copy(reverbMix = it)) }, valueRange = 0f..0.6f)
                }
            }

            // Limiter
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.safe_limiter), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                    Text(stringResource(R.string.limiter_description), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Switch(
                    checked         = config.limiterEnabled,
                    onCheckedChange = { onConfigChange(config.copy(limiterEnabled = it)) },
                )
            }

            HorizontalDivider(color = colors.outline.copy(0.15f))

            // ── Quick preset strip ────────────────────────────────────────
            Text("Quick presets", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            LazyRow(contentPadding = PaddingValues(vertical = 2.dp, horizontal = 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AudioQuickPreset.entries) { preset ->
                    FilterChip(
                        selected = preset.matches(config),
                        onClick  = { onConfigChange(preset.apply(config)) },
                        label    = { Text(preset.label) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun modeLabel(mode: PitchTempoMode): String = when (mode) {
    PitchTempoMode.INDEPENDENT -> "Independent"
    PitchTempoMode.TEMPO_LOCK  -> "Tempo lock"
    PitchTempoMode.VINYL       -> "Vinyl"
}

// ── Quick presets ─────────────────────────────────────────────────────────────
private enum class AudioQuickPreset(val label: String) {
    ORIGINAL("Original"),
    SLOWED("Slowed"),
    SLOWED_REVERB("Slowed+Reverb"),
    NIGHTCORE("Nightcore"),
    EIGHT_D("8D"),
    DEEP_BASS("Deep Bass");

    fun apply(base: AudioConfig): AudioConfig = when (this) {
        ORIGINAL     -> AudioConfig(mode = base.mode)
        SLOWED       -> AudioConfig(speed = 0.8f, pitchSemitones = -2f, mode = PitchTempoMode.INDEPENDENT)
        SLOWED_REVERB -> AudioConfig(speed = 0.8f, pitchSemitones = -2f, reverbMix = 0.30f, mode = PitchTempoMode.INDEPENDENT)
        NIGHTCORE    -> AudioConfig(speed = 1.25f, pitchSemitones = 3f, mode = PitchTempoMode.INDEPENDENT)
        EIGHT_D      -> AudioConfig(eightDEnabled = true, eightDStrength = 0.85f, mode = base.mode)
        DEEP_BASS    -> AudioConfig(eqBandsDb = AudioConfig().eqBandsDb.toMutableList().also { it[0] = 6f; it[1] = 4f }.toList(), mode = base.mode)
    }

    fun matches(c: AudioConfig): Boolean = when (this) {
        ORIGINAL     -> c.speed == 1f && c.pitchSemitones == 0f && !c.eightDEnabled && c.reverbMix < 0.01f
        SLOWED       -> kotlin.math.abs(c.speed - 0.8f) < 0.01f && kotlin.math.abs(c.pitchSemitones - (-2f)) < 0.1f
        SLOWED_REVERB -> kotlin.math.abs(c.speed - 0.8f) < 0.01f && c.reverbMix > 0.01f
        NIGHTCORE    -> kotlin.math.abs(c.speed - 1.25f) < 0.01f && c.pitchSemitones > 0
        EIGHT_D      -> c.eightDEnabled && kotlin.math.abs(c.speed - 1f) < 0.01f
        DEEP_BASS    -> false // complex match not needed
    }
}
