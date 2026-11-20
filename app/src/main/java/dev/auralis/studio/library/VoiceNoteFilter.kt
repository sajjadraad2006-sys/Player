package dev.auralis.studio.library

import java.util.regex.Pattern

/**
 * Intelligent 4-layer validation engine for filtering out voice notes,
 * voice memos, call recordings, and chat audio clips from the music library.
 */
object VoiceNoteFilter {

    private val EXCLUDED_DIRECTORY_PATTERNS = listOf(
        "whatsapp voice notes",
        "whatsapp voice note",
        "whatsapp/voice notes",
        "whatsapp/voice note",
        "voice notes",
        "voice note",
        "whatsapp audio",
        "whatsapp business voice notes",
        "whatsapp business audio",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "telegram/telegram audio",
        "telegram audio",
        "telegram/telegram documents",
        "telegram documents",
        "org.telegram.messenger",
        "recordings",
        "recordings/call",
        "record/call",
        "call recordings",
        "callrecordings",
        "callrecord",
        "call recording",
        "voice recorder",
        "voicerecorder",
        "voice memos",
        "voicememos",
        "sound recorder",
        "soundrecorder",
        "sound_recorder",
        "miui/sound_recorder",
        "com.facebook.orca",
        "viber/media",
    )

    private val EXCLUDED_BUCKET_NAMES = setOf(
        "whatsapp voice notes",
        "whatsapp voice note",
        "voice notes",
        "voice note",
        "whatsapp/voice notes",
        "whatsapp audio",
        "whatsapp business voice notes",
        "telegram audio",
        "telegram documents",
        "recordings",
        "record/call",
        "call recordings",
        "call",
        "voice recorder",
        "voicerecorder",
        "voice memos",
        "sound recorder",
        "soundrecorder",
        "sound_recorder",
    )

    private val VOICE_NOTE_NAME_PATTERNS = listOf(
        Pattern.compile("""^PTT-\d{8}-WA\d+.*""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""^AUD-\d{8}-WA\d+.*""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""^(audio|voice|msg)[_-]\d{4}[-_]?\d{2}[-_]?\d{2}.*""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""^(rec|record|recording)[_-]?\d+.*""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""^(voice|voicenote|voice memo)[ _-]?\d+.*""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""^call[ _@-].*""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""^(call|callrecord|callrecording)[_-]?\d+.*""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""^\d{4}[-_]?\d{2}[-_]?\d{2}[-_ ]\d{2}[-_]?\d{2}[-_]?\d{2}.*""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""^\d{6,}\.(opus|ogg|amr|3gp|m4a|mp3|wav|aac)$""", Pattern.CASE_INSENSITIVE),
    )

    private val VOICE_CODEC_MIMES = setOf(
        "audio/amr",
        "audio/amr-wb",
        "audio/3gpp",
        "audio/3gpp2",
    )

    private val VOICE_EXTENSIONS = setOf(
        "amr",
        "3gp",
        "3gpp",
    )

    private val EXCLUDED_ARTIST_NAMES = setOf(
        "<unknown>",
        "unknown artist",
        "unknown",
        "music",
        "voice recorder",
        "lyrune",
        "whatsapp",
        "whatsapp audio",
        "whatsapp voice notes",
        "telegram",
        "telegram audio",
        "sound recorder",
        "call recorder",
        "recorder",
        "voice memo",
        "voice notes",
        "ptt",
        "aud",
    )

    private val EXCLUDED_ALBUM_NAMES = setOf(
        "<unknown>",
        "unknown album",
        "unknown",
        "voice recorder",
        "whatsapp",
        "whatsapp audio",
        "whatsapp voice notes",
        "telegram",
        "telegram audio",
        "recordings",
        "call recordings",
        "voice memos",
        "voice notes",
        "sound recorder",
    )

    fun hasValidArtist(artist: String?): Boolean {
        if (artist.isNullOrBlank()) return false
        val trimmed = artist.trim().lowercase()
        return trimmed !in EXCLUDED_ARTIST_NAMES &&
            !trimmed.startsWith("whatsapp") &&
            !trimmed.startsWith("telegram")
    }

