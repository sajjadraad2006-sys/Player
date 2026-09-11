package dev.auralis.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.auralis.desktop.artwork.DesktopArtworkManager

@Composable fun TrackArtwork(key: String?, modifier: Modifier = Modifier) {
    var artwork by remember(key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(key) { artwork = key?.takeIf(String::isNotBlank)?.let { DesktopArtworkManager.getArtwork(it) } }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF222E36)), contentAlignment = Alignment.Center) {
        artwork?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(Icons.Default.MusicNote, null, tint = Color(0xFF71E2BE), modifier = Modifier.size(26.dp))
    }
}
