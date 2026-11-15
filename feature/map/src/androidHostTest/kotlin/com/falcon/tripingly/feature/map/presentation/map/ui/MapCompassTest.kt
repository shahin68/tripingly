package com.falcon.tripingly.feature.map.presentation.map.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class MapCompassTest {
    @Test
    fun `bearings are read the short way round`() {
        assertEquals(0f, 0f.normalizedBearing())
        assertEquals(-1f, 359f.normalizedBearing())
        assertEquals(90f, 90f.normalizedBearing())
        assertEquals(-90f, 270f.normalizedBearing())
        assertEquals(10f, (-350f).normalizedBearing())
    }
}
