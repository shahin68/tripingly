package com.falcon.tripingly.core.network.users

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.model.UserPageDto
import com.falcon.tripingly.core.network.model.UserSearchResultDto
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query

/** Other people: search by name and the signed-in user's friends. */
interface UsersApi {
    /** Up to 20 people whose username or display name starts with [query] (2–50 characters). */
    @GET("users/search")
    suspend fun search(@Query("q") query: String): AppResult<UserSearchResultDto, DataError.Network>

    @GET("me/friends")
    suspend fun friends(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): AppResult<UserPageDto, DataError.Network>
}

