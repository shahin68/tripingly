package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.PlaceCategory
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlaceRepositoryTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0")
    private val requests = mutableListOf<HttpRequestData>()
    private var status = HttpStatusCode.OK

    private val repository = DefaultPlaceRepository(
        createKtorfit(
            createHttpClient(
                engine = MockEngine { request ->
                    requests += request
                    respond(IN_VIEW, status, headersOf(HttpHeaders.ContentType, "application/json"))
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
    fun `asks for the visible area as plain decimals`() = runTest {
        val result = repository.placesInView(GeoBounds(48.2, 16.35, 48.22, 16.39), 15.4f)

        val places = assertIs<AppResult.Success<PlacesInView>>(result).data.places
        assertEquals("Stephansdom", places.single().name)
        assertEquals(PlaceCategory.Attraction, places.single().category)
        val url = requests.single().url
        assertEquals("/v1/places/in-view", url.encodedPath)
        assertEquals("16.350000,48.200000,16.390000,48.220000", url.parameters["bbox"])
        assertEquals(15.4, url.parameters["zoom"]!!.toDouble(), 0.001)
    }

    @Test
    fun `a small pan at the same zoom doesn't ask again`() = runTest {
        repository.placesInView(GeoBounds(48.2, 16.35, 48.22, 16.39), 15f)
        // Inside the area the server answered for (a grid of quarter tiles around the asked box).
        repository.placesInView(GeoBounds(48.201, 16.351, 48.219, 16.389), 15.6f)
        assertEquals(1, requests.size)

        // Zooming to the next level asks again.
        repository.placesInView(GeoBounds(48.205, 16.36, 48.215, 16.38), 16f)
        assertEquals(2, requests.size)
    }

    @Test
    fun `a failed answer is asked for again`() = runTest {
        status = HttpStatusCode.ServiceUnavailable
        repository.placesInView(GeoBounds(48.2, 16.35, 48.22, 16.39), 15f)
        status = HttpStatusCode.OK
        repository.placesInView(GeoBounds(48.2, 16.35, 48.22, 16.39), 15f)

        assertEquals(2, requests.size)
    }

    @Test
    fun `negative coordinates keep their sign`() = runTest {
        repository.placesInView(GeoBounds(-34.0, -58.5, -33.9, -58.3), 14f)

        assertEquals("-58.500000,-34.000000,-58.300000,-33.900000", requests.single().url.parameters["bbox"])
    }

    private companion object {
        val IN_VIEW = """
            {
              "places": [{
                "id": "p1", "name": "Stephansdom", "category": "attraction",
                "location": {"lat": 48.2085, "lng": 16.3731},
                "isTripinly": true, "likeCount": 3, "coverThumbUrl": null, "likedByMe": false
              }],
              "clusters": [],
              "attribution": "© OpenStreetMap contributors"
            }
        """.trimIndent()
    }
}
