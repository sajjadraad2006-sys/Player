package dev.auralis.studio.ui.screens

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auralis.studio.R
import dev.auralis.studio.core.model.AudioConfig
import dev.auralis.studio.core.model.BuiltInPresets
import dev.auralis.studio.core.model.ChannelMode
import dev.auralis.studio.core.model.OrbitDirection
import dev.auralis.studio.core.model.PitchTempoMode
import dev.auralis.studio.core.model.PitchMath
import dev.auralis.studio.designsystem.AuralisFilterChip
import dev.auralis.studio.designsystem.AuralisSlider
import dev.auralis.studio.designsystem.AuralisSwitch
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
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp, 20.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                stringResource(R.string.audio_lab),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
        }
        item {
            SectionHeader(stringResource(R.string.quick_presets))
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BuiltInPresets.all) { preset ->
                    StudioChip(
                        selected = config == preset.config,
                        onClick = { onChange(preset.config) },
                        label = presetLabel(preset.name),
                    )
                }
            }
        }
        item { DspPanel(config, onChange) }
        item { StemIsolationPanel(config, onChange) }
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
                    Text(stringResource(R.string.audio_chain), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(stringResource(R.string.audio_chain_value), color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StemIsolationPanel(config: AudioConfig, onChange: (AudioConfig) -> Unit) {
    val colors = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingSwitch(
                title = stringResource(R.string.stem_isolator_unavailable),
                description = stringResource(R.string.stem_unavailable_description),
                checked = false,
                enabled = false,
            ) { }
            Text(
                stringResource(R.string.stem_future_description),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DspPanel(config: AudioConfig, onChange: (AudioConfig) -> Unit) {
    val colors = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingSwitch(stringResource(R.string.dsp), stringResource(R.string.dsp_bypass_description), config.dspEnabled) {
                onChange(config.copy(dspEnabled = it))
            }
            Text(
                stringResource(if (config.dspEnabled) R.string.dsp_on_effects_active else R.string.dsp_bypassed_pure_playback),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (config.dspEnabled) colors.primary else colors.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (config.dspEnabled) colors.primaryContainer else colors.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Button(
                onClick = { onChange(config.pure()) },
                enabled = config.dspEnabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.pure_playback), fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.pure_playback_description),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PitchTempoPanel(config: AudioConfig, onChange: (AudioConfig) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val displayedPitch = config.effectivePitchSemitones
    fun applyPitch(value: Float): AudioConfig {
        val rounded = (value * 10f).roundToInt() / 10f
        return if (config.mode == PitchTempoMode.VINYL) {
            config.copy(speed = PitchMath.semitonesToFactor(rounded).coerceIn(0.25f, 3f), dspEnabled = true)
        } else {
            config.copy(pitchSemitones = rounded, mode = PitchTempoMode.INDEPENDENT, dspEnabled = true)
        }
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pitch_tempo), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.onSurface)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PitchTempoMode.entries) { mode ->
                    StudioChip(
                        selected = config.mode == mode,
                        onClick = { onChange(config.copy(mode = mode, dspEnabled = true)) },
                        label = modeLabel(mode),
                    )
                }
            }
            Text(modeDescription(config.mode), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            
            // Pitch Header & Value
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.pitch),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(R.string.effective_pitch_value, displayedPitch),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
            }

            StudioSlider(
                value = displayedPitch.coerceIn(-12f, 12f),
                onValueChange = { onChange(applyPitch(it)) },
                valueRange = -12f..12f,
                steps = 239,
            )

            // Fine Step Buttons: [-] [Reset] [+]
            FineControls(
                onMinus = {
                    onChange(applyPitch((displayedPitch - 0.1f).coerceAtLeast(-12f)))
                },
                onReset = { onChange(applyPitch(0f)) },
                onPlus = {
                    onChange(applyPitch((displayedPitch + 0.1f).coerceAtMost(12f)))
                },
                minusLabel = stringResource(R.string.decrease_pitch),
                plusLabel = stringResource(R.string.increase_pitch),
            )

            // Quick Semitone Buttons
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(-12f, -7f, -5f, -3f, -2f, -1f, 0f, 1f, 2f, 3f, 5f, 7f, 12f)) { value ->
                    StudioChip(
                        selected = kotlin.math.abs(displayedPitch - value) < 0.05f,
                        onClick = { onChange(applyPitch(value)) },
                        label = if (value > 0) "+${value.toInt()}" else value.toInt().toString(),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Speed Header & Value
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.speed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(R.string.speed_value, config.speed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
            }

            StudioSlider(
                value = config.speed,
                onValueChange = { onChange(config.copy(speed = (it * 100f).roundToInt() / 100f, dspEnabled = true)) },
                valueRange = 0.25f..3f,
            )

            FineControls(
                onMinus = { onChange(config.copy(speed = (config.speed - 0.05f).coerceAtLeast(0.25f), dspEnabled = true)) },
                onReset = { onChange(config.copy(speed = 1f, dspEnabled = true)) },
                onPlus = { onChange(config.copy(speed = (config.speed + 0.05f).coerceAtMost(3f), dspEnabled = true)) },
                minusLabel = stringResource(R.string.decrease_speed),
                plusLabel = stringResource(R.string.increase_speed),
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(0.5f, 0.75f, 0.8f, 0.9f, 1f, 1.1f, 1.25f, 1.33f, 1.5f, 1.75f, 2f)) { value ->
                    StudioChip(
                        selected = kotlin.math.abs(config.speed - value) < 0.01f,
                        onClick = { onChange(config.copy(speed = value, dspEnabled = true)) },
                        label = stringResource(R.string.speed_value, value),
                    )
                }
            }
        }
    }
}

