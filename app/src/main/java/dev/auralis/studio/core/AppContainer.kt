package dev.auralis.studio.core

import android.content.Context
import androidx.room.Room
import dev.auralis.studio.core.database.AppDatabase
import dev.auralis.studio.core.datastore.SettingsRepository
import dev.auralis.studio.library.MediaStoreMusicRepository
import dev.auralis.studio.library.SafMusicRepository
import dev.auralis.studio.audio.visualizer.PcmAudioTap
import dev.auralis.studio.lyrics.LrclibClient
import dev.auralis.studio.lyrics.LyricsRepository

class AppContainer(context: Context) {
    val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "auralis.db",
    )
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
        .build()

    val settings    = SettingsRepository(context)
    val library     = MediaStoreMusicRepository(context)
    val safLibrary  = SafMusicRepository(context)
    val pcmAudioTap = PcmAudioTap()
    val lyrics = LyricsRepository(context, database.lyricsDao(), LrclibClient())
    val downloadManager = dev.auralis.studio.library.DownloadIngestionManager(context, database.downloadsDao(), library).also {
        dev.auralis.studio.library.DownloadWorkerBridge.attach(it)
    }
    val downloadRepair = dev.auralis.studio.library.DownloadRepairManager(context, database.downloadsDao(), library)
}
