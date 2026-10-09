package dev.auralis.studio

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.auralis.studio.core.AppContainer
import dev.auralis.studio.core.ProcessRole
import dev.auralis.studio.core.ProcessUtils

class AuralisApplication : Application(), ImageLoaderFactory {

    private var _container: AppContainer? = null
    private var _imageLoader: ImageLoader? = null

    /**
     * Whether the main [AppContainer] has been instantiated in this process.
     */
    val isMainContainerInitialized: Boolean
        get() = _container != null

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

    override fun newImageLoader(): ImageLoader {
        return _imageLoader ?: synchronized(this) {
            _imageLoader ?: ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.20) // Limit in-memory image cache to 20% of app RAM
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(100L * 1024L * 1024L) // 100 MB disk cache cap
                        .build()
                }
                .crossfade(true)
                .respectCacheHeaders(false)
                .build()
                .also { _imageLoader = it }
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Clear in-memory bitmap cache when the system signals memory pressure
        if (level >= TRIM_MEMORY_BACKGROUND || level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            _imageLoader?.memoryCache?.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        _imageLoader?.memoryCache?.clear()
    }

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
        dev.auralis.studio.library.DownloadNotificationHelper.init(this)
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
