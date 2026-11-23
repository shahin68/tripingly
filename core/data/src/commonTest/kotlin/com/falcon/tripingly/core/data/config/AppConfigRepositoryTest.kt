package com.falcon.tripingly.core.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.config.ConfigItem
import com.falcon.tripingly.core.model.config.TransitArea
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceKind
import com.falcon.tripingly.core.model.place.PlaceKinds
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.config.createAppConfigApi
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AppConfigRepositoryTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0")
    private var requests = 0
    private val store = MemoryStore()
    private var language = "en"

    private fun api(status: HttpStatusCode) = createKtorfit(
        createHttpClient(
            engine = MockEngine { request ->
                requests++
                val cafe = if (request.headers[HttpHeaders.AcceptLanguage] == "de") "Kaffeehaus" else "Café"
                respond(
                    """{"placesRefreshSeconds": 60, "photoMaxEdgePx": 2048, "photoJpegQuality": 80, "photoParallelUploads": 3, "pinLargeMinProminence": 90, "pinMediumMinProminence": 50, "mapMaxPins": 200, "photoPinMinProminence": 80, "photoPinMinHotCount": 5, "mapMaxPhotoPins": 10, "pinDotBelowZoom": 13.5, "osmPinsEnabled": false, "transitAreas": [{"id": "vienna", "name": "Vienna", "south": 48.11, "west": 16.18, "north": 48.33, "east": 16.58}], "placeKinds": [{"key": "other", "label": "Spot", "icon": 1, "forSpots": true, "forFilters": false}, {"key": "cafe", "label": "$cafe", "icon": 4, "forSpots": true, "forFilters": true}], "explorePeriods": [{"key": "month", "label": "This month", "icon": 60, "isDefault": true}], "reportReasons": [{"key": "spam", "label": "Spam"}]}""",
                    status,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            config = config,
            tokenStore = InMemoryTokenStore(),
            sessionEvents = SessionEvents(),
            languageTag = { language },
        ),
        config,
    ).createAppConfigApi()

    @Test
    fun `the server's values replace the defaults`() = runTest {
        val repository = DefaultAppConfigRepository(api(HttpStatusCode.OK), store, { language }, backgroundScope)

        // The network runs on its own threads, so wait in real time.
        val loaded = withContext(Dispatchers.Default) {
            withTimeout(5.seconds) { repository.config.first { it != AppConfig() } }
        }
        val expected = AppConfig(
            placesRefresh = 60.seconds,
            photoMaxEdgePx = 2048,
            photoJpegQuality = 80,
            photoParallelUploads = 3,
            pinLargeMinProminence = 90,
            pinMediumMinProminence = 50,
            mapMaxPins = 200,
            photoPinMinProminence = 80,
            photoPinMinHotCount = 5,
            mapMaxPhotoPins = 10,
            pinDotBelowZoom = 13.5f,
            osmPinsEnabled = false,
            transitAreas = listOf(TransitArea("vienna", "Vienna", 48.11, 16.18, 48.33, 16.58)),
            placeKinds = PlaceKinds(
                listOf(
                    PlaceKind(PlaceCategory.Other, "Spot", 1, forSpots = true, forFilters = false),
                    PlaceKind(PlaceCategory("cafe"), "Café", 4, forSpots = true, forFilters = true),
                ),
            ),
            explorePeriods = listOf(ConfigItem("month", "This month", 60, isDefault = true)),
            reportReasons = listOf(ConfigItem("spam", "Spam")),
        )
        assertEquals(expected, loaded)
        assertEquals("Café", loaded.placeKinds.of(PlaceCategory("cafe")).label)
    }

    @Test
    fun `a kind the list doesn't have shows as Spot`() {
        val kinds = PlaceKinds(listOf(PlaceKind(PlaceCategory.Other, "Spot", 1, forSpots = true, forFilters = false)))

        val unknown = kinds.of(PlaceCategory("ice_cream"))
        assertEquals("ice_cream", unknown.category.key)
        assertEquals("Spot", unknown.label)
        assertEquals(PlaceKind.SPOT_ICON, unknown.icon)
        assertEquals("Spot", kinds.of(null).label)
    }

    @Test
    fun `a new language reads the lists again, labelled in it`() = runTest {
        val repository = DefaultAppConfigRepository(api(HttpStatusCode.OK), store, { language }, backgroundScope)
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { repository.config.first { it != AppConfig() } } }

        repository.refreshIfLanguageChanged()
        withContext(Dispatchers.Default) { delay(100) }
        assertEquals(1, requests)

        language = "de"
        repository.refreshIfLanguageChanged()
        val german = withContext(Dispatchers.Default) {
            withTimeout(5.seconds) { repository.config.first { it.placeKinds.of(PlaceCategory("cafe")).label == "Kaffeehaus" } }
        }
        assertEquals("Kaffeehaus", german.placeKinds.of(PlaceCategory("cafe")).label)
        assertEquals(2, requests)
    }

    @Test
    fun `the defaults stay when the server can't answer`() = runTest {
        val repository = DefaultAppConfigRepository(api(HttpStatusCode.ServiceUnavailable), store, { language }, backgroundScope)

        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) { while (requests == 0) delay(10) }
            delay(100)
        }
        assertEquals(AppConfig(), repository.config.value)
    }

    @Test
    fun theSavedValuesComeFirst_alsoWhenTheServerCantAnswer() = runTest {
        val online = DefaultAppConfigRepository(api(HttpStatusCode.OK), store, { language }, backgroundScope)
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { online.config.first { it != AppConfig() } } }

        val offline = DefaultAppConfigRepository(api(HttpStatusCode.ServiceUnavailable), store, { language }, backgroundScope)

        val loaded = withContext(Dispatchers.Default) { withTimeout(5.seconds) { offline.config.first { it != AppConfig() } } }
        assertEquals(200, loaded.mapMaxPins)
        assertEquals(false, loaded.osmPinsEnabled)
    }

    /** DataStore in memory: what `edit` writes, `data` reads back. */
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }
}
