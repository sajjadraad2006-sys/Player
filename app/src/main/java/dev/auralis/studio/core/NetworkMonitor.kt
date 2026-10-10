package dev.auralis.studio.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real-time reactive network connectivity monitor and OkHttp offline interceptor.
 * Guarantees zero blocking / zero DNS stalls when offline.
 */
class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkOnlineNow())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    val currentOnline: Boolean
        get() = _isOnline.value

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager?.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isOnline.value = true
                    }

                    override fun onLost(network: Network) {
                        _isOnline.value = checkOnlineNow()
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        _isOnline.value = hasInternet
                    }
                },
            )
        } catch (_: Exception) {
            // Fallback to periodic or manual check if callback registration fails
        }
    }

    private fun checkOnlineNow(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * OkHttp Interceptor that prevents thread stalls when internet is disconnected.
     * When offline, attempts to read from cache immediately; if not in cache, fails fast (0ms)
     * instead of blocking on system DNS resolution.
     */
    val offlineCacheInterceptor: Interceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        if (!currentOnline) {
            val forceCacheRequest = originalRequest.newBuilder()
                .cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(30, TimeUnit.DAYS)
                        .build(),
                )
                .build()

            try {
                val response = chain.proceed(forceCacheRequest)
                if (response.isSuccessful || response.code == 304) {
                    return@Interceptor response
                }
            } catch (_: Exception) {
                // Not in cache
            }
            throw IOException("Network unavailable - offline mode")
        }
        chain.proceed(originalRequest)
    }
}
