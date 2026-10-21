package xyz.lukeomalley.palmyra

import kotlin.math.roundToInt

/** Remote navigation has no dependency on Android views. */
@Suppress("MagicNumber")
class LauncherState(
    val count: Int,
    initialRow: Int = 0,
    val allApps: Boolean = false,
) {
    var row = initialRow.coerceIn(0, (count - 1).coerceAtLeast(0))
        private set
    var menu = if (count == 0 && !allApps) 0 else -1
        private set
    var art = false
        private set
    var player = -1
        private set
    var playbackActions = emptyList<Effect>()
        private set
    val hasPlayback get() = playbackActions.isNotEmpty()

    // App rows, three menu items, then player controls. Compared without allocating.
    val selectionId
        get() =
            when {
                player >= 0 -> count + 3 + player
                menu >= 0 -> count + menu
                else -> row
            }

    enum class Key { UP, DOWN, LEFT, RIGHT, SELECT, BACK, HOME, OTHER }

    enum class Effect {
        NONE,
        LAUNCH,
        ALL_APPS,
        SETTINGS,
        PREVIOUS_PHOTO,
        NEXT_PHOTO,
        OPEN_SPOTIFY,
        TOGGLE_PLAYBACK,
        NEXT_TRACK,
    }

    fun key(
        key: Key,
        visibleRowCenter: Float = 143f,
    ): Effect {
        if (key == Key.HOME) {
            art = false
            player = -1
            row = 0
            menu = if (count == 0 && !allApps) 0 else -1
            return Effect.NONE
        }
        if (art) {
            return when (key) {
                Key.LEFT -> Effect.PREVIOUS_PHOTO
                Key.RIGHT -> Effect.NEXT_PHOTO
                else -> {
                    art = false
                    Effect.NONE
                }
            }
        }
        return when {
            player >= 0 -> playerKey(key)
            menu >= 0 -> menuKey(key)
            else -> listKey(key, visibleRowCenter)
        }
    }

    fun updatePlayback(actions: List<Effect>) {
        val selected = playbackActions.getOrNull(player)
        playbackActions = if (allApps) emptyList() else actions
        if (player >= 0) {
            player = if (playbackActions.isEmpty()) -1 else playbackActions.indexOf(selected).coerceAtLeast(0)
            if (player < 0) menu = 2
        }
    }

    fun focusPlayback(index: Int) {
        if (index in playbackActions.indices) {
            player = index
            menu = -1
        }
    }

    private fun enterPlayer() {
        focusPlayback(playbackActions.indexOf(Effect.TOGGLE_PLAYBACK).coerceAtLeast(0))
    }

    private fun playerKey(key: Key): Effect {
        when (key) {
            Key.LEFT -> if (player == 0 && count > 0) player = -1 else player = (player - 1).coerceAtLeast(0)
            Key.RIGHT -> player = (player + 1).coerceAtMost(playbackActions.lastIndex)
            Key.UP -> {
                player = -1
                menu = 2
            }
            Key.BACK -> {
                player = -1
                menu = if (count == 0) 0 else -1
            }
            Key.SELECT -> return playbackActions[player]
            else -> Unit
        }
        return Effect.NONE
    }

    fun focusMenu(index: Int) {
        player = -1
        if (!allApps) menu = index.coerceIn(0, 2)
    }

    private fun menuKey(key: Key): Effect {
        when (key) {
            Key.UP -> menu = (menu - 1).coerceAtLeast(0)
            Key.DOWN -> if (menu == 2 && hasPlayback) enterPlayer() else menu = (menu + 1).coerceAtMost(2)
            Key.LEFT -> if (count > 0) menu = -1
            Key.SELECT -> return when (menu) {
                0 -> {
                    art = true
                    Effect.NONE
                }
                1 -> Effect.ALL_APPS
                else -> Effect.SETTINGS
            }
            else -> Unit
        }
        return Effect.NONE
    }

    private fun listKey(
        key: Key,
        center: Float,
    ): Effect {
        when (key) {
            Key.UP -> row = (row - 1).coerceAtLeast(0)
            Key.DOWN -> row = (row + 1).coerceAtMost((count - 1).coerceAtLeast(0))
            Key.RIGHT ->
                when {
                    allApps -> Unit
                    hasPlayback && center >= Geometry.PLAYER_TOP -> enterPlayer()
                    else -> menu = ((center - Geometry.menuTop(hasPlayback) - 16f) / 32f).roundToInt().coerceIn(0, 2)
                }
            Key.SELECT -> if (count > 0) return Effect.LAUNCH
            else -> Unit
        }
        return Effect.NONE
    }
}

@Suppress("MagicNumber")
object Geometry {
    const val PLAYER_WIDTH = 324
    const val PLAYER_HEIGHT = 68
    const val PLAYER_TOP = 432
    const val PLAYER_RIGHT = 70
    const val ROW_PITCH = 64f
    const val LIST_TOP = 111f
    const val VIEWPORT = 389f
    const val FADE = 60f

    fun menuTop(hasPlayback: Boolean) = if (hasPlayback) 304 else 388

    fun pillWidth(labelWidth: Float) = 64f + labelWidth + 26f

    fun scrollFor(
        row: Int,
        current: Float,
        count: Int,
    ): Float {
        val top = row * ROW_PITCH
        val bottom = top + ROW_PITCH
        val next =
            when {
                row == 0 -> 0f
                top < current + FADE -> top - FADE
                bottom > current + VIEWPORT - FADE -> bottom - VIEWPORT + FADE
                else -> current
            }
        return next.coerceIn(0f, (count * ROW_PITCH + FADE - VIEWPORT).coerceAtLeast(0f))
    }
}

object Rotation {
    fun index(
        saved: Int,
        count: Int,
    ) = Math.floorMod(saved, count)

    fun next(
        current: Int,
        delta: Int,
        count: Int,
    ) = index(current + delta, count)
}
