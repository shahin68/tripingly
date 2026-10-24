package com.falcon.tripingly.core.data.routing

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.trips.PendingTripWrites
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.route.BestOrder
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.TripMarker
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
import com.falcon.tripingly.core.network.routing.createRoutingApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RouteRepositoryTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0")
    private val requests = mutableListOf<HttpRequestData>()
    private var status = HttpStatusCode.OK
    private var body = ROUTE
    private val pending = PendingTripWrites()

    private val repository = DefaultRouteRepository(
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
        ).createRoutingApi(),
        pending = pending,
    )

    @Test
    fun dayRouteDecodesTheLineAndThePlacesAlongTheWay() = runTest {
        val plan = assertIs<AppResult.Success<RoutePlan>>(repository.dayRoute(TRIP_ID, day(), TravelMode.Cycling)).data
        val route = plan.route!!

        assertEquals("/v1/days/d1/route", requests.single().url.encodedPath)
        assertEquals("cycling", requests.single().url.parameters["mode"])
        assertEquals(listOf(GeoPoint(38.5, -120.2), GeoPoint(40.7, -120.95), GeoPoint(43.252, -126.453)), route.points)
        assertEquals(1200, route.distanceMeters)
        assertEquals(900, route.durationSeconds)
        assertEquals("m1", route.legs.single().fromMarkerId)
        val along = plan.alongTheWay.single()
        assertEquals("Café Central", along.place.name)
        assertEquals(PlaceCategory.Cafe, along.place.category)
        assertEquals(80, along.distanceFromRouteMeters)
        assertEquals(720, along.etaFromStartSeconds)
        assertFalse(plan.isEstimated)
    }

    @Test
    fun theSameStopsAreRoutedOnce() = runTest {
        repository.dayRoute(TRIP_ID, day(), TravelMode.Walking)
        repository.dayRoute(TRIP_ID, day(), TravelMode.Walking)
        assertEquals(1, requests.size)

        // Another mode, or a stop that moved, asks again.
        repository.dayRoute(TRIP_ID, day(), TravelMode.Driving)
        repository.dayRoute(TRIP_ID, day(secondStop = GeoPoint(48.21, 16.37)), TravelMode.Walking)
        assertEquals(3, requests.size)
    }

    @Test
    fun anEstimatedRouteIsAskedForAgain() = runTest {
        body = ROUTE.replace("\"degraded\": false", "\"degraded\": true")
        val plan = assertIs<AppResult.Success<RoutePlan>>(
            repository.route(GeoPoint(48.2, 16.3), GeoPoint(48.3, 16.4), TravelMode.Walking, TRIP_ID),
        ).data
        repository.route(GeoPoint(48.2, 16.3), GeoPoint(48.3, 16.4), TravelMode.Walking, TRIP_ID)

        assertTrue(plan.isEstimated)
        assertEquals(2, requests.size)
        assertEquals("48.2,16.3", requests.first().url.parameters["from"])
        assertEquals("48.3,16.4", requests.first().url.parameters["to"])
        assertEquals(TRIP_ID, requests.first().url.parameters["excludeTripId"])
    }

    @Test
    fun routingUnavailableIsAnError() = runTest {
        status = HttpStatusCode.ServiceUnavailable
        body = """{"error":{"code":"ROUTING_UNAVAILABLE","message":"Routes aren't available right now","details":{}}}"""

        val result = assertIs<AppResult.Error<DataError.Network>>(repository.dayRoute(TRIP_ID, day(), TravelMode.Walking))
        assertTrue(result.error.hasCode(RouteErrorCodes.ROUTING_UNAVAILABLE))
    }

    @Test
    fun aDayIsRoutedOnceItsStopChangesHaveReachedTheServer() = runTest {
        pending.begin(TRIP_ID) {}
        val route = async { repository.dayRoute(TRIP_ID, day(), TravelMode.Walking) }
        runCurrent()
        assertTrue(requests.isEmpty())

        pending.end(TRIP_ID)
        route.await()
        assertEquals(1, requests.size)
    }

    @Test
    fun bestOrderIsOnlyProposed() = runTest {
        body = """{"dayId":"d1","markerIds":["m1","m2"],"mode":"travel_time","savedMinutes":12.4,"degraded":false,"applied":false}"""

        val order = assertIs<AppResult.Success<BestOrder>>(
            repository.bestOrder(TRIP_ID, day(), TravelMode.Walking),
        ).data

        assertEquals(HttpMethod.Post, requests.single().method)
        assertEquals("/v1/days/d1/optimize", requests.single().url.encodedPath)
        assertEquals(null, requests.single().url.parameters["apply"])
        assertEquals(listOf("m1", "m2"), order.markerIds)
        assertTrue(order.byTravelTime)
        assertEquals(12, order.savedMinutes)
    }

    @Test
    fun decodesAnEncodedPolyline() {
        assertEquals(
            listOf(GeoPoint(38.5, -120.2), GeoPoint(40.7, -120.95), GeoPoint(43.252, -126.453)),
            decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@"),
        )
        assertTrue(decodePolyline("").isEmpty())
    }

    private fun day(secondStop: GeoPoint = GeoPoint(48.2104, 16.3655)) = TripDay(
        id = "d1",
        position = 0,
        date = null,
        markers = listOf(
            marker("m1", GeoPoint(48.2085, 16.3731), 0),
            marker("m2", secondStop, 1),
        ),
    )

    private fun marker(id: String, location: GeoPoint, position: Int) =
        TripMarker(id = id, tripId = TRIP_ID, dayId = "d1", placeId = "p-$id", name = id, location = location, position = position)

    private companion object {
        const val TRIP_ID = "t1"

        val ROUTE = """
            {
              "route": {
                "polyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
                "distanceMeters": 1200.4,
                "durationSeconds": 899.6,
                "legs": [{"fromMarkerId": "m1", "toMarkerId": "m2", "distanceMeters": 1200.4, "durationSeconds": 899.6}]
              },
              "alongTheWay": [{
                "place": {
                  "id": "p9", "name": "Café Central", "category": "cafe",
                  "location": {"lat": 48.2104, "lng": 16.3655},
                  "isTripinly": false, "hotCount": 0, "prominence": 40, "hotByMe": false,
                  "coverPhotoId": null, "coverImageId": null, "coverSource": null, "coverThumbUrl": null
                },
                "distanceFromRouteMeters": 80.2,
                "positionAlongRoute": 0.4,
                "etaFromStartSeconds": 720
              }],
              "degraded": false,
              "attribution": "© openrouteservice.org | © OpenStreetMap contributors"
            }
        """.trimIndent()
    }
}
