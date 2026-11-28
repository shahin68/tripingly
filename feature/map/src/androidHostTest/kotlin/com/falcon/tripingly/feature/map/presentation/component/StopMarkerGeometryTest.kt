package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals

class StopMarkerGeometryTest {

    // At 1 px per dp.
    private val density = Density(1f)
    private val photo = Size(44f, 44f)
    private val numbered = Size(32f, 32f)

    @Test
    fun `a numbered pin without news is just its circle`() {
        assertEquals(Size(16f, 16f), with(density) { stopMarkerHalfSize(hasCover = false, isNew = false, body = numbered, badgeWidth = 0f) })
    }

    @Test
    fun `the ring corner is the ring's middle at 45 degrees`() {
        assertEquals(21.5f * 0.7071f, ringCorner(hasCover = true).value, 0.001f)
        assertEquals(15.5f * 0.7071f, ringCorner(hasCover = false).value, 0.001f)
    }

    @Test
    fun `the new dot and its cut reach past a numbered pin's box on both axes`() {
        val half = with(density) { stopMarkerHalfSize(hasCover = false, isNew = true, body = numbered, badgeWidth = 0f) }
        // Ring corner 10.96 + red radius 4 + cut 2.
        assertEquals(10.96f + 6f, half.width, 0.01f)
        assertEquals(half.width, half.height, 0.001f)
    }

    @Test
    fun `a wider badge on a photo pin reaches further left, so the marker grows on both sides`() {
        val corner = ringCorner(hasCover = true).value
        val round = with(density) { stopMarkerHalfSize(hasCover = true, isNew = false, body = photo, badgeWidth = 18f) }
        val wide = with(density) { stopMarkerHalfSize(hasCover = true, isNew = false, body = photo, badgeWidth = 40f) }
        // The badge's right half-circle is centred on the ring at 315°: it reaches corner + 9 up and badge - 9 + corner left.
        assertEquals(maxOf(22f, 18f - 9f + corner), round.width, 0.01f)
        assertEquals(40f - 9f + corner, wide.width, 0.01f)
        assertEquals(corner + 9f, wide.height, 0.01f)
    }

    @Test
    fun `a halo larger than the badge and dot sets the size`() {
        val halo = Size(62f, 62f)
        assertEquals(Size(31f, 31f), with(density) { stopMarkerHalfSize(hasCover = true, isNew = true, body = halo, badgeWidth = 18f) })
    }
}
