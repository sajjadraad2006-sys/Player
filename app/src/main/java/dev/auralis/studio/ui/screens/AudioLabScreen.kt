package dev.auralis.studio.ui.screens

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.auralis.studio.R
import dev.auralis.studio.core.model.AudioConfig
import dev.auralis.studio.core.model.BuiltInPresets
import dev.auralis.studio.core.model.ChannelMode
import dev.auralis.studio.core.model.OrbitDirection
import dev.auralis.studio.core.model.PitchTempoMode
import dev.auralis.studio.ui.components.GlassCard
import dev.auralis.studio.ui.components.SectionHeader
import kotlin.math.roundToInt

@Composable
fun AudioLabScreen(
    config: AudioConfig,
    onChange: (AudioConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var eqExpanded by remember { mutableStateOf(false) }
    var stereoExpanded by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(stringResource(R.string.audio_lab), style = MaterialTheme.typography.headlineLarge) }
        item {
            SectionHeader(stringResource(R.string.quick_presets))
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BuiltInPresets.all) { preset ->
                    FilterChip(
                        selected = config == preset.config,
                        onClick = { onChange(preset.config) },
                        label = { Text(presetLabel(preset.name)) },
                    )
                }
            }
        }
        item { DspPanel(config, onChange) }
        item { PitchTempoPanel(config, onChange) }
        item { EightDPanel(config, onChange) }
        item {
            ExpandablePanel(stringResource(R.string.equalizer), eqExpanded, { eqExpanded = !eqExpanded }) {
                EqualizerPanel(config, onChange)
            }
        }
        item {
            ExpandablePanel(stringResource(R.string.stereo_tools), stereoExpanded, { stereoExpanded = !stereoExpanded }) {
                StereoPanel(config, onChange)
            }
        }
        item { ReverbLimiterPanel(config, onChange) }
        item { SpatialStatusPanel() }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.audio_chain), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.audio_chain_value), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun DspPanel(config: AudioConfig, onChange: (AudioConfig) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingSwitch(stringResource(R.string.dsp), stringResource(R.string.dsp_bypass_description), config.dspEnabled) {
                onChange(config.copy(dspEnabled = it))
            }
            Button(onClick = { onChange(config.pure()) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pure_playback))
            }
            Text(stringResource(R.string.pure_playback_description), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PitchTempoPanel(config: AudioConfig, onChange: (AudioConfig) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pitch_tempo), style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PitchTempoMode.entries) { mode ->
                    FilterChip(
                        selected = config.mode == mode,
                        onClick = { onChange(config.copy(mode = mode, pitchSemitones = if (mode == PitchTempoMode.TEMPO_LOCK) 0f else config.pitchSemitones, dspEnabled = true)) },
                        label = { Text(modeLabel(mode)) },
                    )
                }
            }
            Text(modeDescription(config.mode), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
            Text(
                if (config.mode == PitchTempoMode.VINYL) stringResource(R.string.effective_pitch_value, config.effectivePitchSemitones)
                else stringResource(R.string.pitch_value, config.pitchSemitones),
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = if (config.mode == PitchTempoMode.VINYL) config.effectivePitchSemitones else config.pitchSemitones,
                onValueChange = { onChange(config.copy(pitchSemitones = (it * 10f).roundToInt() / 10f, dspEnabled = true)) },
                valueRange = -12f..12f,
                steps = 239,
                enabled = config.mode == PitchTempoMode.INDEPENDENT,
            )
            FineControls(
                onMinus = { onChange(config.copy(pitchSemitones = (config.pitchSemitones - 0.1f).coerceAtLeast(-12f), dspEnabled = true)) },
                onReset = { onChange(config.copy(pitchSemitones = 0f)) },
                onPlus = { onChange(config.copy(pitchSemitones = (config.pitchSemitones + 0.1f).coerceAtMost(12f), dspEnabled = true)) },
                minusLabel = stringResource(R.string.decrease_pitch),
                plusLabel = stringResource(R.string.increase_pitch),
                enabled = config.mode == PitchTempoMode.INDEPENDENT,
            )
            if (config.mode == PitchTempoMode.INDEPENDENT) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(-12f, -7f, -5f, -3f, -2f, -1f, 0f, 1f, 2f, 3f, 5f, 7f, 12f)) { value ->
                        TextButton(onClick = { onChange(config.copy(pitchSemitones = value, dspEnabled = true)) }) {
                            Text(if (value > 0) "+${value.toInt()}" else value.toInt().toString())
                        }
                    }
                }
            }
            Text(stringResource(R.string.speed_value, config.speed), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = config.speed,
                onValueChange = { onChange(config.copy(speed = (it * 100f).roundToInt() / 100f, dspEnabled = true)) },
                valueRange = 0.25f..3f,
            )
            FineControls(
                onMinus = { onChange(config.copy(speed = (config.speed - 0.01f).coerceAtLeast(0.25f), dspEnabled = true)) },
                onReset = { onChange(config.copy(speed = 1f)) },
                onPlus = { onChange(config.copy(speed = (config.speed + 0.01f).coerceAtMost(3f), dspEnabled = true)) },
                minusLabel = stringResource(R.string.decrease_speed),
                plusLabel = stringResource(R.string.increase_speed),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(0.5f, 0.6f, 0.75f, 0.8f, 0.9f, 0.95f, 1f, 1.05f, 1.1f, 1.25f, 1.33f, 1.5f, 1.75f, 2f)) { value ->
                    TextButton(onClick = { onChange(config.copy(speed = value, dspEnabled = true)) }) { Text(stringResource(R.string.speed_value, value)) }
                }
            }
        }
    }
}

