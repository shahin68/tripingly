package com.falcon.tripingly.core.data.routing

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.route.BestOrder
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.route.TransitTime
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.TripDay
import kotlin.time.Instant

/**
 * Routes and the places along them. A route is asked for once: the same day with the same stops, or the same
 * two points, is answered from memory while the app runs. Fails with `ROUTING_UNAVAILABLE` when the server
 * can't route right now ([RouteErrorCodes]).
 */
interface RouteRepository {
    /**
     * Through [day]'s stops in their order. Waits until the trip's own stop changes have reached the server,
     * which routes the stops it has. By public transport the first leg leaves at [departAt] (null: now). The places along
     * the way are only of [categories] (empty: every kind); [RoutePlan.alongTheWayCounts] always counts every kind.
     */
    suspend fun dayRoute(tripId: String, day: TripDay, mode: TravelMode, departAt: Instant? = null, categories: Set<PlaceCategory> = emptySet()): AppResult<RoutePlan, DataError.Network>

    /**
     * From [from] to [to]; the places along the way leave out [tripId]'s own and the places at the ends ([fromPlaceId],
     * [toPlaceId]: null for where I am, an address or a stop). By public transport it leaves or arrives at [time] (null: now).
     * The places along the way are only of [categories] (empty: every kind).
     */
    suspend fun route(
        from: GeoPoint,
        to: GeoPoint,
        mode: TravelMode,
        tripId: String?,
        fromPlaceId: String?,
        toPlaceId: String?,
        time: TransitTime? = null,
        categories: Set<PlaceCategory> = emptySet(),
    ): AppResult<RoutePlan, DataError.Network>

    /** The best order for [day]'s stops, the first one staying first; only proposed, not saved. */
    suspend fun bestOrder(tripId: String, day: TripDay, mode: TravelMode): AppResult<BestOrder, DataError.Network>
}

object RouteErrorCodes {
    /** Routing failed on the server; the app keeps its own lines between the stops. */
    const val ROUTING_UNAVAILABLE = "ROUTING_UNAVAILABLE"

    /** Routes are Tripinly Pro and the user doesn't have it (the app thought otherwise). */
    const val PREMIUM_REQUIRED = "PREMIUM_REQUIRED"

    /** Public transport isn't available between these points (outside the covered cities, or switched off). */
    const val TRANSIT_NO_COVERAGE = "TRANSIT_NO_COVERAGE"

    /** No public transport at that time and too far to walk. */
    const val TRANSIT_NO_CONNECTION = "TRANSIT_NO_CONNECTION"
}
