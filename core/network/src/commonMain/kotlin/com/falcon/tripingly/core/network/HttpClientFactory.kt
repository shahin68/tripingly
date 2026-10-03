package com.falcon.tripingly.core.network

import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.auth.TokenRefresher
import com.falcon.tripingly.core.network.auth.TokenStore
import com.falcon.tripingly.core.network.auth.toBearer
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

private const val TIMEOUT_MILLIS = 15_000L

/**
 * The app's one HTTP client for the Tripinly API. Paths are relative to
 * [ApiConfig.baseUrl] (`client.get("trips")`). Adds the bearer token (except on
 * the auth routes), `Accept-Language` and `X-Client`, refreshes expired tokens once,
 * and logs only method, path and status.
 */
fun createHttpClient(
    engine: HttpClientEngine,
    config: ApiConfig,
    tokenStore: TokenStore,
    sessionEvents: SessionEvents,
    languageTag: () -> String,
    log: (String) -> Unit = ::println,
): HttpClient {
    val refresher = TokenRefresher(tokenStore, sessionEvents)
    return HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(NetworkJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MILLIS
            connectTimeoutMillis = TIMEOUT_MILLIS
            socketTimeoutMillis = TIMEOUT_MILLIS
        }
        defaultRequest {
            url("${config.baseUrl}/")
            header(HttpHeaders.AcceptLanguage, languageTag())
            header(CLIENT_HEADER, config.client)
        }
        install(Auth) {
            bearer {
                loadTokens { tokenStore.get()?.toBearer() }
                refreshTokens {
                    refresher.refresh(client, response) { markAsRefreshTokenRequest() }
                }
                sendWithoutRequest { request -> "auth" !in request.url.pathSegments }
            }
        }
        if (config.logRequests) {
            install(
                createClientPlugin("RequestLog") {
                    onResponse { response ->
                        // Path only: query strings can hold locations, never log them.
                        log("${response.request.method.value} ${response.request.url.encodedPath} → ${response.status.value}")
                    }
                },
            )
        }
    }
}

internal const val CLIENT_HEADER = "X-Client"
