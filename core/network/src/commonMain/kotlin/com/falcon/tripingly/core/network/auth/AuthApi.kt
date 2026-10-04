package com.falcon.tripingly.core.network.auth

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.model.AppleSignInDto
import com.falcon.tripingly.core.network.model.AuthTokensDto
import com.falcon.tripingly.core.network.model.DevSignInDto
import com.falcon.tripingly.core.network.model.GoogleSignInDto
import com.falcon.tripingly.core.network.model.LogoutDto
import com.falcon.tripingly.core.network.model.RefreshDto
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.ReqBuilder
import io.ktor.client.request.HttpRequestBuilder

/** The `/auth` routes. They are sent without an access token. */
interface AuthApi {
    @POST("auth/google")
    @Headers("Content-Type: application/json")
    suspend fun signInWithGoogle(@Body body: GoogleSignInDto): AppResult<AuthTokensDto, DataError.Network>

    @POST("auth/apple")
    @Headers("Content-Type: application/json")
    suspend fun signInWithApple(@Body body: AppleSignInDto): AppResult<AuthTokensDto, DataError.Network>

    /** Local and staging only; the server answers 404 elsewhere. */
    @POST("auth/dev")
    @Headers("Content-Type: application/json")
    suspend fun signInForDevelopment(
        @Body body: DevSignInDto,
        @Header(DEV_AUTH_SECRET_HEADER) secret: String?,
    ): AppResult<AuthTokensDto, DataError.Network>

    @POST("auth/refresh")
    @Headers("Content-Type: application/json")
    suspend fun refresh(
        @Body body: RefreshDto,
        @ReqBuilder builder: HttpRequestBuilder.() -> Unit,
    ): AppResult<AuthTokensDto, DataError.Network>

    /** Always `204`, also for an unknown token. */
    @POST("auth/logout")
    @Headers("Content-Type: application/json")
    suspend fun logout(@Body body: LogoutDto): AppResult<Unit, DataError.Network>
}

const val DEV_AUTH_SECRET_HEADER = "X-Dev-Auth-Secret"
