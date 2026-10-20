package xyz.lukeomalley.palmyra

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test fun playerNavigationPreservesAppSelectionAndSurvivesDisappearingControls() {
        val state = LauncherState(8, 3)
        val full =
            listOf(
                LauncherState.Effect.OPEN_SPOTIFY,
                LauncherState.Effect.TOGGLE_PLAYBACK,
                LauncherState.Effect.NEXT_TRACK,
            )
        state.updatePlayback(full)
        state.focusMenu(2)
        state.key(LauncherState.Key.DOWN)
        assertEquals(LauncherState.Effect.TOGGLE_PLAYBACK, state.key(LauncherState.Key.SELECT))
        state.key(LauncherState.Key.RIGHT)
        assertEquals(LauncherState.Effect.NEXT_TRACK, state.key(LauncherState.Key.SELECT))
        state.updatePlayback(full.take(1))
        assertEquals(LauncherState.Effect.OPEN_SPOTIFY, state.key(LauncherState.Key.SELECT))
        state.key(LauncherState.Key.LEFT)
        assertEquals(-1, state.player)
        assertEquals(3, state.row)
        state.focusMenu(2)
        state.key(LauncherState.Key.DOWN)
        state.key(LauncherState.Key.UP)
        assertEquals(2, state.menu)
        assertEquals(-1, state.player)
        state.key(LauncherState.Key.DOWN)
        state.updatePlayback(emptyList())
        assertEquals(-1, state.player)
        assertEquals(2, state.menu)
        assertEquals(LauncherState.Effect.SETTINGS, state.key(LauncherState.Key.SELECT))
        state.updatePlayback(full)
        state.key(LauncherState.Key.DOWN)
        state.key(LauncherState.Key.HOME)
        assertEquals(-1, state.player)
        assertEquals(0, state.row)
    }

    @Test fun rightTargetsTheMovedMenuOrPlaybackAndUpStaysAtTheTopOfTheList() {
        val state = LauncherState(8)
        state.updatePlayback(listOf(LauncherState.Effect.OPEN_SPOTIFY))
        state.key(LauncherState.Key.UP)
        assertEquals(-1, state.player)
        state.key(LauncherState.Key.RIGHT, 374f)
        assertEquals(2, state.menu)
        state.key(LauncherState.Key.LEFT)
        state.key(LauncherState.Key.RIGHT, 468f)
        assertEquals(0, state.player)
        state.key(LauncherState.Key.BACK)
        assertEquals(-1, state.player)
        assertEquals(-1, state.menu)
    }

    @Test fun emptyHomeCanReachPlayerButAllAppsCannot() {
        val actions = listOf(LauncherState.Effect.OPEN_SPOTIFY)
        val empty = LauncherState(0)
        empty.updatePlayback(actions)
        repeat(3) { empty.key(LauncherState.Key.DOWN) }
        assertEquals(0, empty.player)
        empty.updatePlayback(emptyList())
        assertEquals(2, empty.menu)
        val all = LauncherState(1, allApps = true)
        all.updatePlayback(actions)
        all.key(LauncherState.Key.RIGHT, 468f)
        assertEquals(-1, all.player)
    }

    @Test fun catalogPreservesOwnerOrderAndFiltersMissingApps() {
        assertEquals(
            listOf(
                "YouTube TV",
                "Jellyfin",
                "Netflix",
                "Amazon Prime",
                "YouTube",
                "Hulu",
                "HBO Max",
                "Apple TV",
                "Twitch",
                "Spotify",
                "Play Store",
            ),
            AppCatalog.favorites.map {
                it.label
            },
        )
        assertEquals(
            listOf("Netflix", "Hulu", "Spotify"),
            AppCatalog
                .installed {
                    it in setOf("com.netflix.ninja", "com.hulu.livingroomplus", "com.spotify.tv.android")
                }.map { it.label },
        )
        assertTrue(AppCatalog.installed { false }.isEmpty())
    }

    @Test fun blurPreservesConstantColorsAndMixesAnImpulse() {
        val solid = IntArray(9) { 0xff336699.toInt() }
        assertArrayEquals(solid, StackBlur.blur(solid, 3, 3, 2))
        assertArrayEquals(
            intArrayOf(0xff2f2f2f.toInt(), 0xff545454.toInt(), 0xff7a7a7a.toInt()),
            StackBlur.blur(intArrayOf(0xff000000.toInt(), 0xff000000.toInt(), 0xffffffff.toInt()), 3, 1, 1),
        )
    }

    @Test fun pillAndScrollingKeepSelectionOutsideFadeZones() {
        assertEquals(250f, Geometry.pillWidth(160f), 0f)
        assertEquals(0f, Geometry.scrollFor(0, 100f, 8), 0f)
        for (row in 1..7) {
            val scroll = Geometry.scrollFor(row, 0f, 8)
            assertTrue(row * 64f + 64f - scroll <= Geometry.VIEWPORT - Geometry.FADE)
        }
    }

    @Test fun rotationNormalizesPersistedAndNegativeIndices() {
        assertEquals(2, Rotation.index(12, 10))
        assertEquals(9, Rotation.next(0, -1, 10))
        assertEquals(0, Rotation.next(9, 1, 10))
    }

    @Test fun remoteRestoresListRowAndHomeResetsArtAndScroll() {
        val state = LauncherState(8, 3)
        state.key(LauncherState.Key.RIGHT)
        assertEquals(0, state.menu)
        state.key(LauncherState.Key.LEFT)
        assertEquals(3, state.row)
        assertEquals(-1, state.menu)
        state.key(LauncherState.Key.RIGHT)
        state.key(LauncherState.Key.SELECT)
        assertTrue(state.art)
        assertEquals(LauncherState.Effect.NEXT_PHOTO, state.key(LauncherState.Key.RIGHT))
        assertTrue(state.art)
        state.key(LauncherState.Key.BACK)
        assertFalse(state.art)
        state.key(LauncherState.Key.SELECT)
        state.key(LauncherState.Key.HOME)
        assertFalse(state.art)
        assertEquals(0, state.row)
        assertEquals(-1, state.menu)
    }

    @Test fun menuActionsAndEmptyCatalogAreReachable() {
        val state = LauncherState(0)
        state.key(LauncherState.Key.LEFT)
        assertEquals(0, state.menu)
        state.key(LauncherState.Key.DOWN)
        assertEquals(LauncherState.Effect.ALL_APPS, state.key(LauncherState.Key.SELECT))
        state.key(LauncherState.Key.DOWN)
        assertEquals(LauncherState.Effect.SETTINGS, state.key(LauncherState.Key.SELECT))
        val all = LauncherState(2, 1, true)
        all.key(LauncherState.Key.RIGHT)
        assertEquals(-1, all.menu)
        assertEquals(LauncherState.Effect.LAUNCH, all.key(LauncherState.Key.SELECT))
    }
}
