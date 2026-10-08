package com.falcon.tripingly.core.network.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.IDEMPOTENCY_KEY_HEADER
import com.falcon.tripingly.core.network.model.InviteDto
import com.falcon.tripingly.core.network.model.InvitePreviewDto
import com.falcon.tripingly.core.network.model.InvitesDto
import com.falcon.tripingly.core.network.model.TripDto
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path

/** Invite links: the owner creates and revokes them; anyone signed in can preview and accept one. */
interface InvitesApi {
    @POST("trips/{id}/invites")
    suspend fun create(
        @Path("id") tripId: String,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
    ): AppResult<InviteDto, DataError.Network>

    /** The trip's active invite links. */
    @GET("trips/{id}/invites")
    suspend fun list(@Path("id") tripId: String): AppResult<InvitesDto, DataError.Network>

    @DELETE("trips/{id}/invites/{inviteId}")
    suspend fun revoke(
        @Path("id") tripId: String,
        @Path("inviteId") inviteId: String,
    ): AppResult<Unit, DataError.Network>

    @GET("invites/{token}")
    suspend fun preview(@Path("token") token: String): AppResult<InvitePreviewDto, DataError.Network>

    /** Joins the trip as an editor; returns the trip. */
    @POST("invites/{token}/accept")
    suspend fun accept(@Path("token") token: String): AppResult<TripDto, DataError.Network>
}
