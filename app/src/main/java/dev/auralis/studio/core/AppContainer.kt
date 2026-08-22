package dev.auralis.studio.core

import android.content.Context
import androidx.room.Room
import dev.auralis.studio.core.database.AppDatabase
import dev.auralis.studio.core.datastore.SettingsRepository
import dev.auralis.studio.library.MediaStoreMusicRepository
import dev.auralis.studio.library.SafMusicRepository

class AppContainer(context: Context) {
    val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "auralis.db",
    ).build()
    val settings = SettingsRepository(context)
    val library = MediaStoreMusicRepository(context)
    val safLibrary = SafMusicRepository(context)
}
