package com.falcon.tripingly.core.data.config

import com.falcon.tripingly.core.model.config.AppConfig
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class AppConfigRepositoryTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0")
    private var requests = 0

    private fun api(status: HttpStatusCode) = createKtorfit(
        createHttpClient(
            engine = MockEngine {
                requests++
                respond(
                    """{"placesRefreshSeconds": 60, "photoMaxEdgePx": 2048, "photoJpegQuality": 80, "photoParallelUploads": 3, "pinLargeMinProminence": 90, "pinMediumMinProminence": 50, "mapMaxPins": 200, "photoPinMinProminence": 80, "photoPinMinLikes": 5, "mapMaxPhotoPins": 10}""",
                    status,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            config = config,
            tokenStore = InMemoryTokenStore(),
            sessionEvents = SessionEvents(),
            languageTag = { "en" },
        ),
        config,
    ).createAppConfigApi()

    @Test
    fun `the server's values replace the defaults`() = runTest {
        val repository = DefaultAppConfigRepository(api(HttpStatusCode.OK), backgroundScope)

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
            photoPinMinLikes = 5,
            mapMaxPhotoPins = 10,
        )
        assertEquals(expected, loaded)
    }

    @Test
    fun `the defaults stay when the server can't answer`() = runTest {
        val repository = DefaultAppConfigRepository(api(HttpStatusCode.ServiceUnavailable), backgroundScope)

        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) { while (requests == 0) delay(10) }
            delay(100)
        }
        assertEquals(AppConfig(), repository.config.value)
    }
}
