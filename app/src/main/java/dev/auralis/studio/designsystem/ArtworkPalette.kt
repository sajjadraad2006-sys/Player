package dev.auralis.studio.designsystem

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class ArtworkPalette(
    val dominant: Color,
    val vibrant: Color,
    val muted: Color,
    val dark: Color,
    val light: Color,
)

private object ArtworkPaletteCache {
    val values = LruCache<String, ArtworkPalette>(24)
}

/** Extracts once per artwork URI off the UI thread; Coil and a small LRU prevent repeat bitmap work. */
@Composable
fun rememberArtworkPalette(artworkUri: String?): ArtworkPalette? {
    val context = LocalContext.current
    val palette by produceState<ArtworkPalette?>(initialValue = artworkUri?.let(ArtworkPaletteCache.values::get), key1 = artworkUri) {
        if (artworkUri == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val model: Any = if (artworkUri.startsWith("/") && !artworkUri.startsWith("content://") && !artworkUri.startsWith("file://")) {
                    java.io.File(artworkUri)
                } else {
                    artworkUri
                }
                val appContext = context.applicationContext
                val request = ImageRequest.Builder(appContext)
                    .data(model)
                    .size(128, 128) // Downsample to 128x128 for palette analysis, saving 99% RAM
                    .allowHardware(false)
                    .build()
                val drawable = coil.Coil.imageLoader(appContext).execute(request).drawable ?: return@runCatching null
                val generated = Palette.from(drawable.toBitmap()).maximumColorCount(24).generate()
                val dominant = generated.dominantSwatch?.rgb ?: generated.vibrantSwatch?.rgb ?: return@runCatching null
                ArtworkPalette(
                    dominant = Color(dominant),
                    vibrant = Color(generated.vibrantSwatch?.rgb ?: generated.lightVibrantSwatch?.rgb ?: dominant),
                    muted = Color(generated.mutedSwatch?.rgb ?: generated.darkMutedSwatch?.rgb ?: dominant),
                    dark = Color(generated.darkMutedSwatch?.rgb ?: generated.darkVibrantSwatch?.rgb ?: dominant),
                    light = Color(generated.lightMutedSwatch?.rgb ?: generated.lightVibrantSwatch?.rgb ?: dominant),
                ).also { ArtworkPaletteCache.values.put(artworkUri, it) }
            }.getOrNull()
        }
    }
    return palette
}
