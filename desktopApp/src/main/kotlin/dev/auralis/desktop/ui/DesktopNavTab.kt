package dev.auralis.desktop.ui
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
enum class DesktopNavTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home), LIBRARY("Library", Icons.Default.LibraryMusic), ONLINE("Search", Icons.Default.Search),
    AUDIO_LAB("Audio Lab", Icons.Default.Tune), SETTINGS("Settings", Icons.Default.Settings),
    LYRICS("Lyrics", Icons.Default.FormatQuote), VISUALIZER("Visualizer", Icons.Default.GraphicEq)
}
