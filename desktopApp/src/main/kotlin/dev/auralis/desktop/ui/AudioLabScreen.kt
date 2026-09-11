package dev.auralis.desktop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.studio.core.model.*

@Composable fun AudioLabScreen(config: AudioConfig, onConfigChanged: (AudioConfig) -> Unit, accentColor: Color,
    cardBg: Color, borderColor: Color, textPrimary: Color, textSecondary: Color, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(uiText("Audio Lab"), fontSize = 32.sp, fontWeight = FontWeight.Bold); Text(uiText("Find a sound that feels like you."), color = textSecondary) }
            Text(uiText("Effects"), color = textSecondary); Spacer(Modifier.width(8.dp)); Switch(config.dspEnabled, { onConfigChanged(config.copy(dspEnabled = it)) })
        } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BuiltInPresets.all.forEach { preset -> FilterChip(config == preset.config, { onConfigChanged(preset.config) }, label = { Text(uiText(preset.name)) }) }
        } }
        item { LabSection("Pitch & tempo") {
            SettingChoices("Mode", PitchTempoMode.entries, config.mode) { onConfigChanged(config.copy(mode = it, dspEnabled = true)) }
            Text(when(config.mode) { PitchTempoMode.TEMPO_LOCK -> "Change speed while keeping the original pitch."; PitchTempoMode.INDEPENDENT -> "Adjust pitch and speed separately."; PitchTempoMode.VINYL -> "Pitch follows speed, like a record player." }, color = textSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) {
                    CommitSlider("Pitch · semitones", config.effectivePitchSemitones, -12f..12f) { pitch ->
                        onConfigChanged(if(config.mode == PitchTempoMode.VINYL) config.copy(speed = PitchMath.semitonesToFactor(pitch).coerceIn(.25f, 3f), dspEnabled = true)
                            else config.copy(pitchSemitones = pitch, mode = PitchTempoMode.INDEPENDENT, dspEnabled = true))
                    }
                }
                Column(Modifier.weight(1f)) { CommitSlider("Speed", config.speed, .25f..3f) { onConfigChanged(config.copy(speed = it, dspEnabled = true)) } }
            }
        } }
        item { LabSection("Equalizer") {
            SettingSwitch("10-band EQ", "", config.equalizerEnabled) { onConfigChanged(config.copy(equalizerEnabled = it)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k").forEachIndexed { index, label ->
                    val gain = config.eqBandsDb.getOrElse(index) { 0f }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiText("%.0f").format(gain), fontSize = 11.sp, color = accentColor)
                        GainFader(gain, label, accentColor, Modifier.fillMaxWidth().height(130.dp)) { value ->
                            val bands = List(10) { config.eqBandsDb.getOrElse(it) { 0f } }.toMutableList(); bands[index] = value
                            onConfigChanged(config.copy(eqBandsDb = bands, equalizerEnabled = true, dspEnabled = true))
                        }
                        Text(uiText(label), fontSize = 11.sp, color = textSecondary)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f)) { SettingSlider("Preamp · dB", config.preampDb, -12f..6f) { onConfigChanged(config.copy(preampDb = it)) } }
                Column(Modifier.weight(1f)) { SettingSlider("Bass · dB", config.bassDb, -12f..12f) { onConfigChanged(config.copy(bassDb = it)) } }
                Column(Modifier.weight(1f)) { SettingSlider("Treble · dB", config.trebleDb, -12f..12f) { onConfigChanged(config.copy(trebleDb = it)) } }
            }
            TextButton({ onConfigChanged(config.copy(eqBandsDb = List(10) { 0f }, bassDb = 0f, trebleDb = 0f, preampDb = 0f)) }) { Text(uiText("Reset equalizer")) }
        } }
        item { LabSection("Spatial / 8D") {
            SettingSwitch("8D motion", "Best experienced with headphones", config.eightDEnabled) { onConfigChanged(config.copy(eightDEnabled = it)) }
            SettingSlider("Strength", config.eightDStrength, 0f..1f) { onConfigChanged(config.copy(eightDStrength = it)) }
            SettingSlider("Rotation · Hz", config.eightDRateHz, .02f.. .5f) { onConfigChanged(config.copy(eightDRateHz = it)) }
            SettingChoices("Direction", OrbitDirection.entries, config.orbitDirection) { onConfigChanged(config.copy(orbitDirection = it)) }
        } }
        item { LabSection("Stereo") {
            SettingSlider("Width", config.stereoWidth, 0f..2f) { onConfigChanged(config.copy(stereoWidth = it)) }
            SettingSlider("Balance · left / right", config.balance, -1f..1f) { onConfigChanged(config.copy(balance = it)) }
            SettingChoices("Channels", ChannelMode.entries, config.channelMode) { onConfigChanged(config.copy(channelMode = it)) }
        } }
        item { LabSection("Reverb & dynamics") {
            SettingSlider("Reverb", config.reverbMix, 0f..1f) { onConfigChanged(config.copy(reverbMix = it)) }
            SettingSwitch("Limiter", "Keep peaks under control", config.limiterEnabled) { onConfigChanged(config.copy(limiterEnabled = it)) }
        } }
    }
}
@Composable private fun LabSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = .72f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(uiText(title), fontSize = 19.sp, fontWeight = FontWeight.SemiBold); content()
    }
}
@Composable private fun CommitSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onCommit: (Float) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    Column { Row { Text(uiText(label), modifier = Modifier.weight(1f)); Text(uiText("%.2f").format(draft), color = MaterialTheme.colorScheme.primary) }
        Slider(draft.coerceIn(range.start, range.endInclusive), { draft = it }, valueRange = range, onValueChangeFinished = { onCommit(draft) })
    }
}
@Composable private fun GainFader(value: Float, label: String, accent: Color, modifier: Modifier, onChange: (Float) -> Unit) {
    val update by rememberUpdatedState(onChange)
    Canvas(modifier.semantics { contentDescription = "$label Hz gain"; progressBarRangeInfo = ProgressBarRangeInfo(value, -12f..12f); setProgress { update(it.coerceIn(-12f, 12f)); true } }
        .pointerInput(Unit) { detectTapGestures { position -> update((12f - position.y / size.height * 24f).coerceIn(-12f, 12f)) } }
        .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); update((12f - change.position.y / size.height * 24f).coerceIn(-12f, 12f)) } }) {
        val x = size.width / 2
        val y = (12f - value) / 24f * (size.height - 20) + 10
        drawLine(accent.copy(alpha = .15f), Offset(x, 10f), Offset(x, size.height - 10), strokeWidth = 4f)
        drawLine(accent, Offset(x, size.height / 2), Offset(x, y), strokeWidth = 4f)
        drawLine(accent.copy(alpha = .25f), Offset(x - 9, size.height / 2), Offset(x + 9, size.height / 2), strokeWidth = 1f)
        drawCircle(accent, radius = 7.dp.toPx(), center = Offset(x, y))
    }
}
