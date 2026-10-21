package xyz.lukeomalley.palmyra

import android.media.SoundPool
import android.os.Looper
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NavigationSoundTest {
    @Test
    fun loadingAndPauseNeverQueueTicksForLater() {
        enabled(true)
        val sound = NavigationSound(RuntimeEnvironment.getApplication())
        try {
            sound.start()
            val first = awaitNavigationPool(sound)
            val audio = shadowOf(first)
            sound.play()
            assertFalse(audio.wasResourcePlayed(R.raw.navigation_tick))
            audio.notifyResourceLoaded(R.raw.navigation_tick, false)
            sound.play()
            assertFalse(audio.wasResourcePlayed(R.raw.navigation_tick))
            audio.notifyResourceLoaded(R.raw.navigation_tick, true)
            sound.play()
            val tick = audio.getResourcePlaybacks(R.raw.navigation_tick).single()
            assertEquals(0, tick.loop)
            assertEquals(1f, tick.rate)
            sound.stop()
            assertNull(pool(sound))
            sound.play()
            assertEquals(1, audio.getResourcePlaybacks(R.raw.navigation_tick).size)
            sound.start()
            val second = awaitNavigationPool(sound)
            assertNotSame(first, second)
            audio.notifyResourceLoaded(R.raw.navigation_tick, true)
            sound.play()
            assertFalse(shadowOf(second).wasResourcePlayed(R.raw.navigation_tick))
            shadowOf(second).notifyResourceLoaded(R.raw.navigation_tick, true)
            sound.play()
            assertEquals(1, shadowOf(second).getResourcePlaybacks(R.raw.navigation_tick).size)
        } finally {
            sound.stop()
        }
    }

    @Test
    fun mutedSystemSoundsSkipAudioAllocationAndAreReadAgainOnResume() {
        val sound = NavigationSound(RuntimeEnvironment.getApplication())
        try {
            enabled(false)
            sound.start()
            assertNull(pool(sound))
            enabled(true)
            sound.start()
            val audio = shadowOf(awaitNavigationPool(sound))
            audio.notifyResourceLoaded(R.raw.navigation_tick, true)
            sound.play()
            assertEquals(1, audio.getResourcePlaybacks(R.raw.navigation_tick).size)
            sound.stop()
            enabled(false)
            sound.start()
            sound.play()
            assertNull(pool(sound))
        } finally {
            sound.stop()
        }
    }

    private fun pool(sound: NavigationSound) =
        NavigationSound::class.java
            .getDeclaredField("pool")
            .apply { isAccessible = true }
            .get(sound) as SoundPool?

    private fun enabled(value: Boolean) {
        Settings.System.putInt(
            RuntimeEnvironment.getApplication().contentResolver,
            Settings.System.SOUND_EFFECTS_ENABLED,
            if (value) 1 else 0,
        )
    }
}

internal fun awaitNavigationPool(sound: NavigationSound): SoundPool {
    val field = NavigationSound::class.java.getDeclaredField("pool").apply { isAccessible = true }
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (System.nanoTime() < deadline) {
        shadowOf(Looper.getMainLooper()).idle()
        (field.get(sound) as SoundPool?)?.let { return it }
        Thread.sleep(1)
    }
    error("Navigation audio did not finish initializing")
}
