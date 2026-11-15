package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.TripMarker
import kotlin.test.Test
import kotlin.test.assertEquals

class TripStopPlacesTest {
    private fun stop(id: String, dayId: String, placeId: String) =
        TripMarker(id, "trip", dayId, placeId, "Place $placeId", GeoPoint(48.85, 2.29), position = 0)

    private val day1 = TripDay("d1", 0, null, listOf(stop("m1", "d1", "eiffel"), stop("m2", "d1", "louvre")))
    private val day2 = TripDay("d2", 1, null, listOf(stop("m3", "d2", "eiffel")))
    private val day3 = TripDay("d3", 2, null, emptyList())

    @Test
    fun `only the open day's stops hide their places`() {
        assertEquals(setOf("eiffel", "louvre"), stopPlaceIds(day1))
        assertEquals(emptySet(), stopPlaceIds(day3))
    }

    @Test
    fun `other days' stops stay places to add, each place once`() {
        val days = listOf(day1, day2, day3)
        assertEquals(listOf("eiffel", "louvre"), tripStopPlaces(days, day3).map { it.id })
        assertEquals(listOf("eiffel"), tripStopPlaces(days, day1).map { it.id })
        assertEquals(listOf("eiffel", "louvre"), tripStopPlaces(days, day2).map { it.id })
    }
}
