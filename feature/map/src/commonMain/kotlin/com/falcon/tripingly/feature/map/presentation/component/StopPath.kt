package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.location.model.Coordinates

/*
 * While no route shows, the open day's stops are joined by straight lines with an arrow on each stretch
 * pointing to the next stop, so the order of the day reads at a glance. Drawn like every map line (see
 * MapLine.kt) by the app alone: no routing call.
 */

/** Calm slate blue: a plan, quieter than a real route's colors. */
internal val StopPathColor = Color(0xFF3D5A80)

internal val StopPathStyle = MapLineStyle(StopPathColor, 3.dp)

/** Points on each stretch, so the streak glides instead of jumping from stop to stop. */
private const val STEPS_PER_LEG = 24

/** Straight lines through the [stops] in order. Stops in the same spot are joined directly and get no arrow. */
internal fun stopPathShape(stops: List<Coordinates>): MapLine {
    if (stops.size < 2) return MapLine(stops, emptyList(), StopPathStyle)
    val xScale = xScale(stops)
    val path = mutableListOf(stops.first())
    val arrows = mutableListOf<PathArrow>()
    for ((start, end) in stops.zipWithNext()) {
        if (start == end) {
            path += end
            continue
        }
        val lat = end.latitude - start.latitude
        val lng = end.longitude - start.longitude
        for (step in 1 until STEPS_PER_LEG) {
            val t = step.toDouble() / STEPS_PER_LEG
            path += Coordinates(latitude = start.latitude + lat * t, longitude = start.longitude + lng * t)
        }
        path += end
        val middle = Coordinates(latitude = start.latitude + lat / 2, longitude = start.longitude + lng / 2)
        arrows += PathArrow(middle, bearing(start, end, xScale))
    }
    return MapLine(path, arrows, StopPathStyle)
}
