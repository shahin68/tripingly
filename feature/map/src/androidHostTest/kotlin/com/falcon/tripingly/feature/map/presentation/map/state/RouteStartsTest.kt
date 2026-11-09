package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.TripMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteStartsTest {

    private val prater = GeoPoint(48.2166, 16.3958)
    private val stephansdom = stop("s1", GeoPoint(48.2085, 16.3731), position = 0)
    private val opera = stop("s2", GeoPoint(48.2031, 16.3691), position = 1)
    private val atPrater = stop("s3", GeoPoint(48.21661, 16.39581), position = 2)

    @Test
    fun `the user comes first, then the stops nearest first, keeping their day numbers`() {
        val starts = routeStarts(prater, me = GeoPoint(48.30, 16.40), listOf(opera, stephansdom))

        assertEquals(listOf(MY_LOCATION_START, "s1", "s2"), starts.map { it.id })
        assertEquals(listOf(null, 2, 1), starts.map { it.number })
        assertTrue(starts[1].distanceMeters < starts[2].distanceMeters)
    }

    @Test
    fun `a stop at the place itself is no start`() {
        assertEquals(listOf("s1"), routeStarts(prater, me = null, listOf(stephansdom, atPrater)).map { it.id })
    }

    @Test
    fun `the guess is the user while near, else the nearest stop, else the user however far`() {
        val near = GeoPoint(48.20, 16.37)
        val far = GeoPoint(48.8566, 2.3522)

        assertEquals(MY_LOCATION_START, routeStarts(prater, near, listOf(stephansdom)).guess()?.id)
        assertEquals("s1", routeStarts(prater, far, listOf(opera, stephansdom)).guess()?.id)
        assertEquals(MY_LOCATION_START, routeStarts(prater, far, emptyList()).guess()?.id)
        assertNull(routeStarts(prater, me = null, emptyList()).guess())
    }

    @Test
    fun `distances are straight lines on the earth`() {
        // Stephansdom to the Prater wheel: about 1.9 km.
        assertTrue(distanceMeters(stephansdom.location, prater) in 1800..2000)
        assertEquals(0, distanceMeters(prater, prater))
    }

    private fun stop(id: String, location: GeoPoint, position: Int) =
        TripMarker(id = id, tripId = "t", dayId = "d", placeId = "p-$id", name = "Stop $id", location = location, position = position)
}
