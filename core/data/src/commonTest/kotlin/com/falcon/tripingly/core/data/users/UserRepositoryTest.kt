package com.falcon.tripingly.core.data.users

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
import com.falcon.tripingly.core.network.users.createUsersApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserRepositoryTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0")
    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(body: String, status: HttpStatusCode = HttpStatusCode.OK) = DefaultUserRepository(
        createKtorfit(
            createHttpClient(
                engine = MockEngine { request ->
                    requests += request
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
                config = config,
                tokenStore = InMemoryTokenStore(),
                sessionEvents = SessionEvents(),
                languageTag = { "en" },
            ),
            config,
        ).createUsersApi(),
    )

    @Test
    fun search_asksWithoutTheAt_andMapsThePeople() = runTest {
        val repository = repository("""{"items":[$ANNA]}""")

        val result = repository.search(" @an")

        assertEquals(listOf(UserSummary(ANNA_ID, "anna", "Anna")), (result as AppResult.Success).data)
        assertEquals("/v1/users/search", requests.single().url.encodedPath)
        assertEquals("an", requests.single().url.parameters["q"])
    }

    @Test
    fun search_withOneLetter_returnsNothingWithoutAsking() = runTest {
        val repository = repository("""{"items":[$ANNA]}""")

        val result = repository.search("a")

        assertEquals(emptyList(), (result as AppResult.Success).data)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun friends_asksForTheFirstPage_andMapsThePeople() = runTest {
        val repository = repository("""{"items":[$ANNA],"nextCursor":"c2"}""")

        val result = repository.friends()

        assertEquals(listOf(UserSummary(ANNA_ID, "anna", "Anna")), (result as AppResult.Success).data)
        val request = requests.single()
        assertEquals("/v1/me/friends", request.url.encodedPath)
        assertEquals("${UserRepository.FRIENDS_PAGE_SIZE}", request.url.parameters["limit"])
        assertEquals(null, request.url.parameters["cursor"])
    }

    @Test
    fun friends_refused_returnsTheErrorCode() = runTest {
        val repository = repository(
            """{"error":{"code":"RATE_LIMITED","message":"Slow down","details":{}}}""",
            HttpStatusCode.TooManyRequests,
        )

        val error = (repository.friends() as AppResult.Error).error

        assertIs<DataError.Network.Api>(error)
        assertEquals("RATE_LIMITED", error.code)
    }

    private companion object {
        const val ANNA_ID = "22222222-2222-4222-8222-222222222222"
        const val ANNA = """{"id":"$ANNA_ID","username":"anna","displayName":"Anna"}"""
    }
}
