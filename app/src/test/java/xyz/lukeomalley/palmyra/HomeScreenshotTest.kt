package xyz.lukeomalley.palmyra

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenshotTest {
    @Test fun empty() = render("empty", emptyList(), LauncherState(0))

    @Test fun home() = render("home", AppCatalog.favorites, LauncherState(AppCatalog.favorites.size))

    @Test fun fourthRow() = render("fourth", AppCatalog.favorites, LauncherState(AppCatalog.favorites.size, 3))

    @Test fun lastRow() =
        render("last", AppCatalog.favorites, LauncherState(AppCatalog.favorites.size, AppCatalog.favorites.lastIndex))

    @Test fun menu() {
        val state = LauncherState(AppCatalog.favorites.size)
        state.key(LauncherState.Key.RIGHT)
        render("menu", AppCatalog.favorites, state)
    }

    @Test fun art() {
        val state = LauncherState(AppCatalog.favorites.size)
        state.key(LauncherState.Key.RIGHT)
        state.key(LauncherState.Key.SELECT)
        render("art", AppCatalog.favorites, state)
    }

    @Test fun allApps() =
        render(
            "all-apps",
            AppCatalog.favorites.sortedBy { it.label },
            LauncherState(AppCatalog.favorites.size, 0, true),
        )

    @Test fun reference() =
        render(
            "reference",
            listOf(
                AppEntry("Stremio", "fixture.stremio", R.drawable.ic_stremio),
                AppEntry("Netflix", "fixture.netflix", R.drawable.ic_netflix),
                AppEntry("Disney+", "fixture.disney", R.drawable.ic_disneyplus),
                AppEntry("YouTube", "fixture.youtube", R.drawable.ic_youtube),
                AppEntry("Apple TV", "fixture.apple", R.drawable.ic_appletv),
                AppEntry("Prime Video", "fixture.prime", R.drawable.ic_primevideo),
            ),
            LauncherState(6),
        )

    @Test fun spotifyPlaying() = player("spotify-playing", NowPlaying("Dreams", "Fleetwood Mac", true, canNext = true))

    @Test fun spotifyFocused() =
        player(
            "spotify-focused",
            NowPlaying("Dreams", "Fleetwood Mac", true, canNext = true),
            true,
        )

    @Test fun spotifyPausedWithLongTitleAndNoNextAction() =
        player(
            "spotify-paused",
            NowPlaying("A very long song title that must stay inside the card", "Artist", false),
            true,
        )

    @Test fun spotifyArtMode() =
        player("spotify-art", NowPlaying("Dreams", "Fleetwood Mac", true, canNext = true), art = true)

    @Test fun spotifyMenuAbovePlayback() {
        val track = NowPlaying("Dreams", "Fleetwood Mac", true, canNext = true)
        val state = LauncherState(AppCatalog.favorites.size)
        state.updatePlayback(track.actions)
        state.focusMenu(2)
        render("spotify-menu", AppCatalog.favorites, state, track = track)
    }

    @Test fun spotifyArtworkOnAutumnWallpaper() {
        val artwork =
            Bitmap.createBitmap(
                intArrayOf(
                    0xFFADC2BD.toInt(),
                    0xFF3F5E57.toInt(),
                    0xFFD3C49E.toInt(),
                    0xFF29483F.toInt(),
                ),
                2,
                2,
                Bitmap.Config.ARGB_8888,
            )
        val track = NowPlaying("Dreams", "Fleetwood Mac", true, canNext = true, artwork = artwork)
        val state = LauncherState(AppCatalog.favorites.size)
        state.updatePlayback(track.actions)
        render("spotify-autumn", AppCatalog.favorites, state, track = track, photoName = "10")
        artwork.recycle()
    }

    private fun player(
        name: String,
        track: NowPlaying,
        focused: Boolean = false,
        art: Boolean = false,
    ) {
        val state = LauncherState(AppCatalog.favorites.size)
        state.updatePlayback(track.actions)
        if (focused) state.focusPlayback(track.actions.indexOf(LauncherState.Effect.TOGGLE_PLAYBACK))
        if (art) {
            state.key(LauncherState.Key.RIGHT)
            state.key(LauncherState.Key.SELECT)
        }
        render(name, AppCatalog.favorites, state, track = track)
    }

    private fun render(
        name: String,
        apps: List<AppEntry>,
        state: LauncherState,
        track: NowPlaying? = null,
        photoName: String = "00",
    ) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.AppTheme)
        controller.setup()
        val scene = HomeView(activity, apps, state.allApps)
        activity.setContentView(scene)
        val photo = activity.assets.open("wallpapers/$photoName.jpg").use { BitmapFactory.decodeStream(it)!! }
        val small = Bitmap.createScaledBitmap(photo, 240, 135, true)
        val pixels = IntArray(240 * 135)
        small.getPixels(pixels, 0, 240, 0, 0, 240, 135)
        val blur = Bitmap.createBitmap(StackBlur.blur(pixels, 240, 135, 6), 240, 135, Bitmap.Config.ARGB_8888)
        small.recycle()
        val glass = activity.assets.open("wallpapers/glass/$photoName.png").use { BitmapFactory.decodeStream(it)!! }
        scene.backdrop.show(WallpaperFrame(photo, blur, glass), false)
        Clock(activity, scene.clock) { (10 * 60 + 24) * 60_000L }.update()
        scene.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        scene.layout(0, 0, 1920, 1080)
        scene.nowPlaying.show(track)
        scene.render(state, false)
        scene.captureRoboImage("src/test/screenshots/$name.png")
        scene.clear()
        controller.pause().stop().destroy()
    }
}
