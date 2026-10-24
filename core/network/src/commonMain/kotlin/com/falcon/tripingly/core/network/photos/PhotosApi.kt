package com.falcon.tripingly.core.network.photos

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.model.CoverDto
import com.falcon.tripingly.core.network.model.MarkerDto
import com.falcon.tripingly.core.network.model.PhotoDto
import com.falcon.tripingly.core.network.model.UploadUrlRequestDto
import com.falcon.tripingly.core.network.model.UploadUrlResponseDto
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT
import de.jensklingenberg.ktorfit.http.Path

/** Photos on markers and places. The file itself goes straight to storage, see [StorageClient]. */
interface PhotosApi {
    /** A new photo, waiting for its upload, and the signed URL to `PUT` the file to (10 minutes). */
    @POST("markers/{id}/photos/upload-url")
    @Headers("Content-Type: application/json")
    suspend fun uploadUrl(
        @Path("id") markerId: String,
        @Body body: UploadUrlRequestDto,
    ): AppResult<UploadUrlResponseDto, DataError.Network>

    /** Like [uploadUrl], for a photo on a place (no trip); at most the server's daily number per person. */
    @POST("places/{id}/photos/upload-url")
    @Headers("Content-Type: application/json")
    suspend fun placeUploadUrl(
        @Path("id") placeId: String,
        @Body body: UploadUrlRequestDto,
    ): AppResult<UploadUrlResponseDto, DataError.Network>

    /** The file is in storage: the server makes the thumbnail and display sizes. */
    @POST("photos/{id}/complete")
    suspend fun complete(@Path("id") id: String): AppResult<PhotoDto, DataError.Network>

    /**
     * The stop's photo grid: members' uploads still processing or failed first, then its photos, the place's popular
     * photos and its Commons images ranked by likes.
     */
    @GET("markers/{id}/photos")
    suspend fun photos(@Path("id") markerId: String): AppResult<List<PhotoDto>, DataError.Network>

    /** The place's photos, most liked first; my own processing and failed ones too. */
    @GET("places/{id}/photos")
    suspend fun placePhotos(@Path("id") placeId: String): AppResult<List<PhotoDto>, DataError.Network>

    @PUT("markers/{id}/cover")
    @Headers("Content-Type: application/json")
    suspend fun setCover(@Path("id") markerId: String, @Body body: CoverDto): AppResult<MarkerDto, DataError.Network>

    @DELETE("photos/{id}")
    suspend fun delete(@Path("id") id: String): AppResult<Unit, DataError.Network>
}
