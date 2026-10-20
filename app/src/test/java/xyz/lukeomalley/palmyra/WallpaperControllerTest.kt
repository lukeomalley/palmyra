package xyz.lukeomalley.palmyra

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperControllerTest {
    @Test
    fun rotationLoadsSavedIndexPersistsNextAndStopsWhenHidden() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("palmyra", Context.MODE_PRIVATE)
        val files =
            context.assets
                .list("wallpapers")!!
                .filter { it.endsWith(".jpg") }
                .sorted()
        prefs.edit().putInt("wallpaper", files.lastIndex).commit()
        val samples = mutableListOf<Int>()
        val controller =
            WallpaperController(context) {
                samples += it.photo.getPixel(0, 0)
                it.release()
                assertTrue(it.photo.isRecycled && it.blur.isRecycled && it.playerGlass.isRecycled)
            }
        try {
            controller.start()
            await { samples.size == 1 }
            assertEquals(pixel(context, files.last()), samples[0])
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(15))
            await { samples.size == 2 }
            assertEquals(0, prefs.getInt("wallpaper", -1))
            assertEquals(pixel(context, "00.jpg"), samples[1])
            controller.stop()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(15))
            assertEquals(2, samples.size)
        } finally {
            controller.close()
        }
    }

    private fun pixel(
        context: Context,
        name: String,
    ): Int {
        val bitmap = context.assets.open("wallpapers/$name").use { BitmapFactory.decodeStream(it)!! }
        val pixel = bitmap.getPixel(0, 0)
        bitmap.recycle()
        return pixel
    }

    private fun await(ready: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!ready()) {
            check(System.nanoTime() < deadline) { "Wallpaper worker timed out" }
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
