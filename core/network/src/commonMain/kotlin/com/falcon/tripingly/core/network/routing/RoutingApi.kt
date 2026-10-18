package com.falcon.tripingly.core.network.routing

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.model.OptimizeResultDto
import com.falcon.tripingly.core.network.model.RouteResponseDto
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query

/**
 * Routes by openrouteservice, with places along the way. Free for everyone. `503 ROUTING_UNAVAILABLE` when
 * routing fails; straight lines with `degraded` when it's out of quota. [mode] is `walking`, `cycling` or `driving`.
 */
interface RoutingApi {
    /** From [from] to [to] (`lat,lng`); [excludeTripId]'s places are left out of the places along the way. */
    @GET("routes")
    suspend fun route(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("mode") mode: String,
        @Query("excludeTripId") excludeTripId: String?,
    ): AppResult<RouteResponseDto, DataError.Network>

    /** Through the day's stops in order; `route` is null for fewer than two stops. */
    @GET("days/{id}/route")
    suspend fun dayRoute(
        @Path("id") dayId: String,
        @Query("mode") mode: String,
    ): AppResult<RouteResponseDto, DataError.Network>

    /**
     * The best order for the day's stops, the first one staying first (up to 25): by real travel times with
     * Tripinly Pro, else by straight-line distance. Only proposed; the app saves the order itself.
     */
    @POST("days/{id}/optimize")
    suspend fun optimize(
        @Path("id") dayId: String,
        @Query("mode") mode: String,
    ): AppResult<OptimizeResultDto, DataError.Network>
}
