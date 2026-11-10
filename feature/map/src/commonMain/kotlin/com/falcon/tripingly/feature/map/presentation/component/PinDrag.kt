package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.MapPlace
import kotlin.math.abs
import kotlin.math.max

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

/** A pin on screen: its middle and how far around it a finger still counts as on it, in px. */
internal data class ShownPin(val end: PinEnd, val middle: Offset, val radius: Float)

/** The pins as they show at [zoom], placed by [project] (null when off the map), stops first since they're drawn on top. */
internal fun MapLayersState.shownPins(zoom: Float, density: Density, project: (Coordinates) -> Offset?): List<ShownPin> = with(density) {
    val slop = PIN_TOUCH_SLOP.toPx()
    val stopPins = stops.mapNotNull { stop ->
        val size = if (stop.coverPhotoId != null) COVER_PIN_SIZE else PIN_SIZE
        project(stop.position)?.let { ShownPin(PinEnd.Stop(stop), it, size.toPx() / 2 + slop) }
    }
    val selected = selectedPlace?.let { place ->
        val end = PinEnd.Place(place)
        project(end.position)?.let { ShownPin(end, it, SelectedPinSize.toPx() / 2 + slop) }
    }
    val places = placePins.filter { it.place.id != selectedPlace?.id }.mapNotNull { pin ->
        val end = PinEnd.Place(pin.place)
        val size = if (zoom < pinDotBelowZoom || pin.isDot) PlaceDotSize else pin.size
        project(end.position)?.let { ShownPin(end, it, size.toPx() / 2 + slop) }
    }
    stopPins + listOfNotNull(selected) + places
}

/** The pin under a finger at [touch]: among the pins it's on, the one whose middle is nearest, stops before places. */
internal fun List<ShownPin>.pinAt(touch: Offset): PinEnd? {
    val hits = filter { (it.middle - touch).getDistance() <= it.radius }
    return (hits.filter { it.end is PinEnd.Stop }.ifEmpty { hits }).minByOrNull { (it.middle - touch).getDistance() }?.end
}

/**
 * A pin held to pull a route from it. The pin stays where it is; a handle floats a little above the finger and the
 * line runs from the pin to it; [target] is the pin the handle is near, the one it'll be let go on. Positions are px in the map view.
 */
@Stable
internal class PinHold(val from: PinEnd, val pressedAt: Offset) {
    var finger by mutableStateOf(pressedAt)
        internal set
    var target by mutableStateOf<ShownPin?>(null)
        internal set

    /** The finger moved off where it pressed: only from then on does pulling toward the edges move the camera. */
    var hasMoved by mutableStateOf(false)
        internal set
}

/** Follows a held pin: the map views report the finger ([move]) and the lift ([end]), and refresh the pins as the camera moves. */
@Stable
internal class PinHolder {
    var hold by mutableStateOf<PinHold?>(null)
        private set

    fun start(from: PinEnd, at: Offset) {
        hold = PinHold(from, at)
    }

    /** The finger is at [finger]; the nearest of [pins] within [SNAP_DISTANCE] of the handle is the target. */
    fun move(finger: Offset, pins: List<ShownPin>, density: Density) {
        val hold = hold ?: return
        hold.finger = finger
        if (!hold.hasMoved && (finger - hold.pressedAt).getDistance() > with(density) { MOVE_SLOP.toPx() }) hold.hasMoved = true
        retarget(pins, density)
    }

    /** The pins moved under a still finger (the camera moved): the handle may be near another one now. */
    fun retarget(pins: List<ShownPin>, density: Density) {
        val hold = hold ?: return
        val handle = handleOf(hold.finger, density)
        val snap = with(density) { SNAP_DISTANCE.toPx() }
        hold.target = if (!hold.hasMoved) {
            null
        } else {
            pins.filter { it.end.key != hold.from.key && !it.end.isSameSpot(hold.from) }
                .map { it to (it.middle - handle).getDistance() }
                .filter { (_, distance) -> distance <= snap }
                .minByOrNull { (_, distance) -> distance }
                ?.first
        }
    }

    /** Lifted: both ends when the handle was on another pin, else null. */
    fun end(): Pair<PinEnd, PinEnd>? {
        val last = hold ?: return null
        hold = null
        return last.target?.let { last.from to it.end }
    }
}

/** Where the handle floats for a finger at [finger]: a little above it, so the finger doesn't cover it. */
internal fun handleOf(finger: Offset, density: Density): Offset = finger - Offset(0f, with(density) { HANDLE_LIFT.toPx() })

/** A stop's own place, under its pin, is no other end. */
private fun PinEnd.isSameSpot(other: PinEnd): Boolean = when {
    this is PinEnd.Place && other is PinEnd.Stop -> place.id == other.stop.placeId
    this is PinEnd.Stop && other is PinEnd.Place -> stop.placeId == other.place.id
    else -> false
}

/**
 * How fast the camera moves while a held pin is pulled, in px per second toward the finger: still in the middle of the
 * map ([PAN_STILL_SHARE] of the way to its edges), then faster the nearer the finger gets to an edge, up to
 * [PAN_MAX_SPEED] there.
 */
internal fun edgePanVelocity(finger: Offset, size: Size, density: Density): Offset {
    if (size.width <= 0f || size.height <= 0f) return Offset.Zero
    val fromMiddle = finger - Offset(size.width / 2, size.height / 2)
    val reach = max(abs(fromMiddle.x) / (size.width / 2), abs(fromMiddle.y) / (size.height / 2)).coerceAtMost(1f)
    if (reach <= PAN_STILL_SHARE) return Offset.Zero
    val share = (reach - PAN_STILL_SHARE) / (1 - PAN_STILL_SHARE)
    val speed = with(density) { PAN_MAX_SPEED.toPx() } * share * share
    return fromMiddle / fromMiddle.getDistance() * speed
}

/** How far a finger may be off a pin's circle and still hold it. */
private val PIN_TOUCH_SLOP = 6.dp

/** The finger moved this far from where it pressed: it's pulling, not just holding. */
internal val MOVE_SLOP = 12.dp

/** How far above the finger the handle floats, middle to middle. */
internal val HANDLE_LIFT = 56.dp

/** A pin this close to the handle, middle to middle, is the one it'll be let go on. */
internal val SNAP_DISTANCE = 40.dp

/** The middle of the map, as a share of the way to its edges, where pulling doesn't move the camera. */
internal const val PAN_STILL_SHARE = 0.45f

/** The camera's speed with the finger at an edge, per second. */
internal val PAN_MAX_SPEED = 900.dp
