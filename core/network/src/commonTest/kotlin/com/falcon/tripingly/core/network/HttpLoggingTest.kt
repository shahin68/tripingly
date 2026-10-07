package com.falcon.tripingly.core.network

import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpLoggingTest {

    @Test
    fun redactForLog_hidesSecretsPersonalDataAndLocations() {
        val body = """{"accessToken":"a.b.c","refreshToken":"r1","email":"x@y.z","birthDate":"2000-01-01",""" +
            """"location":{"lat":48.8584,"lng":-2.29},"coverThumbUrl":"https://cdn/x?sig=1","url":"https://t/invites/abc","name":"Louvre"}"""

        val redacted = redactForLog(body)

        assertEquals(
            """{"accessToken":"██","refreshToken":"██","email":"██","birthDate":"██",""" +
                """"location":{"lat":"██","lng":"██"},"coverThumbUrl":"██","url":"██","name":"Louvre"}""",
            redacted,
        )
    }

    @Test
    fun redactForLog_hidesLocationQueriesAndInviteTokens() {
        assertEquals(
            "--> GET https://api.test/v1/places/nearby?lat=██&lng=██&radius=500",
            redactForLog("--> GET https://api.test/v1/places/nearby?lat=48.85&lng=2.35&radius=500"),
        )
        assertEquals(
            "GET https://api.test/v1/places/in-view?bbox=██&q=cafe",
            redactForLog("GET https://api.test/v1/places/in-view?bbox=2.1,48.8,2.4,48.9&q=cafe"),
        )
        assertEquals("--> GET https://api.test/v1/invites/██", redactForLog("--> GET https://api.test/v1/invites/secret-token"))
    }

    @Test
    fun bodyLevel_logsHeadersAndBodies_withoutSecrets() = runTest {
        val lines = mutableListOf<String>()
        val client = createHttpClient(
            engine = MockEngine { json("""{"accessToken":"server-token","name":"Paris"}""") },
            config = testConfig.copy(httpLogLevel = HttpLogLevel.BODY),
            tokenStore = InMemoryTokenStore(),
            sessionEvents = SessionEvents(),
            languageTag = { "en" },
            log = { lines += it },
        )

        client.post("auth/dev") {
            header("X-Dev-Auth-Secret", "dev-secret")
            contentType(ContentType.Application.Json)
            setBody("""{"email":"me@example.com","subject":"s1"}""")
        }.bodyAsText()
        val log = lines.joinToString("\n")

        assertTrue("/v1/auth/dev" in log, log)
        assertTrue("Paris" in log, log)
        assertTrue("s1" in log, log)
        assertFalse("server-token" in log, log)
        assertFalse("me@example.com" in log, log)
        assertFalse("dev-secret" in log, log)
    }

    @Test
    fun basicLevel_logsNoHeadersOrBodies() = runTest {
        val lines = mutableListOf<String>()
        val client = createHttpClient(
            engine = MockEngine { json("""{"name":"Paris"}""") },
            config = testConfig.copy(httpLogLevel = HttpLogLevel.BASIC),
            tokenStore = InMemoryTokenStore(),
            sessionEvents = SessionEvents(),
            languageTag = { "en" },
            log = { lines += it },
        )

        client.get("trips/t1") { header(HttpHeaders.Authorization, "Bearer abc") }.bodyAsText()
        val log = lines.joinToString("\n")

        assertTrue("/v1/trips/t1" in log, log)
        assertFalse("Paris" in log, log)
        assertFalse("abc" in log, log)
    }

    @Test
    fun noneLevel_logsNothing() = runTest {
        val lines = mutableListOf<String>()
        val client = createHttpClient(
            engine = MockEngine { json("""{"name":"Paris"}""") },
            config = testConfig,
            tokenStore = InMemoryTokenStore(),
            sessionEvents = SessionEvents(),
            languageTag = { "en" },
            log = { lines += it },
        )

        client.get("trips/t1").bodyAsText()

        assertTrue(lines.isEmpty())
    }
}
