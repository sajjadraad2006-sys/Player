package dev.auralis.studio.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.auralis.studio.AuralisApplication
import dev.auralis.studio.MainActivity
import dev.auralis.studio.R
import dev.auralis.studio.audio.dsp.StudioAudioProcessor
import dev.auralis.studio.audio.dsp.StudioAudioProcessorChain
import dev.auralis.studio.core.database.PlaybackHistoryEntity
import dev.auralis.studio.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var processor: StudioAudioProcessor
    private lateinit var player: ExoPlayer
    private lateinit var librarySession: MediaLibrarySession

    private val container get() = (application as AuralisApplication).container

    override fun onCreate() {
        super.onCreate()
        processor = StudioAudioProcessor(container.pcmAudioTap)
        val renderersFactory = StudioRenderersFactory(this, processor)
        player = ExoPlayer.Builder(this, renderersFactory)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                addListener(historyListener)
                addListener(queuePersistenceListener)
            }

        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        librarySession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(activityIntent)
            .build()

        serviceScope.launch {
            val config = container.settings.audioConfig.first()
            applyAudioConfig(config)
            val minimumDuration = container.settings.minimumTrackDurationMs.first()
            if (container.library.hasPermission) container.library.refresh(minimumDuration)
            val safTracks = container.safLibrary.scan(container.settings.musicFolderUris.first(), minimumDuration)
            container.library.updateSafTracks(safTracks)
            restoreQueue()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = librarySession

    override fun onDestroy() {
        librarySession.release()
        player.removeListener(historyListener)
        player.removeListener(queuePersistenceListener)
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun applyAudioConfig(config: dev.auralis.studio.core.model.AudioConfig) {
        processor.update(config)
        val parameters = if (config.dspEnabled) {
            PlaybackParameters(config.speed, config.effectivePitchFactor)
        } else {
            PlaybackParameters.DEFAULT
        }
        if (player.playbackParameters != parameters) player.playbackParameters = parameters
    }

    private val historyListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val uri = mediaItem?.localConfiguration?.uri?.toString() ?: return
            serviceScope.launch(Dispatchers.IO) {
                val old = container.database.dao().history(uri)
                container.database.dao().upsertHistory(
                    PlaybackHistoryEntity(
                        trackUri = uri,
                        lastPlayedAt = System.currentTimeMillis(),
                        playCount = (old?.playCount ?: 0) + 1,
                        lastPositionMs = 0,
                    ),
                )
            }
        }
    }

    private val queuePersistenceListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = persistQueue()
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) persistQueue()
        }
    }

    private fun persistQueue() {
        val uris = List(player.mediaItemCount) { index ->
            player.getMediaItemAt(index).localConfiguration?.uri?.toString().orEmpty()
        }.filter(String::isNotBlank)
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val position = player.currentPosition.coerceAtLeast(0)
        serviceScope.launch(Dispatchers.IO) { container.settings.saveQueue(uris, index, position) }
    }

    private suspend fun restoreQueue() {
        if (player.mediaItemCount > 0) return
        val snapshot = container.settings.queueSnapshot.first()
        val items = snapshot.uris.mapNotNull(container.library::track).map { it.toMediaItem() }
        if (items.isNotEmpty()) {
            player.setMediaItems(items, snapshot.index.coerceIn(items.indices), snapshot.positionMs)
            player.prepare()
        }
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val defaultCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            val commands: SessionCommands = if (controller.packageName == packageName) {
                defaultCommands.buildUpon().add(PlaybackCommands.setAudioConfigCommand).build()
            } else defaultCommands
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (controller.packageName != packageName) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
            }
            if (customCommand.customAction == PlaybackCommands.SET_AUDIO_CONFIG) {
                applyAudioConfig(PlaybackCommands.fromBundle(args))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            LibraryResult.ofItem(category(ROOT, getString(R.string.app_name)), params),
        )

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = container.library.track(mediaId)?.toMediaItem()
            return Futures.immediateFuture(
                item?.let { LibraryResult.ofItem(it, null) }
                    ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE),
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return CallbackToFutureAdapter.getFuture { completer ->
                serviceScope.launch {
                    runCatching {
                        val items = buildChildren(parentId)
                        val start = (page * pageSize).coerceAtMost(items.size)
                        val end = (start + pageSize).coerceAtMost(items.size)
                        LibraryResult.ofItemList(items.subList(start, end), params)
                    }.onSuccess(completer::set).onFailure(completer::setException)
                }
                "Lyrune children: $parentId"
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            val count = searchTracks(query).size
            session.notifySearchResultChanged(browser, query, count, params)
            return Futures.immediateFuture(LibraryResult.ofVoid(params))
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val matches = searchTracks(query).map { it.toMediaItem() }
            val start = (page * pageSize).coerceAtMost(matches.size)
            val end = (start + pageSize).coerceAtMost(matches.size)
            return Futures.immediateFuture(LibraryResult.ofItemList(matches.subList(start, end), params))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            val resolved = mediaItems.mapNotNull { requested ->
                requested.takeIf { it.localConfiguration != null }
                    ?: container.library.track(requested.mediaId)?.toMediaItem()
                    ?: requested.requestMetadata.searchQuery?.toString()?.let(::searchTracks)?.firstOrNull()?.toMediaItem()
            }
            return Futures.immediateFuture(resolved)
        }
    }

    private fun searchTracks(query: String): List<Track> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        return container.library.tracks.value.filter {
            it.title.contains(normalized, ignoreCase = true) ||
                it.artist.contains(normalized, ignoreCase = true) ||
                it.album.contains(normalized, ignoreCase = true)
        }
    }

    private suspend fun buildChildren(parentId: String): List<MediaItem> {
        val tracks = container.library.tracks.value
        return when (parentId) {
            ROOT -> listOf(
                category(SONGS, getString(R.string.songs)),
                category(FAVORITES, getString(R.string.favorites)),
                category(RECENT, getString(R.string.recently_played)),
                category(ALBUMS, getString(R.string.albums)),
                category(ARTISTS, getString(R.string.artists)),
            )
            SONGS -> tracks.map { it.toMediaItem() }
            FAVORITES -> container.database.dao().observeFavoriteUris().first().mapNotNull(container.library::track).map { it.toMediaItem() }
            RECENT -> container.database.dao().observeHistory(100).first().mapNotNull { container.library.track(it.trackUri) }.map { it.toMediaItem() }
            ALBUMS -> tracks.groupBy { it.album.ifBlank { getString(R.string.unknown_album) } }
                .map { (album, albumTracks) -> category("album:$album", album, albumTracks.firstOrNull()?.artworkUri) }
            ARTISTS -> tracks.groupBy { it.artist.ifBlank { getString(R.string.unknown_artist) } }
                .map { (artist, _) -> category("artist:$artist", artist) }
            else -> when {
                parentId.startsWith("album:") -> tracks.filter { it.album == parentId.removePrefix("album:") }.map { it.toMediaItem() }
                parentId.startsWith("artist:") -> tracks.filter { it.artist == parentId.removePrefix("artist:") }.map { it.toMediaItem() }
                else -> emptyList()
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

    private fun category(id: String, title: String, artwork: String? = null): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtworkUri(artwork?.let(Uri::parse))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build(),
        )
        .build()

    private class StudioRenderersFactory(
        context: Context,
        private val processor: StudioAudioProcessor,
    ) : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(false)
            .setAudioProcessorChain(StudioAudioProcessorChain(processor))
            .build()
    }

    private companion object {
        const val ROOT = "root"
        const val SONGS = "songs"
        const val FAVORITES = "favorites"
        const val RECENT = "recent"
        const val ALBUMS = "albums"
        const val ARTISTS = "artists"
    }
}
