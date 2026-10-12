package dev.auralis.studio.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.studio.R
import dev.auralis.studio.audio.stems.StemKind
import dev.auralis.studio.audio.stems.StemUiState
import dev.auralis.studio.audio.stems.StemMixGains
import dev.auralis.studio.core.model.AudioConfig
import dev.auralis.studio.core.model.AudioPreset
import dev.auralis.studio.core.model.BuiltInPresets
import dev.auralis.studio.core.model.ChannelMode
import dev.auralis.studio.core.model.EightDMode
import dev.auralis.studio.core.model.OrbitDirection
import dev.auralis.studio.core.model.PitchMath
import dev.auralis.studio.core.model.PitchTempoMode
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.ui.components.GlassCard
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val EQ_BAND_FREQS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

fun countActiveEffects(config: AudioConfig): Int {
    if (!config.dspEnabled) return 0
    var count = 0
    if (abs(config.effectivePitchSemitones) > 0.05f) count++
    if (abs(config.speed - 1f) > 0.05f) count++
    if (config.equalizerEnabled || config.bassDb != 0f || config.trebleDb != 0f || config.preampDb != 0f || config.eqBandsDb.any { abs(it) > 0.1f }) count++
    if (config.eightDEnabled) count++
    if (config.reverbMix > 0.02f) count++
    if (config.channelMode != ChannelMode.STEREO) count++
    if (abs(config.balance) > 0.05f) count++
    if (abs(config.stereoWidth - 1f) > 0.05f) count++
    return count
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectsSheet(
    config: AudioConfig,
    onConfigChange: (AudioConfig) -> Unit,
    stemState: StemUiState = StemUiState.ReadyForTrack,
    stemGains: StemMixGains = StemMixGains(),
    currentTrack: Track? = null,
    onPrepareStems: (Track) -> Unit = {},
    onCancelStemWork: () -> Unit = {},
    onPlayStem: (StemKind) -> Unit = {},
    onVocalsGainChange: (Float) -> Unit = {},
    onInstrumentalGainChange: (Float) -> Unit = {},
    onBassGainChange: (Float) -> Unit = {},
    onPlayStemMix: () -> Unit = {},
    customPresets: List<AudioPreset> = emptyList(),
    onSavePreset: (String, AudioConfig) -> Unit = { _, _ -> },
    onDeletePreset: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var eqExpanded by remember { mutableStateOf(false) }
    var spatialExpanded by remember { mutableStateOf(false) }

    val activeCount = remember(config) { countActiveEffects(config) }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { dismiss() },
        containerColor = colors.surface,
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Top Header with Master Bypass & Reset All ─────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.audio_effects),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (config.dspEnabled && activeCount > 0) colors.primary else colors.outline),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (!config.dspEnabled) stringResource(R.string.dsp_bypassed)
                                else if (activeCount > 0) stringResource(R.string.active_effects_count, activeCount)
                                else stringResource(R.string.dsp_active),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (config.dspEnabled && activeCount > 0) colors.primary else colors.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Reset All Button
                    IconButton(
                        onClick = { onConfigChange(AudioConfig(dspEnabled = true)) },
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.reset_all_effects),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                    // Close button
                    IconButton(onClick = { dismiss() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = colors.onSurfaceVariant)
                    }
                }
            }

            // ── Master DSP Bypass Banner (Instant A/B Testing) ─────────────────
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (config.dspEnabled) colors.primary else colors.outline,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (config.dspEnabled) stringResource(R.string.dsp_active) else stringResource(R.string.master_dsp_bypass),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                            )
                            Text(
                                text = if (config.dspEnabled) "All DSP processing applied" else "Pure unprocessed original audio",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = config.dspEnabled,
                        onCheckedChange = { onConfigChange(config.copy(dspEnabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                        ),
                    )
                }
            }

            // ── Presets Carousel (Built-in + Custom) ──────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.quick_presets),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    TextButton(onClick = { showSavePresetDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.save_preset), style = MaterialTheme.typography.labelMedium)
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    items(BuiltInPresets.all) { preset ->
                        val isSelected = isPresetMatching(config, preset.config)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onConfigChange(preset.config.copy(dspEnabled = true)) },
                            label = { Text(preset.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    items(customPresets) { custom ->
                        FilterChip(
                            selected = isPresetMatching(config, custom.config),
                            onClick = { onConfigChange(custom.config.copy(dspEnabled = true)) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(custom.name)
                                }
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onDeletePreset(custom.name) },
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))

            // ── Section 1: Pitch & Speed / Tempo ─────────────────────────────
            SectionCard(
                icon = Icons.Default.Speed,
                title = stringResource(R.string.pitch_tempo),
                onReset = {
                    onConfigChange(
                        config.copy(
                            speed = 1f,
                            pitchSemitones = 0f,
                            mode = PitchTempoMode.TEMPO_LOCK,
                        ),
                    )
                },
            ) {
                // Pitch/Speed Mode Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PitchTempoMode.entries.forEach { mode ->
                        val isSel = config.mode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) colors.primary else colors.surfaceVariant.copy(alpha = 0.6f))
                                .clickable { onConfigChange(config.copy(mode = mode, dspEnabled = true)) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when (mode) {
                                    PitchTempoMode.INDEPENDENT -> stringResource(R.string.independent)
                                    PitchTempoMode.TEMPO_LOCK -> stringResource(R.string.tempo_lock)
                                    PitchTempoMode.VINYL -> stringResource(R.string.vinyl)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) colors.onPrimary else colors.onSurface,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Playback Speed Slider
                NumericSliderRow(
                    label = stringResource(R.string.speed),
                    formattedValue = "%.2fx".format(config.speed),
                    value = config.speed,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { onConfigChange(config.copy(speed = it, dspEnabled = true)) },
                    colors = colors,
                )

                // Pitch Semitones Slider (shown in Independent or Vinyl mode)
                NumericSliderRow(
                    label = stringResource(R.string.pitch),
                    formattedValue = "%+.1f st".format(config.effectivePitchSemitones),
                    value = config.effectivePitchSemitones,
                    valueRange = -12f..12f,
                    onValueChange = { semitones ->
                        val updated = if (config.mode == PitchTempoMode.VINYL) {
                            config.copy(speed = PitchMath.semitonesToFactor(semitones).coerceIn(0.5f, 2f), dspEnabled = true)
                        } else {
                            config.copy(pitchSemitones = semitones.coerceIn(-12f, 12f), mode = PitchTempoMode.INDEPENDENT, dspEnabled = true)
                        }
                        onConfigChange(updated)
                    },
                    colors = colors,
                )
            }

            // ── Section 2: Equalizer & Tone (10-Band + Shelves) ──────────────
            SectionCard(
                icon = Icons.Default.GraphicEq,
                title = stringResource(R.string.equalizer),
                isActive = config.equalizerEnabled,
                onToggleActive = { onConfigChange(config.copy(equalizerEnabled = it, dspEnabled = true)) },
                onReset = {
                    onConfigChange(
                        config.copy(
                            equalizerEnabled = false,
                            preampDb = 0f,
                            bassDb = 0f,
                            trebleDb = 0f,
                            eqBandsDb = List(10) { 0f },
                        ),
                    )
                },
            ) {
                // Preamp, Bass, Treble quick dials
                NumericSliderRow(
                    label = stringResource(R.string.preamp),
                    formattedValue = "%+.1f dB".format(config.preampDb),
                    value = config.preampDb,
                    valueRange = -12f..12f,
                    onValueChange = { onConfigChange(config.copy(preampDb = it, equalizerEnabled = true, dspEnabled = true)) },
                    colors = colors,
                )

                NumericSliderRow(
                    label = stringResource(R.string.bass),
                    formattedValue = "%+.1f dB".format(config.bassDb),
                    value = config.bassDb,
                    valueRange = -12f..12f,
                    onValueChange = { onConfigChange(config.copy(bassDb = it, equalizerEnabled = true, dspEnabled = true)) },
                    colors = colors,
                )

                NumericSliderRow(
                    label = stringResource(R.string.treble),
                    formattedValue = "%+.1f dB".format(config.trebleDb),
                    value = config.trebleDb,
                    valueRange = -12f..12f,
                    onValueChange = { onConfigChange(config.copy(trebleDb = it, equalizerEnabled = true, dspEnabled = true)) },
                    colors = colors,
                )

                Spacer(Modifier.height(4.dp))

                TextButton(
                    onClick = { eqExpanded = !eqExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (eqExpanded) "Hide 10-Band EQ" else "Show 10-Band Detailed EQ", color = colors.primary)
                }

                AnimatedVisibility(visible = eqExpanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        config.eqBandsDb.forEachIndexed { index, gain ->
                            val freqLabel = EQ_BAND_FREQS.getOrElse(index) { "B${index + 1}" }
                            NumericSliderRow(
                                label = "$freqLabel Hz",
                                formattedValue = "%+.1f dB".format(gain),
                                value = gain,
                                valueRange = -12f..12f,
                                onValueChange = { newGain ->
                                    val newBands = config.eqBandsDb.toMutableList()
                                    newBands[index] = newGain
                                    onConfigChange(config.copy(eqBandsDb = newBands, equalizerEnabled = true, dspEnabled = true))
                                },
                                colors = colors,
                            )
                        }
                    }
                }
            }

            // ── Section 3: Stereo, 8D Spatial & Orbit ────────────────────────
            SectionCard(
                icon = Icons.Default.SpatialAudio,
                title = stringResource(R.string.stereo_tools),
                onReset = {
                    onConfigChange(
                        config.copy(
                            stereoWidth = 1f,
                            balance = 0f,
                            eightDEnabled = false,
                            channelMode = ChannelMode.STEREO,
                        ),
                    )
                },
            ) {
                // Stereo Width
                NumericSliderRow(
                    label = stringResource(R.string.stereo_width),
                    formattedValue = "${(config.stereoWidth * 100).roundToInt()}%",
                    value = config.stereoWidth,
                    valueRange = 0f..2.0f,
                    onValueChange = { onConfigChange(config.copy(stereoWidth = it, dspEnabled = true)) },
                    colors = colors,
                )

                // Stereo Balance
                NumericSliderRow(
                    label = stringResource(R.string.balance),
                    formattedValue = when {
                        config.balance < -0.05f -> "L ${(-config.balance * 100).roundToInt()}%"
                        config.balance > 0.05f -> "R ${(config.balance * 100).roundToInt()}%"
                        else -> "Center"
                    },
                    value = config.balance,
                    valueRange = -1.0f..1.0f,
                    onValueChange = { onConfigChange(config.copy(balance = it, dspEnabled = true)) },
                    colors = colors,
                )

                // Channel Mode selector
                Text(
                    stringResource(R.string.channel_mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(ChannelMode.entries) { mode ->
                        FilterChip(
                            selected = config.channelMode == mode,
                            onClick = { onConfigChange(config.copy(channelMode = mode, dspEnabled = true)) },
                            label = { Text(mode.name) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 8D Audio Toggle & Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(stringResource(R.string.eight_d_audio), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        Text(stringResource(R.string.spatial_orbit_effect), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.eightDEnabled,
                        onCheckedChange = { onConfigChange(config.copy(eightDEnabled = it, dspEnabled = true)) },
                    )
                }

                AnimatedVisibility(visible = config.eightDEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericSliderRow(
                            label = stringResource(R.string.strength),
                            formattedValue = "${(config.eightDStrength * 100).roundToInt()}%",
                            value = config.eightDStrength,
                            valueRange = 0f..1.0f,
                            onValueChange = { onConfigChange(config.copy(eightDStrength = it, dspEnabled = true)) },
                            colors = colors,
                        )

                        NumericSliderRow(
                            label = stringResource(R.string.rotation_speed),
                            formattedValue = "%.2f Hz".format(config.eightDRateHz),
                            value = config.eightDRateHz,
                            valueRange = 0.02f..0.35f,
                            onValueChange = { onConfigChange(config.copy(eightDRateHz = it, dspEnabled = true)) },
                            colors = colors,
                        )

                        // Spatial Mode
                        Text(
                            stringResource(R.string.spatial_mode),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurfaceVariant,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(EightDMode.entries) { mode ->
                                FilterChip(
                                    selected = config.eightDMode == mode,
                                    onClick = { onConfigChange(config.copy(eightDMode = mode, eightDEnabled = true, dspEnabled = true)) },
                                    label = {
                                        Text(when (mode) {
                                            EightDMode.DYNAMIC_ORBIT -> stringResource(R.string.mode_dynamic_orbit)
                                            EightDMode.BASS_SEPARATION -> stringResource(R.string.mode_bass_separation)
                                            EightDMode.VOCAL_SEPARATION -> stringResource(R.string.mode_vocal_separation)
                                            EightDMode.BINAURAL_3D -> stringResource(R.string.mode_binaural_3d)
                                            EightDMode.SPHERICAL_3D -> stringResource(R.string.mode_spherical_3d)
                                        })
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                )
                            }
                        }

                        if (config.eightDMode == EightDMode.BASS_SEPARATION || config.eightDMode == EightDMode.SPHERICAL_3D) {
                            NumericSliderRow(
                                label = stringResource(R.string.bass_separation_depth),
                                formattedValue = "${(config.eightDBassSeparation * 100).roundToInt()}%",
                                value = config.eightDBassSeparation,
                                valueRange = 0.2f..1.0f,
                                onValueChange = { onConfigChange(config.copy(eightDBassSeparation = it, dspEnabled = true)) },
                                colors = colors,
                            )
                        }
                        if (config.eightDMode == EightDMode.VOCAL_SEPARATION || config.eightDMode == EightDMode.SPHERICAL_3D) {
                            NumericSliderRow(
                                label = stringResource(R.string.vocal_separation_depth),
                                formattedValue = "${(config.eightDVocalSeparation * 100).roundToInt()}%",
                                value = config.eightDVocalSeparation,
                                valueRange = 0.2f..1.0f,
                                onValueChange = { onConfigChange(config.copy(eightDVocalSeparation = it, dspEnabled = true)) },
                                colors = colors,
                            )
                        }
                        if (config.eightDMode == EightDMode.BINAURAL_3D || config.eightDMode == EightDMode.SPHERICAL_3D) {
                            NumericSliderRow(
                                label = stringResource(R.string.binaural_depth),
                                formattedValue = "${(config.eightDBinauralDepth * 100).roundToInt()}%",
                                value = config.eightDBinauralDepth,
                                valueRange = 0.2f..1.0f,
                                onValueChange = { onConfigChange(config.copy(eightDBinauralDepth = it, dspEnabled = true)) },
                                colors = colors,
                            )
                        }

                        // Orbit Direction
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(OrbitDirection.entries) { direction ->
                                FilterChip(
                                    selected = config.orbitDirection == direction,
                                    onClick = { onConfigChange(config.copy(orbitDirection = direction, eightDEnabled = true, dspEnabled = true)) },
                                    label = { Text(direction.name.replace('_', ' ')) },
                                    shape = RoundedCornerShape(12.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Section 3b: Studio Mastering & Audio Exciter ────────────────
            SectionCard(
                icon = Icons.Default.GraphicEq,
                title = stringResource(R.string.soundstage_expansion),
                onReset = {
                    onConfigChange(config.copy(soundstageExpansion = 0f, analogWarmth = 0f, clarityExciter = 0f))
                },
            ) {
                NumericSliderRow(
                    label = stringResource(R.string.soundstage_expansion),
                    formattedValue = "${(config.soundstageExpansion * 100).roundToInt()}%",
                    value = config.soundstageExpansion,
                    valueRange = 0f..1.0f,
                    onValueChange = { onConfigChange(config.copy(soundstageExpansion = it, dspEnabled = true)) },
                    colors = colors,
                )
                NumericSliderRow(
                    label = stringResource(R.string.analog_warmth),
                    formattedValue = "${(config.analogWarmth * 100).roundToInt()}%",
                    value = config.analogWarmth,
                    valueRange = 0f..1.0f,
                    onValueChange = { onConfigChange(config.copy(analogWarmth = it, dspEnabled = true)) },
                    colors = colors,
                )
                NumericSliderRow(
                    label = stringResource(R.string.clarity_exciter),
                    formattedValue = "${(config.clarityExciter * 100).roundToInt()}%",
                    value = config.clarityExciter,
                    valueRange = 0f..1.0f,
                    onValueChange = { onConfigChange(config.copy(clarityExciter = it, dspEnabled = true)) },
                    colors = colors,
                )
            }

            // ── Section 4: Reverb & Safe Limiter ─────────────────────────────
            SectionCard(
                icon = Icons.Default.Waves,
                title = stringResource(R.string.reverb),
                onReset = {
                    onConfigChange(config.copy(reverbMix = 0f, limiterEnabled = true))
                },
            ) {
                NumericSliderRow(
                    label = stringResource(R.string.reverb),
                    formattedValue = "${(config.reverbMix * 100).roundToInt()}%",
                    value = config.reverbMix,
                    valueRange = 0f..0.5f,
                    onValueChange = { onConfigChange(config.copy(reverbMix = it, dspEnabled = true)) },
                    colors = colors,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.safe_limiter), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        Text(stringResource(R.string.limiter_description), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.limiterEnabled,
                        onCheckedChange = { onConfigChange(config.copy(limiterEnabled = it, dspEnabled = true)) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Save Custom Preset Dialog
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text(stringResource(R.string.save_preset)) },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text(stringResource(R.string.preset_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPresetName.trim()
                        if (trimmed.isNotBlank()) {
                            onSavePreset(trimmed, config)
                            newPresetName = ""
                            showSavePresetDialog = false
                        }
                    },
                    enabled = newPresetName.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    isActive: Boolean? = null,
    onToggleActive: ((Boolean) -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive != null && onToggleActive != null) {
                        Switch(
                            checked = isActive,
                            onCheckedChange = onToggleActive,
                        )
                    }
                    if (onReset != null) {
                        IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun NumericSliderRow(
    label: String,
    formattedValue: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    colors: androidx.compose.material3.ColorScheme,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
            Text(
                formattedValue,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
    }
}

private fun isPresetMatching(a: AudioConfig, b: AudioConfig): Boolean {
    return abs(a.speed - b.speed) < 0.02f &&
        abs(a.effectivePitchSemitones - b.effectivePitchSemitones) < 0.1f &&
        a.eightDEnabled == b.eightDEnabled &&
        abs(a.reverbMix - b.reverbMix) < 0.05f &&
        a.equalizerEnabled == b.equalizerEnabled
}
