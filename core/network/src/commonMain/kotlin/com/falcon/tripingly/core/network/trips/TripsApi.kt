package com.falcon.tripingly.core.network.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.IDEMPOTENCY_KEY_HEADER
import com.falcon.tripingly.core.network.model.AddMemberDto
import com.falcon.tripingly.core.network.model.CreateTripDto
import com.falcon.tripingly.core.network.model.TripDayDto
import com.falcon.tripingly.core.network.model.TripDto
import com.falcon.tripingly.core.network.model.TripMemberDto
import com.falcon.tripingly.core.network.model.TripSummaryPageDto
import com.falcon.tripingly.core.network.model.UpdateTripDto
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.PATCH
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query

/** Trips, their days and members. Access rules (owner, editor, viewer) are the server's. */
interface TripsApi {
    /** Owned and collaborating trips, most recently updated first. */
    @GET("me/trips")
    suspend fun myTrips(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): AppResult<TripSummaryPageDto, DataError.Network>

    @POST("trips")
    @Headers("Content-Type: application/json")
    suspend fun create(
        @Body body: CreateTripDto,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
    ): AppResult<TripDto, DataError.Network>

    @GET("trips/{id}")
    suspend fun trip(@Path("id") id: String): AppResult<TripDto, DataError.Network>

    @PATCH("trips/{id}")
    @Headers("Content-Type: application/json")
    suspend fun update(@Path("id") id: String, @Body body: UpdateTripDto): AppResult<TripDto, DataError.Network>

    @DELETE("trips/{id}")
    suspend fun delete(@Path("id") id: String): AppResult<Unit, DataError.Network>

    /** "Add to my trips": an independent copy of someone else's public trip. */
    @POST("trips/{id}/copy")
    suspend fun copy(
        @Path("id") id: String,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
    ): AppResult<TripDto, DataError.Network>

    /** Appends an empty day (the end date moves one day later). */
    @POST("trips/{id}/days")
    suspend fun addDay(
        @Path("id") tripId: String,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
    ): AppResult<TripDayDto, DataError.Network>

    /** Deletes the day and its markers; later days move up. */
    @DELETE("days/{id}")
    suspend fun deleteDay(@Path("id") dayId: String): AppResult<Unit, DataError.Network>

    @POST("trips/{id}/members")
    @Headers("Content-Type: application/json")
    suspend fun addMember(
        @Path("id") tripId: String,
        @Body body: AddMemberDto,
    ): AppResult<TripMemberDto, DataError.Network>

    /** The owner removes an editor, or an editor removes themselves (leaves). */
    @DELETE("trips/{id}/members/{userId}")
    suspend fun removeMember(
        @Path("id") tripId: String,
        @Path("userId") userId: String,
    ): AppResult<Unit, DataError.Network>
}