@Composable
private fun EightDPanel(config: AudioConfig, onChange: (AudioConfig) -> Unit) {
    val colors = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingSwitch(stringResource(R.string.eight_d_audio), stringResource(R.string.eight_d_description), config.eightDEnabled) {
                onChange(config.copy(eightDEnabled = it, dspEnabled = true))
            }
            
            // Strength Slider
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.strength),
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.percent_value, (config.eightDStrength * 100).roundToInt()),
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            StudioSlider(
                value = config.eightDStrength,
                onValueChange = { onChange(config.copy(eightDStrength = it, eightDEnabled = true, dspEnabled = true)) },
                valueRange = 0f..1f,
            )

            // Rotation Speed Slider
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.rotation_speed),
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.rate_hz, config.eightDRateHz),
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            StudioSlider(
                value = config.eightDRateHz,
                onValueChange = { onChange(config.copy(eightDRateHz = it, eightDEnabled = true, dspEnabled = true)) },
                valueRange = 0.02f..0.5f,
            )

            // Orbit Direction Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(OrbitDirection.entries) { direction ->
                    StudioChip(
                        selected = config.orbitDirection == direction,
                        onClick = { onChange(config.copy(orbitDirection = direction, eightDEnabled = true, dspEnabled = true)) },
                        label = orbitLabel(direction),
                    )
                }
            }
        }
    }
}

