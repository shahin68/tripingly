package com.falcon.tripingly.core.network.social

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.IDEMPOTENCY_KEY_HEADER
import com.falcon.tripingly.core.network.model.CommentDto
import com.falcon.tripingly.core.network.model.CommentPageDto
import com.falcon.tripingly.core.network.model.CreateCommentDto
import com.falcon.tripingly.core.network.model.CreateReportDto
import com.falcon.tripingly.core.network.model.HotStateDto
import com.falcon.tripingly.core.network.model.LikeStateDto
import com.falcon.tripingly.core.network.model.ReportDto
import com.falcon.tripingly.core.network.model.TripSummaryPageDto
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query

/** Explore, comments, likes and reports. */
interface SocialApi {
    /**
     * Public trips, mine included, most liked first. [period]: `week`, `month`, `year`, `season` or `all`;
     * [lat] and [lng] together keep trips with a stop nearby.
     */
    @GET("explore/trips")
    suspend fun explore(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
        @Query("period") period: String,
        @Query("lat") lat: Double?,
        @Query("lng") lng: Double?,
    ): AppResult<TripSummaryPageDto, DataError.Network>

    /** Oldest first; comments by people with a block with me are left out. */
    @GET("markers/{id}/comments")
    suspend fun comments(
        @Path("id") markerId: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): AppResult<CommentPageDto, DataError.Network>

    @POST("markers/{id}/comments")
    @Headers("Content-Type: application/json")
    suspend fun addComment(
        @Path("id") markerId: String,
        @Body body: CreateCommentDto,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
    ): AppResult<CommentDto, DataError.Network>

    /** The author or the trip's owner. */
    @DELETE("comments/{id}")
    suspend fun deleteComment(@Path("id") commentId: String): AppResult<Unit, DataError.Network>

    /** [targetType]: `trip`, `photo`, `comment` or `place_image`. Idempotent. */
    @PUT("likes/{targetType}/{targetId}")
    suspend fun like(
        @Path("targetType") targetType: String,
        @Path("targetId") targetId: String,
    ): AppResult<LikeStateDto, DataError.Network>

    @DELETE("likes/{targetType}/{targetId}")
    suspend fun unlike(
        @Path("targetType") targetType: String,
        @Path("targetId") targetId: String,
    ): AppResult<LikeStateDto, DataError.Network>

    /** A stop's flame marks its place. Idempotent. */
    @PUT("places/{id}/hot")
    suspend fun markHot(@Path("id") placeId: String): AppResult<HotStateDto, DataError.Network>

    @DELETE("places/{id}/hot")
    suspend fun unmarkHot(@Path("id") placeId: String): AppResult<HotStateDto, DataError.Network>

    /** An open report by me on the same target is answered again (200). 20 a day. */
    @POST("reports")
    @Headers("Content-Type: application/json")
    suspend fun report(@Body body: CreateReportDto): AppResult<ReportDto, DataError.Network>
}
