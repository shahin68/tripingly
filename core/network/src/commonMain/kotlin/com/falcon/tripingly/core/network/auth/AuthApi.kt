package com.falcon.tripingly.core.network.auth

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.model.AuthTokensDto
import com.falcon.tripingly.core.network.model.RefreshDto
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.ReqBuilder
import io.ktor.client.request.HttpRequestBuilder

/** The `/auth` routes. They are sent without an access token. */
interface AuthApi {
    @POST("auth/refresh")
    @Headers("Content-Type: application/json")
    suspend fun refresh(
        @Body body: RefreshDto,
        @ReqBuilder builder: HttpRequestBuilder.() -> Unit,
    ): AppResult<AuthTokensDto, DataError.Network>
}
