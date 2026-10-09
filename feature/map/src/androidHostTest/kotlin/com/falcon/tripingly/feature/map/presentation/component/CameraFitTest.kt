package com.falcon.tripingly.feature.map.presentation.component

import com.falcon.tripingly.feature.map.domain.model.Coordinates
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CameraFitTest {

    private val vienna = listOf(
        Coordinates(48.20849, 16.37208),
        Coordinates(48.20659, 16.36553),
        Coordinates(48.20383, 16.36958),
    )

    /** Where [point] lands on a [width] x [height] map with the camera at [fit], in dp from the top left. */
    private fun screenPosition(point: Coordinates, fit: CameraFit, width: Float, height: Float): Pair<Double, Double> {
        val scale = 256 * 2.0.pow(fit.zoom.toDouble())
        fun y(lat: Double) = ln(tan(PI / 4 + lat * PI / 360)) / (2 * PI) * scale
        val x = width / 2 + (point.longitude - fit.center.longitude) / 360 * scale
        val top = height / 2 - (y(point.latitude) - y(fit.center.latitude))
        return x to top
    }

    @Test
    fun noPoints_noFit() {
        assertNull(fitCamera(emptyList(), 400f, 800f, 100f, 200f, 48f))
    }

    @Test
    fun closeStops_stopAtStreetZoom() {
        assertEquals(15f, fitCamera(vienna, 400f, 800f, top = 100f, bottom = 300f, margin = 48f)!!.zoom)
    }

    @Test
    fun onePoint_isCenteredInTheFreeArea_atStreetZoom() {
        val fit = fitCamera(vienna.take(1), 400f, 800f, top = 100f, bottom = 300f, margin = 48f)!!
        assertEquals(15f, fit.zoom)
        val (x, y) = screenPosition(vienna[0], fit, 400f, 800f)
        assertEquals(200.0, x, 0.5)
        // The free area runs from 100 to 500, so its middle is at 300.
        assertEquals(300.0, y, 0.5)
    }

    @Test
    fun everyPoint_landsInsideTheFreeArea_andFillsIt() {
        // Far enough apart that the zoom isn't capped.
        val stops = vienna + Coordinates(48.18487, 16.31221)
        val fit = fitCamera(stops, 400f, 800f, top = 100f, bottom = 300f, margin = 48f)!!
        assertTrue(fit.zoom < 15f)
        val positions = stops.map { screenPosition(it, fit, 400f, 800f) }
        positions.forEach { (x, y) ->
            assertTrue(x in 47.5..352.5, "x $x")
            assertTrue(y in 147.5..452.5, "y $y")
        }
        // Tight: the stops span the free area (304 dp each way) in at least one direction.
        val width = positions.maxOf { it.first } - positions.minOf { it.first }
        val height = positions.maxOf { it.second } - positions.minOf { it.second }
        assertTrue(abs(width - 304) < 1 || abs(height - 304) < 1, "width $width, height $height")
    }
}
