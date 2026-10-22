package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.model.place.GeoBounds
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals

class FittedCameraTest {
    @Test
    fun `a wide area fills the width, inside the margin`() {
        // A quarter of the world across, 256 dp wide once the margins are off: zoom 2.
        val (center, zoom) = fittedCamera(GeoBounds(south = -1.0, west = -45.0, north = 1.0, east = 45.0), 256.dp + FitMargin * 2, 2000.dp)

        assertEquals(2f, zoom, 1e-4f)
        assertEquals(0.0, center.latitude, 1e-9)
        assertEquals(0.0, center.longitude, 1e-9)
    }

    @Test
    fun `a tall area fills the height, centered as the map projects it`() {
        val bounds = GeoBounds(south = 40.0, west = 16.0, north = 50.0, east = 16.1)
        val (center, zoom) = fittedCamera(bounds, 2000.dp, 256.dp + FitMargin * 2)

        fun mercatorY(latitude: Double) = ln(tan(PI / 4 + latitude * PI / 360))
        val spanY = (mercatorY(50.0) - mercatorY(40.0)) / (2 * PI)
        assertEquals(kotlin.math.log2(1 / spanY).toFloat(), zoom, 1e-4f)
        // Further north than the plain middle, as on the map.
        assertEquals(atan(sinh((mercatorY(50.0) + mercatorY(40.0)) / 2)) * 180 / PI, center.latitude, 1e-9)
        assertEquals(16.05, center.longitude, 1e-9)
    }

    @Test
    fun `a raise moves the target south so the area sits that far higher on screen`() {
        // Zoom 2 makes the world 1024 dp tall, so 128 dp is an eighth of it: a quarter of pi in Mercator.
        val (target, zoom) = fittedCamera(
            GeoBounds(south = -1.0, west = -45.0, north = 1.0, east = 45.0),
            256.dp + FitMargin * 2,
            2000.dp,
            raise = 128.dp,
        )

        assertEquals(2f, zoom, 1e-4f)
        assertEquals(atan(sinh(-PI / 4)) * 180 / PI, target.latitude, 1e-9)
        assertEquals(0.0, target.longitude, 1e-9)
    }
}
