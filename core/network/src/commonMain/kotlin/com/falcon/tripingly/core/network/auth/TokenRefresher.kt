package com.falcon.tripingly.core.network.auth

import com.falcon.tripingly.core.network.apiErrorCode
import com.falcon.tripingly.core.network.model.AuthTokensDto
import com.falcon.tripingly.core.network.model.RefreshDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * Refreshes the access token after a `401 TOKEN_EXPIRED`. Ktor's bearer provider
 * runs one refresh at a time and makes concurrent requests wait for it; the check
 * against the stored token also covers a request that failed just after another
 * one refreshed, because sending a refresh token twice revokes the whole session.
 */
internal class TokenRefresher(
    private val tokenStore: TokenStore,
    private val sessionEvents: SessionEvents,
) {
    suspend fun refresh(
        client: HttpClient,
        failed: HttpResponse,
        markAsRefreshRequest: HttpRequestBuilder.() -> Unit,
    ): BearerTokens? {
        if (failed.apiErrorCode() != TOKEN_EXPIRED) return null
        val stored = tokenStore.get() ?: return null
        val sentToken = failed.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
        if (sentToken != stored.accessToken) {
            // Another request already refreshed; retry with the new token.
            return stored.toBearer()
        }

        val response = try {
            client.post("auth/refresh") {
                markAsRefreshRequest()
                contentType(ContentType.Application.Json)
                setBody(RefreshDto(refreshToken = stored.refreshToken))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline or timed out: keep the tokens and let the call fail; the next one retries.
            return null
        }

        if (response.status.isSuccess()) {
            val dto = response.body<AuthTokensDto>()
            val tokens = AuthTokens(dto.accessToken, dto.refreshToken)
            tokenStore.save(tokens)
            return tokens.toBearer()
        }
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            tokenStore.clear()
            sessionEvents.end(
                if (response.apiErrorCode() == ACCOUNT_SUSPENDED) {
                    SessionEndReason.ACCOUNT_SUSPENDED
                } else {
                    SessionEndReason.REFRESH_REFUSED
                },
            )
        }
        return null
    }

    private companion object {
        const val TOKEN_EXPIRED = "TOKEN_EXPIRED"
        const val ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED"
    }
}

internal fun AuthTokens.toBearer() = BearerTokens(accessToken, refreshToken)
