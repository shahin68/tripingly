package com.falcon.tripingly.shell

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategyScope
import com.falcon.tripingly.core.navigation.Home
import com.falcon.tripingly.core.navigation.Paywall
import com.falcon.tripingly.core.navigation.Profile
import com.falcon.tripingly.core.navigation.Settings
import com.falcon.tripingly.core.navigation.TripMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeepMapSceneStrategyTest {

    private val strategy = KeepMapSceneStrategy<NavKey>()

    private fun entry(key: NavKey) = NavEntry(key, metadata = if (key is TripMap) KeepMapSceneStrategy.keepUnder else emptyMap()) {}

    private fun scene(vararg keys: NavKey) = with(strategy) { SceneStrategyScope<NavKey>().calculateScene(keys.map(::entry)) }

    @Test
    fun withoutAMap_theDefaultSceneShows() {
        assertNull(scene(Home))
        assertNull(scene(Home, Settings, Paywall))
    }

    @Test
    fun theScreensOverAMap_shareItsScene_soTheMapStays() {
        val map = TripMap("t1")
        val alone = scene(Home, map)!!
        val covered = scene(Home, map, Profile("anna"))!!
        val deeper = scene(Home, map, Profile("anna"), Settings, Paywall)!!

        // One key: opening or closing a screen over the map never swaps the scene.
        assertEquals(entry(map).contentKey, alone.key)
        assertEquals(alone.key, covered.key)
        assertEquals(alone.key, deeper.key)
        // The map, and only the screen on top over it.
        assertEquals(listOf(entry(map).contentKey), alone.entries.map { it.contentKey })
        assertEquals(listOf(entry(map).contentKey, entry(Profile("anna")).contentKey), covered.entries.map { it.contentKey })
        assertEquals(listOf(entry(map).contentKey, entry(Paywall).contentKey), deeper.entries.map { it.contentKey })
        // Back goes one screen back.
        assertEquals(listOf(Home, map, Profile("anna"), Settings).map { entry(it).contentKey }, deeper.previousEntries.map { it.contentKey })
        assertEquals(listOf(entry(Home).contentKey), alone.previousEntries.map { it.contentKey })
    }

    @Test
    fun onlyTheTopMostMapIsKept() {
        val first = TripMap("t1")
        val second = TripMap("t2")
        val scene = scene(Home, first, Profile("anna"), second, Settings)!!

        assertEquals(entry(second).contentKey, scene.key)
        assertEquals(listOf(second, Settings).map { entry(it).contentKey }, scene.entries.map { it.contentKey })
    }
}
