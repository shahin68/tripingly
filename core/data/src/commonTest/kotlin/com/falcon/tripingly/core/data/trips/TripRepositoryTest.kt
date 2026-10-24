package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.place.OsmRef
import com.falcon.tripingly.core.model.place.OsmType
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.Destination
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.JoinRequestStatus
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.TripDates
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMapView
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.TripUpdate
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.IDEMPOTENCY_KEY_HEADER
import com.falcon.tripingly.core.network.NetworkJson
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
import com.falcon.tripingly.core.network.model.TripDto
import com.falcon.tripingly.core.network.realtime.FakeRealtimeClient
import com.falcon.tripingly.core.network.trips.createMarkersApi
import com.falcon.tripingly.core.network.trips.createTripsApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TripRepositoryTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0")

    /** The API over a mock engine plus an in-memory cache; [handler] answers each request. */
    private inner class Harness(
        private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        val requests = mutableListOf<HttpRequestData>()
        val local = InMemoryTripLocalDataSource()
        private val ktorfit = createKtorfit(
            createHttpClient(
                engine = MockEngine { request ->
                    requests += request
                    handler(request)
                },
                config = config,
                tokenStore = InMemoryTokenStore(),
                sessionEvents = SessionEvents(),
                languageTag = { "en" },
            ),
            config,
        )
        val pending = PendingTripWrites()
        val realtime = FakeRealtimeClient()
        val mapViews = FakeTripMapViewRepository()
        val trips = OfflineFirstTripRepository(ktorfit.createTripsApi(), local, pending, realtime, mapViews, currentUserId = { ME })

        /** Sends on a real dispatcher, like the app. */
        fun markers() = DefaultMarkerRepository(ktorfit.createMarkersApi(), trips, local, pending, failureScope, currentUserId = { ME })

        suspend fun awaitTrip(condition: (TripDetails) -> Boolean) {
            realTime { local.trips.first { all -> all["t1"]?.let(condition) == true } }
        }

        suspend fun awaitRequests(count: Int) {
            realTime { while (requests.size < count) delay(5.milliseconds) }
        }

        /** Until every queued marker change has been sent (or undone). */
        suspend fun awaitQueueEmpty() {
            realTime { while (!pending.saveIfQuiet("t1", pending.version("t1")) {}) delay(5.milliseconds) }
        }
    }

    /** Where marker queues run; cancelled after each test. */
    private val failureScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun cancelQueues() = failureScope.cancel()

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.bodyText() = (body as TextContent).text

    @Test
    fun refreshMyTrips_walksEveryPage_andReplacesTheCachedList() = runTest {
        val harness = Harness { request ->
            when (request.url.parameters["cursor"]) {
                null -> json(TripJson.page("c2", TripJson.summary("t1", "Paris", "2026-10-04T12:00:00.000Z", editorCount = 2)))
                "c2" -> json(TripJson.page(null, TripJson.summary("t2", "Rome", "2026-10-03T12:00:00.000Z", role = "editor")))
                else -> error("unexpected cursor")
            }
        }
        harness.local.saveTrip(details(TripJson.trip("gone")))

        val result = harness.trips.refreshMyTrips()

        assertIs<AppResult.Success<Unit>>(result)
        assertEquals(listOf("t1", "t2"), harness.trips.observeMyTrips().first().map { it.id })
        assertEquals(TripRole.EDITOR, harness.trips.observeMyTrips().first()[1].role)
        // The owner sees who the trip is shared with.
        assertEquals(2, harness.trips.observeMyTrips().first()[0].editorCount)
        assertEquals(listOf("50", "50"), harness.requests.map { it.url.parameters["limit"] })
    }

    @Test
    fun refreshMyTrips_offline_keepsTheCachedTrips() = runTest {
        val harness = Harness { throw IOException("offline") }
        harness.local.saveTrip(details(TripJson.trip("t1")))

        val result = harness.trips.refreshMyTrips()

        assertEquals(DataError.Network.NoInternet, (result as AppResult.Error).error)
        assertEquals(listOf("t1"), harness.trips.observeMyTrips().first().map { it.id })
    }

    @Test
    fun refreshMyTrips_keepsDaysAlreadyCachedForAListedTrip() = runTest {
        val harness = Harness { json(TripJson.page(null, TripJson.summary("t1", "Paris renamed"))) }
        harness.local.saveTrip(details(TripJson.trip("t1")))

        harness.trips.refreshMyTrips()

        val cached = assertNotNull(harness.trips.observeTrip("t1").first())
        assertEquals("Paris renamed", cached.trip.name)
        assertEquals(1, cached.days.size)
    }

    @Test
    fun refreshTrip_mapsDaysMarkersAndMembers_inOrder() = runTest {
        val harness = Harness {
            json(
                TripJson.trip(
                    "t1",
                    days = listOf(
                        TripJson.day("d1", 1, "2026-06-02"),
                        TripJson.day("d0", 0, "2026-06-01", TripJson.marker("m1", "t1", "d0", 1), TripJson.marker("m0", "t1", "d0", 0)),
                    ),
                    endDate = "2026-06-02",
                ),
            )
        }

        val details = (harness.trips.refreshTrip("t1") as AppResult.Success).data

        assertEquals(listOf("d0", "d1"), details.days.map { it.id })
        assertEquals(LocalDate(2026, 6, 1), details.days[0].date)
        assertEquals(listOf("m0", "m1"), details.days[0].markers.map { it.id })
        assertEquals(2, details.trip.markerCount)
        assertEquals(TripVisibility.PRIVATE, details.trip.visibility)
        assertEquals(listOf(TripRole.OWNER, TripRole.EDITOR), details.members.map { it.role })
        assertEquals(1, details.trip.editorCount)
        assertEquals(details, harness.trips.observeTrip("t1").first())
    }

    @Test
    fun refreshTrip_notFound_dropsTheCachedTrip() = runTest {
        val harness = Harness { json(TripJson.error("NOT_FOUND", "Not found"), HttpStatusCode.NotFound) }
        harness.local.saveTrip(details(TripJson.trip("t1")))

        val result = harness.trips.refreshTrip("t1")

        assertTrue((result as AppResult.Error).error.hasCode(TripErrorCodes.NOT_FOUND))
        assertNull(harness.trips.observeTrip("t1").first())
    }

    @Test
    fun createTrip_sendsTitleDatesAndAnIdempotencyKey_andCachesTheTrip() = runTest {
        val harness = Harness { json(TripJson.trip("t9", title = "Vienna"), HttpStatusCode.Created) }

        val result = harness.trips.createTrip(NewTrip("  Vienna ", LocalDate(2026, 6, 1), LocalDate(2026, 6, 1)))

        assertIs<AppResult.Success<*>>(result)
        val request = harness.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/trips", request.url.encodedPath)
        assertEquals("""{"title":"Vienna","startDate":"2026-06-01","endDate":"2026-06-01"}""", request.bodyText())
        assertNotNull(request.headers[IDEMPOTENCY_KEY_HEADER])
        assertEquals("Vienna", harness.trips.observeMyTrips().first().single().name)
    }

    @Test
    fun createTrip_withADestination_sendsIt_andKeepsItInTheCache() = runTest {
        val paris = """{"name":"Paris","location":{"lat":48.8566,"lng":2.3522}}"""
        val harness = Harness { json(TripJson.trip("t9", destination = paris), HttpStatusCode.Created) }

        harness.trips.createTrip(
            NewTrip("Paris", LocalDate(2026, 6, 1), LocalDate(2026, 6, 1), destination = Destination("Paris", GeoPoint(48.8566, 2.3522))),
        )

        assertEquals(
            """{"title":"Paris","startDate":"2026-06-01","endDate":"2026-06-01","destination":$paris}""",
            harness.requests.single().bodyText(),
        )
        assertEquals(Destination("Paris", GeoPoint(48.8566, 2.3522)), harness.trips.observeTrip("t9").first()?.destination)
    }

    @Test
    fun createTrip_withMembers_sendsVisibilityAndUsernames() = runTest {
        val harness = Harness { json(TripJson.trip("t9"), HttpStatusCode.Created) }

        harness.trips.createTrip(NewTrip("Rome", null, null, TripVisibility.PRIVATE, memberUsernames = listOf("anna", "jonas.k")))

        assertEquals(
            """{"title":"Rome","visibility":"private","memberUsernames":["anna","jonas.k"]}""",
            harness.requests.single().bodyText(),
        )
    }

    @Test
    fun createTrip_withAnUnknownMember_reportsTheFieldErrorByIndex() = runTest {
        val harness = Harness {
            json(
                TripJson.error("VALIDATION_FAILED", "Validation failed", """{"fields":{"memberUsernames.1":["notFound"]}}"""),
                HttpStatusCode.BadRequest,
            )
        }

        val error = (harness.trips.createTrip(NewTrip("Rome", null, null, memberUsernames = listOf("anna", "nobody"))) as AppResult.Error).error

        assertTrue(error.hasFieldError("memberUsernames.1", TripErrorCodes.MEMBER_NOT_FOUND))
        assertTrue(harness.trips.observeMyTrips().first().isEmpty())
    }

    @Test
    fun updateTrip_withDaysThatStillHaveStops_reportsTheFieldError() = runTest {
        val harness = Harness {
            json(
                TripJson.error("VALIDATION_FAILED", "Validation failed", """{"fields":{"endDate":["daysNotEmpty"]}}"""),
                HttpStatusCode.BadRequest,
            )
        }

        val result = harness.trips.updateTrip("t1", TripUpdate(dates = TripDates(LocalDate(2026, 6, 1), LocalDate(2026, 6, 1))))

        val error = (result as AppResult.Error).error
        assertTrue(error.hasFieldError("endDate", TripErrorCodes.DAYS_NOT_EMPTY))
        assertEquals("""{"startDate":"2026-06-01","endDate":"2026-06-01"}""", harness.requests.single().bodyText())
    }

    @Test
    fun addMember_sendsTheUsernameWithoutAt_thenReloadsTheTrip() = runTest {
        val harness = Harness { request ->
            when (request.method) {
                HttpMethod.Post -> json("""{"user":${TripJson.EDITOR},"role":"editor"}""")
                else -> json(TripJson.trip("t1"))
            }
        }

        val result = harness.trips.addMember("t1", " @Anna ")

        assertIs<AppResult.Success<*>>(result)
        assertEquals("""{"username":"anna"}""", harness.requests[0].bodyText())
        assertEquals("/v1/trips/t1", harness.requests[1].url.encodedPath)
    }

    @Test
    fun setSaved_putsSomeoneElsesTripInMyTrips_andUnsavingTakesItOut() = runTest {
        val harness = Harness { respond("", HttpStatusCode.NoContent) }
        val trip = details(TripJson.trip("t1", myRole = "viewer")).trip

        assertIs<AppResult.Success<Unit>>(harness.trips.setSaved(trip, saved = true))
        assertEquals(listOf("t1"), harness.trips.observeMyTrips().first().map { it.id })
        assertIs<AppResult.Success<Unit>>(harness.trips.setSaved(trip, saved = false))

        assertTrue(harness.trips.observeMyTrips().first().isEmpty())
        assertEquals(listOf(HttpMethod.Put, HttpMethod.Delete), harness.requests.map { it.method })
        assertEquals("/v1/trips/t1/save", harness.requests[0].url.encodedPath)
    }

    @Test
    fun refreshMyTrips_listsSavedTrips_andKeepsWhatTheTripSaidAboutJoining() = runTest {
        val harness = Harness { json(TripJson.page(null, TripJson.summary("t1", "Paris", role = "viewer", savedByMe = true))) }
        harness.local.saveTrip(details(TripJson.trip("t1", myRole = "viewer", myJoinRequest = "\"pending\"")))

        harness.trips.refreshMyTrips()

        assertTrue(harness.trips.observeMyTrips().first().single().savedByMe)
        assertEquals(JoinRequestStatus.PENDING, harness.trips.observeTrip("t1").first()?.myJoinRequest)
    }

    @Test
    fun requestToJoin_declinedBefore_showsItOnTheTrip() = runTest {
        val harness = Harness {
            json(TripJson.error("JOIN_REQUEST_DECLINED", "The owner declined your request."), HttpStatusCode.Conflict)
        }
        harness.local.saveTrip(details(TripJson.trip("t1", myRole = "viewer")))

        val result = harness.trips.requestToJoin("t1")

        assertTrue((result as AppResult.Error).error.hasCode(TripErrorCodes.JOIN_REQUEST_DECLINED))
        assertEquals(JoinRequestStatus.DECLINED, harness.trips.observeTrip("t1").first()?.myJoinRequest)
    }

    @Test
    fun joinRequests_ownerAccepts_thenTheTripReloadsWithTheNewEditor() = runTest {
        val harness = Harness { request ->
            when (request.url.encodedPath) {
                "/v1/trips/t1/join-requests" -> json(TripJson.page(null, TripJson.joinRequest("r1", "t1")))
                "/v1/join-requests/r1/accept" -> json("""{"user":${TripJson.EDITOR},"role":"editor"}""")
                else -> json(TripJson.trip("t1"))
            }
        }

        val requests = (harness.trips.joinRequests("t1") as AppResult.Success).data
        val accepted = harness.trips.acceptJoinRequest("t1", requests.items.single().id)

        assertEquals("anna", requests.items.single().user.username)
        assertIs<AppResult.Success<*>>(accepted)
        assertEquals(
            listOf("/v1/trips/t1/join-requests", "/v1/join-requests/r1/accept", "/v1/trips/t1"),
            harness.requests.map { it.url.encodedPath },
        )
    }

    @Test
    fun leaveTrip_removesTheSignedInUser_andDropsTheTrip() = runTest {
        val harness = Harness { respond("", HttpStatusCode.NoContent) }
        harness.local.saveTrip(details(TripJson.trip("t1", myRole = "editor")))
        harness.mapViews.save("t1", TripMapView("d0", isRouteShown = false, TravelMode.Walking))

        val result = harness.trips.leaveTrip("t1")

        assertIs<AppResult.Success<Unit>>(result)
        assertEquals(HttpMethod.Delete, harness.requests.single().method)
        assertEquals("/v1/trips/t1/members/$ME", harness.requests.single().url.encodedPath)
        assertTrue(harness.trips.observeMyTrips().first().isEmpty())
        assertNull(harness.mapViews.get("t1"))
    }

    @Test
    fun addMarker_showsAtOnce_sendsItsId_andKeepsTheMatchedPlace_withoutAReload() = runTest {
        val harness = Harness { request -> json(TripJson.marker(request.sentId(), "t1", "d0", 0), HttpStatusCode.Created) }
        harness.local.saveTrip(details(TripJson.trip("t1")))
        val markers = harness.markers()

        val created = markers.addMarker("t1", "d0", NewMarker("Stop #1", GeoPoint(48.8584, 2.2945)))

        // On the map before the server has answered.
        assertEquals(listOf(created.id), harness.local.trip("t1")!!.days[0].markers.map { it.id })
        assertEquals("", created.placeId)
        harness.awaitTrip { it.days[0].markers.singleOrNull()?.placeId?.isNotEmpty() == true }
        assertEquals("/v1/days/d0/markers", harness.requests[0].url.encodedPath)
        assertEquals(
            """{"id":"${created.id}","name":"Stop #1","location":{"lat":48.8584,"lng":2.2945}}""",
            harness.requests[0].bodyText(),
        )
        assertEquals(created.id, harness.requests[0].headers[IDEMPOTENCY_KEY_HEADER])
        harness.awaitQueueEmpty()
        assertEquals(1, harness.requests.size)
    }

    @Test
    fun addMarker_atAHotSpot_takesThePlacesHotCountAndImageFromTheServersAnswer() = runTest {
        val harness = Harness { request ->
            val liked = TripJson.marker(request.sentId(), "t1", "d0", 0)
                .replace(""""hotCount":0,"hotByMe":false""", """"hotCount":12,"hotByMe":true""")
                .replace(""""coverPhotoId":null,"coverThumbUrl":null""", """"coverPhotoId":"img1","coverThumbUrl":"https://cdn/img1"""")
            json(liked, HttpStatusCode.Created)
        }
        harness.local.saveTrip(details(TripJson.trip("t1")))

        harness.markers().addMarker("t1", "d0", NewMarker("Stephansdom", GeoPoint(48.2085, 16.3721), placeId = "p1"))

        harness.awaitTrip { it.days[0].markers.singleOrNull()?.hotCount == 12 }
        val stop = harness.local.trip("t1")!!.days[0].markers.single()
        assertTrue(stop.hotByMe)
        assertEquals("img1", stop.coverPhotoId)
        assertEquals("https://cdn/img1", stop.coverThumbUrl)
    }

    @Test
    fun someoneElsesChange_whileAStopIsOnItsWay_reloadsTheTripOnceItIsSent() = runTest {
        val sent = CompletableDeferred<Unit>()
        val harness = Harness { request ->
            if (request.method == HttpMethod.Get) {
                json(TripJson.trip("t1", title = "Rome"))
            } else {
                sent.await()
                json(TripJson.marker(request.sentId(), "t1", "d0", 0), HttpStatusCode.Created)
            }
        }
        harness.local.saveTrip(details(TripJson.trip("t1")))
        harness.markers().addMarker("t1", "d0", NewMarker("Stop #1", GeoPoint(48.8584, 2.2945)))
        harness.awaitRequests(1)

        // A live change from someone else can't be saved over the unsent stop.
        assertEquals(false, harness.pending.saveIfNothingPending("t1") {})
        sent.complete(Unit)

        harness.awaitTrip { it.trip.name == "Rome" }
        assertEquals(listOf(HttpMethod.Post, HttpMethod.Get), harness.requests.map { it.method })
    }

    @Test
    fun addMarker_tappedOnTheMap_sendsNoName_andTakesTheServersName() = runTest {
        val harness = Harness { request ->
            json(TripJson.marker(request.sentId(), "t1", "d0", 0, name = "Hotel Sacher"), HttpStatusCode.Created)
        }
        harness.local.saveTrip(details(TripJson.trip("t1")))

        val created = harness.markers().addMarker("t1", "d0", NewMarker("", GeoPoint(48.8584, 2.2945)))

        harness.awaitTrip { it.days[0].markers.singleOrNull()?.name == "Hotel Sacher" }
        assertEquals(
            """{"id":"${created.id}","location":{"lat":48.8584,"lng":2.2945}}""",
            harness.requests[0].bodyText(),
        )
    }

    @Test
    fun addMarker_fromAnAddress_sendsItsOsmFeature_andKeepsItsOwnName() = runTest {
        val harness = Harness { request ->
            json(TripJson.marker(request.sentId(), "t1", "d0", 0, name = "Server name"), HttpStatusCode.Created)
        }
        harness.local.saveTrip(details(TripJson.trip("t1")))

        val created = harness.markers().addMarker(
            "t1",
            "d0",
            NewMarker("Kärntner Straße 38", GeoPoint(48.2052, 16.3696), osm = OsmRef(OsmType.Node, "42")),
        )

        harness.awaitQueueEmpty()
        assertEquals(
            """{"id":"${created.id}","name":"Kärntner Straße 38","location":{"lat":48.2052,"lng":16.3696},"osmType":"node","osmId":"42"}""",
            harness.requests[0].bodyText(),
        )
        // The name the app already shows stays.
        assertEquals("Kärntner Straße 38", harness.local.trip("t1")!!.days[0].markers.single().name)
    }

    @Test
    fun addMarker_refused_takesItBackOff_andReportsIt() = runTest {
        val harness = Harness { request ->
            when (request.method) {
                HttpMethod.Post -> json(TripJson.error("FORBIDDEN", "Only editors can add stops."), HttpStatusCode.Forbidden)
                else -> json(TripJson.trip("t1"))
            }
        }
        harness.local.saveTrip(details(TripJson.trip("t1")))
        val markers = harness.markers()
        val failures = markers.collectFailures()

        markers.addMarker("t1", "d0", NewMarker("Stop #1", GeoPoint(48.8584, 2.2945)))
        val failure = failures.next()

        assertTrue(harness.local.trip("t1")!!.days[0].markers.isEmpty())
        assertEquals("Stop #1", failure.markerName)
        assertTrue(failure.error.hasCode(TripErrorCodes.FORBIDDEN))
        assertEquals(false, failure.isConnectionProblem)
    }

    @Test
    fun addMarker_offline_retriesQuietly_thenUndoes_andRetrySendsItAgain() = runTest {
        var online = false
        var sentId = ""
        val harness = Harness { request ->
            if (!online) throw IOException("offline")
            when (request.method) {
                HttpMethod.Post -> json(TripJson.marker(request.sentId().also { sentId = it }, "t1", "d0", 0), HttpStatusCode.Created)
                else -> json(tripWithMarker(sentId))
            }
        }
        harness.local.saveTrip(details(TripJson.trip("t1")))
        val markers = harness.markers()
        val failures = markers.collectFailures()

        val created = markers.addMarker("t1", "d0", NewMarker("Stop #1", GeoPoint(48.8584, 2.2945)))
        val failure = failures.next()

        assertTrue(failure.isConnectionProblem)
        // The first try and two quiet retries.
        assertEquals(3, harness.requests.count { it.method == HttpMethod.Post })
        assertTrue(harness.local.trip("t1")!!.days[0].markers.isEmpty())

        online = true
        markers.retry(failure)

        assertEquals(listOf(created.id), harness.local.trip("t1")!!.days[0].markers.map { it.id })
        harness.awaitTrip { it.days[0].markers.singleOrNull()?.placeId?.isNotEmpty() == true }
        assertEquals(created.id, sentId)
    }

    @Test
    fun addMarker_idConflict_meansAnEarlierAttemptGotThrough() = runTest {
        var sentId = ""
        val harness = Harness { request ->
            when (request.method) {
                HttpMethod.Post -> {
                    sentId = request.sentId()
                    json(TripJson.error("ID_CONFLICT", "Taken."), HttpStatusCode.Conflict)
                }
                else -> json(tripWithMarker(sentId))
            }
        }
        harness.local.saveTrip(details(TripJson.trip("t1")))
        val markers = harness.markers()
        val failures = markers.collectFailures()

        val created = markers.addMarker("t1", "d0", NewMarker("Stop #1", GeoPoint(48.8584, 2.2945)))

        harness.awaitQueueEmpty()
        assertEquals(created.id, harness.local.trip("t1")!!.days[0].markers.single().id)
        assertEquals(created.id, sentId)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun deleteMarker_alreadyGone_staysDeleted() = runTest {
        val harness = Harness { request ->
            when (request.method) {
                HttpMethod.Delete -> json(TripJson.error("NOT_FOUND", "Not found."), HttpStatusCode.NotFound)
                else -> json(TripJson.trip("t1"))
            }
        }
        harness.local.saveTrip(details(TripJson.trip("t1", days = listOf(TripJson.day("d0", 0, null, TripJson.marker("m0", "t1", "d0", 0))))))
        val markers = harness.markers()
        val failures = markers.collectFailures()

        markers.deleteMarker("m0")

        assertTrue(harness.local.trip("t1")!!.days[0].markers.isEmpty())
        harness.awaitQueueEmpty()
        assertEquals("/v1/markers/m0", harness.requests.single().url.encodedPath)
        assertTrue(failures.isEmpty())
        assertTrue(harness.local.trip("t1")!!.days[0].markers.isEmpty())
    }

    @Test
    fun deleteMarker_withOthersContent_asksFirst_thenDeletesAnyway() = runTest {
        val harness = Harness { request ->
            if (request.url.parameters["confirm"] == "true") {
                respond("", HttpStatusCode.NoContent)
            } else {
                json(TripJson.error("STOP_HAS_OTHERS_CONTENT", "Others' photos go too."), HttpStatusCode.Conflict)
            }
        }
        harness.local.saveTrip(details(twoStops()))
        val markers = harness.markers()
        val failures = markers.collectFailures()

        markers.deleteMarker("m0")
        val failure = failures.next()

        // Put back until the user agrees.
        assertTrue(failure.needsConfirm)
        assertEquals(listOf("m0", "m1"), harness.local.trip("t1")!!.days[0].markers.map { it.id })

        markers.deleteAnyway(failure)

        assertEquals(listOf("m1"), harness.local.trip("t1")!!.days[0].markers.map { it.id })
        harness.awaitQueueEmpty()
        assertEquals(listOf("false", "true"), harness.requests.map { it.url.parameters["confirm"] })
        assertEquals(listOf("m1"), harness.local.trip("t1")!!.days[0].markers.map { it.id })
    }

    @Test
    fun addMarker_isMine() = runTest {
        val harness = Harness { request -> json(TripJson.marker(request.sentId(), "t1", "d0", 0), HttpStatusCode.Created) }
        harness.local.saveTrip(details(TripJson.trip("t1")))

        val created = harness.markers().addMarker("t1", "d0", NewMarker("Stop #1", GeoPoint(48.8584, 2.2945)))

        assertEquals(ME, created.createdById)
    }

    @Test
    fun clearDay_removesTheDaysStopsAtOnce_withOneRequest() = runTest {
        val harness = Harness { respond("", HttpStatusCode.NoContent) }
        harness.local.saveTrip(details(twoStops()))
        val markers = harness.markers()

        markers.clearDay("t1", "d0")

        assertTrue(harness.local.trip("t1")!!.days[0].markers.isEmpty())
        harness.awaitQueueEmpty()
        val request = harness.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/v1/days/d0/markers", request.url.encodedPath)
    }

    @Test
    fun clearDay_refused_putsTheStopsBack() = runTest {
        val harness = Harness { json(TripJson.error("FORBIDDEN", "Forbidden"), HttpStatusCode.Forbidden) }
        harness.local.saveTrip(details(twoStops()))
        val markers = harness.markers()
        val failures = markers.collectFailures()

        markers.clearDay("t1", "d0")
        failures.next()

        assertEquals(listOf("m0", "m1"), harness.local.trip("t1")!!.days[0].markers.map { it.id })
    }

    @Test
    fun addMarker_afterADelete_goesLast() = runTest {
        val harness = Harness { request ->
            if (request.method == HttpMethod.Post) json(TripJson.marker(request.sentId(), "t1", "d0", 1), HttpStatusCode.Created)
            else respond("", HttpStatusCode.NoContent)
        }
        harness.local.saveTrip(details(twoStops()))
        val markers = harness.markers()

        markers.deleteMarker("m0")
        val created = markers.addMarker("t1", "d0", NewMarker("Stop #2", GeoPoint(48.8584, 2.2945)))

        assertEquals(listOf("m1", created.id), harness.local.trip("t1")!!.days[0].markers.map { it.id })
    }

    @Test
    fun reorderMarkers_refused_putsTheOldOrderBack() = runTest {
        // The refusal waits until the new order has been checked; otherwise the queue may undo it first.
        val answer = CompletableDeferred<Unit>()
        val harness = Harness {
            answer.await()
            json(TripJson.error("FORBIDDEN", "Forbidden"), HttpStatusCode.Forbidden)
        }
        harness.local.saveTrip(
            details(
                TripJson.trip(
                    "t1",
                    days = listOf(TripJson.day("d0", 0, null, TripJson.marker("m0", "t1", "d0", 0), TripJson.marker("m1", "t1", "d0", 1))),
                ),
            ),
        )
        val markers = harness.markers()
        val failures = markers.collectFailures()

        markers.reorderMarkers("t1", "d0", listOf("m1", "m0"))

        assertEquals(listOf("m1", "m0"), harness.local.trip("t1")!!.days[0].markers.map { it.id })
        answer.complete(Unit)
        failures.next()
        assertEquals(listOf("m0", "m1"), harness.local.trip("t1")!!.days[0].markers.map { it.id })
    }

    @Test
    fun refreshTrip_withAMarkerChangeUnsent_keepsTheDevicesVersion() = runTest {
        val harness = Harness { json(TripJson.trip("t1", title = "From the server")) }
        harness.local.saveTrip(details(TripJson.trip("t1")))
        harness.pending.begin("t1") {}

        val result = harness.trips.refreshTrip("t1")

        assertEquals("From the server", (result as AppResult.Success).data.trip.name)
        assertEquals("Paris", harness.local.trip("t1")!!.trip.name)

        harness.pending.end("t1")
        harness.trips.refreshTrip("t1")

        assertEquals("From the server", harness.local.trip("t1")!!.trip.name)
    }

    private fun twoStops() = TripJson.trip(
        "t1",
        days = listOf(TripJson.day("d0", 0, null, TripJson.marker("m0", "t1", "d0", 0), TripJson.marker("m1", "t1", "d0", 1))),
    )

    private fun tripWithMarker(markerId: String) =
        TripJson.trip("t1", days = listOf(TripJson.day("d0", 0, "2026-06-01", TripJson.marker(markerId, "t1", "d0", 0))))

    /** The marker ID the app chose, from a create request's body. */
    private fun HttpRequestData.sentId(): String =
        Regex(""""id":"([^"]+)"""").find(bodyText())!!.groupValues[1]

    private fun MarkerRepository.collectFailures(): FailureLog {
        val log = FailureLog()
        // Undispatched, so it is listening before the first change is made.
        failureScope.launch(start = CoroutineStart.UNDISPATCHED) { failures.collect { log.channel.send(it) } }
        return log
    }

    private class FailureLog {
        val channel = Channel<MarkerChangeFailure>(Channel.UNLIMITED)

        suspend fun next(): MarkerChangeFailure = realTime { channel.receive() }

        fun isEmpty(): Boolean = channel.tryReceive().isFailure
    }

    private fun details(json: String) =
        NetworkJson.decodeFromString<TripDto>(json).toDetails()

    private companion object {
        const val ME = "22222222-2222-4222-8222-222222222222"
    }
}

/** Waits on the real clock: the marker queue runs outside the test's virtual time. */
private suspend fun <T> realTime(block: suspend () -> T): T =
    withContext(Dispatchers.Default) { withTimeout(10.seconds) { block() } }
