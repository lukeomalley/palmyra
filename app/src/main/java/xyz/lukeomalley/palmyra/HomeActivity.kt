package xyz.lukeomalley.palmyra

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import android.window.OnBackInvokedDispatcher

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "TooManyFunctions")
open class HomeActivity : Activity() {
    protected open val allApps = false
    private lateinit var scene: HomeView
    private lateinit var state: LauncherState
    private lateinit var wallpaper: WallpaperController
    private lateinit var clock: Clock
    private lateinit var playback: SpotifyPlayback
    private val navigationSound = NavigationSound(this)
    private var foreground = false
    private var stopped = true
    private val prefs by lazy { getSharedPreferences("palmyra", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        wallpaper =
            WallpaperController(this) { frame ->
                scene.backdrop.show(frame)
                window.setBackgroundDrawable(null)
            }
        playback =
            SpotifyPlayback(this) { track ->
                if (foreground && ::scene.isInitialized) {
                    state.updatePlayback(track?.actions.orEmpty())
                    scene.nowPlaying.show(track)
                    scene.render(state, false)
                }
            }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        stopped = false
        val apps =
            if (allApps) {
                AppCatalog.all(packageManager)
            } else {
                AppCatalog.installed(packageManager)
            }
        val rebuild = !::scene.isInitialized || scene.apps != apps
        if (rebuild && ::scene.isInitialized) scene.clear()
        // Decode in parallel with row construction. Results return on the main loop.
        wallpaper.start(!rebuild && scene.backdrop.hasPhoto)
        if (rebuild) {
            val savedPackage = prefs.getString(if (allApps) "all_app" else "app", null)
            val index =
                apps.indexOfFirst { it.packageName == savedPackage }.takeIf { it >= 0 }
                    ?: prefs.getInt(if (allApps) "all_row" else "row", 0)
            state = LauncherState(apps.size, index, allApps)
            scene = HomeView(this, apps, allApps)
            setContentView(scene)
            clock = Clock(this, scene.clock)
            scene.onSelect = { handle(LauncherState.Key.SELECT) }
            scene.onTouchRow = { row, menu ->
                val playbackActions = state.playbackActions
                state = LauncherState(apps.size, if (menu) state.row else row, allApps)
                state.updatePlayback(playbackActions)
                if (menu) state.focusMenu(row)
            }
            scene.nowPlaying.onAction = { index ->
                state.focusPlayback(index)
                handle(LauncherState.Key.SELECT)
            }
            scene.backdrop.onPhotoDrawn = { reportFullyDrawn() }
        }
        scene.post { if (foreground) scene.render(state, false) }
        clock.start()
        navigationSound.start()
        if (!allApps) playback.start()
    }

    override fun onPause() {
        foreground = false
        navigationSound.stop()
        playback.stop()
        clock.stop()
        wallpaper.stop()
        scene.pauseAnimations()
        super.onPause()
    }

    override fun onStop() {
        stopped = true
        scene.clear()
        super.onStop()
    }

    override fun onDestroy() {
        wallpaper.close()
        if (::scene.isInitialized) scene.clear()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!foreground && ::scene.isInitialized) scene.clear()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Android pauses even a visible singleTask activity before onNewIntent.
        // onStop distinguishes returning from another app from Home pressed here.
        if (::state.isInitialized && (!stopped || state.art)) handle(LauncherState.Key.HOME)
    }

    // Native gesture callback above and physical TV remote events share this path.
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (allApps && !state.art) finish() else handle(LauncherState.Key.BACK)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::state.isInitialized) return super.dispatchKeyEvent(event)
        val key = remoteKey(event.keyCode)
        if (key == LauncherState.Key.OTHER && !state.art) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (key == LauncherState.Key.BACK && allApps && !state.art) finish() else handle(key)
        }
        return true
    }

    private fun handle(key: LauncherState.Key) {
        val selection = state.selectionId
        val effect = state.key(key, scene.visibleRowCenter(state))
        scene.render(state)
        if (key != LauncherState.Key.HOME && selection != state.selectionId) {
            navigationSound.play()
        }
        val app = scene.apps.getOrNull(state.row)
        val rowKey = if (allApps) "all_row" else "row"
        val appKey = if (allApps) "all_app" else "app"
        if (prefs.getInt(rowKey, -1) != state.row || prefs.getString(appKey, null) != app?.packageName) {
            prefs
                .edit()
                .putInt(rowKey, state.row)
                .putString(appKey, app?.packageName)
                .apply()
        }
        perform(effect, app)
    }

    private fun perform(
        effect: LauncherState.Effect,
        app: AppEntry?,
    ) {
        when (effect) {
            LauncherState.Effect.LAUNCH -> app?.let { open(AppCatalog.launchIntent(packageManager, it.packageName)) }
            LauncherState.Effect.ALL_APPS -> open(Intent(this, AllAppsActivity::class.java))
            LauncherState.Effect.SETTINGS -> open(Intent(Settings.ACTION_SETTINGS))
            LauncherState.Effect.PREVIOUS_PHOTO -> wallpaper.change(-1)
            LauncherState.Effect.NEXT_PHOTO -> wallpaper.change(1)
            LauncherState.Effect.OPEN_SPOTIFY -> open(AppCatalog.launchIntent(packageManager, SpotifyPlayback.PACKAGE))
            LauncherState.Effect.TOGGLE_PLAYBACK, LauncherState.Effect.NEXT_TRACK -> playback.perform(effect)
            LauncherState.Effect.NONE -> Unit
        }
    }

    private fun open(intent: Intent?) {
        try {
            if (intent == null) throw ActivityNotFoundException()
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("GestureBackNavigation")
    private fun remoteKey(code: Int) =
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> LauncherState.Key.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> LauncherState.Key.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> LauncherState.Key.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> LauncherState.Key.RIGHT
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> LauncherState.Key.SELECT
            KeyEvent.KEYCODE_BACK -> LauncherState.Key.BACK
            KeyEvent.KEYCODE_HOME -> LauncherState.Key.HOME
            else -> LauncherState.Key.OTHER
        }
}

class AllAppsActivity : HomeActivity() {
    override val allApps = true
}
