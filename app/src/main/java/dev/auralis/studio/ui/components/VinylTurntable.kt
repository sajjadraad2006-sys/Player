package dev.auralis.studio.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Realistic Studio Vinyl Turntable component with rotating grooved vinyl record,
 * center label artwork, and reactive tonearm needle.
 */
@Composable
fun VinylTurntable(
    artworkUri: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin_infinite")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
        ),
        label = "vinyl_spin",
    )
    val effectiveRotation = if (isPlaying) spinAngle else 0f

    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 28f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "tonearm_angle",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Shadow base platter
        Box(
            modifier = Modifier
                .fillMaxSize(0.94f)
                .shadow(16.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFF0C0D14)),
        )

        // Vinyl disc with concentric grooves and rotating specular light
        Box(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .rotate(effectiveRotation)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF1E1F2A),
                            Color(0xFF111218),
                            Color(0xFF090A0E),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.width / 2f
                val grooveColor = Color.White.copy(alpha = 0.045f)
                val specularColor = Color.White.copy(alpha = 0.08f)

                // Draw realistic vinyl grooves
                for (r in (maxRadius * 0.44f).toInt()..(maxRadius * 0.95f).toInt() step 6) {
                    drawCircle(
                        color = grooveColor,
                        radius = r.toFloat(),
                        center = center,
                        style = Stroke(width = 0.8.dp.toPx()),
                    )
                }

                // Specular light sheen across record surface
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            specularColor,
                            Color.Transparent,
                            specularColor,
                            Color.Transparent,
                        ),
                        center = center,
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true,
                )
            }

            // Center Record Label (Artwork)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.40f)
                    .clip(CircleShape)
                    .border(2.dp, Color(0xFF2E3145), CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!artworkUri.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Center spindle hole
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF090A0E))
                        .border(1.5.dp, Color(0xFF6C6A80), CircleShape)
                )
            }
        }

        // Tonearm
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(tonearmAngle),
        ) {
            val pivotX = size.width * 0.88f
            val pivotY = size.height * 0.12f
            val pivotCenter = Offset(pivotX, pivotY)

            // Tonearm pivot base
            drawCircle(Color(0xFF4A4B5C), 14.dp.toPx(), pivotCenter)
            drawCircle(Color(0xFF2A2B38), 9.dp.toPx(), pivotCenter)

            // Metallic arm tube
            val armEndX = size.width * 0.58f
            val armEndY = size.height * 0.52f
            drawLine(
                color = Color(0xFFC0BECE),
                start = pivotCenter,
                end = Offset(armEndX, armEndY),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Cartridge / headshell at end of needle
            val headX = armEndX - 8.dp.toPx()
            val headY = armEndY + 12.dp.toPx()
            drawLine(
                color = Color(0xFF555268),
                start = Offset(armEndX, armEndY),
                end = Offset(headX, headY),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Square,
            )
        }
    }
}
