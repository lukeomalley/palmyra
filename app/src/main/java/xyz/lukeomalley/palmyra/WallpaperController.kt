package xyz.lukeomalley.palmyra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.Future

class WallpaperFrame(
    val photo: Bitmap,
    val blur: Bitmap,
    val playerGlass: Bitmap,
) {
    fun release() {
        photo.recycle()
        blur.recycle()
        playerGlass.recycle()
    }
}

/** One worker, no polling. Generation tokens discard results after stop or a new request. */
@Suppress("MagicNumber")
class WallpaperController(
    private val context: Context,
    private val ready: (WallpaperFrame) -> Unit,
) {
    private val prefs = context.getSharedPreferences("palmyra", Context.MODE_PRIVATE)
    private val files =
        context.assets
            .list("wallpapers")!!
            .filter { it.endsWith(".jpg") }
            .sorted()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var pending: Future<*>? = null
    private var generation = 0
    private var visible = false
    private var index = Rotation.index(prefs.getInt("wallpaper", 0), files.size)
    private val rotate = Runnable { change(1) }

    fun start(hasPhoto: Boolean = false) {
        visible = true
        if (hasPhoto) {
            main.removeCallbacks(rotate)
            main.postDelayed(rotate, 15 * 60 * 1000L)
        } else {
            load()
        }
    }

    fun stop() {
        visible = false
        pending?.cancel(true)
        generation++
        main.removeCallbacks(rotate)
    }

    fun close() {
        stop()
        worker.shutdown()
    }

    fun change(delta: Int) {
        index = Rotation.next(index, delta, files.size)
        prefs.edit().putInt("wallpaper", index).apply()
        load()
    }

    private fun load() {
        main.removeCallbacks(rotate)
        val token = ++generation
        val file = files[index]
        pending?.cancel(true)
        pending =
            worker.submit {
                val photo =
                    context.assets.open("wallpapers/$file").use {
                        BitmapFactory.decodeStream(
                            it,
                            null,
                            BitmapFactory.Options().apply {
                                inPreferredConfig =
                                    Bitmap.Config.ARGB_8888
                            },
                        )
                    }
                if (photo != null && Thread.currentThread().isInterrupted) photo.recycle()
                if (photo != null && !photo.isRecycled) {
                    val blur =
                        context.assets.open("wallpapers/blur/${file.removeSuffix(".jpg")}.png").use {
                            BitmapFactory.decodeStream(it)!!
                        }
                    photo.prepareToDraw()
                    blur.prepareToDraw()
                    val playerGlass =
                        context.assets.open("wallpapers/glass/${file.removeSuffix(".jpg")}.png").use {
                            BitmapFactory.decodeStream(it)!!
                        }
                    playerGlass.prepareToDraw()
                    val frame = WallpaperFrame(photo, blur, playerGlass)
                    main.post {
                        if (visible && token == generation) {
                            ready(frame)
                            main.postDelayed(rotate, 15 * 60 * 1000L)
                        } else {
                            frame.release()
                        }
                    }
                }
            }
    }
}
