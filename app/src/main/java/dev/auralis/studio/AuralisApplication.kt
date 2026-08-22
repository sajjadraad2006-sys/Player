package dev.auralis.studio

import android.app.Application
import dev.auralis.studio.core.AppContainer

class AuralisApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
