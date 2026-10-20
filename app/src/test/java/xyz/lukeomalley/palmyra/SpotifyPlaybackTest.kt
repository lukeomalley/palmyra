package xyz.lukeomalley.palmyra

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SpotifyPlaybackTest {
    @Test fun metadataAndActionsFollowTheActualPlaybackState() {
        assertNull(SpotifyPlayback.snapshot(null, state(PlaybackState.STATE_PLAYING)))
        assertNull(SpotifyPlayback.snapshot(metadata(""), state(PlaybackState.STATE_PLAYING)))
        assertNull(SpotifyPlayback.snapshot(metadata("Song"), state(PlaybackState.STATE_STOPPED)))
        assertNull(SpotifyPlayback.snapshot(metadata("Song"), state(PlaybackState.STATE_ERROR)))
        val playing = SpotifyPlayback.snapshot(metadata("Song"), state(PlaybackState.STATE_PLAYING))!!
        assertEquals("Song", playing.title)
        assertEquals("Artist", playing.artist)
        assertTrue(playing.playing)
        assertTrue(playing.canToggle)
        assertTrue(playing.canNext)
        val paused = SpotifyPlayback.snapshot(metadata("Song"), state(PlaybackState.STATE_PAUSED, 0L))!!
        assertFalse(paused.playing)
        assertFalse(paused.canToggle)
        assertFalse(paused.canNext)
        assertEquals(listOf(LauncherState.Effect.OPEN_SPOTIFY), paused.actions)
        assertFalse(SpotifyPlayback.snapshot(metadata("Song"), state(PlaybackState.STATE_BUFFERING))!!.playing)
    }

    @Test fun albumArtReplacesTheLogoWhenItArrivesAfterTheFirstMetadataUpdate() {
        val context = RuntimeEnvironment.getApplication() as Context
        val session = MediaSession(context, "artwork")
        val controller = MediaController(context, session.sessionToken)
        val shadow = shadowOf(controller)
        val logo = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val cover = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val initial =
            MediaMetadata
                .Builder(metadata("Song"))
                .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, logo)
                .build()
        shadow.setPackageName(SpotifyPlayback.PACKAGE)
        shadow.setMetadata(initial)
        shadow.setPlaybackState(state(PlaybackState.STATE_PLAYING))
        shadowOf(context.getSystemService(MediaSessionManager::class.java)).addController(controller)
        val updates = mutableListOf<NowPlaying?>()
        val source = SpotifyPlayback(context) { updates += it }
        try {
            source.start()
            assertSame(logo, updates.last()?.artwork)
            val withCover =
                MediaMetadata
                    .Builder(initial)
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover)
                    .build()
            shadow.executeOnMetadataChanged(withCover)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, updates.size)
            assertSame(cover, updates.last()?.artwork)
            val genericArt =
                MediaMetadata
                    .Builder(initial)
                    .putBitmap(MediaMetadata.METADATA_KEY_ART, cover)
                    .build()
            assertSame(cover, SpotifyPlayback.snapshot(genericArt, state(PlaybackState.STATE_PLAYING))?.artwork)
        } finally {
            source.stop()
            session.release()
            logo.recycle()
            cover.recycle()
        }
    }

    @Test fun onlySpotifyIsObservedAndPauseDetachesAllCallbacks() {
        val context = RuntimeEnvironment.getApplication() as Context
        val manager = shadowOf(context.getSystemService(MediaSessionManager::class.java))
        val session = MediaSession(context, "test")
        val controller = MediaController(context, session.sessionToken)
        val shadow = shadowOf(controller)
        shadow.setPackageName("other.app")
        shadow.setMetadata(metadata("Other app"))
        shadow.setPlaybackState(state(PlaybackState.STATE_PLAYING))
        manager.addController(controller)
        val updates = mutableListOf<NowPlaying?>()
        val source = SpotifyPlayback(context) { updates += it }
        try {
            source.start()
            assertNull(updates.last())
            shadow.setPackageName(SpotifyPlayback.PACKAGE)
            manager.clearControllers()
            manager.addController(controller)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Other app", updates.last()?.title)
            shadow.executeOnMetadataChanged(metadata("New song"))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("New song", updates.last()?.title)
            val unchanged = updates.size
            shadow.executeOnPlaybackStateChanged(state(PlaybackState.STATE_PLAYING))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(unchanged, updates.size)
            assertEquals(1, shadow.callbacks.size)
            source.stop()
            assertTrue(shadow.callbacks.isEmpty())
            assertNull(MediaAccessService.onAccessChanged)
            val count = updates.size
            shadow.executeOnPlaybackStateChanged(state(PlaybackState.STATE_PAUSED))
            manager.clearControllers()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(count, updates.size)
            source.start()
            assertNull(updates.last())
        } finally {
            source.stop()
            session.release()
        }
    }

    private fun metadata(title: String) =
        MediaMetadata
            .Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Artist")
            .build()

    private fun state(
        value: Int,
        actions: Long =
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT,
    ) = PlaybackState
        .Builder()
        .setState(value, 0L, 1f)
        .setActions(actions)
        .build()
}
