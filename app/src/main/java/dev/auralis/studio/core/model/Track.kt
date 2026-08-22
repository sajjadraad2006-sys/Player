package dev.auralis.studio.core.model

data class Track(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long?,
    val durationMs: Long,
    val dateAddedSeconds: Long,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val trackNumber: Int?,
    val year: Int?,
) {
    val artworkUri: String?
        get() = albumId?.let { "content://media/external/audio/albumart/$it" }
}
