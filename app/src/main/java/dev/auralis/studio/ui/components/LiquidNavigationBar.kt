package dev.auralis.studio.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.auralis.studio.core.model.NavigationBarStyle
import kotlin.math.roundToInt

data class NavigationBarItemSpec<T>(
    val destination: T,
    val icon: ImageVector,
    val labelRes: Int,
)

/**
 * High-performance liquid floating navigation bar with fluid gliding indicator
 * and spring dynamics inspired by LastWave & BitChord.
 */
@Composable
fun <T> LiquidFloatingNavBar(
    items: List<NavigationBarItemSpec<T>>,
    currentDestination: T,
    onNavigate: (T) -> Unit,
    modifier: Modifier = Modifier,
    style: NavigationBarStyle = NavigationBarStyle.LIQUID_BUBBLE,
) {
    val colors = MaterialTheme.colorScheme
    val selectedIndex = items.indexOfFirst { it.destination == currentDestination }.coerceAtLeast(0)

    if (style == NavigationBarStyle.CLASSIC_DOCK) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = colors.surfaceColorAtElevation(3.dp),
            tonalElevation = 4.dp,
            border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.25f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (item in items) {
                    val selected = item.destination == currentDestination
                    Column(
                        modifier = Modifier
                            .clickable { onNavigate(item.destination) }
                            .padding(vertical = 4.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = stringResource(item.labelRes),
                            tint = if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = stringResource(item.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    } else {
        // LIQUID_BUBBLE Floating Glass Island
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(32.dp),
            color = colors.surfaceColorAtElevation(5.dp).copy(alpha = 0.92f),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    listOf(
                        colors.primary.copy(alpha = 0.45f),
                        colors.secondary.copy(alpha = 0.25f),
                        colors.outlineVariant.copy(alpha = 0.15f),
                    )
                ),
            ),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .height(52.dp),
            ) {
                val totalWidthPx = constraints.maxWidth.toFloat()
                val itemCount = items.size
                val slotWidthPx = if (itemCount > 0) totalWidthPx / itemCount else 0f
                val slotWidthDp = maxWidth / itemCount

                val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
                val targetSlot = if (isRtl && itemCount > 0) (itemCount - 1 - selectedIndex).coerceAtLeast(0) else selectedIndex
                val animatedIndicatorX by animateFloatAsState(
                    targetValue = targetSlot * slotWidthPx,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                    label = "nav_bubble_slide",
                )

                // Gliding Liquid Pill Indicator
                Box(
                    modifier = Modifier
                        .offset { IntOffset(animatedIndicatorX.roundToInt(), 0) }
                        .width(slotWidthDp)
                        .fillMaxHeight()
                        .padding(2.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    colors.primaryContainer.copy(alpha = 0.85f),
                                    colors.primary.copy(alpha = 0.25f),
                                )
                            )
                        )
                )

                // Tab items
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for ((index, item) in items.withIndex()) {
                        val selected = index == selectedIndex
                        val scale by animateFloatAsState(
                            targetValue = if (selected) 1.08f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
                            label = "tab_scale",
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onNavigate(item.destination) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.scale(scale),
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = stringResource(item.labelRes),
                                    tint = if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.75f),
                                    modifier = Modifier.size(21.dp),
                                )
                                if (selected) {
                                    Text(
                                        text = stringResource(item.labelRes),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onPrimaryContainer,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
