package dev.auralis.studio.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AuralisSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    val semantic = LocalAuralisControlColors.current
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = semantic.sliderThumb,
            activeTrackColor = semantic.sliderActive,
            inactiveTrackColor = semantic.sliderInactive,
            activeTickColor = semantic.onActive.copy(alpha = 0.70f),
            inactiveTickColor = semantic.inactiveContent.copy(alpha = 0.75f),
            disabledThumbColor = semantic.disabledContent,
            disabledActiveTrackColor = semantic.disabledContent,
            disabledInactiveTrackColor = semantic.disabledContainer,
        ),
    )
}

@Composable
fun AuralisSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val semantic = LocalAuralisControlColors.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = semantic.switchCheckedThumb,
            checkedTrackColor = semantic.switchCheckedTrack,
            checkedBorderColor = semantic.switchCheckedTrack,
            uncheckedThumbColor = semantic.switchUncheckedThumb,
            uncheckedTrackColor = semantic.switchUncheckedTrack,
            uncheckedBorderColor = semantic.controlBorder,
            disabledCheckedThumbColor = semantic.disabledContent,
            disabledCheckedTrackColor = semantic.disabledContainer,
            disabledUncheckedThumbColor = semantic.disabledContent,
            disabledUncheckedTrackColor = semantic.disabledContainer,
        ),
    )
}

@Composable
fun AuralisFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AuralisFilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun AuralisFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val semantic = LocalAuralisControlColors.current
    val tokens = LocalAuralisTokens.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(tokens.chipCorner.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = semantic.inactiveContainer,
            labelColor = semantic.inactiveContent,
            selectedContainerColor = semantic.selectedContainer,
            selectedLabelColor = semantic.selectedContent,
            disabledContainerColor = semantic.disabledContainer,
            disabledLabelColor = semantic.disabledContent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = semantic.controlBorder,
            selectedBorderColor = semantic.active,
            disabledBorderColor = semantic.disabledContent,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.5.dp,
        ),
    )
}
