package com.falcon.tripingly.core.data.social

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.trips.InMemoryTripLocalDataSource
import com.falcon.tripingly.core.data.trips.TripJson
import com.falcon.tripingly.core.data.trips.toDetails
import com.falcon.tripingly.core.model.social.CommentChange
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.NetworkJson
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
import com.falcon.tripingly.core.network.model.CommentDto
import com.falcon.tripingly.core.network.model.TripDto
import com.falcon.tripingly.core.network.realtime.FakeRealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import com.falcon.tripingly.core.network.social.createSocialApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SocialRepositoryTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0")
    private val requests = mutableListOf<HttpRequestData>()
    private val local = InMemoryTripLocalDataSource()

    private fun repository(body: String, status: HttpStatusCode = HttpStatusCode.OK) = DefaultSocialRepository(
        api = createKtorfit(
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
        ).createSocialApi(),
        local = local,
        realtime = FakeRealtimeClient(),
        currentUserId = { ME },
    )

    private suspend fun cacheTrip() = local.saveTrip(
        NetworkJson.decodeFromString<TripDto>(
            TripJson.trip("t1", days = listOf(TripJson.day("d0", 0, "2026-06-01", TripJson.marker("m0", "t1", "d0", 0)))),
        ).toDetails(),
    )

    private fun HttpRequestData.bodyText() = (body as TextContent).text

    @Test
    fun likingAStop_likesItsPlace_andSavesTheServersCount_inTheCachedTrip() = runTest {
        cacheTrip()
        val repository = repository("""{"targetType":"place","targetId":"$PLACE","liked":true,"likeCount":3}""")

        val state = (repository.setLiked(LikeTarget.Place, PLACE, liked = true) as AppResult.Success).data

        assertEquals(3, state.likeCount)
        assertEquals("PUT /v1/likes/place/$PLACE", requests.single().let { "${it.method.value} ${it.url.encodedPath}" })
        val stop = local.marker("m0")!!
        assertEquals(3, stop.likeCount)
        assertTrue(stop.likedByMe)
    }

    @Test
    fun likingACommonsImage_usesThePlaceImageTarget() = runTest {
        val repository = repository("""{"targetType":"place_image","targetId":"i1","liked":true,"likeCount":1}""")

        repository.setLiked(LikeTarget.PlaceImage, "i1", liked = true)

        assertEquals("PUT /v1/likes/place_image/i1", requests.single().let { "${it.method.value} ${it.url.encodedPath}" })
    }

    @Test
    fun likingAStop_refused_putsTheHeartBack() = runTest {
        cacheTrip()
        val repository = repository(
            """{"error":{"code":"NOT_FOUND","message":"Gone","details":{}}}""",
            HttpStatusCode.NotFound,
        )

        val result = repository.setLiked(LikeTarget.Place, PLACE, liked = true)

        assertTrue(result is AppResult.Error)
        val stop = local.marker("m0")!!
        assertEquals(0, stop.likeCount)
        assertFalse(stop.likedByMe)
    }

    @Test
    fun addingAComment_countsItOnTheStop_andSendsAnIdempotencyKey() = runTest {
        cacheTrip()
        val repository = repository(comment(author = ME, canDelete = true))

        val comment = (repository.addComment("m0", "  Lovely  ") as AppResult.Success).data

        assertTrue(comment.canDelete)
        val request = requests.single()
        assertEquals("/v1/markers/m0/comments", request.url.encodedPath)
        assertEquals("""{"body":"Lovely"}""", request.bodyText())
        assertTrue(request.headers["Idempotency-Key"]!!.isNotBlank())
        assertEquals(1, local.marker("m0")!!.commentCount)
    }

    @Test
    fun report_sendsTheReasonAndLeavesOutBlankDetails() = runTest {
        val repository = repository(
            """{"id":"r1","targetType":"comment","targetId":"c1","reason":"spam","status":"open","createdAt":"2026-10-11T10:00:00Z"}""",
            HttpStatusCode.Created,
        )

        val result = repository.report(ReportTarget.Comment, "c1", ReportReason.Spam, details = "  ")

        assertTrue(result is AppResult.Success)
        assertEquals("""{"targetType":"comment","targetId":"c1","reason":"spam"}""", requests.single().bodyText())
    }

    @Test
    fun liveComments_leaveOutMyOwn() = runTest {
        val realtime = FakeRealtimeClient()
        val repository = DefaultSocialRepository(
            api = createKtorfit(createHttpClient(MockEngine { error("no request") }, config, InMemoryTokenStore(), SessionEvents(), languageTag = { "en" }), config).createSocialApi(),
            local = local,
            realtime = realtime,
            currentUserId = { ME },
        )
        val changes = mutableListOf<CommentChange>()
        val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.commentChanges.collect { changes += it }
        }

        realtime.send(RealtimeEvent.CommentAdded("t1", ME, NetworkJson.decodeFromString<CommentDto>(comment(author = ME))))
        realtime.send(RealtimeEvent.CommentAdded("t1", OTHER, NetworkJson.decodeFromString<CommentDto>(comment(author = OTHER))))
        realtime.send(RealtimeEvent.CommentDeleted("t1", OTHER, "c1", "m0"))
        collecting.cancel()

        assertEquals(2, changes.size)
        assertEquals(OTHER, (changes[0] as CommentChange.Added).comment.author.id)
        assertEquals(CommentChange.Deleted("m0", "c1"), changes[1])
    }

    private fun comment(author: String, canDelete: Boolean = false) =
        """{"id":"c1","markerId":"m0","body":"Lovely","author":{"id":"$author","username":"u","displayName":"U"},
            "likeCount":0,"likedByMe":false,"canDelete":$canDelete,"createdAt":"2026-10-11T10:00:00Z"}"""

    private companion object {
        const val ME = "11111111-1111-4111-8111-111111111111"
        const val OTHER = "33333333-3333-4333-8333-333333333333"

        /** The place of the cached trip's first stop (see [TripJson.marker]). */
        const val PLACE = "99999999-9999-4999-8999-999999999990"
    }
}
