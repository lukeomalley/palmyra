package xyz.lukeomalley.palmyra

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperAssetsTest {
    @Test
    fun bundledBlursMatchTheHostRendering() {
        val assets = RuntimeEnvironment.getApplication().assets
        val files = assets.list("wallpapers")!!.filter { it.endsWith(".jpg") }
        for (name in files) {
            val photo = assets.open("wallpapers/$name").use { BitmapFactory.decodeStream(it)!! }
            assertEquals(name, 1920, photo.width)
            assertEquals(name, 1080, photo.height)
            val blur = blur(photo, 240, 135, 6)
            verifyOrGenerate(blur, "wallpapers/blur/${name.removeSuffix(".jpg")}.png")
            blur.recycle()
            // Match the shallow 4dp glass in the approved concept. Crop after blur,
            // retaining surrounding pixels at the card edges. Runtime holds only 22 KB.
            val shallow = blur(photo, 480, 270, 2)
            val glass = Bitmap.createBitmap(shallow, 283, 216, 162, 34)
            verifyOrGenerate(glass, "wallpapers/glass/${name.removeSuffix(".jpg")}.png")
            glass.recycle()
            shallow.recycle()
            photo.recycle()
        }
    }

    private fun blur(
        photo: Bitmap,
        width: Int,
        height: Int,
        radius: Int,
    ): Bitmap {
        val small = Bitmap.createScaledBitmap(photo, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        small.recycle()
        return Bitmap.createBitmap(
            StackBlur.blur(pixels, width, height, radius),
            width,
            height,
            Bitmap.Config.ARGB_8888,
        )
    }

    private fun verifyOrGenerate(
        expected: Bitmap,
        path: String,
    ) {
        if (System.getenv("PALMYRA_GENERATE_BLURS") == "1") {
            val output = File("src/main/assets/$path")
            output.parentFile!!.mkdirs()
            output.outputStream().use { expected.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else {
            val actual =
                RuntimeEnvironment
                    .getApplication()
                    .assets
                    .open(path)
                    .use { BitmapFactory.decodeStream(it)!! }
            assertEquals(path, expected.width, actual.width)
            assertEquals(path, expected.height, actual.height)
            assertArrayEquals(path, pixels(expected), pixels(actual))
            actual.recycle()
        }
    }

    private fun pixels(bitmap: Bitmap) =
        IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
}
