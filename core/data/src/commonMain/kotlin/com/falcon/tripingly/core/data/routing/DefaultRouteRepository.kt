package com.falcon.tripingly.core.data.routing

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.data.places.toMapPlace
import com.falcon.tripingly.core.data.trips.PendingTripWrites
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.route.BestOrder
import com.falcon.tripingly.core.model.route.Route
import com.falcon.tripingly.core.model.route.RouteLeg
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.network.model.OptimizeResultDto
import com.falcon.tripingly.core.network.model.RouteResponseDto
import com.falcon.tripingly.core.network.routing.RoutingApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

internal class DefaultRouteRepository(
    private val api: RoutingApi,
    private val pending: PendingTripWrites,
) : RouteRepository {

    private val lock = Mutex()

    /** Answered routes by what was asked, least recently used first. */
    private val routes = LinkedHashMap<RouteKey, RoutePlan>()

    override suspend fun dayRoute(tripId: String, day: TripDay, mode: TravelMode): AppResult<RoutePlan, DataError.Network> {
        val key = RouteKey(day.id, mode, day.markers.map { it.id to it.location })
        return cached(key) {
            pending.awaitSent(tripId)
            api.dayRoute(day.id, mode.value).map { it.toRoutePlan() }
        }
    }

    override suspend fun route(from: GeoPoint, to: GeoPoint, mode: TravelMode, tripId: String?): AppResult<RoutePlan, DataError.Network> {
        val key = RouteKey(tripId, mode, listOf("from" to from, "to" to to))
        return cached(key) {
            api.route(from.query(), to.query(), mode.value, tripId).map { it.toRoutePlan() }
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

    /** Only real routes are kept: a straight-line estimate is asked for again next time. */
    private suspend fun cached(key: RouteKey, load: suspend () -> AppResult<RoutePlan, DataError.Network>): AppResult<RoutePlan, DataError.Network> {
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

    private data class RouteKey(val owner: String?, val mode: TravelMode, val points: List<Pair<String, GeoPoint>>)

    private fun GeoPoint.query() = "$lat,$lng"

    private val TravelMode.value: String
        get() = when (this) {
            TravelMode.Walking -> "walking"
            TravelMode.Cycling -> "cycling"
            TravelMode.Driving -> "driving"
        }

    private fun RouteResponseDto.toRoutePlan() = RoutePlan(
        route = route?.let { route ->
            Route(
                points = decodePolyline(route.polyline),
                distanceMeters = route.distanceMeters.roundToInt(),
                durationSeconds = route.durationSeconds.roundToInt(),
                legs = route.legs.map { RouteLeg(it.fromMarkerId, it.toMarkerId, it.distanceMeters.roundToInt(), it.durationSeconds.roundToInt()) },
            )
        },
        alongTheWay = alongTheWay.map {
            AlongTheWayPlace(it.place.toMapPlace(), it.distanceFromRouteMeters.roundToInt(), it.etaFromStartSeconds.roundToInt())
        },
        isEstimated = degraded,
        attribution = attribution,
    )

    private companion object {
        /** A few trips' days in every mode. */
        const val MAX_ROUTES = 50
    }
}
