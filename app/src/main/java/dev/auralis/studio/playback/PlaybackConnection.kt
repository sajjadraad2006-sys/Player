package dev.auralis.studio.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.auralis.studio.core.model.AudioConfig
import dev.auralis.studio.core.model.Track
import dev.auralis.studio.playback.PlaybackCommands.toBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val connected: Boolean = false,
    val currentMediaItem: MediaItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val error: String? = null,
)

class PlaybackConnection(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var pendingConfig: AudioConfig? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _state.value = _state.value.copy(error = error.localizedMessage)
        }
    }

    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
    ).buildAsync().also { future ->
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaController ->
                        controller = mediaController
                        mediaController.addListener(listener)
                        publish(mediaController)
                        pendingConfig?.let(::applyAudioConfig)
                        startTicker()
                    }
                    .onFailure { _state.value = _state.value.copy(error = it.localizedMessage) }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun play(track: Track, queue: List<Track>) {
        val player = controller ?: return
        val items = queue.map { it.toMediaItem() }
        val index = queue.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
        player.setMediaItems(items, index, 0L)
        player.prepare()
        player.play()
    }

    fun playPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() = controller?.seekToNextMediaItem() ?: Unit
    fun previous() = controller?.seekToPreviousMediaItem() ?: Unit
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs) ?: Unit
    fun playQueueIndex(index: Int) = controller?.seekToDefaultPosition(index) ?: Unit

    fun playNext(track: Track) {
        val player = controller ?: return
        val index = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(index, track.toMediaItem())
    }

    fun addToQueue(track: Track) {
        controller?.addMediaItem(track.toMediaItem())
    }

    fun removeQueueItem(index: Int) {
        controller?.takeIf { index in 0 until it.mediaItemCount }?.removeMediaItem(index)
    }

    fun clearQueue() {
        controller?.clearMediaItems()
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun applyAudioConfig(config: AudioConfig) {
        pendingConfig = config
        controller?.sendCustomCommand(PlaybackCommands.setAudioConfigCommand, config.toBundle())
    }

    fun release() {
        ticker?.cancel()
        controller?.removeListener(listener)
        controller = null
        MediaController.releaseFuture(controllerFuture)
    }

    private fun publish(player: Player) {
        val queue = List(player.mediaItemCount) { player.getMediaItemAt(it) }
        _state.value = PlaybackUiState(
            connected = true,
            currentMediaItem = player.currentMediaItem,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: 0,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queue = queue,
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            error = _state.value.error,
        )
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                controller?.let(::publish)
                delay(if (controller?.isPlaying == true) 250 else 1_000)
            }
        }
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(uri)
        .setUri(Uri.parse(uri))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri?.let(Uri::parse))
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()
}
