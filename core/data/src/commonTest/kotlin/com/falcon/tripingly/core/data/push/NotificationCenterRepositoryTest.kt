package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
import com.falcon.tripingly.core.network.push.NotificationEntryDto
import com.falcon.tripingly.core.network.push.createPushApi
import com.falcon.tripingly.core.network.realtime.FakeRealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class NotificationCenterRepositoryTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sent = MutableStateFlow<List<HttpRequestData>>(emptyList())
    private val realtime = FakeRealtimeClient()
    private var unseenCount = 2

    @AfterTest
    fun cancel() = scope.cancel()

    private fun repository(): DefaultNotificationCenterRepository {
        val engine = MockEngine { request ->
            sent.update { it + request }
            if (request.method == HttpMethod.Post) {
                respond("", HttpStatusCode.NoContent)
            } else {
                respond(
                    // A kind this app doesn't know reads like any other.
                    """{"items":[{"id":"n1","type":"some_new_kind","actor":{"id":"u1","username":"anna","displayName":"Anna"},"actors":[{"id":"u1","username":"anna","displayName":"Anna"},{"id":"u2","username":"jonas","displayName":"Jonas"}],"trip":null,"markerId":null,"aboutComments":false,"count":3,"title":"Anna and 2 others liked your photo","body":"Albertina","deepLink":"tripinly://notifications","read":false,"createdAt":"2026-11-07T10:00:00.000Z","updatedAt":"2026-11-07T10:00:00.000Z"}],"nextCursor":"c1","unreadCount":1,"unseenCount":$unseenCount}""",
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json"),
                )
            }
        }
        val api = createKtorfit(createHttpClient(engine, config, InMemoryTokenStore(), SessionEvents(), languageTag = { "en" }), config).createPushApi()
        return DefaultNotificationCenterRepository(api, realtime, scope)
    }

    @Test
    fun readsPagesWhateverTheirKind() = runTest {
        val page = repository().entries(cursor = null)

        assertIs<AppResult.Success<*>>(page)
        val entry = (page as AppResult.Success).data.items.single()
        assertEquals("anna", entry.actor?.username)
        assertEquals(listOf("anna", "jonas"), entry.actors.map { it.username })
        assertEquals("Anna and 2 others liked your photo", entry.title)
        assertEquals(Instant.parse("2026-11-07T10:00:00Z"), entry.at)
        assertEquals("c1", page.data.nextCursor)
        assertEquals("/v1/notifications", sent.value.single().url.encodedPath)
    }

    @Test
    fun theIndicatorFollowsTheServerArrivalsAndOpens() = runTest {
        val center = repository()
        center.start()
        realTime { center.hasUnseen.first { it } }

        center.markSeen()
        assertEquals(false, center.hasUnseen.value)
        realTime { sent.first { requests -> requests.any { it.url.encodedPath == "/v1/notifications/seen" } } }

        realtime.send(
            RealtimeEvent.NotificationCreated(
                tripId = null,
                markerId = null,
                entry = NotificationEntryDto(id = "n2", title = "t", body = "b", deepLink = "tripinly://notifications", createdAt = "2026-11-07T10:01:00Z"),
            ),
        )
        realTime { center.hasUnseen.first { it } }

        // After a reconnect the server decides again.
        unseenCount = 0
        realtime.connect()
        realTime { center.hasUnseen.first { !it } }
    }
}

private suspend fun <T> realTime(block: suspend () -> T): T =
    withContext(Dispatchers.Default) { withTimeout(10.seconds) { block() } }
