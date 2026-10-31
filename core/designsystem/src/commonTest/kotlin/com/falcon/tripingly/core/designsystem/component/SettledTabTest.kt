package com.falcon.tripingly.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals

class SettledTabTest {

    @Test
    fun aSlowRelease_settlesOnTheNearestTab() {
        assertEquals(1, settledTab(position = 1.4f, velocity = 0f, lastIndex = 3))
        assertEquals(2, settledTab(position = 1.6f, velocity = 100f, lastIndex = 3))
    }

    @Test
    fun aFlick_goesOnInItsDirection() {
        assertEquals(2, settledTab(position = 1.2f, velocity = 900f, lastIndex = 3))
        assertEquals(1, settledTab(position = 1.8f, velocity = -900f, lastIndex = 3))
    }

    @Test
    fun theSelectionNeverLeavesTheBar() {
        assertEquals(3, settledTab(position = 3.3f, velocity = 900f, lastIndex = 3))
        assertEquals(0, settledTab(position = -0.4f, velocity = -900f, lastIndex = 3))
    }
}
