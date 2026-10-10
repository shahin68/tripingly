package com.falcon.tripingly.core.network

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.auth.AuthTokens
import com.falcon.tripingly.core.network.auth.SessionEndReason
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenRefreshTest {

    @Serializable
    private data class Trip(val id: String)

    private val newTokensJson = """
        {"accessToken":"access-2","accessTokenExpiresAt":"2026-10-03T16:00:00Z",
         "refreshToken":"refresh-2","refreshTokenExpiresAt":"2026-12-02T16:00:00Z",
         "onboardingRequired":false}
    """.trimIndent()

    /** /trips accepts only access-2; /auth/refresh hands out access-2 for refresh-1. */
    private fun api(refresh: suspend io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData) =
        TestApi { request ->
            when (request.path) {
                "/v1/auth/refresh" -> refresh()
                else -> if (request.bearer == "access-2") {
                    json("""{"id":"t1"}""")
                } else {
                    apiError(HttpStatusCode.Unauthorized, "TOKEN_EXPIRED")
                }
            }
        }

    private val TestApi.refreshCalls get() = requests.count { it.path == "/v1/auth/refresh" }

    @Test
    fun expiredToken_isRefreshedOnceAndTheRequestRetried() = runTest {
        val api = api { json(newTokensJson) }
        api.tokenStore.save(AuthTokens("access-1", "refresh-1"))

        val result = apiCall<Trip> { api.client.get("trips/t1") }

        assertEquals(AppResult.Success(Trip("t1")), result)
        assertEquals(1, api.refreshCalls)
        assertEquals(AuthTokens("access-2", "refresh-2"), api.tokenStore.get())
        val refresh = api.requests.first { it.path == "/v1/auth/refresh" }
        assertNull(refresh.bearer)
        assertEquals("""{"refreshToken":"refresh-1"}""", (refresh.body as TextContent).text)
    }

    @Test
    fun concurrentExpiredRequests_shareOneRefresh() = runTest {
        val api = api { json(newTokensJson) }
        api.tokenStore.save(AuthTokens("access-1", "refresh-1"))

        val results = List(5) { async { apiCall<Trip> { api.client.get("trips/t1") } } }.awaitAll()

        results.forEach { assertEquals(AppResult.Success(Trip("t1")), it) }
        assertEquals(1, api.refreshCalls)
    }

    @Test
    fun refreshFinishesAndIsSaved_whenTheRequestThatStartedItIsCancelled() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        val answerRefresh = CompletableDeferred<Unit>()
        val api = api {
            refreshStarted.complete(Unit)
            answerRefresh.await()
            json(newTokensJson)
        }
        api.tokenStore.save(AuthTokens("access-1", "refresh-1"))

        val request = launch(Dispatchers.Default) { apiCall<Trip> { api.client.get("trips/t1") } }
        refreshStarted.await()
        request.cancelAndJoin()
        answerRefresh.complete(Unit)

        // The server rotated the token, so the new one is kept.
        // The refresh runs on the network's threads, so wait in real time.
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (api.tokenStore.get() != AuthTokens("access-2", "refresh-2")) delay(10)
            }
        }
        // The next request uses it without another refresh.
        assertEquals(AppResult.Success(Trip("t1")), apiCall<Trip> { api.client.get("trips/t1") })
        assertEquals(1, api.refreshCalls)
    }

    @Test
    fun refusedRefresh_clearsTokensAndEndsTheSession() = runTest {
        val api = api { apiError(HttpStatusCode.Unauthorized, "REFRESH_TOKEN_REUSED") }
        api.tokenStore.save(AuthTokens("access-1", "refresh-1"))
        // Subscribe before the call: the events have no replay.
        val ended = async(start = CoroutineStart.UNDISPATCHED) { api.sessionEvents.sessionEnded.first() }

        val result = apiCall<Trip> { api.client.get("trips/t1") }

        assertEquals(AppResult.Error(DataError.Network.Unauthorized), result)
        assertNull(api.tokenStore.get())
        assertEquals(SessionEndReason.REFRESH_REFUSED, ended.await())
    }

    @Test
    fun suspendedAccount_endsTheSessionWithItsReason() = runTest {
        val api = api { apiError(HttpStatusCode.Forbidden, "ACCOUNT_SUSPENDED") }
        api.tokenStore.save(AuthTokens("access-1", "refresh-1"))
        // Subscribe before the call: the events have no replay.
        val ended = async(start = CoroutineStart.UNDISPATCHED) { api.sessionEvents.sessionEnded.first() }

        apiCall<Trip> { api.client.get("trips/t1") }

        assertEquals(SessionEndReason.ACCOUNT_SUSPENDED, ended.await())
    }

    @Test
    fun refreshFailingOnTheServer_keepsTheTokens() = runTest {
        val api = api { apiError(HttpStatusCode.ServiceUnavailable, "SERVICE_UNAVAILABLE") }
        api.tokenStore.save(AuthTokens("access-1", "refresh-1"))

        apiCall<Trip> { api.client.get("trips/t1") }

        assertEquals(AuthTokens("access-1", "refresh-1"), api.tokenStore.get())
    }

    @Test
    fun otherUnauthorizedCodes_doNotRefresh() = runTest {
        val api = TestApi { request ->
            if (request.path == "/v1/auth/refresh") json(newTokensJson)
            else apiError(HttpStatusCode.Unauthorized, "UNAUTHENTICATED")
        }
        api.tokenStore.save(AuthTokens("access-1", "refresh-1"))

        val result = apiCall<Trip> { api.client.get("trips/t1") }

        assertEquals(AppResult.Error(DataError.Network.Unauthorized), result)
        assertEquals(0, api.refreshCalls)
    }

    @Test
    fun anotherAccountsTokens_areSentRightAway() = runTest {
        val api = TestApi { json("""{"id":"t1"}""") }
        api.tokenStore.save(AuthTokens("access-a", "refresh-a"))
        apiCall<Trip> { api.client.get("trips/t1") }

        // Sign-out, then sign-in with another account.
        api.tokenStore.clear()
        apiCall<Trip> { api.client.get("trips/t1") }
        api.tokenStore.save(AuthTokens("access-b", "refresh-b"))
        apiCall<Trip> { api.client.get("trips/t1") }

        assertEquals(listOf("access-a", null, "access-b"), api.requests.map { it.bearer })
    }
}
