package dev.auralis.studio.core

import android.content.Context
import androidx.room.Room
import dev.auralis.studio.BuildConfig
import dev.auralis.studio.core.database.AppDatabase
import dev.auralis.studio.core.datastore.SettingsRepository
import dev.auralis.studio.library.MediaStoreMusicRepository
import dev.auralis.studio.library.SafMusicRepository
import dev.auralis.studio.audio.visualizer.PcmAudioTap
import dev.auralis.studio.lyrics.LrclibClient
import dev.auralis.studio.lyrics.LyricsRepository
import dev.auralis.studio.online.AudiusClient
import dev.auralis.studio.online.ITunesCatalogProvider
import dev.auralis.studio.online.DeezerCatalogProvider
import dev.auralis.studio.online.JamendoClient
import dev.auralis.studio.online.MusicProviderRegistry
import dev.auralis.studio.online.UnifiedSearchAggregator

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
    val audiusProvider = AudiusClient(BuildConfig.AUDIUS_API_KEY)
    val jamendoProvider = JamendoClient(BuildConfig.JAMENDO_CLIENT_ID)
    val itunesProvider = ITunesCatalogProvider()
    val deezerProvider = DeezerCatalogProvider()
    val youtubeProvider = dev.auralis.studio.online.YouTubeCatalogProvider(
        apiKey = BuildConfig.YOUTUBE_API_KEY,
        enableWebFallback = true,
    )
    val musicProviders = MusicProviderRegistry(
        catalogProviders = listOf(youtubeProvider),
        streamProviders = emptyList(),
        downloadProviders = emptyList(),
    )
    val localDiscovery = dev.auralis.studio.online.LocalLibraryDiscovery(library)
    val unifiedSearch = UnifiedSearchAggregator(
        providers = listOf(youtubeProvider),
        localDiscovery = localDiscovery,
        debugLogging = BuildConfig.DEBUG,
    )
    val downloadManager = dev.auralis.studio.library.DownloadIngestionManager(
        context,
        database.downloadsDao(),
        library,
        requestHeadersFor = musicProviders::downloadHeaders,
    ).also {
        dev.auralis.studio.library.DownloadWorkerBridge.attach(it)
    }
    val downloadRepair = dev.auralis.studio.library.DownloadRepairManager(context, database.downloadsDao(), library)
    val mediaResolver: dev.auralis.studio.resolver.api.MediaResolver = dev.auralis.studio.resolver.android.AndroidProcessMediaResolver(context).also {
        dev.auralis.studio.library.DownloadWorkerBridge.attachResolver(it)
    }
}
