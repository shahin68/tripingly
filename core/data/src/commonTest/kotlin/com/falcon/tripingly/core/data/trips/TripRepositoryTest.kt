package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.TripDates
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.TripUpdate
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.IDEMPOTENCY_KEY_HEADER
import com.falcon.tripingly.core.network.NetworkJson
import com.falcon.tripingly.core.network.model.TripDto
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripRepositoryTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0", logRequests = false)

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
        val trips = OfflineFirstTripRepository(ktorfit.createTripsApi(), local, currentUserId = { ME })
        val markers = DefaultMarkerRepository(ktorfit.createMarkersApi(), trips, local)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.bodyText() = (body as TextContent).text

    @Test
    fun refreshMyTrips_walksEveryPage_andReplacesTheCachedList() = runTest {
        val harness = Harness { request ->
            when (request.url.parameters["cursor"]) {
                null -> json(TripJson.page("c2", TripJson.summary("t1", "Paris", "2026-10-04T12:00:00.000Z")))
                "c2" -> json(TripJson.page(null, TripJson.summary("t2", "Rome", "2026-10-03T12:00:00.000Z", role = "editor")))
                else -> error("unexpected cursor")
            }
        }
        harness.local.saveTrip(details(TripJson.trip("gone")))

        val result = harness.trips.refreshMyTrips()

        assertIs<AppResult.Success<Unit>>(result)
        assertEquals(listOf("t1", "t2"), harness.trips.observeMyTrips().first().map { it.id })
        assertEquals(TripRole.EDITOR, harness.trips.observeMyTrips().first()[1].role)
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
    fun leaveTrip_removesTheSignedInUser_andDropsTheTrip() = runTest {
        val harness = Harness { respond("", HttpStatusCode.NoContent) }
        harness.local.saveTrip(details(TripJson.trip("t1", myRole = "editor")))

        val result = harness.trips.leaveTrip("t1")

        assertIs<AppResult.Success<Unit>>(result)
        assertEquals(HttpMethod.Delete, harness.requests.single().method)
        assertEquals("/v1/trips/t1/members/$ME", harness.requests.single().url.encodedPath)
        assertTrue(harness.trips.observeMyTrips().first().isEmpty())
    }

    @Test
    fun addMarker_showsTheServersMarker_andReloadsTheTrip() = runTest {
        val withMarker = TripJson.trip("t1", days = listOf(TripJson.day("d0", 0, "2026-06-01", TripJson.marker("m0", "t1", "d0", 0))))
        val harness = Harness { request ->
            when (request.method) {
                HttpMethod.Post -> json(TripJson.marker("m0", "t1", "d0", 0), HttpStatusCode.Created)
                else -> json(withMarker)
            }
        }
        harness.local.saveTrip(details(TripJson.trip("t1")))

        val result = harness.markers.addMarker("d0", NewMarker("Stop #1", GeoPoint(48.8584, 2.2945)))

        assertEquals("m0", (result as AppResult.Success).data.id)
        assertEquals("/v1/days/d0/markers", harness.requests[0].url.encodedPath)
        assertEquals(
            """{"name":"Stop #1","location":{"lat":48.8584,"lng":2.2945}}""",
            harness.requests[0].bodyText(),
        )
        assertEquals(listOf("m0"), harness.trips.observeTrip("t1").first()!!.days[0].markers.map { it.id })
    }

    @Test
    fun reorderMarkers_refused_putsTheOldOrderBack() = runTest {
        val harness = Harness { json(TripJson.error("FORBIDDEN", "Forbidden"), HttpStatusCode.Forbidden) }
        harness.local.saveTrip(
            details(
                TripJson.trip(
                    "t1",
                    days = listOf(TripJson.day("d0", 0, null, TripJson.marker("m0", "t1", "d0", 0), TripJson.marker("m1", "t1", "d0", 1))),
                ),
            ),
        )

        val result = harness.markers.reorderMarkers("t1", "d0", listOf("m1", "m0"))

        assertIs<AppResult.Error<*>>(result)
        assertEquals(listOf("m0", "m1"), harness.trips.observeTrip("t1").first()!!.days[0].markers.map { it.id })
    }

    private fun details(json: String) =
        NetworkJson.decodeFromString<TripDto>(json).toDetails()

    private companion object {
        const val ME = "22222222-2222-4222-8222-222222222222"
    }
}
