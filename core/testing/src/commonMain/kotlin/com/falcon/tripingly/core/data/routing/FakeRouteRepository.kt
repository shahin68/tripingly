package com.falcon.tripingly.core.data.routing

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.route.BestOrder
import com.falcon.tripingly.core.model.route.Route
import com.falcon.tripingly.core.model.route.RouteLeg
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.route.TransitLine
import com.falcon.tripingly.core.model.route.TransitStep
import com.falcon.tripingly.core.model.route.TransitStop
import com.falcon.tripingly.core.model.route.TransitTime
import com.falcon.tripingly.core.model.route.TransitVehicle
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.TripDay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Instant

/** Straight lines between the stops, for building and demoing without the server. */
class FakeRouteRepository : RouteRepository {

    /** The public transport times asked for, in order: a day route's `departAt` as a leaving time. */
    val transitTimes = mutableListOf<TransitTime?>()

    override suspend fun dayRoute(tripId: String, day: TripDay, mode: TravelMode, departAt: Instant?): AppResult<RoutePlan, DataError.Network> {
        if (mode == TravelMode.Transit) transitTimes += departAt?.let { TransitTime(it) }
        val stops = day.markers
        if (stops.size < 2) return AppResult.Success(RoutePlan(null, emptyList(), isEstimated = false, ATTRIBUTION))
        val legs = stops.zipWithNext { from, to -> leg(from.location, to.location, mode, from.id, to.id) }
        return AppResult.Success(RoutePlan(route(stops.map { it.location }, legs), emptyList(), isEstimated = false, ATTRIBUTION))
    }

    override suspend fun route(
        from: GeoPoint,
        to: GeoPoint,
        mode: TravelMode,
        tripId: String?,
        fromPlaceId: String?,
        toPlaceId: String?,
        time: TransitTime?,
    ): AppResult<RoutePlan, DataError.Network> {
        if (mode == TravelMode.Transit) transitTimes += time
        return AppResult.Success(RoutePlan(route(listOf(from, to), listOf(leg(from, to, mode, null, null))), emptyList(), isEstimated = false, ATTRIBUTION))
    }

    /** Nearest stop next, from the first. */
    override suspend fun bestOrder(tripId: String, day: TripDay, mode: TravelMode): AppResult<BestOrder, DataError.Network> {
        val left = day.markers.drop(1).toMutableList()
        val order = day.markers.take(1).toMutableList()
        while (left.isNotEmpty()) {
            val next = left.minBy { meters(order.last().location, it.location) }
            left -= next
            order += next
        }
        return AppResult.Success(BestOrder(order.map { it.id }, byTravelTime = false, savedMinutes = null))
    }

    private fun route(points: List<GeoPoint>, legs: List<RouteLeg>) =
        Route(points, legs.sumOf { it.distanceMeters }, legs.sumOf { it.durationSeconds }, legs)

    private fun leg(from: GeoPoint, to: GeoPoint, mode: TravelMode, fromId: String?, toId: String?): RouteLeg {
        val meters = meters(from, to)
        val metersPerSecond = when (mode) {
            TravelMode.Walking -> 1.4
            TravelMode.Cycling -> 4.2
            TravelMode.Driving -> 11.0
            TravelMode.Transit -> 6.0
        }
        val seconds = (meters / metersPerSecond).roundToInt()
        // By public transport: one ride on a red line the whole way.
        val steps = if (mode == TravelMode.Transit) {
            val start = Instant.fromEpochSeconds(0)
            listOf(
                TransitStep(
                    isRide = true,
                    vehicle = TransitVehicle.Subway,
                    line = TransitLine("U1", 0xFFE3000F, 0xFFFFFFFF),
                    headsign = null,
                    from = TransitStop(null, from, start),
                    to = TransitStop(null, to, Instant.fromEpochSeconds(seconds.toLong())),
                    stops = 0,
                    distanceMeters = meters.roundToInt(),
                    durationSeconds = seconds,
                    points = listOf(from, to),
                ),
            )
        } else {
            emptyList()
        }
        return RouteLeg(fromId, toId, meters.roundToInt(), seconds, steps)
    }

    private fun meters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = (b.lat - a.lat) * METERS_PER_DEGREE
        val dLng = (b.lng - a.lng) * METERS_PER_DEGREE * cos(a.lat * PI / 180)
        return sqrt(dLat * dLat + dLng * dLng)
    }

    private companion object {
        const val METERS_PER_DEGREE = 111_320.0
        const val ATTRIBUTION = "© openrouteservice.org | © OpenStreetMap contributors"
    }
}
