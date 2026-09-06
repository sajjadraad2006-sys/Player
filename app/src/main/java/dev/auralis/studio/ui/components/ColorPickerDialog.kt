package dev.auralis.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auralis.studio.R
import dev.auralis.studio.designsystem.AccentPalette
import dev.auralis.studio.designsystem.HexColorParser
import dev.auralis.studio.designsystem.isColorDark

private val EXTENDED_PALETTE = listOf(
    Color(0xFFEF4444), // Vibrant Red
    Color(0xFFDC2626), // Deep Crimson
    Color(0xFFB91C1C), // Dark Red
    Color(0xFFA78BFA), // Purple / Lavender
    Color(0xFF6366F1), // Indigo
    Color(0xFF38BDF8), // Sky Cyan
    Color(0xFF06B6D4), // Deep Cyan
    Color(0xFF2DD4BF), // Teal
    Color(0xFF5EE6B8), // Mint
    Color(0xFF10B981), // Emerald
    Color(0xFF84CC16), // Lime
    Color(0xFFFBBF24), // Amber
    Color(0xFFF59E0B), // Gold
    Color(0xFFFB923C), // Orange
    Color(0xFFF43F5E), // Rose
    Color(0xFFFF6FAE), // Vivid Pink
    Color(0xFFE879F9), // Fuchsia
    Color(0xFFE2E8F0), // Pure Silver
    Color(0xFF94A3B8), // Slate
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    initialPrimaryArgb: Long,
    initialSecondaryArgb: Long,
    onApply: (primaryArgb: Long, secondaryArgb: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val colors = MaterialTheme.colorScheme

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Primary, 1: Secondary
    var currentPrimaryArgb by remember { mutableStateOf(initialPrimaryArgb) }
    var currentSecondaryArgb by remember { mutableStateOf(initialSecondaryArgb) }

    var showManualHexInput by remember { mutableStateOf(false) }

    val activeColorArgb = if (selectedTab == 0) currentPrimaryArgb else currentSecondaryArgb
    var hexInputText by remember(selectedTab, activeColorArgb) {
        mutableStateOf(HexColorParser.formatToHex(activeColorArgb))
    }
    val parsedInputColor = remember(hexInputText) {
        HexColorParser.parse(hexInputText)
    }

    val primaryColor = remember(currentPrimaryArgb) { Color(currentPrimaryArgb.toInt()) }
    val secondaryColor = remember(currentSecondaryArgb) { Color(currentSecondaryArgb.toInt()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.color_customization),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Dual-Color Live Harmony Preview Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(primaryColor, secondaryColor),
                            ),
                        )
                        .border(
                            1.dp,
                            colors.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.primary_color),
                            color = if (isColorDark(primaryColor)) Color.White else Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = "Harmony",
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        )
                        Text(
                            text = stringResource(R.string.secondary_color),
                            color = if (isColorDark(secondaryColor)) Color.White else Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }

                // Selector Tabs: Primary vs Secondary
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = colors.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = colors.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(primaryColor),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.primary_accent), fontWeight = FontWeight.SemiBold)
                            }
                        },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(secondaryColor),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.secondary_accent), fontWeight = FontWeight.SemiBold)
                            }
                        },
                    )
                }

                // Curated Palette Grid
                Text(
                    stringResource(R.string.palette_presets),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EXTENDED_PALETTE.forEach { swatch ->
                        val swatchArgb = HexColorParser.fromComposeColor(swatch)
                        val isSelected = (activeColorArgb and 0xFFFFFFFFL) == swatchArgb
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) colors.onSurface else colors.outline.copy(alpha = 0.35f),
                                    shape = CircleShape,
                                )
                                .clickable {
                                    if (selectedTab == 0) {
                                        currentPrimaryArgb = swatchArgb
                                    } else {
                                        currentSecondaryArgb = swatchArgb
                                    }
                                    hexInputText = HexColorParser.formatToHex(swatchArgb)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (isColorDark(swatch)) Color.White else Color.Black,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                // Optional Manual HEX Toggle
                TextButton(
                    onClick = { showManualHexInput = !showManualHexInput },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(
                        if (showManualHexInput) "▲ ${stringResource(R.string.custom_hex_input)}"
                        else "▼ ${stringResource(R.string.custom_hex_input)} (${HexColorParser.formatToHex(activeColorArgb)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                    )
                }

                if (showManualHexInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    parsedInputColor?.let { Color(it.toInt()) } ?: colors.surfaceVariant,
                                )
                                .border(1.dp, colors.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (parsedInputColor == null) {
                                Text("?", color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedTextField(
                            value = hexInputText,
                            onValueChange = { input ->
                                hexInputText = input
                                val parsed = HexColorParser.parse(input)
                                if (parsed != null) {
                                    if (selectedTab == 0) currentPrimaryArgb = parsed
                                    else currentSecondaryArgb = parsed
                                }
                            },
                            placeholder = { Text("#RRGGBB") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            isError = parsedInputColor == null && hexInputText.isNotBlank(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.outline.copy(alpha = 0.4f),
                            ),
                        )

                        IconButton(
                            onClick = {
                                val pasted = clipboardManager.getText()?.text
                                if (!pasted.isNullOrBlank()) {
                                    hexInputText = pasted
                                    val parsed = HexColorParser.parse(pasted)
                                    if (parsed != null) {
                                        if (selectedTab == 0) currentPrimaryArgb = parsed
                                        else currentSecondaryArgb = parsed
                                    }
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = stringResource(R.string.paste),
                                tint = colors.primary,
                            )
                        }
                    }

                    if (parsedInputColor == null && hexInputText.isNotBlank()) {
                        Text(
                            stringResource(R.string.invalid_hex),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                        )
                    }
                }

                // Quick Reset to Default Button
                TextButton(
                    onClick = {
                        if (selectedTab == 0) {
                            currentPrimaryArgb = AccentPalette.DEFAULT_ARGB
                            hexInputText = HexColorParser.formatToHex(AccentPalette.DEFAULT_ARGB)
                        } else {
                            currentSecondaryArgb = 0xFF38BDF8L
                            hexInputText = HexColorParser.formatToHex(0xFF38BDF8L)
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.reset_to_default))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(currentPrimaryArgb, currentSecondaryArgb)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(24.dp),
    )
}
