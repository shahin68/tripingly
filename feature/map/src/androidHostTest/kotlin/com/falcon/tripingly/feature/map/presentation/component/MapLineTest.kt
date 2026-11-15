package com.falcon.tripingly.feature.map.presentation.component

import com.falcon.tripingly.core.location.model.Coordinates
import androidx.compose.ui.graphics.Color
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.model.route.TransitLine
import com.falcon.tripingly.core.model.route.TransitStep
import com.falcon.tripingly.core.model.route.TransitStop
import com.falcon.tripingly.core.model.route.TransitVehicle
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.GeoPoint
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MapLineTest {

    private val opera = Coordinates(48.2031, 16.3691)
    private val stephansdom = Coordinates(48.2085, 16.3731)
    private val prater = Coordinates(48.2166, 16.3958)

    /** A route along streets: it starts and ends a little off the stops, on the nearest street. */
    private val streets = listOf(
        Coordinates(48.2033, 16.3695),
        Coordinates(48.2060, 16.3700),
        Coordinates(48.2083, 16.3735),
        Coordinates(48.2120, 16.3850),
        Coordinates(48.2163, 16.3950),
    )

    @Test
    fun aRouteStartsAndEndsExactlyOnTheStops() {
        val line = routeLine(streets, listOf(opera, stephansdom, prater), TravelMode.Walking)

        assertEquals(opera, line.points.first())
        assertEquals(prater, line.points.last())
    }

    @Test
    fun aRouteHasAnArrowBetweenEachTwoStops() {
        val arrows = routeLine(streets, listOf(opera, stephansdom, prater), TravelMode.Walking).arrows

        assertEquals(2, arrows.size)
        // Both stretches run north-east.
        assertTrue(arrows.all { it.bearing in 0f..90f }, "${arrows.map { it.bearing }}")
        assertTrue(arrows[0].position.latitude < stephansdom.latitude)
        assertTrue(arrows[1].position.latitude > stephansdom.latitude)
    }

    @Test
    fun aLongStraightRouteIsCutIntoEvenSteps() {
        val points = routeLine(listOf(opera, prater), listOf(opera, prater), TravelMode.Driving).points

        assertTrue(points.size > 100, "${points.size}")
        assertEquals(opera, points.first())
        assertEquals(prater, points.last())
    }

    @Test
    fun eachWayOfTravellingHasItsOwnColor() {
        val colors = TravelMode.entries.map { routeLine(streets, listOf(opera, prater), it).style.color }

        assertEquals(colors.size, colors.toSet().size)
        assertTrue(colors.none { it == StopPathStyle.color })
        assertNotEquals(StopPathStyle, routeLine(streets, listOf(opera, prater), TravelMode.Walking).style)
    }

    @Test
    fun publicTransportDrawsItsRidesInTheirLinesColoursAndItsWalksDotted() {
        fun stop(at: Coordinates) = TransitStop(null, GeoPoint(at.latitude, at.longitude), Instant.fromEpochSeconds(0))
        fun step(from: Coordinates, to: Coordinates, line: TransitLine?) = TransitStep(
            isRide = line != null,
            vehicle = TransitVehicle.Subway.takeIf { line != null },
            line = line,
            headsign = null,
            from = stop(from),
            to = stop(to),
            stops = 0,
            distanceMeters = 500,
            durationSeconds = 300,
            points = listOf(from, to).map { GeoPoint(it.latitude, it.longitude) },
        )
        val steps = listOf(
            step(opera, stephansdom, line = null),
            step(stephansdom, prater, TransitLine("U1", 0xFFE3000F, 0xFFFFFFFF)),
            step(prater, opera, TransitLine("5A", color = null, textColor = null)),
        )

        val line = transitLine(streets, steps, listOf(opera, prater))

        assertEquals(listOf(true, false, false), line.parts.map { it.isDotted })
        assertEquals(listOf(MapColors.Walk, Color(0xFFE3000F), MapColors.Transit), line.parts.map { it.color })
        assertTrue(line.arrows.isEmpty())
        // Steps without lines of their own: the whole route in one colour.
        assertTrue(transitLine(streets, steps.map { it.copy(points = emptyList()) }, listOf(opera, prater)).parts.isEmpty())
    }
}
