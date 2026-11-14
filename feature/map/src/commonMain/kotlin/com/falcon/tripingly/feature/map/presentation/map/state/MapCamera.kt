package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.feature.map.presentation.component.CameraCommand
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Sends the map's camera where the screen needs it and keeps where it rests. The map fits and flies by
 * itself; this only says where to.
 */
internal class MapCamera(private val context: MapContext) {
    private val _commands = Channel<CameraCommand>(capacity = Channel.BUFFERED)
    val commands: Flow<CameraCommand> = _commands.receiveAsFlow()

    /** The bounds the camera last fitted, while it still shows them: no finger or other move since. */
    private var shownFit: CameraCommand.Fit? = null

    val target: Coordinates get() = context.model.value.camera.target
    val zoom: Float get() = context.model.value.camera.zoom

    /** Moves the camera somewhere else than where I am: the map stops following me. */
    fun moveTo(target: Coordinates, zoom: Float) {
        stopFollowing()
        follow(target, zoom)
    }

    /** Moves the camera to where I am, following me (see [MapLocation]). */
    fun follow(target: Coordinates, zoom: Float) {
        shownFit = null
        context.update { it.copy(camera = it.camera.copy(target = target, zoom = zoom, isReady = true)) }
        _commands.trySend(CameraCommand.MoveTo(target, zoom))
    }

    /** A finger moved the map, or the screen showed something else: the camera no longer follows me. */
    fun stopFollowing() = context.update { it.copy(location = it.location.copy(isFollowing = false, isSignalLost = false, isWaitingForFirst = false)) }

    /** Shows all of [points]; without points the camera stays where it is. A new map opens on them already fitted. */
    fun frame(points: List<Coordinates>) {
        if (points.isEmpty()) return
        stopFollowing()
        val fit = CameraCommand.fit(points)
        shownFit = fit
        if (context.model.value.camera.isReady) {
            _commands.trySend(fit)
        } else {
            context.update { it.copy(camera = it.camera.copy(openingBounds = fit.bounds, isReady = true)) }
        }
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

    /** A finger moved the map: it no longer shows a fit, nor follows me. */
    /** The compass: north up where the map is, keeping its center and zoom. */
    fun northUp() {
        _commands.trySend(CameraCommand.NorthUp)
    }

    fun onUserMove() {
        shownFit = null
        stopFollowing()
    }

    /**
     * The screen switched layouts (a rotation, a split screen), so the free part of the map has another shape: a fit
     * still showing is fitted again to it, and a map built anew opens on it already fitted (Shahin, 2026-11-14). A
     * camera moved since stays where it is.
     */
    fun onLayoutChange() {
        val fit = shownFit ?: return
        context.update { it.copy(camera = it.camera.copy(openingBounds = fit.bounds)) }
        _commands.trySend(fit)
    }

    /** The map opens where it is: it has nothing to show first. */
    fun showAsItIs() = context.update { it.copy(camera = it.camera.copy(isReady = true)) }

    /** Where the map came to rest. */
    fun onRest(target: Coordinates, zoom: Float) =
        context.update { it.copy(camera = it.camera.copy(target = target, zoom = zoom, openingBounds = null)) }

    /** The camera already rests on [location] at [zoom], so it won't move. */
    fun isAt(location: GeoPoint, zoom: Float): Boolean =
        abs(this.zoom - zoom) < 0.01f && isCentered(location, target, zoom)

    /** [location] is within a couple of screen points of the camera's [center]. */
    private fun isCentered(location: GeoPoint, center: Coordinates, zoom: Float): Boolean {
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
