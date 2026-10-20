package com.falcon.tripingly.core.data.realtime

import com.falcon.tripingly.core.data.config.FakeAppConfigRepository
import com.falcon.tripingly.core.data.photos.DefaultPhotoRepository
import com.falcon.tripingly.core.data.photos.PhotoPreparer
import com.falcon.tripingly.core.data.social.FakeSocialRepository
import com.falcon.tripingly.core.data.trips.FakeTripMapViewRepository
import com.falcon.tripingly.core.data.trips.InMemoryTripLocalDataSource
import com.falcon.tripingly.core.data.trips.OfflineFirstTripRepository
import com.falcon.tripingly.core.data.trips.PendingTripWrites
import com.falcon.tripingly.core.data.trips.TripJson
import com.falcon.tripingly.core.data.trips.toDetails
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.NetworkJson
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
import com.falcon.tripingly.core.network.model.CommentDto
import com.falcon.tripingly.core.network.model.MarkerDto
import com.falcon.tripingly.core.network.model.TripDto
import com.falcon.tripingly.core.network.photos.StorageClient
import com.falcon.tripingly.core.network.photos.createPhotosApi
import com.falcon.tripingly.core.network.realtime.FakeRealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import com.falcon.tripingly.core.network.trips.createMarkersApi
import com.falcon.tripingly.core.network.trips.createTripsApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RealtimeSyncTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sent = MutableStateFlow<List<HttpRequestData>>(emptyList())
    private val paths get() = sent.value.map { it.url.encodedPath }
    private val local = InMemoryTripLocalDataSource()
    private val pending = PendingTripWrites()
    private val realtime = FakeRealtimeClient()

    @AfterTest
    fun cancel() = scope.cancel()

    private fun sync(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): RealtimeSync {
        val engine = MockEngine { request ->
            sent.update { it + request }
            handler(request)
        }
        val ktorfit = createKtorfit(createHttpClient(engine, config, InMemoryTokenStore(), SessionEvents(), languageTag = { "en" }), config)
        val trips = OfflineFirstTripRepository(ktorfit.createTripsApi(), local, pending, realtime, FakeTripMapViewRepository(), currentUserId = { ME })
        val photos = DefaultPhotoRepository(
            api = ktorfit.createPhotosApi(),
            markersApi = ktorfit.createMarkersApi(),
            storage = StorageClient(engine),
            local = local,
            config = FakeAppConfigRepository(),
            social = FakeSocialRepository(),
            preparer = PhotoPreparer { _, _, _ -> null },
            scope = scope,
        )
        return RealtimeSync(realtime, trips, local, pending, photos, currentUserId = { ME }, scope = scope)
    }

    private fun MockRequestHandleScope.json(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private suspend fun cacheTrip() = local.saveTrip(
        NetworkJson.decodeFromString<TripDto>(
            TripJson.trip("t1", days = listOf(TripJson.day("d0", 0, "2026-06-01", TripJson.marker("m0", "t1", "d0", 0)))),
        ).toDetails(),
    )

    private fun marker(id: String, name: String) =
        NetworkJson.decodeFromString<MarkerDto>(TripJson.marker(id, "t1", "d0", 1, name = name))

    private suspend fun markerNames() = local.trip("t1")!!.days.single().markers.map { it.name }

    @Test
    fun othersStops_areSaved_andOwnEchoesSkipped() = runTest {
        val sync = sync { error("no request expected") }
        cacheTrip()

        sync.apply(RealtimeEvent.MarkerSaved("t1", OTHER, marker("m1", "Louvre")))
        sync.apply(RealtimeEvent.MarkerSaved("t1", ME, marker("m2", "Mine, applied already")))
        sync.apply(RealtimeEvent.MarkersReordered("t1", OTHER, listOf("m1", "m0")))
        assertEquals(listOf("Louvre", "Stop #1"), markerNames())

        sync.apply(RealtimeEvent.MarkerDeleted("t1", OTHER, "m1"))
        assertEquals(listOf("Stop #1"), markerNames())
        // Trips not on the device keep no stops.
        sync.apply(RealtimeEvent.MarkerSaved("t2", OTHER, marker("m3", "Elsewhere")))
        assertNull(local.trip("t2"))
        assertTrue(paths.isEmpty())
    }

    @Test
    fun othersLikesAndComments_countOnTheStop_andKeepWhetherILikeIt() = runTest {
        val sync = sync { error("no request expected") }
        cacheTrip()
        local.savePlaceLike(PLACE, likeCount = 1, likedByMe = true)
        val comment = NetworkJson.decodeFromString<CommentDto>(
            """{"id":"c1","markerId":"m0","body":"Nice","author":{"id":"$OTHER","username":"anna","displayName":"Anna"},
               "likeCount":0,"likedByMe":false,"canDelete":false,"createdAt":"2026-10-11T10:00:00Z"}""",
        )

        sync.apply(RealtimeEvent.MarkerSaved("t1", OTHER, NetworkJson.decodeFromString<MarkerDto>(TripJson.marker("m0", "t1", "d0", 0, name = "Renamed"))))
        sync.apply(RealtimeEvent.LikeCountChanged("t1", OTHER, "place", PLACE, 2))
        sync.apply(RealtimeEvent.CommentAdded("t1", OTHER, comment))
        sync.apply(RealtimeEvent.CommentAdded("t1", ME, comment))

        val stop = local.marker("m0")!!
        assertEquals("Renamed", stop.name)
        assertEquals(2, stop.likeCount)
        assertTrue(stop.likedByMe)
        assertEquals(1, stop.commentCount)
        sync.apply(RealtimeEvent.CommentDeleted("t1", OTHER, "c1", "m0"))
        assertEquals(0, local.marker("m0")!!.commentCount)
    }

    @Test
    fun othersChange_whileOwnChangesAreOnTheirWay_reloadsAfter() = runTest {
        val sync = sync { error("no request expected") }
        cacheTrip()
        pending.begin("t1") {}

        sync.apply(RealtimeEvent.MarkerSaved("t1", OTHER, marker("m1", "Louvre")))

        assertEquals(listOf("Stop #1"), markerNames())
        assertTrue(pending.takeStale("t1"))
    }

    @Test
    fun aBurstOfTripChanges_reloadsTheTripAtMostTwice() = runTest {
        val release = CompletableDeferred<Unit>()
        val sync = sync {
            release.await()
            json(TripJson.trip("t1", title = "Rome"))
        }
        cacheTrip()

        repeat(5) { sync.apply(RealtimeEvent.TripChanged("t1", OTHER)) }
        realTime { sent.first { it.isNotEmpty() } }
        repeat(5) { sync.apply(RealtimeEvent.TripChanged("t1", OTHER)) }
        // Every ask has arrived while the first reload is still running.
        realTime { delay(100.milliseconds) }
        release.complete(Unit)

        realTime { local.trips.first { it["t1"]?.trip?.name == "Rome" } }
        realTime { delay(100.milliseconds) }
        assertEquals(listOf("/v1/trips/t1", "/v1/trips/t1"), paths)
        // Own changes reloaded the trip already.
        sync.apply(RealtimeEvent.TripChanged("t1", ME))
        realTime { delay(100.milliseconds) }
        assertEquals(2, paths.size)
    }

    @Test
    fun deletedTrip_leavesTheCache_andMyTripsChanges_reloadMyTrips() = runTest {
        val sync = sync { json(TripJson.page(null)) }
        cacheTrip()

        sync.apply(RealtimeEvent.TripDeleted("t1", OTHER))
        assertNull(local.trip("t1"))

        sync.apply(RealtimeEvent.MyTripsChanged("t9"))
        realTime { sent.first { it.isNotEmpty() } }
        assertEquals(listOf("/v1/me/trips"), paths)
    }

    @Test
    fun aFollowEvent_leavesLiveUpdatesWorking() = runTest {
        val sync = sync { json(TripJson.trip("t1")) }
        cacheTrip()
        sync.start()
        // Listening starts on the sync's scope.
        realTime { delay(100.milliseconds) }

        realtime.send(RealtimeEvent.FollowsChanged(followerId = OTHER, followeeId = ME, following = true))
        realtime.send(RealtimeEvent.TripChanged("t1", OTHER))

        realTime { sent.first { it.isNotEmpty() } }
        assertEquals(listOf("/v1/trips/t1"), paths)
    }

    @Test
    fun reconnect_reloadsWhatWasMissed_butTheFirstConnectionDoesNot() = runTest {
        val sync = sync { request ->
            if (request.url.encodedPath == "/v1/me/trips") json(TripJson.page(null)) else json(TripJson.trip("t1"))
        }
        sync.start()
        realtime.watchTrip("t1")
        realTime { while (!realtime.isRunning) delay(5.milliseconds) }
        // Listening starts on the sync's scope.
        realTime { delay(100.milliseconds) }

        realtime.connect()
        realTime { delay(100.milliseconds) }
        assertTrue(paths.isEmpty())

        realtime.connect()
        realTime { while (paths.size < 2) delay(5.milliseconds) }
        assertEquals(setOf("/v1/me/trips", "/v1/trips/t1"), paths.toSet())

        // A new account starts over.
        sync.clear()
        realtime.connect()
        realTime { delay(100.milliseconds) }
        assertEquals(2, paths.size)
        sync.stop()
        assertTrue(!realtime.isRunning)
    }

    private companion object {
        const val ME = "22222222-2222-4222-8222-222222222222"
        const val OTHER = "33333333-3333-4333-8333-333333333333"

        /** The place of the cached trip's first stop (see [TripJson.marker]). */
        const val PLACE = "99999999-9999-4999-8999-999999999990"
    }
}

private suspend fun <T> realTime(block: suspend () -> T): T =
    withContext(Dispatchers.Default) { withTimeout(10.seconds) { block() } }
