package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.place.PlacesInView
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
import com.falcon.tripingly.core.network.places.createPlacesApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaceRepositoryTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0")
    private val requests = mutableListOf<HttpRequestData>()
    private val requestsLock = Mutex()
    private var status = HttpStatusCode.OK

    private val repository = DefaultPlaceRepository(
        createKtorfit(
            createHttpClient(
                engine = MockEngine { request ->
                    // Requests of one batch arrive together.
                    requestsLock.withLock { requests += request }
                    respond(tilesAnswer(request.url.parameters["tiles"].orEmpty()), status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
                config = config,
                tokenStore = InMemoryTokenStore(),
                sessionEvents = SessionEvents(),
                languageTag = { "en" },
            ),
            config,
        ).createPlacesApi(),
    )

    @Test
    fun `squares asked for together go in one request`() = runTest {
        val square = PlaceSquare(15, 372, 1096)
        val (first, second) = listOf(
            async { repository.placesIn(listOf(square)) },
            async { repository.placesIn(listOf(PlaceSquare(15, 373, 1096), square)) },
        ).awaitAll()

        val places = assertIs<AppResult.Success<Map<PlaceSquare, PlacesInView>>>(first).data.getValue(square).places
        assertEquals("Stephansdom", places.single().name)
        assertEquals(PlaceCategory.Attraction, places.single().category)
        assertEquals(2, assertIs<AppResult.Success<Map<PlaceSquare, PlacesInView>>>(second).data.size)
        val url = requests.single().url
        assertEquals("/v1/places/tiles", url.encodedPath)
        assertEquals(setOf("13/372/1096", "13/373/1096"), url.parameters["tiles"]!!.split(",").toSet())
        assertEquals("15", url.parameters["zoom"])
    }

    @Test
    fun `a loaded square isn't asked for again`() = runTest {
        repository.placesIn(listOf(PlaceSquare(15, 372, 1096)))
        repository.placesIn(listOf(PlaceSquare(15, 372, 1096)))
        assertEquals(1, requests.size)
        assertEquals(1, repository.loadedPlacesIn(listOf(PlaceSquare(15, 372, 1096), PlaceSquare(15, 1, 1))).size)

        // The next zoom has its own squares.
        repository.placesIn(listOf(PlaceSquare(16, 744, 2192)))
        assertEquals(2, requests.size)
    }

    @Test
    fun `at most 16 squares go in one request`() = runTest {
        repository.placesIn((0 until 20).map { PlaceSquare(15, it, 1096) })

        assertEquals(listOf(4, 16), requests.map { it.url.parameters["tiles"]!!.split(",").size }.sorted())
    }

    @Test
    fun `a failed square is asked for again`() = runTest {
        status = HttpStatusCode.ServiceUnavailable
        assertIs<AppResult.Error<*>>(repository.placesIn(listOf(PlaceSquare(15, 372, 1096))))
        assertTrue(repository.loadedPlacesIn(listOf(PlaceSquare(15, 372, 1096))).isEmpty())
        status = HttpStatusCode.OK
        assertIs<AppResult.Success<*>>(repository.placesIn(listOf(PlaceSquare(15, 372, 1096))))

        assertEquals(2, requests.size)
    }

    @Test
    fun `squares cover the view on the server's grid`() {
        // Zoom 15 loads squares of level 13, 360 / 2^13 ≈ 0.044° a side.
        val squares = PlaceSquare.covering(GeoBounds(48.20, 16.35, 48.22, 16.39), 15)
        assertEquals(listOf(PlaceSquare(15, 372, 1096), PlaceSquare(15, 372, 1097)), squares)
        assertEquals(12, PlaceSquare.covering(GeoBounds(48.20, 16.35, 48.22, 16.39), 15, ring = 1).size)
        // West of Greenwich and south of the equator, squares count down from -1.
        assertEquals(PlaceSquare(15, -1, -1), PlaceSquare.covering(GeoBounds(-0.01, -0.01, -0.005, -0.005), 15).single())
        // Across the antimeridian, both sides.
        assertEquals(setOf(-4096, 4095), PlaceSquare.covering(GeoBounds(10.0, 179.99, 10.01, -179.99), 15).map { it.x }.toSet())
    }

    private companion object {
        fun tilesAnswer(tiles: String) = tiles.split(",").joinToString(
            prefix = "{\"tiles\": [",
            postfix = "], \"attribution\": \"© OpenStreetMap contributors\"}",
        ) { tile ->
            """
                {
                  "tile": "$tile",
                  "places": [{
                    "id": "p-$tile", "name": "Stephansdom", "category": "attraction",
                    "location": {"lat": 48.2085, "lng": 16.3731},
                    "isTripinly": true, "likeCount": 3, "coverThumbUrl": null, "likedByMe": false
                  }],
                  "clusters": []
                }
            """.trimIndent()
        }
    }
}
