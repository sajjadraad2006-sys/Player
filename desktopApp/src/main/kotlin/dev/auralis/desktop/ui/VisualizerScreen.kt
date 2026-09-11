package dev.auralis.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.studio.audio.visualizer.VisualizerAnalyzer
import kotlin.math.max

/**
 * Visualizer Screen providing real-time 48-band FFT spectrum.
 *
 * Performance Hardening:
 * - Automatically stops the analyzer when playback is paused or when navigating away to prevent CPU/GC churn.
 * - Steady-state loop runs on reusable FloatArray buffers.
 */
@Composable
fun VisualizerScreen(
    analyzer: VisualizerAnalyzer,
    isPlaying: Boolean,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier,
) {
    val spectrum by analyzer.bands.collectAsState()

    // Stop analyzer when Composable leaves the composition tree
    DisposableEffect(Unit) {
        onDispose {
            analyzer.stop()
        }
    }

    // Control analyzer lifecycle based on playback state
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            analyzer.start()
        } else {
            analyzer.stop()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(bottom = 20.dp)) {
            Text(uiText("Feel the music"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Text(if (isPlaying) "Moving with your music" else "Play a song to bring this to life", fontSize = 12.sp, color = textSecondary)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(accentColor.copy(alpha = 0.04f))
                .padding(24.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barCount = spectrum.size
                val barSpacing = minOf(4.dp.toPx(), size.width / (barCount * 3f))
                val totalSpacing = barSpacing * (barCount - 1)
                val barWidth = (size.width - totalSpacing) / barCount

                for (i in 0 until barCount) {
                    val magnitude = if (isPlaying) spectrum[i].coerceIn(0f, 1f) else 0.05f
                    val barHeight = max(6f, magnitude * size.height * 0.9f)
                    val left = i * (barWidth + barSpacing)
                    val top = size.height - barHeight

                    // Gradient color across spectrum: Mint -> Cyan -> Violet
                    val fraction = i.toFloat() / barCount
                    val barColor = when {
                        fraction < 0.35f -> accentColor
                        fraction < 0.7f -> Color(0xFF38BDF8)
                        else -> Color(0xFFA78BFA)
                    }

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(barColor, barColor.copy(alpha = 0.35f)),
                            startY = top,
                            endY = size.height,
                        ),
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                }
            }
        }
    }
}
