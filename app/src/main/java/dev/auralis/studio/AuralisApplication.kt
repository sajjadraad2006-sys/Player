package dev.auralis.studio

import android.app.Application
import dev.auralis.studio.core.AppContainer

class AuralisApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Eagerly initialize application container upon process start (including WorkManager process creation)
        container
    }
}
