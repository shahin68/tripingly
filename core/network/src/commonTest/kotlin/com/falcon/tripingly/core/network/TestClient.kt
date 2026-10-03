package com.falcon.tripingly.core.network

import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val testConfig = ApiConfig(
    baseUrl = "https://api.test/v1",
    environment = "test",
    client = "android/1.0",
    logRequests = false,
)

internal class TestApi(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val tokenStore = InMemoryTokenStore()
    val sessionEvents = SessionEvents()
    private val lock = Mutex()
    private val recorded = mutableListOf<HttpRequestData>()
    val requests: List<HttpRequestData> get() = recorded.toList()
    val client: HttpClient = createHttpClient(
        engine = MockEngine { request ->
            lock.withLock { recorded += request }
            handler(request)
        },
        config = testConfig,
        tokenStore = tokenStore,
        sessionEvents = sessionEvents,
        languageTag = { "de-AT" },
    )
}

internal fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

internal fun MockRequestHandleScope.apiError(status: HttpStatusCode, code: String, details: String = "{}") =
    json("""{"error":{"code":"$code","message":"Localized text","details":$details}}""", status)

internal val HttpRequestData.path: String get() = url.encodedPath
internal val HttpRequestData.bearer: String? get() = headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
