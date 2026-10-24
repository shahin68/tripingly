package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.ImageCredit
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceImage
import com.falcon.tripingly.core.model.place.PlaceImageSource
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.place.PlacesInView
import com.falcon.tripingly.core.model.trip.UserSummary
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaceRepositoryTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0")
    private val requests = mutableListOf<HttpRequestData>()
    private val requestsLock = Mutex()
    private var status = HttpStatusCode.OK

    /** The name the server gives every place. */
    private var placeName = "Stephansdom"

    private val placesApi = createKtorfit(
        createHttpClient(
            engine = MockEngine { request ->
                // Requests of one batch arrive together.
                val count = requestsLock.withLock { requests += request; requests.size }
                val body = if (request.url.encodedPath.endsWith("places/search")) {
                    """{"items":[],"attribution":"© OpenStreetMap contributors"}"""
                } else if (request.url.encodedPath.endsWith("places/p1")) {
                    PLACE_DETAIL
                } else if (request.url.encodedPath.endsWith("places/u1")) {
                    USER_PLACE_DETAIL
                } else {
                    // The photo's link is signed anew on every answer.
                    tilesAnswer(request.url.parameters["tiles"].orEmpty(), placeName, signature = count)
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
            config = config,
            tokenStore = InMemoryTokenStore(),
            sessionEvents = SessionEvents(),
            languageTag = { "en" },
        ),
        config,
    ).createPlacesApi()

    private fun TestScope.repository(placesRefresh: Duration = AppConfig().placesRefresh) = DefaultPlaceRepository(
        api = placesApi,
        appConfig = object : AppConfigRepository {
            override val config = MutableStateFlow(AppConfig(placesRefresh = placesRefresh))
        },
        scope = backgroundScope,
    )

    @Test
    fun `a destination search asks for areas only and the map search for everything`() = runTest {
        val repository = repository()

        repository.searchDestinations(" Vienn ")
        repository.search("Vienn")

        val (destination, map) = requests.map { it.url.parameters }
        assertEquals("Vienn", destination["q"])
        assertEquals("destination", destination["scope"])
        assertNull(map["scope"])
    }

    @Test
    fun `squares asked for together go in one request`() = runTest {
        val repository = repository()
        val square = PlaceSquare(15, 372, 1096)
        val (first, second) = listOf(
            async { repository.placesIn(listOf(square)) },
            async { repository.placesIn(listOf(PlaceSquare(15, 373, 1096), square)) },
        ).awaitAll()

        val places = assertIs<AppResult.Success<Map<PlaceSquare, PlacesInView>>>(first).data.getValue(square).places
        assertEquals("Stephansdom", places.single().name)
        assertEquals(PlaceCategory.Attraction, places.single().category)
        assertEquals(90, places.single().prominence)
        assertEquals(2, assertIs<AppResult.Success<Map<PlaceSquare, PlacesInView>>>(second).data.size)
        val url = requests.single().url
        assertEquals("/v1/places/tiles", url.encodedPath)
        assertEquals(setOf("13/372/1096", "13/373/1096"), url.parameters["tiles"]!!.split(",").toSet())
        assertEquals("15", url.parameters["zoom"])
    }

    @Test
    fun `a loaded square isn't asked for again`() = runTest {
        val repository = repository()
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
        val repository = repository()
        repository.placesIn((0 until 20).map { PlaceSquare(15, it, 1096) })

        assertEquals(listOf(4, 16), requests.map { it.url.parameters["tiles"]!!.split(",").size }.sorted())
    }

    @Test
    fun `a failed square is asked for again`() = runTest {
        val repository = repository()
        status = HttpStatusCode.ServiceUnavailable
        assertIs<AppResult.Error<*>>(repository.placesIn(listOf(PlaceSquare(15, 372, 1096))))
        assertTrue(repository.loadedPlacesIn(listOf(PlaceSquare(15, 372, 1096))).isEmpty())
        status = HttpStatusCode.OK
        assertIs<AppResult.Success<*>>(repository.placesIn(listOf(PlaceSquare(15, 372, 1096))))

        assertEquals(2, requests.size)
    }

    @Test
    fun `an old square is answered at once and reloaded in the background`() = runTest {
        val repository = repository(placesRefresh = Duration.ZERO)
        val square = PlaceSquare(15, 372, 1096)
        val changed = mutableListOf<PlaceSquare>()
        backgroundScope.launch { repository.changedSquares.collect { changed += it } }
        repository.placesIn(listOf(square))

        // Answered from what's kept, then reloaded; nothing new (only the photo's link), so nothing to redraw.
        val again = assertIs<AppResult.Success<Map<PlaceSquare, PlacesInView>>>(repository.placesIn(listOf(square)))
        assertEquals("Stephansdom", again.data.getValue(square).places.single().name)
        eventually { requests.size == 2 }

        // Something new: the square is redrawn once the reload brings it.
        placeName = "Stephansdom (renamed)"
        eventually {
            repository.placesIn(listOf(square))
            changed.isNotEmpty()
        }
        assertEquals(listOf(square), changed)
        assertEquals("Stephansdom (renamed)", repository.loadedPlacesIn(listOf(square)).getValue(square).places.single().name)
    }

    @Test
    fun `a failed reload keeps the places shown and is tried again next time`() = runTest {
        val repository = repository(placesRefresh = Duration.ZERO)
        val square = PlaceSquare(15, 372, 1096)
        repository.placesIn(listOf(square))

        status = HttpStatusCode.ServiceUnavailable
        assertIs<AppResult.Success<*>>(repository.placesIn(listOf(square)))
        eventually {
            repository.placesIn(listOf(square))
            requests.size >= 3
        }

        assertEquals("Stephansdom", repository.loadedPlacesIn(listOf(square)).getValue(square).places.single().name)
    }

    /** Waits in real time: the requests run on the network's own threads. */
    private suspend fun eventually(check: suspend () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5.seconds) { while (!check()) delay(10) }
    }

    @Test
    fun `a place's images keep their order and Commons images keep their credit`() = runTest {
        val details = assertIs<AppResult.Success<PlaceDetails>>(repository().place("p1")).data

        assertEquals(
            listOf(
                PlaceImage("ph1", PlaceImageSource.Trip, "https://r2/ph1/thumb", "https://r2/ph1/display", 1600, 1200, credit = null),
                PlaceImage(
                    id = "pi1",
                    source = PlaceImageSource.Commons,
                    thumbUrl = "https://r2/pi1/thumb",
                    displayUrl = "https://r2/pi1/display",
                    width = 1280,
                    height = 960,
                    credit = ImageCredit(
                        author = "Jane Doe",
                        title = null,
                        license = "CC BY-SA 4.0",
                        licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0",
                        sourceUrl = "https://commons.wikimedia.org/wiki/File:Stephansdom_Wien.jpg",
                    ),
                ),
            ),
            details.images,
        )
        assertNull(details.addedBy)
    }

    @Test
    fun `a custom spot credits whoever added it`() = runTest {
        val details = assertIs<AppResult.Success<PlaceDetails>>(repository().place("u1")).data

        assertEquals(UserSummary(id = "u-anna", username = "anna", displayName = "Anna"), details.addedBy)
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
        val PLACE_DETAIL = """
            {
              "id": "p1", "name": "Stephansdom", "category": "attraction",
              "location": {"lat": 48.2085, "lng": 16.3731},
              "isTripinly": true, "hotCount": 3, "prominence": 90,
              "coverPhotoId": "ph1", "coverImageId": "ph1", "coverSource": "trip", "coverThumbUrl": "https://r2/ph1/thumb",
              "hotByMe": false, "source": "osm", "isActive": true, "osmType": "way", "osmId": "1",
              "tags": {"openingHours": null, "website": null, "cuisine": null, "wikidata": null},
              "photoThumbUrls": ["https://r2/ph1/thumb"],
              "images": [
                {"id": "ph1", "source": "trip", "thumbUrl": "https://r2/ph1/thumb", "displayUrl": "https://r2/ph1/display",
                 "width": 1600, "height": 1200, "credit": null},
                {"id": "pi1", "source": "commons", "thumbUrl": "https://r2/pi1/thumb", "displayUrl": "https://r2/pi1/display",
                 "width": 1280, "height": 960,
                 "credit": {"author": "Jane Doe", "title": null, "license": "CC BY-SA 4.0",
                            "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0",
                            "sourceUrl": "https://commons.wikimedia.org/wiki/File:Stephansdom_Wien.jpg"}}
              ],
              "addedBy": null,
              "attribution": "© OpenStreetMap contributors"
            }
        """.trimIndent()

        val USER_PLACE_DETAIL = PLACE_DETAIL
            .replace("\"id\": \"p1\"", "\"id\": \"u1\"")
            .replace("\"source\": \"osm\"", "\"source\": \"user\"")
            .replace("\"addedBy\": null", "\"addedBy\": {\"id\": \"u-anna\", \"username\": \"anna\", \"displayName\": \"Anna\"}")

        fun tilesAnswer(tiles: String, placeName: String, signature: Int) = tiles.split(",").joinToString(
            prefix = "{\"tiles\": [",
            postfix = "], \"attribution\": \"© OpenStreetMap contributors\"}",
        ) { tile ->
            """
                {
                  "tile": "$tile",
                  "places": [{
                    "id": "p-$tile", "name": "$placeName", "category": "attraction",
                    "location": {"lat": 48.2085, "lng": 16.3731},
                    "isTripinly": true, "hotCount": 3, "prominence": 90, "hotByMe": false,
                    "coverImageId": "img-$tile", "coverThumbUrl": "https://images.test/img-$tile?sig=$signature"
                  }],
                  "clusters": []
                }
            """.trimIndent()
        }
    }
}
