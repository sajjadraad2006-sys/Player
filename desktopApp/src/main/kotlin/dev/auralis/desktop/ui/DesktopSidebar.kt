package dev.auralis.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Desktop navigation sidebar with brand header, tab navigation, and live process resource metrics.
 */
@Composable
fun DesktopSidebar(
    currentTab: DesktopNavTab,
    onTabSelected: (DesktopNavTab) -> Unit,
    accentColor: Color,
    surfaceColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    trackCount: Int,
    modifier: Modifier = Modifier,
) {
    var heapUsedMb by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val runtime = Runtime.getRuntime()
            heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            delay(2000)
        }
    }

    Column(
        modifier = modifier
            .width(230.dp)
            .fillMaxHeight()
            .background(surfaceColor)
            .border(width = 1.dp, color = borderColor),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Brand Logo & Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 28.dp, top = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(accentColor, Color(0xFF6366F1)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Lyrune",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "LYRUNE",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = textPrimary,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "Desktop Studio",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Navigation Items
            DesktopNavTab.values().forEach { tab ->
                val selected = currentTab == tab
                val itemBg = if (selected) accentColor.copy(alpha = 0.15f) else Color.Transparent
                val itemColor = if (selected) accentColor else textSecondary

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(itemBg)
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = itemColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = tab.title,
                        color = if (selected) textPrimary else textSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // Bottom Memory & Library Metrics
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F1221))
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Library", color = textSecondary, fontSize = 11.sp)
                Text("$trackCount tracks", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Heap RAM", color = textSecondary, fontSize = 11.sp)
                Text("${heapUsedMb} MB", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Streaming audio • Bounded buffers",
                color = textSecondary.copy(alpha = 0.6f),
                fontSize = 9.sp,
            )
        }
    }
}
