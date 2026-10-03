package com.falcon.tripingly.core.network

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.auth.AuthTokens
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiCallTest {

    @Serializable
    private data class Ping(val status: String)

    @Test
    fun request_sendsBaseUrlLanguageClientAndBearer() = runTest {
        val api = TestApi { json("""{"status":"ok"}""") }
        api.tokenStore.save(AuthTokens("access-1", "refresh-1"))

        val result = apiCall<Ping> { api.client.get("health") }

        assertEquals(AppResult.Success(Ping("ok")), result)
        val request = api.requests.single()
        assertEquals("https://api.test/v1/health", request.url.toString())
        assertEquals("de-AT", request.headers[HttpHeaders.AcceptLanguage])
        assertEquals("android/1.0", request.headers["X-Client"])
        assertEquals("access-1", request.bearer)
    }

    @Test
    fun authRoutes_goWithoutBearer() = runTest {
        val api = TestApi { json("""{"status":"ok"}""") }
        api.tokenStore.save(AuthTokens("access-1", "refresh-1"))

        apiCall<Ping> { api.client.post("auth/dev") }

        assertNull(api.requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun errorEnvelope_mapsToApiErrorWithFlattenedDetails() = runTest {
        val api = TestApi {
            apiError(
                HttpStatusCode.BadRequest,
                "VALIDATION_FAILED",
                """{"fields":{"title":["isLength"],"dates":["a","b"]}}""",
            )
        }

        val result = apiCall<Ping> { api.client.post("trips") }

        assertEquals(
            AppResult.Error(
                DataError.Network.Api(
                    status = 400,
                    code = "VALIDATION_FAILED",
                    message = "Localized text",
                    details = mapOf("fields.title" to "isLength", "fields.dates" to "a,b"),
                ),
            ),
            result,
        )
    }

    @Test
    fun unknownErrorCode_stillParses() = runTest {
        val api = TestApi { apiError(HttpStatusCode.UnprocessableEntity, "SOMETHING_NEW") }

        val result = apiCall<Ping> { api.client.post("trips") }

        assertEquals("SOMETHING_NEW", ((result as AppResult.Error).error as DataError.Network.Api).code)
    }

    @Test
    fun serverErrorWithoutEnvelope_mapsToServerError() = runTest {
        val api = TestApi { respondError(HttpStatusCode.BadGateway, "<html>") }

        assertEquals(AppResult.Error(DataError.Network.ServerError), apiCall<Ping> { api.client.get("trips") })
    }

    @Test
    fun unauthorized_mapsToUnauthorized() = runTest {
        val api = TestApi { apiError(HttpStatusCode.Unauthorized, "UNAUTHENTICATED") }

        assertEquals(AppResult.Error(DataError.Network.Unauthorized), apiCall<Ping> { api.client.get("me") })
    }

    @Test
    fun noContent_mapsToUnit() = runTest {
        val api = TestApi { respond("", HttpStatusCode.NoContent) }

        assertEquals(AppResult.Success(Unit), apiCall<Unit> { api.client.delete("trips/1") })
    }

    @Test
    fun unexpectedBody_mapsToSerialization() = runTest {
        val api = TestApi { json("""{"unexpected":true}""") }

        assertEquals(AppResult.Error(DataError.Network.Serialization), apiCall<Ping> { api.client.get("health") })
    }

    @Test
    fun connectionFailure_mapsToNoInternet() = runTest {
        val api = TestApi { throw IOException("offline") }

        assertEquals(AppResult.Error(DataError.Network.NoInternet), apiCall<Ping> { api.client.get("health") })
    }

    @Test
    fun timeout_mapsToRequestTimeout() = runTest {
        val api = TestApi { throw HttpRequestTimeoutException("https://api.test/v1/health", 15_000) }

        assertEquals(AppResult.Error(DataError.Network.RequestTimeout), apiCall<Ping> { api.client.get("health") })
    }

    @Test
    fun idempotencyKey_isSentAsHeader() = runTest {
        val api = TestApi { json("""{"status":"ok"}""", HttpStatusCode.Created) }
        val key = newIdempotencyKey()

        apiCall<Ping> { api.client.post("trips") { idempotencyKey(key) } }

        assertEquals(key, api.requests.single().headers[IDEMPOTENCY_KEY_HEADER])
    }
}
