package com.falcon.tripingly.feature.map.presentation.component

import com.falcon.tripingly.core.location.model.Coordinates
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StopPathTest {

    private val opera = Coordinates(48.2031, 16.3691)
    private val stephansdom = Coordinates(48.2085, 16.3731)
    private val prater = Coordinates(48.2166, 16.3958)

    @Test
    fun straightLinesPassThroughEveryStopInOrder() {
        val path = stopPathShape(listOf(opera, stephansdom, prater)).points

        assertEquals(opera, path.first())
        assertEquals(prater, path.last())
        val middle = path.indexOf(stephansdom)
        assertTrue(middle in 1 until path.lastIndex)
        // Halfway to the cathedral, the line sits on the straight line between the two stops.
        val half = path[middle / 2]
        assertTrue(abs(half.latitude - (opera.latitude + stephansdom.latitude) / 2) < 1e-9)
        assertTrue(abs(half.longitude - (opera.longitude + stephansdom.longitude) / 2) < 1e-9)
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
