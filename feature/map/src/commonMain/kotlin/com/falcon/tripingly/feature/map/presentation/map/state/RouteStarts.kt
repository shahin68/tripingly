package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.TripMarker
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where a route to an open place can start: the user ([stop] null, while their location is known) or one of the open
 * day's stops ([number] counts from 1), with how far it is from the place in a straight line.
 */
internal data class RouteStart(val id: String, val location: GeoPoint, val stop: TripMarker?, val number: Int?, val distanceMeters: Int)

/**
 * The starts on offer for a route to [place]: the user first, then the day's [stops] nearest first. A stop at the place
 * itself is no start.
 */
internal fun routeStarts(place: GeoPoint, me: GeoPoint?, stops: List<TripMarker>): List<RouteStart> {
    val mine = me?.let { RouteStart(MY_LOCATION_START, it, stop = null, number = null, distanceMeters = distanceMeters(it, place)) }
    val fromStops = stops.mapIndexed { index, stop -> RouteStart(stop.id, stop.location, stop, index + 1, distanceMeters(stop.location, place)) }
        .filter { it.distanceMeters >= SAME_SPOT_METERS }
        .sortedBy { it.distanceMeters }
    return listOfNotNull(mine) + fromStops
}

/**
 * The start guessed for the user: themselves while they're near the place (on the trip), else the nearest stop, else
 * themselves however far.
 */
internal fun List<RouteStart>.guess(): RouteStart? =
    firstOrNull { it.stop == null && it.distanceMeters <= NEAR_ME_METERS } ?: firstOrNull { it.stop != null } ?: firstOrNull()

/** The id of the user's own start, beside the stops' ids. */
internal const val MY_LOCATION_START = "my-location"

/** Closer than this, a stop is at the place: no route from it. */
private const val SAME_SPOT_METERS = 25

/** Within this, the user is taken to be on the trip, so the route starts where they are. */
private const val NEAR_ME_METERS = 20_000

/** The straight-line distance on the earth's surface. */
internal fun distanceMeters(a: GeoPoint, b: GeoPoint): Int {
    fun rad(degrees: Double) = degrees * kotlin.math.PI / 180
    val dLat = rad(b.lat - a.lat)
    val dLng = rad(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLng / 2).pow(2)
    return (2 * EARTH_RADIUS_METERS * asin(sqrt(h))).roundToInt()
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
