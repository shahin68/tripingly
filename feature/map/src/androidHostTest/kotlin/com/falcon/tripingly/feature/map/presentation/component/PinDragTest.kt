package com.falcon.tripingly.feature.map.presentation.component

import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinDragTest {

    private val stephansdom = PinEnd.Stop(MapMarker("s1", Coordinates(48.2085, 16.3731), UiText.Server("Stephansdom"), 1, placeId = "p-dom"))
    private val opera = PinEnd.Stop(MapMarker("s2", Coordinates(48.2031, 16.3691), UiText.Server("Oper"), 2))
    private val dom = PinEnd.Place(MapPlace("p-dom", "Stephansdom", PlaceCategory.Attraction, GeoPoint(48.2085, 16.3731), isTripinly = false, hotCount = 0, prominence = 90))
    private val zoom = 15f

    @Test
    fun `the line snaps to a pin near enough and follows the finger elsewhere`() {
        val dragger = PinDragger()
        val nearOpera = Coordinates(48.2032, 16.3692)
        dragger.move(stephansdom, nearOpera, zoom, listOf(stephansdom, opera))
        assertEquals(opera, dragger.drag?.target)
        assertEquals(opera.position, dragger.drag?.end)

        val nowhere = Coordinates(48.2200, 16.3500)
        dragger.move(stephansdom, nowhere, zoom, listOf(stephansdom, opera))
        assertNull(dragger.drag?.target)
        assertEquals(nowhere, dragger.drag?.end)
    }

    @Test
    fun `letting go on a pin gives both ends, elsewhere nothing`() {
        val dragger = PinDragger()
        dragger.move(stephansdom, opera.position, zoom, listOf(opera))
        assertEquals(stephansdom to opera, dragger.end())
        assertNull(dragger.drag)

        dragger.move(stephansdom, Coordinates(48.2200, 16.3500), zoom, listOf(opera))
        assertNull(dragger.end())
        assertNull(dragger.end())
    }

    @Test
    fun `neither the pulled pin nor its stop's own place is a target`() {
        val dragger = PinDragger()
        dragger.move(stephansdom, stephansdom.position, zoom, listOf(stephansdom, dom))
        assertNull(dragger.drag?.target)
    }

    @Test
    fun `screen distance doubles with each zoom level`() {
        val a = Coordinates(48.2085, 16.3731)
        val b = Coordinates(48.2031, 16.3691)
        val at15 = dpBetween(a, b, 15f)
        assertEquals(at15 * 2, dpBetween(a, b, 16f), 1e-6)
        // About 670 m apart: at zoom 15 (some 3.2 m per dp at this latitude) a couple of hundred dp.
        assertTrue(at15 in 150.0..300.0)
    }
}
