package com.falcon.tripingly.feature.map.presentation.component

import com.falcon.tripingly.core.location.model.Coordinates
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StopPathTest {

    private val opera = Coordinates(48.2031, 16.3691)
    private val stephansdom = Coordinates(48.2085, 16.3731)
    private val prater = Coordinates(48.2166, 16.3958)

    @Test
    fun theCurvesPassThroughEveryStopInOrder() {
        val path = stopPathShape(listOf(opera, stephansdom, prater)).points

        assertEquals(opera, path.first())
        assertEquals(prater, path.last())
        val middle = path.indexOfFirst { abs(it.latitude - stephansdom.latitude) < 1e-9 && abs(it.longitude - stephansdom.longitude) < 1e-9 }
        assertTrue(middle in 1 until path.lastIndex)
    }

    @Test
    fun twoStopsAreJoinedByAnArcBowingToTheRightOfTravel() {
        // Due north: right is east.
        val path = stopPathShape(listOf(Coordinates(48.0, 16.0), Coordinates(48.1, 16.0))).points
        assertTrue(path[path.size / 2].longitude > 16.0)
    }

    @Test
    fun theLineHasNoCornerAtAStop() {
        // Like a hand-drawn U: down from the first stop, level through the low one, up to the last.
        val low = Coordinates(48.0, 16.1)
        val path = stopPathShape(listOf(Coordinates(48.1, 16.0), low, Coordinates(48.15, 16.2))).points
        val at = path.indexOf(low)
        val before = path[at - 1]
        val after = path[at + 1]
        // Arriving and leaving in the same direction: the neighbours sit on a line through the stop.
        val inLat = low.latitude - before.latitude
        val inLng = low.longitude - before.longitude
        val outLat = after.latitude - low.latitude
        val outLng = after.longitude - low.longitude
        val sine = (inLat * outLng - inLng * outLat) / (hypot(inLat, inLng) * hypot(outLat, outLng))
        assertTrue(abs(sine) < 0.3, "turns by asin($sine) at the stop")
        // The line dips from the first stop instead of heading straight for the low one.
        assertTrue(path[1].latitude < 48.1 - (48.1 - 48.0) / 24)
    }

    @Test
    fun stopsInTheSameSpotAreJoinedDirectly() {
        assertEquals(listOf(opera, opera), stopPathShape(listOf(opera, opera)).points)
        assertEquals(listOf(opera), stopPathShape(listOf(opera)).points)
    }

    @Test
    fun eachStretchHasAnArrowPointingToTheNextStop() {
        // East, then north.
        val arrows = stopPathShape(listOf(Coordinates(48.0, 16.0), Coordinates(48.0, 16.1), Coordinates(48.1, 16.1))).arrows

        assertEquals(2, arrows.size)
        assertTrue(arrows[0].bearing in 45f..135f, "east-ish: ${arrows[0].bearing}")
        assertTrue(arrows[1].bearing in -45f..45f, "north-ish: ${arrows[1].bearing}")
        assertTrue(stopPathShape(listOf(opera, opera)).arrows.isEmpty())
    }

    @Test
    fun theStreakRunsFromTheStartToTheEnd() {
        val points = (0..100).toList()

        assertTrue(sweepOf(points, 0f).size < 2)
        val early = sweepOf(points, 0.2f)
        val late = sweepOf(points, 0.7f)
        assertEquals(0, early.first())
        assertTrue(late.first() > early.last())
        assertTrue(sweepOf(points, 1f).size < 2)
    }
}
