package com.falcon.tripingly.core.network.users

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.model.BlockedUsersDto
import com.falcon.tripingly.core.network.model.ProfileDto
import com.falcon.tripingly.core.network.model.UserPageDto
import com.falcon.tripingly.core.network.model.UserSearchResultDto
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query

/** Other people: search, profiles, follows and blocks. */
interface UsersApi {
    /** Up to 20 people whose username or display name starts with [query] (2–50 characters). */
    @GET("users/search")
    suspend fun search(@Query("q") query: String): AppResult<UserSearchResultDto, DataError.Network>

    /** A profile with a page of the person's public trips. Unknown, or a block either way: 404. */
    @GET("users/{username}")
    suspend fun profile(
        @Path("username") username: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): AppResult<ProfileDto, DataError.Network>

    /** Idempotent. */
    @PUT("users/{id}/follow")
    suspend fun follow(@Path("id") userId: String): AppResult<Unit, DataError.Network>

    /** Idempotent. */
    @DELETE("users/{id}/follow")
    suspend fun unfollow(@Path("id") userId: String): AppResult<Unit, DataError.Network>

    /** Also removes follows both ways and each of us from the other's trips. Idempotent. */
    @POST("users/{id}/block")
    suspend fun block(@Path("id") userId: String): AppResult<Unit, DataError.Network>

    @DELETE("users/{id}/block")
    suspend fun unblock(@Path("id") userId: String): AppResult<Unit, DataError.Network>

    /** People I blocked, most recently blocked first. */
    @GET("me/blocks")
    suspend fun blocked(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): AppResult<BlockedUsersDto, DataError.Network>

    /** People I follow who follow me back. */
    @GET("me/friends")
    suspend fun friends(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): AppResult<UserPageDto, DataError.Network>

    /** People I follow, most recently followed first. */
    @GET("me/following")
    suspend fun following(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): AppResult<UserPageDto, DataError.Network>

    /** People who follow me, most recent first. */
    @GET("me/followers")
    suspend fun followers(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): AppResult<UserPageDto, DataError.Network>
}
