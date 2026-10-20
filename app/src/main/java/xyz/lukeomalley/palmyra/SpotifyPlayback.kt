package xyz.lukeomalley.palmyra

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

data class NowPlaying(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val canToggle: Boolean = true,
    val canNext: Boolean = false,
    val artwork: Bitmap? = null,
) {
    val actions: List<LauncherState.Effect>
        get() =
            buildList {
                add(LauncherState.Effect.OPEN_SPOTIFY)
                if (canToggle) add(LauncherState.Effect.TOGGLE_PLAYBACK)
                if (canNext) add(LauncherState.Effect.NEXT_TRACK)
            }
}

/** No polling, network, artwork downloads or work while Home is paused. */
class SpotifyPlayback(
    context: Context,
    private val changed: (NowPlaying?) -> Unit,
) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaAccessService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { if (active) select(it.orEmpty()) }
    private var active = false
    private var listening = false
    private var controller: MediaController? = null
    private var callback: MediaController.Callback? = null
    private var published = false
    private var last: NowPlaying? = null

    fun start() {
        if (active) return
        active = true
        MediaAccessService.onAccessChanged = { refreshAccess() }
        refreshAccess()
    }

    fun stop() {
        active = false
        MediaAccessService.onAccessChanged = null
        detach()
        if (listening) manager.removeOnActiveSessionsChangedListener(sessions)
        listening = false
        published = false
        last = null
    }

    private fun refreshAccess() {
        if (!active) return
        try {
            if (!listening) {
                manager.addOnActiveSessionsChangedListener(sessions, component, handler)
                listening = true
            }
            select(manager.getActiveSessions(component))
        } catch (_: SecurityException) {
            if (listening) manager.removeOnActiveSessionsChangedListener(sessions)
            listening = false
            detach()
            emit(null)
        }
    }

    private fun select(available: List<MediaController>) {
        val spotify = available.filter { it.packageName == PACKAGE }
        val next =
            spotify.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: spotify.firstOrNull()
        if (next?.sessionToken != controller?.sessionToken) {
            detach()
            controller = next
            if (next != null) {
                val observer =
                    object : MediaController.Callback() {
                        override fun onMetadataChanged(metadata: MediaMetadata?) = update()

                        override fun onPlaybackStateChanged(state: PlaybackState?) = update()

                        override fun onSessionDestroyed() {
                            if (active && controller === next) {
                                detach()
                                emit(null)
                            }
                        }

                        private fun update() {
                            if (active && controller === next) publish()
                        }
                    }
                callback = observer
                next.registerCallback(observer, handler)
            }
        }
        publish()
    }

    private fun publish() {
        val current = controller
        emit(snapshot(current?.metadata, current?.playbackState))
    }

    private fun emit(track: NowPlaying?) {
        if (!published || last != track) {
            published = true
            last = track
            changed(track)
        }
    }

    private fun detach() {
        callback?.let { controller?.unregisterCallback(it) }
        callback = null
        controller = null
    }

    fun perform(effect: LauncherState.Effect) {
        if (!active) return
        try {
            val current = controller ?: return
            val track = snapshot(current.metadata, current.playbackState) ?: return
            when {
                effect == LauncherState.Effect.TOGGLE_PLAYBACK && track.canToggle ->
                    if (track.playing) current.transportControls.pause() else current.transportControls.play()
                effect == LauncherState.Effect.NEXT_TRACK && track.canNext -> current.transportControls.skipToNext()
            }
        } catch (_: SecurityException) {
            refreshAccess()
        }
    }

    companion object {
        const val PACKAGE = "com.spotify.tv.android"

        fun snapshot(
            metadata: MediaMetadata?,
            playback: PlaybackState?,
        ): NowPlaying? {
            if (metadata == null || playback == null) return null
            val title =
                metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            if (title == null || playback.state !in visibleStates) return null
            val playing = playback.state == PlaybackState.STATE_PLAYING
            val toggle = if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
            return NowPlaying(
                title = title,
                artist =
                    metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty(),
                playing = playing,
                canToggle = playback.actions and (toggle or PlaybackState.ACTION_PLAY_PAUSE) != 0L,
                canNext = playback.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                artwork =
                    metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            )
        }

        private val visibleStates =
            setOf(
                PlaybackState.STATE_PLAYING,
                PlaybackState.STATE_PAUSED,
                PlaybackState.STATE_BUFFERING,
                PlaybackState.STATE_CONNECTING,
            )
    }
}
