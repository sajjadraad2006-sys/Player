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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import dev.auralis.studio.core.model.PitchMath
import dev.auralis.studio.designsystem.AuralisFilterChip as FilterChip
import dev.auralis.studio.designsystem.AuralisSlider as Slider
import dev.auralis.studio.designsystem.AuralisSwitch as Switch
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

    fun setPitch(semitones: Float): AudioConfig = if (config.mode == PitchTempoMode.VINYL) {
        config.copy(speed = PitchMath.semitonesToFactor(semitones).coerceIn(0.25f, 3f), dspEnabled = true)
    } else {
        config.copy(pitchSemitones = semitones.coerceIn(-12f, 12f), mode = PitchTempoMode.INDEPENDENT, dspEnabled = true)
    }

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
                    Text(stringResource(R.string.audio), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                }
                TextButton(onClick = { dismiss(); onOpenAudioLab() }) {
                    Text(stringResource(R.string.audio_lab), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                }
            }
            HorizontalDivider(color = colors.outline.copy(0.45f))

            // ── Pitch/Speed mode ──────────────────────────────────────────
            Text(stringResource(R.string.pitch_tempo), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            LazyRow(contentPadding = PaddingValues(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PitchTempoMode.entries) { mode ->
                    FilterChip(
                        selected = config.mode == mode,
                        onClick  = { onConfigChange(config.copy(mode = mode, dspEnabled = true)) },
                        label    = { Text(modeLabel(mode)) },
                    )
                }
            }

            // ── Pitch stepper ─────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.pitch_value, config.pitchSemitones),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onConfigChange(setPitch(config.effectivePitchSemitones - 1f)) }) { Text("−1") }
                    TextButton(onClick = { onConfigChange(setPitch(config.effectivePitchSemitones - 0.5f)) }) { Text("−½") }
                    TextButton(onClick = { onConfigChange(setPitch(config.effectivePitchSemitones + 0.5f)) }) { Text("+½") }
                    TextButton(onClick = { onConfigChange(setPitch(config.effectivePitchSemitones + 1f)) }) { Text("+1") }
                    TextButton(onClick = { onConfigChange(setPitch(0f)) }) { Text(stringResource(R.string.reset)) }
                }
            }

            // ── Speed slider ──────────────────────────────────────────────
            Text(
                stringResource(R.string.speed_value, config.speed),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
            Slider(
                value       = config.speed,
                onValueChange = { onConfigChange(config.copy(speed = it, dspEnabled = true)) },
                valueRange  = 0.25f..2.5f,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { spd ->
                    FilterChip(
                        selected = kotlin.math.abs(config.speed - spd) < 0.01f,
                        onClick  = { onConfigChange(config.copy(speed = spd, dspEnabled = true)) },
                        label    = { Text(stringResource(R.string.speed_value, spd)) },
                    )
                }
            }

            HorizontalDivider(color = colors.outline.copy(0.45f))

            // ── 8D + Reverb toggles ───────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.eight_d_audio), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                    Text(stringResource(R.string.spatial_orbit_effect), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Switch(
                    checked         = config.eightDEnabled,
                    onCheckedChange = { onConfigChange(config.copy(eightDEnabled = it, dspEnabled = true)) },
                )
            }
            AnimatedVisibility(visible = config.eightDEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.strength_value, (config.eightDStrength * 100).toInt()), style = MaterialTheme.typography.labelMedium, color = colors.onSurface.copy(0.7f))
                    Slider(
                        config.eightDStrength,
                        { onConfigChange(config.copy(eightDStrength = it, dspEnabled = true)) },
                        valueRange = 0f..1f,
                    )
                }
            }

            // Reverb
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.reverb), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                    Text(stringResource(R.string.room_reverb_wet_mix), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Switch(
                    checked         = config.reverbMix > 0.01f,
                    onCheckedChange = { onConfigChange(config.copy(reverbMix = if (it) 0.25f else 0f, dspEnabled = true)) },
                )
            }
            AnimatedVisibility(visible = config.reverbMix > 0.01f, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.wet_mix_value, (config.reverbMix * 100).toInt()), style = MaterialTheme.typography.labelMedium, color = colors.onSurface.copy(0.7f))
                    Slider(
                        config.reverbMix,
                        { onConfigChange(config.copy(reverbMix = it, dspEnabled = true)) },
                        valueRange = 0f..0.6f,
                    )
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
                    onCheckedChange = { onConfigChange(config.copy(limiterEnabled = it, dspEnabled = true)) },
                )
            }

            HorizontalDivider(color = colors.outline.copy(0.45f))

            // ── Quick preset strip ────────────────────────────────────────
            Text(stringResource(R.string.quick_presets), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            LazyRow(contentPadding = PaddingValues(vertical = 2.dp, horizontal = 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AudioQuickPreset.entries) { preset ->
                    FilterChip(
                        selected = preset.matches(config),
                        onClick  = { onConfigChange(preset.apply(config)) },
                        label    = { Text(quickPresetLabel(preset)) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun modeLabel(mode: PitchTempoMode): String = stringResource(when (mode) {
    PitchTempoMode.INDEPENDENT -> R.string.independent
    PitchTempoMode.TEMPO_LOCK  -> R.string.tempo_lock
    PitchTempoMode.VINYL       -> R.string.vinyl
})

// ── Quick presets ─────────────────────────────────────────────────────────────
private enum class AudioQuickPreset {
    ORIGINAL,
    SLOWED,
    SLOWED_REVERB,
    NIGHTCORE,
    EIGHT_D,
    DEEP_BASS;

    fun apply(base: AudioConfig): AudioConfig = when (this) {
        ORIGINAL      -> AudioConfig(mode = base.mode)
        SLOWED        -> AudioConfig(speed = 0.8f, pitchSemitones = -2f, mode = PitchTempoMode.INDEPENDENT)
        SLOWED_REVERB -> AudioConfig(speed = 0.8f, pitchSemitones = -2f, reverbMix = 0.30f, mode = PitchTempoMode.INDEPENDENT)
        NIGHTCORE     -> AudioConfig(speed = 1.25f, pitchSemitones = 3f, mode = PitchTempoMode.INDEPENDENT)
        EIGHT_D       -> AudioConfig(eightDEnabled = true, eightDStrength = 0.85f, mode = base.mode)
        DEEP_BASS     -> AudioConfig(equalizerEnabled = true, eqBandsDb = AudioConfig().eqBandsDb.toMutableList().also { it[0] = 6f; it[1] = 4f }.toList(), mode = base.mode)
    }

    fun matches(c: AudioConfig): Boolean = when (this) {
        ORIGINAL      -> c.speed == 1f && c.pitchSemitones == 0f && !c.eightDEnabled && c.reverbMix < 0.01f
        SLOWED        -> kotlin.math.abs(c.speed - 0.8f) < 0.01f && kotlin.math.abs(c.pitchSemitones - (-2f)) < 0.1f
        SLOWED_REVERB -> kotlin.math.abs(c.speed - 0.8f) < 0.01f && c.reverbMix > 0.01f
        NIGHTCORE     -> kotlin.math.abs(c.speed - 1.25f) < 0.01f && c.pitchSemitones > 0
        EIGHT_D       -> c.eightDEnabled && kotlin.math.abs(c.speed - 1f) < 0.01f
        DEEP_BASS     -> c.equalizerEnabled && c.eqBandsDb.firstOrNull()?.let { it >= 5.5f } == true
    }
}

@Composable
private fun quickPresetLabel(preset: AudioQuickPreset): String = stringResource(when (preset) {
    AudioQuickPreset.ORIGINAL -> R.string.preset_original
    AudioQuickPreset.SLOWED -> R.string.preset_slowed
    AudioQuickPreset.SLOWED_REVERB -> R.string.preset_slowed_reverb
    AudioQuickPreset.NIGHTCORE -> R.string.preset_nightcore
    AudioQuickPreset.EIGHT_D -> R.string.preset_8d
    AudioQuickPreset.DEEP_BASS -> R.string.preset_deep_bass
})
