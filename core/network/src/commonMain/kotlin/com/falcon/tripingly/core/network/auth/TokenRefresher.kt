package com.falcon.tripingly.core.network.auth

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.apiErrorCode
import com.falcon.tripingly.core.network.createKtorfit
import com.falcon.tripingly.core.network.model.RefreshDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlin.concurrent.Volatile

/**
 * Refreshes the access token after a `401 TOKEN_EXPIRED`. Ktor's bearer provider
 * runs one refresh at a time and makes concurrent requests wait for it; the check
 * against the stored token also covers a request that failed just after another
 * one refreshed, because sending a refresh token twice revokes the whole session.
 *
 * The refresh outlives the request that started it. Requests get cancelled all the time (the map
 * drops its places request when the camera moves on); once the server has rotated the token, the
 * new one must still be saved, or the next refresh sends the old one and the server signs the user
 * out. A request that comes while a refresh runs waits for that refresh instead of starting another.
 */
internal class TokenRefresher(
    private val config: ApiConfig,
    private val tokenStore: TokenStore,
    private val sessionEvents: SessionEvents,
) {
    // Built on first use from the client the bearer provider hands in (the app's own client).
    private var authApi: AuthApi? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Read and written under the bearer provider's lock, which runs one refresh call at a time.
    @Volatile
    private var running: Deferred<BearerTokens?>? = null

    suspend fun refresh(
        client: HttpClient,
        failed: HttpResponse,
        markAsRefreshRequest: HttpRequestBuilder.() -> Unit,
    ): BearerTokens? {
        if (failed.apiErrorCode() != TOKEN_EXPIRED) return null
        running?.takeIf { it.isActive }?.let { return it.await() }
        val stored = tokenStore.get() ?: return null
        val sentToken = failed.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
        if (sentToken != stored.accessToken) {
            // Another request already refreshed; retry with the new token.
            return stored.toBearer()
        }

        val api = authApi ?: createKtorfit(client, config).createAuthApi().also { authApi = it }
        val refresh = scope.async { refresh(api, stored, markAsRefreshRequest) }
        running = refresh
        return refresh.await()
    }

    private suspend fun refresh(
        api: AuthApi,
        stored: AuthTokens,
        markAsRefreshRequest: HttpRequestBuilder.() -> Unit,
    ): BearerTokens? {
        return when (val result = api.refresh(RefreshDto(refreshToken = stored.refreshToken), markAsRefreshRequest)) {
            is AppResult.Success -> {
                val tokens = AuthTokens(result.data.accessToken, result.data.refreshToken)
                tokenStore.save(tokens)
                tokens.toBearer()
            }
            is AppResult.Error -> {
                val error = result.error
                val refused = error is DataError.Network.Unauthorized ||
                    (error is DataError.Network.Api && error.status == FORBIDDEN)
                if (refused) {
                    tokenStore.clear()
                    sessionEvents.end(
                        if (error is DataError.Network.Api && error.code == ACCOUNT_SUSPENDED) {
                            SessionEndReason.ACCOUNT_SUSPENDED
                        } else {
                            SessionEndReason.REFRESH_REFUSED
                        },
                    )
                }
                // Offline, timed out or a server error: keep the tokens; the next call retries.
                null
            }
        }
    }

    private companion object {
        const val TOKEN_EXPIRED = "TOKEN_EXPIRED"
        const val ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED"
        const val FORBIDDEN = 403
    }
}

internal fun AuthTokens.toBearer() = BearerTokens(accessToken, refreshToken)
