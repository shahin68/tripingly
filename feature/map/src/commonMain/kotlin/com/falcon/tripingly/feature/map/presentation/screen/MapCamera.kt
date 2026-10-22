package com.falcon.tripingly.feature.map.presentation.screen

import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.feature.map.domain.model.CameraCommand
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Sends the map's camera where the screen needs it and keeps where it rests. The map fits and flies by
 * itself; this only says where to.
 */
internal class MapCamera(private val context: MapContext) {
    private val _commands = Channel<CameraCommand>(capacity = Channel.BUFFERED)
    val commands: Flow<CameraCommand> = _commands.receiveAsFlow()

    val target: Coordinates get() = context.model.value.camera.target
    val zoom: Float get() = context.model.value.camera.zoom

    fun moveTo(target: Coordinates, zoom: Float) {
        context.update { it.copy(camera = it.camera.copy(target = target, zoom = zoom, isReady = true)) }
        _commands.trySend(CameraCommand.MoveTo(target, zoom))
    }

    /** Shows all of [points]; without points the camera stays where it is. A new map starts in their middle. */
    fun frame(points: List<Coordinates>) {
        if (points.isEmpty()) return
        val fit = CameraCommand.fit(points).copy(isAnimated = context.model.value.camera.isReady)
        context.update {
            if (it.camera.isReady) {
                it
            } else {
                val center = Coordinates((fit.bounds.south + fit.bounds.north) / 2, (fit.bounds.west + fit.bounds.east) / 2)
                it.copy(camera = it.camera.copy(target = center, isReady = true))
            }
        }
        _commands.trySend(fit)
    }

    /**
     * Shows the open day: its stops; the whole trip's while that day has none; the trip's destination while the
     * trip has no stops at all; else the map stays where it is. Opening a trip and tapping a day tab both show a day
     * this way, so they frame alike.
     */
    fun showOpenDay() {
        val details = context.trip.value ?: return showAsItIs()
        val stops = context.activeDay()?.markers.orEmpty().ifEmpty { details.days.flatMap { it.markers } }
        val destination = details.destination
        when {
            stops.isNotEmpty() -> frame(stops.map { Coordinates(it.location.lat, it.location.lng) })
            destination != null -> moveTo(Coordinates(destination.location.lat, destination.location.lng), DESTINATION_ZOOM)
            else -> showAsItIs()
        }
    }

    /** The map opens where it is: it has nothing to show first. */
    fun showAsItIs() = context.update { it.copy(camera = it.camera.copy(isReady = true)) }

    /** Where the map came to rest. */
    fun onRest(target: Coordinates, zoom: Float) =
        context.update { it.copy(camera = it.camera.copy(target = target, zoom = zoom)) }

    /** The camera already rests on [location] at [zoom], so it won't move. */
    fun isAt(location: GeoPoint, zoom: Float): Boolean =
        abs(this.zoom - zoom) < 0.01f && isCentered(location, target, zoom)

    /** [location] is within a couple of screen points of the camera's [center]. */
    fun isCentered(location: GeoPoint, center: Coordinates, zoom: Float): Boolean {
        val pointsPerDegree = 256 * 2.0.pow(zoom.toDouble()) / 360
        val dx = (location.lng - center.longitude) * pointsPerDegree
        val dy = (location.lat - center.latitude) * pointsPerDegree / cos(center.latitude * PI / 180)
        return sqrt(dx * dx + dy * dy) <= CENTERED_POINTS
    }

    private companion object {
        /** How far the camera may rest from a place it flew to and still count as on it (rounding). */
        const val CENTERED_POINTS = 2.0
        /** A city fills the screen. */
        const val DESTINATION_ZOOM = 12f
    }
}
