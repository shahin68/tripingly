package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.MapPlace
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** One end of a route pulled from one pin to another: a stop or a place. [key] tells them apart. */
@Immutable
sealed interface PinEnd {
    val key: String
    val position: Coordinates

    data class Stop(val stop: MapMarker) : PinEnd {
        override val key get() = stop.id
        override val position get() = stop.position
    }

    data class Place(val place: MapPlace) : PinEnd {
        override val key get() = "place:${place.id}"
        override val position get() = Coordinates(place.location.lat, place.location.lng)
    }
}

/** A pin held and pulled from [from]: its line ends at [end], on [target] while one is near enough to let go on. */
@Immutable
internal data class PinDrag(val from: PinEnd, val end: Coordinates, val target: PinEnd?)

/** Where a pulled pin can be let go: the stops and the places on the map, each once. */
internal fun MapLayersState.dropTargets(): List<PinEnd> =
    stops.map { PinEnd.Stop(it) } + (placePins.map { it.place } + listOfNotNull(selectedPlace)).distinctBy { it.id }.map { PinEnd.Place(it) }

/**
 * Follows a pin while it's pulled. The map views report where the finger has it ([move]) and when it's let go
 * ([end]); [drag] is what to draw.
 */
@Stable
internal class PinDragger {
    var drag: PinDrag? by mutableStateOf(null)
        private set

    /** [from] is pulled to [at]; the line snaps to the nearest of [targets] within [SNAP_DISTANCE_DP]. */
    fun move(from: PinEnd, at: Coordinates, zoom: Float, targets: List<PinEnd>) {
        val target = targets
            .filter { it.key != from.key && !it.isSameSpot(from) }
            .map { it to dpBetween(it.position, at, zoom) }
            .filter { (_, distance) -> distance <= SNAP_DISTANCE_DP }
            .minByOrNull { (_, distance) -> distance }
            ?.first
        drag = PinDrag(from, target?.position ?: at, target)
    }

    /** Let go: both ends when it was let go on another pin, else null. */
    fun end(): Pair<PinEnd, PinEnd>? {
        val last = drag ?: return null
        drag = null
        return last.target?.let { last.from to it }
    }
}

/** A stop's own place, under its pin, is no other end. */
private fun PinEnd.isSameSpot(other: PinEnd): Boolean = when {
    this is PinEnd.Place && other is PinEnd.Stop -> place.id == other.stop.placeId
    this is PinEnd.Stop && other is PinEnd.Place -> stop.placeId == other.place.id
    else -> false
}

/** How far apart two points are on screen at [zoom], in dp, along Web Mercator as the SDKs draw (256 dp world at zoom 0). */
internal fun dpBetween(a: Coordinates, b: Coordinates, zoom: Float): Double {
    val world = 256.0 * 2.0.pow(zoom.toDouble())
    fun x(point: Coordinates) = point.longitude / 360 * world
    fun y(point: Coordinates) = ln(tan(PI / 4 + point.latitude * PI / 360)) / (2 * PI) * world
    return hypot(x(a) - x(b), y(a) - y(b))
}

/** A pin this close to where the pulled pin is, on screen, is the one it'll be let go on. */
internal const val SNAP_DISTANCE_DP = 40.0
