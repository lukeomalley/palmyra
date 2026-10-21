package xyz.lukeomalley.palmyra

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.provider.Settings
import android.view.KeyEvent
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeActivityTest {
    @Test
    fun navigationTicksOnlyWhenSelectionMoves() {
        available("com.netflix.ninja")
        available("com.spotify.tv.android")
        Settings.System.putInt(
            RuntimeEnvironment.getApplication().contentResolver,
            Settings.System.SOUND_EFFECTS_ENABLED,
            1,
        )
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup().visible()
        try {
            val activity = controller.get()
            val sound =
                HomeActivity::class.java
                    .getDeclaredField("navigationSound")
                    .apply { isAccessible = true }
                    .get(activity) as NavigationSound
            val pool = awaitNavigationPool(sound)
            val audio = shadowOf(pool)
            audio.notifyResourceLoaded(R.raw.navigation_tick, true)
            key(activity, KeyEvent.KEYCODE_DPAD_UP)
            assertFalse(audio.wasResourcePlayed(R.raw.navigation_tick))
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            assertEquals(1, audio.getResourcePlaybacks(R.raw.navigation_tick).size)
            key(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
            assertEquals(2, audio.getResourcePlaybacks(R.raw.navigation_tick).size)
            key(activity, KeyEvent.KEYCODE_DPAD_CENTER)
            key(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
            key(activity, KeyEvent.KEYCODE_BACK)
            key(activity, KeyEvent.KEYCODE_HOME)
            assertEquals(2, audio.getResourcePlaybacks(R.raw.navigation_tick).size)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun returningFromAnAppReusesRowsAndPreservesSelection() {
        available("com.netflix.ninja")
        available("com.spotify.tv.android", Intent.CATEGORY_LAUNCHER)
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup().visible()
        try {
            val activity = controller.get()
            val original = scene(activity)
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            controller
                .pause()
                .stop()
                .newIntent(Intent(Intent.ACTION_MAIN))
                .restart()
                .start()
                .resume()
                .visible()
            assertSame(original, scene(activity))
            assertEquals("App: Spotify", original.contentDescription)
            assertFalse(original.backdrop.hasPhoto)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun homeWhileVisibleReusesRowsAndResetsSelection() {
        available("com.netflix.ninja")
        available("com.spotify.tv.android")
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup().visible()
        try {
            val activity = controller.get()
            val original = scene(activity)
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            controller
                .pause()
                .newIntent(Intent(Intent.ACTION_MAIN))
                .resume()
                .visible()
            assertSame(original, scene(activity))
            assertEquals("App: Netflix", original.contentDescription)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun changedInstalledAppsRebuildRowsWithoutLosingTheSelectedPackage() {
        available("com.netflix.ninja")
        available("com.spotify.tv.android")
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup().visible()
        try {
            val activity = controller.get()
            val original = scene(activity)
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            controller.pause().stop()
            available("org.jellyfin.androidtv")
            controller
                .restart()
                .start()
                .resume()
                .visible()
            assertNotSame(original, scene(activity))
            assertEquals(listOf("Jellyfin", "Netflix", "Spotify"), scene(activity).apps.map { it.label })
            assertEquals("App: Spotify", scene(activity).contentDescription)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun homeExitsArtModeEvenAfterTheActivityStopped() {
        available("com.netflix.ninja")
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup().visible()
        try {
            val activity = controller.get()
            key(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
            key(activity, KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals("Art Mode", scene(activity).contentDescription)
            controller
                .pause()
                .stop()
                .newIntent(Intent(Intent.ACTION_MAIN))
                .restart()
                .start()
                .resume()
                .visible()
            assertEquals("App: Netflix", scene(activity).contentDescription)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun scene(activity: HomeActivity) =
        activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as HomeView

    private fun key(
        activity: HomeActivity,
        code: Int,
    ) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
    }

    private fun available(
        pkg: String,
        category: String = Intent.CATEGORY_LEANBACK_LAUNCHER,
    ) {
        val info =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = pkg
                        name = "$pkg.MainActivity"
                        applicationInfo = ApplicationInfo().apply { packageName = pkg }
                    }
            }
        shadowOf(RuntimeEnvironment.getApplication().packageManager)
            .addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(category), info)
    }
}
