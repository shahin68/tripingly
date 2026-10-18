package com.falcon.tripingly.feature.map.presentation.component

import com.falcon.tripingly.core.location.model.Coordinates
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.PatternItem
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
        val path = curvedStopPath(listOf(opera, stephansdom, prater))

        assertEquals(opera, path.first())
        assertEquals(prater, path.last())
        val middle = path.indexOfFirst { abs(it.latitude - stephansdom.latitude) < 1e-9 && abs(it.longitude - stephansdom.longitude) < 1e-9 }
        assertTrue(middle in 1 until path.lastIndex)
    }

    @Test
    fun twoStopsAreJoinedByAnArcBowingToTheRightOfTravel() {
        // Due north: right is east.
        val path = curvedStopPath(listOf(Coordinates(48.0, 16.0), Coordinates(48.1, 16.0)))
        assertTrue(path[path.size / 2].longitude > 16.0)
    }

    @Test
    fun theLineHasNoCornerAtAStop() {
        // Like a hand-drawn U: down from the first stop, level through the low one, up to the last.
        val low = Coordinates(48.0, 16.1)
        val path = curvedStopPath(listOf(Coordinates(48.1, 16.0), low, Coordinates(48.15, 16.2)))
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
        assertEquals(listOf(opera, opera), curvedStopPath(listOf(opera, opera)))
        assertEquals(listOf(opera), curvedStopPath(listOf(opera)))
    }

    @Test
    fun theDashesMoveTowardsTheEnd() {
        assertEquals(listOf("gap" to 3f, "dash" to 10f, "gap" to 5f), marchingDashes(dash = 10f, gap = 8f, shift = 3f).described())
        // Past the gap, the dash wraps around: 2 at the start, 8 at the end of the pattern.
        assertEquals(listOf("dash" to 2f, "gap" to 8f, "dash" to 8f), marchingDashes(dash = 10f, gap = 8f, shift = 10f).described())
    }

    private fun List<PatternItem>.described() = map {
        when (it) {
            is Dash -> "dash" to it.length
            is Gap -> "gap" to it.length
            else -> error("Unexpected $it")
        }
    }
}
