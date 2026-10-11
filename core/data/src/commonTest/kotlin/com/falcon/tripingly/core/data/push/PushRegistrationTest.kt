package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.AppLanguage
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
import com.falcon.tripingly.core.network.push.createPushApi
import com.falcon.tripingly.core.storage.SecureStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PushRegistrationTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sent = MutableStateFlow<List<HttpRequestData>>(emptyList())
    private val store = MapSecureStore()
    private var language = "de-AT"
    private var userId: String? = "u1"

    private val messaging = object : PushMessaging {
        var current: String? = "token-1"
        override suspend fun token() = current
        override val newTokens = MutableSharedFlow<String>()
    }

    @AfterTest
    fun cancel() = scope.cancel()

    private fun registration(): PushRegistration {
        val engine = MockEngine { request ->
            sent.update { it + request }
            respond("", HttpStatusCode.NoContent)
        }
        val api = createKtorfit(createHttpClient(engine, config, InMemoryTokenStore(), SessionEvents(), languageTag = { "en" }), config).createPushApi()
        return PushRegistration(messaging, api, store, config, AppLanguage { language }, currentUserId = { userId }, scope = scope)
    }

    private suspend fun awaitRequests(count: Int) = realTime { sent.first { it.size >= count } }

    @Test
    fun registersOnce_untilTheAccountTokenOrLanguageChanges() = runTest {
        val push = registration()

        push.start()
        awaitRequests(1)
        val put = sent.value.single()
        assertEquals(HttpMethod.Put, put.method)
        assertEquals("/v1/me/devices/token-1", put.url.encodedPath)
        assertEquals("""{"platform":"android","locale":"de-AT"}""", (put.body as TextContent).text)

        // The next launch asks nothing.
        push.start()
        realTime { delay(100.milliseconds) }
        assertEquals(1, sent.value.size)

        language = "hu"
        push.start()
        awaitRequests(2)

        messaging.newTokens.emit("token-2")
        awaitRequests(3)
        assertEquals("/v1/me/devices/token-2", sent.value[2].url.encodedPath)
    }

    @Test
    fun signedOut_registersNothing() = runTest {
        userId = null
        registration().start()
        realTime { delay(100.milliseconds) }
        assertEquals(0, sent.value.size)
    }

    @Test
    fun unregister_removesTheDevice_andTheNextAccountRegistersAgain() = runTest {
        val push = registration()
        push.start()
        awaitRequests(1)

        push.unregister()
        assertEquals(HttpMethod.Delete, sent.value[1].method)
        assertEquals("/v1/me/devices/token-1", sent.value[1].url.encodedPath)

        userId = "u2"
        push.start()
        awaitRequests(3)
        assertEquals(HttpMethod.Put, sent.value[2].method)
    }

    @Test
    fun opened_marksTheNotificationRead() = runTest {
        registration().opened("n1")
        val read = sent.value.single()
        assertEquals("/v1/notifications/read", read.url.encodedPath)
        assertEquals("""{"ids":["n1"]}""", (read.body as TextContent).text)
    }

    private class MapSecureStore : SecureStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun get(key: String) = values[key]
        override suspend fun put(key: String, value: String) {
            values[key] = value
        }
        override suspend fun remove(key: String) {
            values.remove(key)
        }
    }
}

private suspend fun <T> realTime(block: suspend () -> T): T =
    withContext(Dispatchers.Default) { withTimeout(10.seconds) { block() } }
