package com.falcon.tripingly.core.data.routing

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.data.places.query
import com.falcon.tripingly.core.data.places.toCategoryCounts
import com.falcon.tripingly.core.data.places.toMapPlace
import com.falcon.tripingly.core.data.trips.PendingTripWrites
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
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
import com.falcon.tripingly.core.network.model.OptimizeResultDto
import com.falcon.tripingly.core.network.model.RouteResponseDto
import com.falcon.tripingly.core.network.model.TransitPlaceDto
import com.falcon.tripingly.core.network.model.TransitStepDto
import com.falcon.tripingly.core.network.routing.RoutingApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlin.time.Instant

internal class DefaultRouteRepository(
    private val api: RoutingApi,
    private val pending: PendingTripWrites,
) : RouteRepository {

    private val lock = Mutex()

    /** Answered routes by what was asked, least recently used first. */
    private val routes = LinkedHashMap<RouteKey, RoutePlan>()

    override suspend fun dayRoute(tripId: String, day: TripDay, mode: TravelMode, departAt: Instant?, categories: Set<PlaceCategory>): AppResult<RoutePlan, DataError.Network> {
        val time = departAt?.let { TransitTime(it) }.takeIf { mode == TravelMode.Transit }
        val key = RouteKey(day.id, mode, day.markers.map { it.id to it.location }, categories)
        return cached(key) {
            pending.awaitSent(tripId)
            api.dayRoute(day.id, mode.value, time?.at?.toString(), categories.query()).map { it.toRoutePlan() }
        }
    }

    override suspend fun route(
        from: GeoPoint,
        to: GeoPoint,
        mode: TravelMode,
        tripId: String?,
        fromPlaceId: String?,
        toPlaceId: String?,
        time: TransitTime?,
        categories: Set<PlaceCategory>,
    ): AppResult<RoutePlan, DataError.Network> {
        val transitTime = time.takeIf { mode == TravelMode.Transit }
        // The ends' places change the places along the way, so they're part of what was asked.
        val key = RouteKey(tripId, mode, listOf("from:$fromPlaceId" to from, "to:$toPlaceId" to to), categories)
        return cached(key) {
            api.route(
                from = from.query(),
                to = to.query(),
                mode = mode.value,
                excludeTripId = tripId,
                fromPlaceId = fromPlaceId,
                toPlaceId = toPlaceId,
                departAt = transitTime?.takeUnless { it.isArriveBy }?.at?.toString(),
                arriveBy = transitTime?.takeIf { it.isArriveBy }?.at?.toString(),
                categories = categories.query(),
            ).map { it.toRoutePlan() }
        }
    }

    override suspend fun bestOrder(tripId: String, day: TripDay, mode: TravelMode): AppResult<BestOrder, DataError.Network> {
        pending.awaitSent(tripId)
        return api.optimize(day.id, mode.value).map { result ->
            BestOrder(
                markerIds = result.markerIds,
                byTravelTime = result.mode == OptimizeResultDto.Mode.TRAVEL_TIME,
                savedMinutes = result.savedMinutes?.roundToInt(),
            )
        }
    }

    /**
     * Only real routes are kept: a straight-line estimate is asked for again next time. Public transport isn't kept here:
     * its answers depend on the time, and the map keeps them in its own memory (Shahin, 2026-11-15).
     */
    private suspend fun cached(key: RouteKey, load: suspend () -> AppResult<RoutePlan, DataError.Network>): AppResult<RoutePlan, DataError.Network> {
        if (key.mode == TravelMode.Transit) return load()
        lock.withLock { routes.remove(key)?.let { routes[key] = it; return AppResult.Success(it) } }
        val result = load()
        if (result is AppResult.Success && !result.data.isEstimated) {
            lock.withLock {
                routes[key] = result.data
                if (routes.size > MAX_ROUTES) routes.remove(routes.keys.first())
            }
        }
        return result
    }

    /** Forgets the routes, whose places along the way were for the account signed in. */
    suspend fun clear() = lock.withLock { routes.clear() }

    private data class RouteKey(val owner: String?, val mode: TravelMode, val points: List<Pair<String, GeoPoint>>, val categories: Set<PlaceCategory>)

    private fun GeoPoint.query() = "$lat,$lng"

    private val TravelMode.value: String
        get() = when (this) {
            TravelMode.Walking -> "walking"
            TravelMode.Cycling -> "cycling"
            TravelMode.Driving -> "driving"
            TravelMode.Transit -> "transit"
        }

    private fun RouteResponseDto.toRoutePlan() = RoutePlan(
        route = route?.let { route ->
            Route(
                points = decodePolyline(route.polyline),
                distanceMeters = route.distanceMeters.roundToInt(),
                durationSeconds = route.durationSeconds.roundToInt(),
                legs = route.legs.map { leg ->
                    RouteLeg(leg.fromMarkerId, leg.toMarkerId, leg.distanceMeters.roundToInt(), leg.durationSeconds.roundToInt(), leg.steps.orEmpty().map { it.toStep() })
                },
                departure = route.departure,
                arrival = route.arrival,
                transfers = route.transfers?.roundToInt() ?: 0,
                isTypicalTimes = route.typicalTimes == true,
            )
        },
        alongTheWay = alongTheWay.map {
            AlongTheWayPlace(it.place.toMapPlace(), it.distanceFromRouteMeters.roundToInt(), it.etaFromStartSeconds.roundToInt())
        },
        alongTheWayCounts = alongTheWayCounts.toCategoryCounts(),
        isEstimated = degraded,
        attribution = attribution,
    )

    private fun TransitStepDto.toStep() = TransitStep(
        isRide = kind == TransitStepDto.Kind.RIDE,
        vehicle = vehicle?.let {
            when (it) {
                TransitStepDto.Vehicle.TRAM -> TransitVehicle.Tram
                TransitStepDto.Vehicle.SUBWAY -> TransitVehicle.Subway
                TransitStepDto.Vehicle.BUS -> TransitVehicle.Bus
                TransitStepDto.Vehicle.TRAIN -> TransitVehicle.Train
                TransitStepDto.Vehicle.FERRY -> TransitVehicle.Ferry
                TransitStepDto.Vehicle.CABLE_CAR -> TransitVehicle.CableCar
                TransitStepDto.Vehicle.OTHER -> TransitVehicle.Other
            }
        },
        line = line?.let { TransitLine(it.name, it.color?.let(::argb), it.textColor?.let(::argb)) },
        headsign = headsign,
        from = from.toStop(),
        to = to.toStop(),
        stops = stops.roundToInt(),
        distanceMeters = distanceMeters.roundToInt(),
        durationSeconds = durationSeconds.roundToInt(),
        points = decodePolyline(polyline),
    )

    private fun TransitPlaceDto.toStop() = TransitStop(name, GeoPoint(lat, lng), time)

    /** "#A862A4" as an opaque ARGB colour; null when it isn't one. */
    private fun argb(hex: String): Long? = hex.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it }

    private companion object {
        /** A few trips' days in every mode. */
        const val MAX_ROUTES = 50
    }
}
