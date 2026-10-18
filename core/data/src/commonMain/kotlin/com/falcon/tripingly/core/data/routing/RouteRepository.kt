package com.falcon.tripingly.core.data.routing

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.route.BestOrder
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.TripDay

/**
 * Routes and the places along them. A route is asked for once: the same day with the same stops, or the same
 * two points, is answered from memory while the app runs. Fails with `ROUTING_UNAVAILABLE` when the server
 * can't route right now ([RouteErrorCodes]).
 */
interface RouteRepository {
    /**
     * Through [day]'s stops in their order. Waits until the trip's own stop changes have reached the server,
     * which routes the stops it has.
     */
    suspend fun dayRoute(tripId: String, day: TripDay, mode: TravelMode): AppResult<RoutePlan, DataError.Network>

    /** From [from] to [to]; the places along the way leave out [tripId]'s own. */
    suspend fun route(from: GeoPoint, to: GeoPoint, mode: TravelMode, tripId: String?): AppResult<RoutePlan, DataError.Network>

    /** The best order for [day]'s stops, the first one staying first; only proposed, not saved. */
    suspend fun bestOrder(tripId: String, day: TripDay, mode: TravelMode): AppResult<BestOrder, DataError.Network>
}

object RouteErrorCodes {
    /** Routing failed on the server; the app draws straight lines between the stops. */
    const val ROUTING_UNAVAILABLE = "ROUTING_UNAVAILABLE"
}
