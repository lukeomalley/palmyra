package xyz.lukeomalley.palmyra

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** One short, preloaded tick. No audio focus request, so music keeps playing. */
class NavigationSound(
    private val context: Context,
) {
    private var pool: SoundPool? = null
    private var sample = 0
    private var ready = false
    private var starting = false
    private var generation = 0
    private val main = Handler(Looper.getMainLooper())

    fun start() {
        if (pool != null || starting ||
            Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 0
        ) {
            return
        }
        starting = true
        val request = ++generation
        // SoundPool construction talks to the audio service. Keep that off the UI thread.
        Thread({
            val next = createPool()
            main.post { accept(request, next) }
        }, "PalmyraSound").start()
    }

    private fun createPool() =
        SoundPool
            .Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).build()

    private fun accept(
        request: Int,
        next: SoundPool,
    ) {
        if (request != generation) {
            next.release()
            return
        }
        starting = false
        pool = next
        next.setOnLoadCompleteListener { loaded, _, status ->
            if (pool === loaded) ready = status == 0
        }
        sample = next.load(context, R.raw.navigation_tick, 1)
    }

    @Suppress("MagicNumber")
    fun play() {
        if (ready) pool?.play(sample, .3f, .3f, 1, 0, 1f)
    }

    fun stop() {
        generation++
        starting = false
        ready = false
        pool?.release()
        pool = null
    }
}
