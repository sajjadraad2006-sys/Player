package dev.auralis.studio.core.model

data class Track(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val albumId: Long? = null,
    val durationMs: Long = 0L,
    val dateAddedSeconds: Long = 0L,
    val displayName: String = "",
    val mimeType: String? = "audio/mpeg",
    val sizeBytes: Long = 0L,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val customArtworkUri: String? = null,
) {
    val artworkUri: String?
        get() = customArtworkUri?.takeIf { it.isNotBlank() }
            ?: albumId?.let { "content://media/external/audio/albumart/$it" }
}
