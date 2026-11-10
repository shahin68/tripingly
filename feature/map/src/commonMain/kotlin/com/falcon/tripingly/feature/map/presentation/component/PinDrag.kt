package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
 * How fast the camera moves while a held pin is pulled, in px per second toward the finger. [free] is the part of the
 * map nothing covers (between the header and the plan, left of the panel in landscape): the camera stays still in its
 * middle ([PAN_STILL_SHARE_X] and [PAN_STILL_SHARE_Y] of the way to its edges), then moves faster the nearer the
 * finger gets to an edge, up to [PAN_MAX_SPEED] there and beyond.
 */
internal fun edgePanVelocity(finger: Offset, free: Rect, density: Density): Offset {
    if (free.width <= 0f || free.height <= 0f) return Offset.Zero
    val fromMiddle = finger - free.center
    fun share(distance: Float, half: Float, still: Float) = ((abs(distance) / half - still) / (1 - still)).coerceIn(0f, 1f)
    val share = max(share(fromMiddle.x, free.width / 2, PAN_STILL_SHARE_X), share(fromMiddle.y, free.height / 2, PAN_STILL_SHARE_Y))
    if (share <= 0f) return Offset.Zero
    val speed = with(density) { PAN_MAX_SPEED.toPx() } * share * share
    return fromMiddle / fromMiddle.getDistance() * speed
}

/** The part of a map of [size] (px) that its [padding] leaves free: what the header and cards don't cover. */
internal fun freeArea(size: Size, padding: PaddingValues, density: Density, layoutDirection: LayoutDirection): Rect = with(density) {
    Rect(
        left = padding.calculateLeftPadding(layoutDirection).toPx(),
        top = padding.calculateTopPadding().toPx(),
        right = size.width - padding.calculateRightPadding(layoutDirection).toPx(),
        bottom = size.height - padding.calculateBottomPadding().toPx(),
    )
}

/** How far a finger may be off a pin's circle and still hold it. */
private val PIN_TOUCH_SLOP = 6.dp

/** The finger moved this far from where it pressed: it's pulling, not just holding. */
internal val MOVE_SLOP = 12.dp

/** How far above the finger the handle floats, middle to middle. */
internal val HANDLE_LIFT = 56.dp

/** A pin this close to the handle, middle to middle, is the one it'll be let go on. */
internal val SNAP_DISTANCE = 40.dp

/** The middle of the free map, as a share of the way to its sides, where pulling doesn't move the camera. */
internal const val PAN_STILL_SHARE_X = 0.45f

/** The same up and down: smaller, since the free map is short between the header and the plan (Shahin, 2026-11-10). */
internal const val PAN_STILL_SHARE_Y = 0.25f

/** The camera's speed with the finger at an edge, per second. */
internal val PAN_MAX_SPEED = 900.dp
