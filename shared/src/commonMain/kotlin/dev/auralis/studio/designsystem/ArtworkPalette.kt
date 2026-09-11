package dev.auralis.studio.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ArtworkPalette(
    val dominant: Color,
    val vibrant: Color,
    val muted: Color,
    val dark: Color,
    val light: Color,
)
