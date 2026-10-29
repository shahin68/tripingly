package com.falcon.tripingly.core.network.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.IDEMPOTENCY_KEY_HEADER
import com.falcon.tripingly.core.network.model.AddMembersDto
import com.falcon.tripingly.core.network.model.CreateTripDto
import com.falcon.tripingly.core.network.model.JoinRequestDto
import com.falcon.tripingly.core.network.model.JoinRequestPageDto
import com.falcon.tripingly.core.network.model.TripDayDto
import com.falcon.tripingly.core.network.model.TripDto
import com.falcon.tripingly.core.network.model.TripMemberDto
import com.falcon.tripingly.core.network.model.TripMembersDto
import com.falcon.tripingly.core.network.model.TripSummaryPageDto
import com.falcon.tripingly.core.network.model.UpdateTripDto
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.PATCH
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query

/** Trips, their days and members. Access rules (owner, editor, viewer) are the server's. */
interface TripsApi {
    /** Owned, collaborating and saved trips, most recently updated first. */
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

    /** Saves someone else's public trip to My Trips (read-only, the live original). Idempotent. */
    @PUT("trips/{id}/save")
    suspend fun save(@Path("id") id: String): AppResult<Unit, DataError.Network>

    @DELETE("trips/{id}/save")
    suspend fun unsave(@Path("id") id: String): AppResult<Unit, DataError.Network>

    /** Asks the owner to join as an editor; `409 JOIN_REQUEST_DECLINED` once the owner said no. */
    @POST("trips/{id}/join-request")
    suspend fun requestJoin(@Path("id") id: String): AppResult<JoinRequestDto, DataError.Network>

    /** Withdraws my pending request. */
    @DELETE("trips/{id}/join-request")
    suspend fun cancelJoinRequest(@Path("id") id: String): AppResult<Unit, DataError.Network>

    /** The owner's pending requests, newest first. */
    @GET("trips/{id}/join-requests")
    suspend fun joinRequests(
        @Path("id") id: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): AppResult<JoinRequestPageDto, DataError.Network>

    /** The owner accepts: the person becomes an editor. */
    @POST("join-requests/{id}/accept")
    suspend fun acceptJoinRequest(@Path("id") requestId: String): AppResult<TripMemberDto, DataError.Network>

    @POST("join-requests/{id}/decline")
    suspend fun declineJoinRequest(@Path("id") requestId: String): AppResult<Unit, DataError.Network>

    /** Appends an empty day (the end date moves one day later). */
    @POST("trips/{id}/days")
    suspend fun addDay(
        @Path("id") tripId: String,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
    ): AppResult<TripDayDto, DataError.Network>

    /** Deletes the day and its markers; later days move up. [confirm] as for [MarkersApi.delete]. */
    @DELETE("days/{id}")
    suspend fun deleteDay(@Path("id") dayId: String, @Query("confirm") confirm: Boolean): AppResult<Unit, DataError.Network>

    /** The owner adds friends as editors, several at once; all or nothing. */
    @POST("trips/{id}/members/batch")
    @Headers("Content-Type: application/json")
    suspend fun addMembers(
        @Path("id") tripId: String,
        @Body body: AddMembersDto,
    ): AppResult<TripMembersDto, DataError.Network>

    /** The owner removes an editor, or an editor removes themselves (leaves). */
    @DELETE("trips/{id}/members/{userId}")
    suspend fun removeMember(
        @Path("id") tripId: String,
        @Path("userId") userId: String,
    ): AppResult<Unit, DataError.Network>
}
