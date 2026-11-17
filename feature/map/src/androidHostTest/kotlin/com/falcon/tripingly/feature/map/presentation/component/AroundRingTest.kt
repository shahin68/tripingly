package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.ui.geometry.Offset
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.ui.UiText
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AroundRingTest {

    private val ring = AroundRing(Coordinates(48.2, 16.37), radiusMeters = 500, label = UiText.Server("500 m"))

    @Test
    fun `the handle sits the ring's radius east of the pin, on the same latitude`() {
        val edge = ring.edge()
        assertEquals(48.2, edge.latitude)
        val meters = (edge.longitude - 16.37) * 111_320.0 * cos(48.2 * PI / 180)
        assertTrue(abs(meters - 500) < 0.01)
    }

    @Test
    fun `a finger twice as far from the pin as the edge makes the ring twice as big, in any direction`() {
        val center = Offset(100f, 100f)
        assertEquals(1000, ring.radiusAt(center, radiusPx = 50f, finger = Offset(200f, 100f)))
        assertEquals(1000, ring.radiusAt(center, radiusPx = 50f, finger = Offset(100f, 0f)))
        assertEquals(250, ring.radiusAt(center, radiusPx = 50f, finger = Offset(125f, 100f)))
    }

    @Test
    fun `a ring with no size on screen gives no radius`() {
        assertNull(ring.radiusAt(Offset.Zero, radiusPx = 0f, finger = Offset(10f, 0f)))
    }
}
