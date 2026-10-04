package com.falcon.tripingly.core.network.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.IDEMPOTENCY_KEY_HEADER
import com.falcon.tripingly.core.network.model.CopyMarkerDto
import com.falcon.tripingly.core.network.model.CreateMarkerDto
import com.falcon.tripingly.core.network.model.MarkerDto
import com.falcon.tripingly.core.network.model.MarkerOrderDto
import com.falcon.tripingly.core.network.model.MarkerOrderResultDto
import com.falcon.tripingly.core.network.model.UpdateMarkerDto
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.PATCH
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT
import de.jensklingenberg.ktorfit.http.Path

/** Markers (stops) on a trip's days. */
interface MarkersApi {
    @POST("days/{id}/markers")
    @Headers("Content-Type: application/json")
    suspend fun create(
        @Path("id") dayId: String,
        @Body body: CreateMarkerDto,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
    ): AppResult<MarkerDto, DataError.Network>

    @PATCH("markers/{id}")
    @Headers("Content-Type: application/json")
    suspend fun update(@Path("id") id: String, @Body body: UpdateMarkerDto): AppResult<MarkerDto, DataError.Network>

    @DELETE("markers/{id}")
    suspend fun delete(@Path("id") id: String): AppResult<Unit, DataError.Network>

    /** The day's complete marker list in the new order. */
    @PUT("days/{id}/marker-order")
    @Headers("Content-Type: application/json")
    suspend fun reorder(
        @Path("id") dayId: String,
        @Body body: MarkerOrderDto,
    ): AppResult<MarkerOrderResultDto, DataError.Network>

    /** Copies a marker into a day of one of the user's own trips. */
    @POST("markers/{id}/copy")
    @Headers("Content-Type: application/json")
    suspend fun copy(
        @Path("id") id: String,
        @Body body: CopyMarkerDto,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
    ): AppResult<MarkerDto, DataError.Network>
}
