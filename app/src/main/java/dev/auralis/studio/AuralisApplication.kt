package dev.auralis.studio

import android.app.Application
import dev.auralis.studio.core.AppContainer
import dev.auralis.studio.core.ProcessRole
import dev.auralis.studio.core.ProcessUtils

class AuralisApplication : Application() {

    private var _container: AppContainer? = null

    /**
     * Strongly-typed access to the main application container.
     * Guaranteed to be available in the main process (`dev.auralis.studio`).
     * Throws an [IllegalStateException] if invoked inside the `:resolver` process.
     */
    val mainContainer: AppContainer
        get() = requireNotNull(_container) {
            "AppContainer is only available in the main application process (current: ${ProcessUtils.currentProcessName(this)})"
        }

    /**
     * Alias for backward compatibility with existing main-process components.
     */
    val container: AppContainer
        get() = mainContainer

    override fun onCreate() {
        super.onCreate()

        when (ProcessUtils.detectProcessRole(this)) {
            ProcessRole.MAIN -> initializeMainProcess()
            ProcessRole.RESOLVER -> initializeResolverProcess()
            ProcessRole.OTHER -> initializeOtherProcess()
        }
    }

    private fun initializeMainProcess() {
        // Main process: eagerly instantiate container (Room DB, DownloadWorkerBridge, Providers, Resolver Client)
        _container = AppContainer(this)
    }

    private fun initializeResolverProcess() {
        // Resolver process (:resolver): lightweight bootstrap ONLY.
        // Do NOT instantiate AppContainer, Room DB, DownloadWorkerBridge, or AndroidProcessMediaResolver client.
        // Python, Chaquopy, yt-dlp, and QuickJS initialize lazily inside LrrResolverService.
    }

    private fun initializeOtherProcess() {
        // Auxiliary or test process: minimal initialization.
    }
}
