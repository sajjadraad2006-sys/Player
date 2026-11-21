package dev.auralis.studio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.auralis.studio.core.model.SeekbarStyle
import dev.auralis.studio.designsystem.LocalAuralisControlColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private const val WAVE_CLOCK_WRAP_NANOS = 93_600_000_000L
private const val WAVE_FRAME_MS = 33L

private fun wavePhase(seconds: Float, periodSeconds: Float): Float {
    val cycles = seconds / periodSeconds
    return (cycles - kotlin.math.floor(cycles)) * (2 * PI).toFloat()
}

/**
 * Modern Seekbar supporting animated rolling sine waves, dynamic audio waveforms,
 * or minimal glowing pill lines. Fully touch and scrub interactive with Material
 * accessibility and zero playback stutter.
 */
@Composable
fun WavySeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    isPlaying: Boolean = false,
    style: SeekbarStyle = SeekbarStyle.WAVY,
    waveformBands: FloatArray? = null,
) {
    val semantic = LocalAuralisControlColors.current
    val colors = MaterialTheme.colorScheme
    val primaryColor = semantic.sliderActive
    val secondaryColor = colors.secondary
    val inactiveTrackColor = colors.onSurface.copy(alpha = 0.20f)

    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()

    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
    val currentFraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    val waveClockSeconds = remember { mutableFloatStateOf(0f) }
    val wavesActive = isPlaying && !isDragging && (style == SeekbarStyle.WAVY || style == SeekbarStyle.WAVEFORM_BAR)

    LaunchedEffect(wavesActive) {
        if (!wavesActive) return@LaunchedEffect
        var startNanos = -1L
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0L) startNanos = now
                waveClockSeconds.floatValue = ((now - startNanos) % WAVE_CLOCK_WRAP_NANOS) / 1_000_000_000f
            }
            delay(WAVE_FRAME_MS)
        }
    }

    val amp1 by animateFloatAsState(if (isDragging) 1.5f else 11f, tween(180), label = "Amp1")
    val amp2 by animateFloatAsState(if (isDragging) 1.5f else 8f, tween(180), label = "Amp2")
    val amp3 by animateFloatAsState(if (isDragging) 1.5f else 6f, tween(180), label = "Amp3")

    val contour1 = remember { Path() }
    val contour2 = remember { Path() }
    val contour3 = remember { Path() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(42.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(42.dp)) {
                val width = size.width
                val centerY = size.height / 2f
                val thumbX = (currentFraction * width).coerceIn(0f, width)
                val trackHeightPx = 4.5.dp.toPx()

                when (style) {
                    SeekbarStyle.MINIMAL_LINE -> {
                        // Sleek gradient rounded bar
                        drawLine(
                            color = inactiveTrackColor,
                            start = Offset(0f, centerY),
                            end = Offset(width, centerY),
                            strokeWidth = trackHeightPx,
                            cap = StrokeCap.Round,
                        )
                        if (thumbX > 0f) {
                            drawLine(
                                brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor), 0f, thumbX.coerceAtLeast(1f)),
                                start = Offset(0f, centerY),
                                end = Offset(thumbX, centerY),
                                strokeWidth = trackHeightPx + (if (isDragging) 2.dp.toPx() else 0f),
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                    SeekbarStyle.WAVEFORM_BAR -> {
                        // Dynamic multi-bar audio waveform seeker
                        val barCount = 48
                        val barSpacing = width / barCount
                        val barWidth = (barSpacing * 0.65f).coerceAtLeast(2.dp.toPx())
                        val t = waveClockSeconds.floatValue

                        for (i in 0 until barCount) {
                            val barCenterX = i * barSpacing + barSpacing / 2f
                            val bandIdx = (i % (waveformBands?.size ?: 1))
                            val bandEnergy = waveformBands?.getOrNull(bandIdx) ?: 0.35f
                            val waveOscillation = if (isPlaying) sin(t * 4f + i * 0.3f) * 0.25f else 0f
                            val normalizedHeight = (bandEnergy + waveOscillation).coerceIn(0.12f, 1.0f)
                            val barHeight = normalizedHeight * (size.height * 0.75f)

                            val isPlayed = barCenterX <= thumbX
                            val barColor = if (isPlayed) {
                                primaryColor.copy(alpha = if (isDragging) 0.95f else 0.85f)
                            } else {
                                inactiveTrackColor
                            }

                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(barCenterX - barWidth / 2f, centerY - barHeight / 2f),
                                size = Size(barWidth, barHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
                            )
                        }
                    }
                    SeekbarStyle.WAVY -> {
                        // 3-layer rolling sinusoidal waveform
                        val trackPx = 4.5.dp.toPx()
                        val half = trackPx / 2f
                        val topY = centerY - half

                        drawLine(inactiveTrackColor, Offset(0f, centerY), Offset(width, centerY), trackPx, StrokeCap.Round)

                        if (thumbX > 0f) {
                            val transition = minOf(40.dp.toPx(), thumbX * 0.5f)
                            val invTransition = if (transition > 0f) 1f / transition else 0f
                            val step = 2.dp.toPx()

                            fun smootherstep(tVal: Float): Float {
                                val c = tVal.coerceIn(0f, 1f)
                                return c * c * c * (c * (c * 6f - 15f) + 10f)
                            }

                            fun wave(path: Path, wavelengthDp: Float, amplitudeDp: Float, phase: Float) {
                                val amplitude = amplitudeDp.dp.toPx()
                                val k = (2 * PI / wavelengthDp.dp.toPx()).toFloat()
                                path.reset()
                                path.moveTo(0f, topY)
                                var x = 0f
                                while (x < thumbX) {
                                    val envelope = if (invTransition > 0f) {
                                        smootherstep(x * invTransition) * smootherstep((thumbX - x) * invTransition)
                                    } else 1f
                                    path.lineTo(x, topY - (0.5f + 0.5f * sin(x * k - phase)) * amplitude * envelope)
                                    x += step
                                }
                                path.lineTo(thumbX, topY)
                            }

                            val t = waveClockSeconds.floatValue
                            wave(contour1, 150f, amp1, wavePhase(t, 2.4f) + 2.2f)
                            wave(contour2, 115f, amp2, wavePhase(t, 1.8f) + 1.2f)
                            wave(contour3, 85f, amp3, wavePhase(t, 1.3f))

                            val activeWidth = thumbX.coerceAtLeast(1f)
                            val join = StrokeJoin.Round

                            val layer1Colors = listOf(secondaryColor.copy(alpha = 0.40f), primaryColor.copy(alpha = 0.35f))
                            val layer2Colors = listOf(primaryColor.copy(alpha = 0.65f), secondaryColor.copy(alpha = 0.60f))
                            val layer3Colors = listOf(primaryColor.copy(alpha = 0.95f), secondaryColor.copy(alpha = 0.90f))
                            val baseColors = listOf(primaryColor, secondaryColor)

                            clipRect(left = -half, right = thumbX + half) {
                                drawPath(contour1, Brush.horizontalGradient(layer1Colors, 0f, activeWidth), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round, join = join))
                                drawPath(contour2, Brush.horizontalGradient(layer2Colors, 0f, activeWidth), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, join = join))
                                drawPath(contour3, Brush.horizontalGradient(layer3Colors, 0f, activeWidth), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = join))
                                drawLine(Brush.horizontalGradient(baseColors, 0f, activeWidth), Offset(0f, centerY), Offset(thumbX, centerY), trackPx, StrokeCap.Round)
                            }
                        }
                    }
                }

                // Scrub Thumb
                val thumbRadius = if (isDragging) 9.dp.toPx() else 7.5.dp.toPx()
                val center = Offset(thumbX, centerY)
                // Outer glow ring
                drawCircle(primaryColor.copy(alpha = if (isDragging) 0.45f else 0.25f), thumbRadius + 5.dp.toPx(), center)
                // Main solid thumb
                drawCircle(primaryColor, thumbRadius, center)
                // Crisp inner highlight
                drawCircle(Color.White.copy(alpha = 0.75f), thumbRadius * 0.45f, center)
            }

            // Transparent Material Slider for robust gestures, talkback, and zero-stutter scrubbing
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                enabled = enabled,
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    disabledThumbColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}
