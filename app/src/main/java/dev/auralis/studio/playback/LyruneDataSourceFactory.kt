package dev.auralis.studio.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import dev.auralis.studio.audio.stems.StemMixingDataSource

/**
 * Universal DataSource.Factory that intercepts `lyrune-stem://` URIs to route through
 * the synchronized [StemMixingDataSource], and uses standard [DefaultDataSource] for normal media.
 */
@OptIn(UnstableApi::class)
class LyruneDataSourceFactory(
    private val context: Context,
) : DataSource.Factory {

    private val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .setDefaultRequestProperties(
            mapOf(
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/",
            )
        )
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(25_000)

    private val defaultFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    override fun createDataSource(): DataSource = object : DataSource {
        private var activeSource: DataSource? = null
        private val stemSource = StemMixingDataSource()
        private val defaultSource = defaultFactory.createDataSource()

        override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
            defaultSource.addTransferListener(transferListener)
            stemSource.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val scheme = dataSpec.uri.scheme
            val source = if (scheme == "lyrune-stem" || scheme == "stem") {
                stemSource
            } else {
                defaultSource
            }
            activeSource = source
            return source.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return activeSource?.read(buffer, offset, length) ?: -1
        }

        override fun getUri() = activeSource?.uri

        override fun close() {
            activeSource?.close()
            activeSource = null
        }
    }
}
