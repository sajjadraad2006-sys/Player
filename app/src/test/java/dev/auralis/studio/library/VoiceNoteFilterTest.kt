package dev.auralis.studio.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceNoteFilterTest {

    // ── Layer (a): Directory path and bucket exclusion ───────────────────────

    @Test
    fun whatsappVoiceNotesDirectory_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes/202305/PTT-20230501-WA0001.opus",
            relativePath = "WhatsApp/Media/WhatsApp Voice Notes/202305/",
            bucketName = "WhatsApp Voice Notes",
            displayName = "PTT-20230501-WA0001.opus",
            title = "PTT-20230501-WA0001",
            durationMs = 25_000L,
            mimeType = "audio/opus",
        )
        assertTrue("WhatsApp voice note path must be filtered", filtered)
    }

    @Test
    fun whatsappAudioDirectory_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/AUD-20230501-WA0002.m4a",
            relativePath = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/",
            bucketName = "WhatsApp Audio",
            displayName = "AUD-20230501-WA0002.m4a",
            title = "AUD-20230501-WA0002",
            durationMs = 45_000L,
            mimeType = "audio/mp4",
        )
        assertTrue("WhatsApp Audio path must be filtered", filtered)
    }

    @Test
    fun whatsappBusinessDirectory_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes/PTT-1.opus",
            relativePath = "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes/",
            bucketName = "WhatsApp Business Voice Notes",
            displayName = "PTT-1.opus",
            title = "PTT-1",
            durationMs = 12_000L,
            mimeType = "audio/ogg",
        )
        assertTrue("WhatsApp Business voice note must be filtered", filtered)
    }

    @Test
    fun telegramAudioDirectory_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Telegram/Telegram Audio/audio_2023-08-14_18-20-11.ogg",
            relativePath = "Telegram/Telegram Audio/",
            bucketName = "Telegram Audio",
            displayName = "audio_2023-08-14_18-20-11.ogg",
            title = "audio_2023-08-14_18-20-11",
            durationMs = 30_000L,
            mimeType = "audio/ogg",
        )
        assertTrue("Telegram audio folder must be filtered", filtered)
    }

    @Test
    fun recordingsCallDirectory_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Recordings/Call/Call_20230814.m4a",
            relativePath = "Recordings/Call/",
            bucketName = "Call",
            displayName = "Call_20230814.m4a",
            title = "Call_20230814",
            durationMs = 90_000L,
            mimeType = "audio/mp4",
        )
        assertTrue("Call recording folder must be filtered", filtered)
    }

    @Test
    fun voiceRecorderDirectory_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Voice Recorder/Voice_001.m4a",
            relativePath = "Voice Recorder/",
            bucketName = "Voice Recorder",
            displayName = "Voice_001.m4a",
            title = "Voice_001",
            durationMs = 60_000L,
            mimeType = "audio/mp4",
        )
        assertTrue("Voice Recorder folder must be filtered", filtered)
    }

    @Test
    fun soundRecorderBucket_withoutMetadata_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/MIUI/sound_recorder/rec.mp3",
            relativePath = "MIUI/sound_recorder/",
            bucketName = "sound_recorder",
            displayName = "rec.mp3",
            title = "rec",
            artist = null,
            album = null,
            durationMs = 40_000L,
            mimeType = "audio/mpeg",
        )
        assertTrue("MIUI sound recorder folder must be filtered", filtered)
    }

    @Test
    fun whatsappVoiceNotesSlashDirectory_longDurationNoTags_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/WhatsApp/Voice Notes/note.opus",
            displayName = "note.opus",
            durationMs = 65_000L,
            artist = null,
            album = null,
        )
        assertTrue("WhatsApp/Voice Notes path without tags must be filtered even if > 60s", filtered)
    }

    @Test
    fun whatsappMediaVoiceNotes_longDurationNoTags_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/WhatsApp/Media/Voice Notes/audio_01.opus",
            displayName = "audio_01.opus",
            durationMs = 70_000L,
            artist = null,
            album = null,
        )
        assertTrue("WhatsApp/Media/Voice Notes path without tags must be filtered", filtered)
    }

    @Test
    fun telegramDocuments_unhyphenatedDate_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Telegram/Telegram Documents/audio_20230814_120000.ogg",
            relativePath = "Telegram/Telegram Documents/",
            bucketName = "Telegram Documents",
            displayName = "audio_20230814_120000.ogg",
            title = "audio_20230814_120000",
            durationMs = 75_000L,
            artist = null,
            album = null,
        )
        assertTrue("Telegram Documents with unhyphenated date must be filtered", filtered)
    }

    @Test
    fun telegramDocuments_voiceMessage_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Telegram/Telegram Documents/voice-message.ogg",
            displayName = "voice-message.ogg",
            durationMs = 90_000L,
            artist = null,
            album = null,
        )
        assertTrue("Telegram Documents voice-message without tags must be filtered", filtered)
    }

    @Test
    fun samsungVoiceRecorder_inSafRecordings_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/tree/primary:Recordings/document/primary:Recordings/Voice 001.m4a",
            relativePath = "/tree/primary:Recordings/document/primary:Recordings/Voice 001.m4a",
            bucketName = "Recordings",
            displayName = "Voice 001.m4a",
            title = "Voice 001",
            durationMs = 65_000L,
            artist = null,
            album = null,
        )
        assertTrue("Samsung Voice Recorder in /Recordings/ with space in name must be filtered", filtered)
    }

    @Test
    fun samsungCallRecorder_withContactName_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            displayName = "Call with John_230814.m4a",
            title = "Call with John_230814",
            durationMs = 80_000L,
            artist = null,
            album = null,
        )
        assertTrue("Call recording with contact name must be filtered", filtered)
    }

    @Test
    fun oppoVivoCallRecorder_recordCall_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Record/Call/rec_1001.m4a",
            displayName = "rec_1001.m4a",
            durationMs = 70_000L,
            artist = null,
            album = null,
        )
        assertTrue("Record/Call path must be filtered", filtered)
    }

    @Test
    fun exactlySixtySecondsVoiceMemo_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Download/memo.opus",
            displayName = "memo.opus",
            title = "memo",
            artist = null,
            album = null,
            durationMs = 60_000L,
            mimeType = "audio/opus",
        )
        assertTrue("Voice memo of exactly 60,000 ms must be filtered", filtered)
    }

    // ── Layer (d): Voice codecs and audio format profiles ────────────────────

    @Test
    fun amrCodecMime_isAlwaysFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Download/memo.amr",
            displayName = "memo.amr",
            title = "memo",
            artist = "Someone",
            album = "Some Album",
            durationMs = 20_000L,
            mimeType = "audio/amr",
        )
        assertTrue("audio/amr MIME must always be filtered", filtered)
    }

    @Test
    fun amrExtension_isAlwaysFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Music/memo.amr",
            displayName = "memo.amr",
            title = "memo",
            durationMs = 30_000L,
            mimeType = null,
        )
        assertTrue(".amr extension must always be filtered", filtered)
    }

    @Test
    fun threeGppCodec_isAlwaysFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Music/sample.3gp",
            displayName = "sample.3gp",
            title = "sample",
            durationMs = 15_000L,
            mimeType = "audio/3gpp",
        )
        assertTrue("3GPP voice recording must be filtered", filtered)
    }

    @Test
    fun monoAudioProfile_shortRecordingWithoutMetadata_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Music/quick_voice.opus",
            displayName = "quick_voice.opus",
            title = "quick_voice",
            artist = null,
            album = null,
            durationMs = 45_000L,
            mimeType = "audio/opus",
            channelCount = 1,
        )
        assertTrue("Mono opus voice recording must be filtered", filtered)
    }

    // ── Layer (c): Voice-note naming patterns ────────────────────────────────

    @Test
    fun whatsappPttPattern_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Download/PTT-20231119-WA0045.opus",
            displayName = "PTT-20231119-WA0045.opus",
            title = "PTT-20231119-WA0045",
            artist = "<unknown>",
            album = "<unknown>",
            durationMs = 35_000L,
        )
        assertTrue("PTT naming pattern must be filtered", filtered)
    }

    @Test
    fun whatsappAudPattern_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Download/AUD-20231119-WA0045.m4a",
            displayName = "AUD-20231119-WA0045.m4a",
            title = "AUD-20231119-WA0045",
            artist = "Unknown Artist",
            album = "Unknown Album",
            durationMs = 45_000L,
        )
        assertTrue("AUD naming pattern must be filtered", filtered)
    }

    @Test
    fun telegramVoicePattern_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            displayName = "voice_2023-11-19_14-30-00.ogg",
            title = "voice_2023-11-19_14-30-00",
            durationMs = 28_000L,
        )
        assertTrue("Telegram voice naming pattern must be filtered", filtered)
    }

    @Test
    fun recordingPrefixPattern_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            displayName = "REC_20231119_120000.m4a",
            title = "REC_20231119_120000",
            durationMs = 50_000L,
        )
        assertTrue("REC prefix pattern must be filtered", filtered)
    }

    @Test
    fun callRecordingPattern_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            displayName = "call@20231119_123456.m4a",
            title = "call@20231119_123456",
            durationMs = 80_000L,
        )
        assertTrue("Call recording pattern must be filtered", filtered)
    }

    @Test
    fun epochTimestampPureNumericPattern_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            displayName = "1700407800000.opus",
            title = "1700407800000",
            durationMs = 18_000L,
        )
        assertTrue("Pure numeric epoch filename without metadata must be filtered", filtered)
    }

    // ── Layer (b): Absence of standard metadata ──────────────────────────────

    @Test
    fun metadataSanityChecks_rejectGenericPlaceholders() {
        assertFalse(VoiceNoteFilter.hasValidArtist(null))
        assertFalse(VoiceNoteFilter.hasValidArtist(""))
        assertFalse(VoiceNoteFilter.hasValidArtist("<unknown>"))
        assertFalse(VoiceNoteFilter.hasValidArtist("Unknown Artist"))
        assertFalse(VoiceNoteFilter.hasValidArtist("Unknown"))
        assertFalse(VoiceNoteFilter.hasValidArtist("Music"))
        assertFalse(VoiceNoteFilter.hasValidArtist("Voice Recorder"))
        assertTrue(VoiceNoteFilter.hasValidArtist("Pink Floyd"))

        assertFalse(VoiceNoteFilter.hasValidAlbum(null))
        assertFalse(VoiceNoteFilter.hasValidAlbum(""))
        assertFalse(VoiceNoteFilter.hasValidAlbum("<unknown>"))
        assertFalse(VoiceNoteFilter.hasValidAlbum("Unknown Album"))
        assertFalse(VoiceNoteFilter.hasValidAlbum("Unknown"))
        assertTrue(VoiceNoteFilter.hasValidAlbum("The Dark Side of the Moon"))
    }

    @Test
    fun shortVoiceMemoWithoutArtistOrAlbum_isFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Download/memo.opus",
            displayName = "memo.opus",
            title = "memo",
            artist = "",
            album = "",
            durationMs = 25_000L,
            mimeType = "audio/opus",
        )
        assertTrue("Short memo without artist or album must be filtered", filtered)
    }

    // ── Negative Tests: Legitimate music MUST NOT be filtered ─────────────────

    @Test
    fun legitimateMusicTrack_isNotFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Music/Pink Floyd - Time.mp3",
            relativePath = "Music/",
            bucketName = "Music",
            displayName = "Pink Floyd - Time.mp3",
            title = "Time",
            artist = "Pink Floyd",
            album = "The Dark Side of the Moon",
            durationMs = 413_000L,
            mimeType = "audio/mpeg",
        )
        assertFalse("Legitimate music track must NOT be filtered", filtered)
    }

    @Test
    fun lyruneDownloadedTrack_isNotFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Music/Lyrune/Chill Artist - Ambient Echoes.mp3",
            relativePath = "Music/Lyrune/",
            bucketName = "Lyrune",
            displayName = "Chill Artist - Ambient Echoes.mp3",
            title = "Ambient Echoes",
            artist = "Chill Artist",
            album = "Relaxation Vol 1",
            durationMs = 210_000L,
            mimeType = "audio/mpeg",
        )
        assertFalse("Lyrune downloaded song must NOT be filtered", filtered)
    }

    @Test
    fun flacMusicTrack_isNotFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Music/Albums/Radiohead/Paranoid Android.flac",
            relativePath = "Music/Albums/Radiohead/",
            bucketName = "Radiohead",
            displayName = "Paranoid Android.flac",
            title = "Paranoid Android",
            artist = "Radiohead",
            album = "OK Computer",
            durationMs = 383_000L,
            mimeType = "audio/flac",
        )
        assertFalse("FLAC track with complete metadata must NOT be filtered", filtered)
    }

    @Test
    fun numericSongTitle_withValidArtistAndAlbum_isNotFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Music/Prince/1999.mp3",
            relativePath = "Music/Prince/",
            bucketName = "Prince",
            displayName = "1999.mp3",
            title = "1999",
            artist = "Prince",
            album = "1999",
            durationMs = 373_000L,
            mimeType = "audio/mpeg",
        )
        assertFalse("Numeric song title with valid artist and album must NOT be filtered", filtered)
    }

    @Test
    fun singleRelease_deadmau5_timestampTitle_isNotFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Music/2023-01-01 12-00-00.mp3",
            displayName = "2023-01-01 12-00-00.mp3",
            title = "2023-01-01 12-00-00",
            artist = "deadmau5",
            album = null,
            durationMs = 360_000L,
            mimeType = "audio/mpeg",
        )
        assertFalse("Electronic single with valid artist deadmau5 and null album must NOT be filtered", filtered)
    }

    @Test
    fun singleRelease_aphexTwin_numericTitle_isNotFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Music/1049281.mp3",
            displayName = "1049281.mp3",
            title = "1049281",
            artist = "Aphex Twin",
            album = null,
            durationMs = 300_000L,
            mimeType = "audio/mpeg",
        )
        assertFalse("Single with numeric title and valid artist Aphex Twin must NOT be filtered", filtered)
    }

    @Test
    fun song_theBeatles_recordingPrefixTitle_isNotFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Music/The Beatles/Recording_1.mp3",
            displayName = "Recording_1.mp3",
            title = "Recording_1",
            artist = "The Beatles",
            album = null,
            durationMs = 180_000L,
            mimeType = "audio/mpeg",
        )
        assertFalse("Song titled Recording_1 with valid artist The Beatles must NOT be filtered", filtered)
    }

    @Test
    fun singleRelease_danger_synthwave_isNotFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Music/2020-05-01 14-30-00.flac",
            displayName = "2020-05-01 14-30-00.flac",
            title = "2020-05-01 14-30-00",
            artist = "Danger",
            album = null,
            durationMs = 240_000L,
            mimeType = "audio/flac",
        )
        assertFalse("Synthwave single with timestamp title and valid artist Danger must NOT be filtered", filtered)
    }

    @Test
    fun songInSoundRecorderFolder_withValidMetadata_isNotFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/MIUI/sound_recorder/Pink Floyd - Time.mp3",
            relativePath = "MIUI/sound_recorder/",
            bucketName = "sound_recorder",
            displayName = "Pink Floyd - Time.mp3",
            title = "Time",
            artist = "Pink Floyd",
            album = "The Dark Side of the Moon",
            durationMs = 413_000L,
            mimeType = "audio/mpeg",
        )
        assertFalse("Legitimate song in sound_recorder folder with full tags must NOT be filtered", filtered)
    }

    @Test
    fun monoAudioProfile_withValidAlbum_isNotFiltered() {
        val filtered = VoiceNoteFilter.isVoiceNote(
            path = "/storage/emulated/0/Music/Intro.m4a",
            displayName = "Intro.m4a",
            title = "Intro",
            artist = "Unknown",
            album = "Greatest Hits",
            durationMs = 90_000L,
            mimeType = "audio/mp4",
            channelCount = 1,
        )
        assertFalse("Mono track with valid album must NOT be filtered", filtered)
    }
}