@Composable
private fun EqualizerPanel(config: AudioConfig, onChange: (AudioConfig) -> Unit) {
    val frequencies = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    val colors = MaterialTheme.colorScheme
    SettingSwitch(stringResource(R.string.equalizer), null, config.equalizerEnabled) { onChange(config.copy(equalizerEnabled = it, dspEnabled = true)) }
    GainSlider(stringResource(R.string.preamp), config.preampDb, -12f..6f) { onChange(config.copy(preampDb = it, equalizerEnabled = true, dspEnabled = true)) }
    frequencies.forEachIndexed { index, frequency ->
        GainSlider(stringResource(R.string.frequency_hz, frequency), config.eqBandsDb[index], -12f..12f) { value ->
            val updated = config.eqBandsDb.toMutableList().also { it[index] = value }
            onChange(config.copy(eqBandsDb = updated, equalizerEnabled = true, dspEnabled = true))
        }
    }
    GainSlider(stringResource(R.string.bass), config.bassDb, -12f..12f) { onChange(config.copy(bassDb = it, equalizerEnabled = true, dspEnabled = true)) }
    GainSlider(stringResource(R.string.treble), config.trebleDb, -12f..12f) { onChange(config.copy(trebleDb = it, equalizerEnabled = true, dspEnabled = true)) }
    TextButton(onClick = { onChange(config.copy(eqBandsDb = List(10) { 0f }, preampDb = 0f, bassDb = 0f, trebleDb = 0f)) }) {
        Text(stringResource(R.string.reset), color = colors.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StereoPanel(config: AudioConfig, onChange: (AudioConfig) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(stringResource(R.string.stereo_width) + " · " + stringResource(R.string.percent_value, (config.stereoWidth * 100).roundToInt()), color = colors.onSurface, fontWeight = FontWeight.SemiBold)
    StudioSlider(config.stereoWidth, { onChange(config.copy(stereoWidth = it, dspEnabled = true)) }, valueRange = 0f..2f)
    Text(stringResource(R.string.balance) + " · " + stringResource(R.string.percent_value, (config.balance * 100).roundToInt()), color = colors.onSurface, fontWeight = FontWeight.SemiBold)
    StudioSlider(config.balance, { onChange(config.copy(balance = it, dspEnabled = true)) }, valueRange = -1f..1f)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ChannelMode.entries) { channel ->
            StudioChip(
                selected = config.channelMode == channel,
                onClick = { onChange(config.copy(channelMode = channel, dspEnabled = true)) },
                label = channelLabel(channel),
            )
        }
    }
}

@Composable
private fun ReverbLimiterPanel(config: AudioConfig, onChange: (AudioConfig) -> Unit) {
    val colors = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.reverb), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(stringResource(R.string.reverb_mix) + " · " + stringResource(R.string.percent_value, (config.reverbMix * 100).roundToInt()), color = colors.onSurface, fontWeight = FontWeight.SemiBold)
            StudioSlider(config.reverbMix, { onChange(config.copy(reverbMix = it, dspEnabled = true)) }, valueRange = 0f..0.6f)
            SettingSwitch(stringResource(R.string.safe_limiter), stringResource(R.string.limiter_description), config.limiterEnabled) {
                onChange(config.copy(limiterEnabled = it, dspEnabled = true))
            }
        }
    }
}

@Composable
private fun SpatialStatusPanel() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
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
            Text(stringResource(R.string.spatial_audio), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(stringResource(status), color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpandablePanel(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.onSurface)
                TextButton(onClick = onToggle) { Text(stringResource(if (expanded) R.string.collapse else R.string.expand), color = colors.primary, fontWeight = FontWeight.Bold) }
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun GainSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.onSurface, fontWeight = FontWeight.Medium)
        Text(stringResource(R.string.db_value, value), color = colors.primary, fontWeight = FontWeight.Bold)
    }
    StudioSlider(value, { onChange((it * 2f).roundToInt() / 2f) }, valueRange = range)
}

@Composable
private fun SettingSwitch(title: String, description: String?, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        }
        AuralisSwitch(checked, onChecked, enabled = enabled)
    }
}

@Composable
private fun FineControls(
    onMinus: () -> Unit,
    onReset: () -> Unit,
    onPlus: () -> Unit,
    minusLabel: String,
    plusLabel: String,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(
            onClick = onMinus,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = colors.surfaceVariant,
                contentColor = colors.onSurface,
            ),
            modifier = Modifier.semantics { contentDescription = minusLabel },
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onReset) {
            Text(stringResource(R.string.reset), color = colors.primary, fontWeight = FontWeight.Bold)
        }
        FilledTonalButton(
            onClick = onPlus,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = colors.surfaceVariant,
                contentColor = colors.onSurface,
            ),
            modifier = Modifier.semantics { contentDescription = plusLabel },
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StudioSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    AuralisSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
    )
}

@Composable
fun StudioChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    AuralisFilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        enabled = enabled,
        modifier = modifier,
    )
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