@Composable
private fun EightDPanel(config: AudioConfig, onChange: (AudioConfig) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingSwitch(stringResource(R.string.eight_d_audio), stringResource(R.string.eight_d_description), config.eightDEnabled) {
                onChange(config.copy(eightDEnabled = it, dspEnabled = true))
            }
            Text(stringResource(R.string.strength) + " · " + stringResource(R.string.percent_value, (config.eightDStrength * 100).roundToInt()))
            Slider(config.eightDStrength, { onChange(config.copy(eightDStrength = it, dspEnabled = true)) }, valueRange = 0f..1f, enabled = config.eightDEnabled)
            Text(stringResource(R.string.rotation_speed) + " · " + stringResource(R.string.rate_hz, config.eightDRateHz))
            Slider(config.eightDRateHz, { onChange(config.copy(eightDRateHz = it, dspEnabled = true)) }, valueRange = 0.02f..0.5f, enabled = config.eightDEnabled)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(OrbitDirection.entries) { direction ->
                    FilterChip(
                        selected = config.orbitDirection == direction,
                        onClick = { onChange(config.copy(orbitDirection = direction, dspEnabled = true)) },
                        label = { Text(orbitLabel(direction)) },
                        enabled = config.eightDEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun EqualizerPanel(config: AudioConfig, onChange: (AudioConfig) -> Unit) {
    val frequencies = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    SettingSwitch(stringResource(R.string.equalizer), null, config.equalizerEnabled) { onChange(config.copy(equalizerEnabled = it, dspEnabled = true)) }
    GainSlider(stringResource(R.string.preamp), config.preampDb, -12f..6f) { onChange(config.copy(preampDb = it, dspEnabled = true)) }
    frequencies.forEachIndexed { index, frequency ->
        GainSlider(stringResource(R.string.frequency_hz, frequency), config.eqBandsDb[index], -12f..12f) { value ->
            val updated = config.eqBandsDb.toMutableList().also { it[index] = value }
            onChange(config.copy(eqBandsDb = updated, equalizerEnabled = true, dspEnabled = true))
        }
    }
    GainSlider(stringResource(R.string.bass), config.bassDb, -12f..12f) { onChange(config.copy(bassDb = it, dspEnabled = true)) }
    GainSlider(stringResource(R.string.treble), config.trebleDb, -12f..12f) { onChange(config.copy(trebleDb = it, dspEnabled = true)) }
    TextButton(onClick = { onChange(config.copy(eqBandsDb = List(10) { 0f }, preampDb = 0f, bassDb = 0f, trebleDb = 0f)) }) { Text(stringResource(R.string.reset)) }
}

@Composable
private fun StereoPanel(config: AudioConfig, onChange: (AudioConfig) -> Unit) {
    Text(stringResource(R.string.stereo_width) + " · " + stringResource(R.string.percent_value, (config.stereoWidth * 100).roundToInt()))
    Slider(config.stereoWidth, { onChange(config.copy(stereoWidth = it, dspEnabled = true)) }, valueRange = 0f..2f)
    Text(stringResource(R.string.balance) + " · " + stringResource(R.string.percent_value, (config.balance * 100).roundToInt()))
    Slider(config.balance, { onChange(config.copy(balance = it, dspEnabled = true)) }, valueRange = -1f..1f)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ChannelMode.entries) { channel ->
            FilterChip(
                selected = config.channelMode == channel,
                onClick = { onChange(config.copy(channelMode = channel, dspEnabled = true)) },
                label = { Text(channelLabel(channel)) },
            )
        }
    }
}

@Composable
private fun ReverbLimiterPanel(config: AudioConfig, onChange: (AudioConfig) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reverb), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.reverb_mix) + " · " + stringResource(R.string.percent_value, (config.reverbMix * 100).roundToInt()))
            Slider(config.reverbMix, { onChange(config.copy(reverbMix = it, dspEnabled = true)) }, valueRange = 0f..0.6f)
            SettingSwitch(stringResource(R.string.safe_limiter), stringResource(R.string.limiter_description), config.limiterEnabled) {
                onChange(config.copy(limiterEnabled = it, dspEnabled = true))
            }
        }
    }
}

