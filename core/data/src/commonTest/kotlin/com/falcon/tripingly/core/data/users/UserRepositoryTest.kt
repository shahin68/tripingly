package com.falcon.tripingly.core.data.users

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.social.PeopleList
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
import com.falcon.tripingly.core.network.realtime.FakeRealtimeClient
import com.falcon.tripingly.core.network.users.createUsersApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        FakeRealtimeClient(),
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

    @Test
    fun profile_asksWithoutTheAt_andMapsCountsAndTrips() = runTest {
        val repository = repository(
            """{"user":$ANNA,"followerCount":3,"followingCount":2,"isFollowing":true,"followsMe":false,"isFriend":false,
               "publicTripCount":1,"trips":[$TRIP],"nextCursor":null}""",
        )

        val profile = (repository.profile("@anna") as AppResult.Success).data

        assertEquals("/v1/users/anna", requests.single().url.encodedPath)
        assertEquals(3, profile.followerCount)
        assertTrue(profile.isFollowing)
        assertEquals("Vienna", profile.trips.single().trip.name)
        assertEquals(4, profile.trips.single().likeCount)
        assertEquals("ph1", profile.trips.single().trip.coverPhotoId)
    }

    @Test
    fun followAndBlock_useTheMatchingMethods() = runTest {
        val repository = repository("", HttpStatusCode.NoContent)

        repository.setFollowing(ANNA_ID, following = true)
        repository.setFollowing(ANNA_ID, following = false)
        repository.setBlocked(ANNA_ID, blocked = true)
        repository.setBlocked(ANNA_ID, blocked = false)

        assertEquals(
            listOf(
                "PUT /v1/users/$ANNA_ID/follow",
                "DELETE /v1/users/$ANNA_ID/follow",
                "POST /v1/users/$ANNA_ID/block",
                "DELETE /v1/users/$ANNA_ID/block",
            ),
            requests.map { "${it.method.value} ${it.url.encodedPath}" },
        )
    }

    @Test
    fun blocks_tellOnlyOfBlocksTheServerTook() = runTest {
        val blocked = mutableListOf<String>()
        val taken = repository("", HttpStatusCode.NoContent)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { taken.blocks.collect { blocked += it } }
        taken.setBlocked(ANNA_ID, blocked = true)
        taken.setBlocked(ANNA_ID, blocked = false)

        val refused = repository("""{"error":{"code":"RATE_LIMITED","message":"Slow down"}}""", HttpStatusCode.TooManyRequests)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { refused.blocks.collect { blocked += it } }
        refused.setBlocked(ANNA_ID, blocked = true)

        assertEquals(listOf(ANNA_ID), blocked)
    }

    @Test
    fun people_asksForTheList_withTheCursor() = runTest {
        val repository = repository("""{"items":[$ANNA],"nextCursor":"c3"}""")

        val page = (repository.people(PeopleList.Followers, cursor = "c2") as AppResult.Success).data

        assertEquals("/v1/me/followers", requests.single().url.encodedPath)
        assertEquals("c2", requests.single().url.parameters["cursor"])
        assertEquals("c3", page.nextCursor)
        assertEquals("anna", page.items.single().username)
    }

    private companion object {
        const val ANNA_ID = "22222222-2222-4222-8222-222222222222"
        const val ANNA = """{"id":"$ANNA_ID","username":"anna","displayName":"Anna"}"""
        const val TRIP = """{"id":"t1","title":"Vienna","startDate":null,"endDate":null,"visibility":"public","owner":$ANNA,
            "role":"viewer","coverPhotoId":"ph1","coverThumbUrl":"https://r2.test/ph1/thumb?sig=1","dayCount":1,"markerCount":2,"editorCount":0,"likeCount":4,"likedByMe":false,
            "saveCount":1,"savedByMe":false,"createdAt":"2026-10-11T09:00:00Z","updatedAt":"2026-10-11T10:00:00Z"}"""
    }
}