    fun hasValidAlbum(album: String?): Boolean {
        if (album.isNullOrBlank()) return false
        val trimmed = album.trim().lowercase()
        return trimmed !in EXCLUDED_ALBUM_NAMES &&
            !trimmed.startsWith("whatsapp") &&
            !trimmed.startsWith("telegram")
    }

    /**
     * Evaluates an audio asset against the 4-layer validation engine:
     * (a) Directory path and bucket exclusion
     * (b) Metadata sanity checking (artist/album presence)
     * (c) Voice-note naming pattern matching
     * (d) Codec and audio format profiling (voice codecs & mono opus in chat folders)
     *
     * Returns true if the asset is identified as a voice note, recording, or chat clip.
     */
    fun isVoiceNote(
        path: String? = null,
        relativePath: String? = null,
        bucketName: String? = null,
        displayName: String = "",
        title: String = "",
        artist: String? = null,
        album: String? = null,
        durationMs: Long = 0L,
        mimeType: String? = null,
        channelCount: Int? = null,
    ): Boolean {
        val cleanPath = (path.orEmpty() + "/" + relativePath.orEmpty() + "/" + bucketName.orEmpty())
            .replace('\\', '/')
            .lowercase()
        val cleanBucket = bucketName?.trim()?.lowercase().orEmpty()
        val cleanName = displayName.trim()
        val cleanTitle = title.trim()
        val cleanMime = mimeType?.lowercase()?.substringBefore(';') ?: ""
        val ext = cleanName.substringAfterLast('.', "").lowercase()

        // 1. Layer (d): Voice-only codecs (AMR, 3GP) are always filtered out
        if (cleanMime in VOICE_CODEC_MIMES || ext in VOICE_EXTENSIONS) {
            return true
        }

        // 2. Strict WhatsApp / Chat Push-To-Talk Name Matching (always voice note regardless of metadata)
        if (cleanName.startsWith("PTT-", ignoreCase = true) || cleanTitle.startsWith("PTT-", ignoreCase = true) ||
            cleanName.matches(Regex("""^AUD-\d{8}-WA\d+.*""", RegexOption.IGNORE_CASE)) ||
            cleanTitle.matches(Regex("""^AUD-\d{8}-WA\d+.*""", RegexOption.IGNORE_CASE))
        ) {
            return true
        }

        // 3. Strict WhatsApp Voice Notes Directory (always voice notes)
        if (cleanPath.contains("whatsapp voice notes") || cleanPath.contains("whatsapp/voice notes") ||
            cleanPath.contains("com.whatsapp/whatsapp/media/whatsapp voice notes") ||
            cleanPath.contains("com.whatsapp.w4b/whatsapp business/media/whatsapp business voice notes")
        ) {
            return true
        }

        val validArtist = hasValidArtist(artist)
        val validAlbum = hasValidAlbum(album)

        // 4. Broader checks apply when the audio lacks BOTH a legitimate artist AND album
        if (!validArtist && !validAlbum) {
            for (pattern in EXCLUDED_DIRECTORY_PATTERNS) {
                if (cleanPath.contains(pattern)) {
                    return true
                }
            }
            if (cleanBucket.isNotEmpty() && cleanBucket in EXCLUDED_BUCKET_NAMES) {
                return true
            }

            for (pattern in VOICE_NOTE_NAME_PATTERNS) {
                if (pattern.matcher(cleanName).matches() || pattern.matcher(cleanTitle).matches()) {
                    return true
                }
            }

            // Short audio recordings (<= 60s) lacking both valid artist and album
            if (durationMs in 1..60_000L) {
                if (ext in setOf("opus", "ogg", "m4a", "wav", "aac")) {
                    return true
                }
            }

            // Mono audio profile check (if channelCount known)
            if (channelCount == 1 && durationMs < 120_000L) {
                if (ext in setOf("opus", "ogg", "m4a", "amr", "wav")) {
                    return true
                }
            }
        }

        return false
    }
}