@Composable
private fun SpatialStatusPanel() {
    val context = LocalContext.current
    val status = remember {
        if (Build.VERSION.SDK_INT >= 32) {
            val spatializer = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).spatializer
            when {
                !spatializer.isAvailable -> R.string.spatial_unavailable
                spatializer.isEnabled -> R.string.spatial_available_enabled
                else -> R.string.spatial_available_disabled
            }
        } else R.string.spatial_unavailable
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.spatial_audio), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(status), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ExpandablePanel(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onToggle) { Text(stringResource(if (expanded) R.string.collapse else R.string.expand)) }
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun GainSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text(label + " · " + stringResource(R.string.db_value, value))
    Slider(value, { onChange((it * 2f).roundToInt() / 2f) }, valueRange = range)
}

@Composable
private fun SettingSwitch(title: String, description: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)) }
        }
        Switch(checked, onChecked)
    }
}

@Composable
private fun FineControls(
    onMinus: () -> Unit,
    onReset: () -> Unit,
    onPlus: () -> Unit,
    minusLabel: String,
    plusLabel: String,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Button(onClick = onMinus, enabled = enabled, modifier = Modifier.semantics { contentDescription = minusLabel }) { Text("−", modifier = Modifier.padding(horizontal = 8.dp)) }
        TextButton(onClick = onReset, enabled = enabled) { Text(stringResource(R.string.reset)) }
        Button(onClick = onPlus, enabled = enabled, modifier = Modifier.semantics { contentDescription = plusLabel }) { Text("+", modifier = Modifier.padding(horizontal = 8.dp)) }
    }
}

@Composable
private fun modeLabel(mode: PitchTempoMode): String = stringResource(
    when (mode) {
        PitchTempoMode.INDEPENDENT -> R.string.independent
        PitchTempoMode.TEMPO_LOCK -> R.string.tempo_lock
        PitchTempoMode.VINYL -> R.string.vinyl
    },
)

@Composable
private fun modeDescription(mode: PitchTempoMode): String = stringResource(
    when (mode) {
        PitchTempoMode.INDEPENDENT -> R.string.independent_description
        PitchTempoMode.TEMPO_LOCK -> R.string.tempo_lock_description
        PitchTempoMode.VINYL -> R.string.vinyl_description
    },
)

@Composable
private fun orbitLabel(direction: OrbitDirection): String = stringResource(
    when (direction) {
        OrbitDirection.CLOCKWISE -> R.string.clockwise
        OrbitDirection.COUNTER_CLOCKWISE -> R.string.counter_clockwise
        OrbitDirection.PING_PONG -> R.string.ping_pong
    },
)

@Composable
private fun channelLabel(mode: ChannelMode): String = stringResource(
    when (mode) {
        ChannelMode.STEREO -> R.string.stereo
        ChannelMode.MONO -> R.string.mono
        ChannelMode.LEFT -> R.string.left_only
        ChannelMode.RIGHT -> R.string.right_only
        ChannelMode.SWAP -> R.string.swap_channels
    },
)
