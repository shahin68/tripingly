package com.falcon.tripingly.core.data.account

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.LegalDocumentType
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.SignOutReason
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.account.createAccountApi
import com.falcon.tripingly.core.network.auth.AuthTokens
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.auth.createAuthApi
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRepositoryTest {

    private val config = ApiConfig(
        baseUrl = "https://api.test/v1",
        environment = "test",
        client = "android/1.0",
        logRequests = false,
        developerSignIn = true,
        devAuthSecret = "s3cret",
    )

    private val tokensJson = """
        {"accessToken":"access-1","accessTokenExpiresAt":"2026-10-03T16:00:00Z",
         "refreshToken":"refresh-1","refreshTokenExpiresAt":"2026-12-02T16:00:00Z",
         "onboardingRequired":true}
    """.trimIndent()

    private fun meJson(completed: Boolean, missingFields: String = "[]", missingConsents: String = "[]") = """
        {"id":"u1","username":${if (completed) "\"jonas.k\"" else "null"},"displayName":"Jonas",
         "birthDate":${if (completed) "\"1995-04-12\"" else "null"},"locale":"de-AT",
         "defaultTripVisibility":"public","role":"user",
         "onboarding":{"completed":$completed,"missingProfileFields":$missingFields,"missingConsents":$missingConsents},
         "entitlements":[]}
    """.trimIndent()

    private inner class Harness(
        scope: TestScope,
        private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        val tokenStore = InMemoryTokenStore()
        private val lock = Mutex()
        private val recorded = mutableListOf<HttpRequestData>()
        val requests: List<HttpRequestData> get() = recorded.toList()
        private val sessionEvents = SessionEvents()
        private val ktorfit = createKtorfit(
            createHttpClient(
                engine = MockEngine { request ->
                    lock.withLock { recorded += request }
                    handler(request)
                },
                config = config,
                tokenStore = tokenStore,
                sessionEvents = sessionEvents,
                languageTag = { "de-AT" },
            ),
            config,
        )
        val session = SessionRepositoryImpl(
            authApi = ktorfit.createAuthApi(),
            accountApi = ktorfit.createAccountApi(),
            tokenStore = tokenStore,
            sessionEvents = sessionEvents,
            developerSignIn = DeveloperSignIn(config.devAuthSecret),
            scope = scope.backgroundScope,
        )
        val account = AccountRepositoryImpl(ktorfit.createAccountApi(), session)

        fun paths(method: HttpMethod) = requests.filter { it.method == method }.map { it.url.encodedPath }
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.apiError(status: HttpStatusCode, code: String) =
        json("""{"error":{"code":"$code","message":"Localized text","details":{}}}""", status)

    @Test
    fun restore_withoutTokens_isSignedOut() = runTest {
        val harness = Harness(this) { error("no request expected") }

        harness.session.restore()

        assertEquals(SessionState.SignedOut(), harness.session.session.value)
    }

    @Test
    fun googleSignIn_savesTokensAndRoutesToOnboarding() = runTest {
        val harness = Harness(this) { request ->
            when (request.url.encodedPath) {
                "/v1/auth/google" -> json(tokensJson)
                "/v1/me" -> json(meJson(completed = false, missingFields = """["username","birthDate"]"""))
                else -> error("unexpected ${request.url}")
            }
        }

        val result = harness.session.signInWithGoogle("google-id-token")

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(AuthTokens("access-1", "refresh-1"), harness.tokenStore.get())
        val state = assertIs<SessionState.Onboarding>(harness.session.session.value)
        assertEquals("Jonas", state.account.displayName)
        val body = (harness.requests.first().body as TextContent).text
        assertEquals("""{"idToken":"google-id-token"}""", body)
    }

    @Test
    fun developerSignIn_sendsTheSecret() = runTest {
        val harness = Harness(this) { request ->
            when (request.url.encodedPath) {
                "/v1/auth/dev" -> json(tokensJson)
                else -> json(meJson(completed = true))
            }
        }

        harness.session.signInForDevelopment("tester-1", "Tester")

        assertEquals("s3cret", harness.requests.first().headers["X-Dev-Auth-Secret"])
        assertIs<SessionState.SignedIn>(harness.session.session.value)
    }

    @Test
    fun signInWhoseProfileFails_forgetsTheTokens() = runTest {
        val harness = Harness(this) { request ->
            when (request.url.encodedPath) {
                "/v1/auth/google" -> json(tokensJson)
                else -> throw IOException("offline")
            }
        }

        val result = harness.session.signInWithGoogle("google-id-token")

        assertEquals(AppResult.Error(DataError.Network.NoInternet), result)
        assertNull(harness.tokenStore.get())
        assertEquals(SessionState.SignedOut(), harness.session.session.value)
    }

    @Test
    fun restore_withTokens_isSignedInWhenOnboarded() = runTest {
        val harness = Harness(this) { json(meJson(completed = true)) }
        harness.tokenStore.save(AuthTokens("access-1", "refresh-1"))

        harness.session.restore()

        val state = assertIs<SessionState.SignedIn>(harness.session.session.value)
        assertEquals(LocalDate(1995, 4, 12), state.account.birthDate)
    }

    @Test
    fun restore_offline_keepsTokensAndStaysRestoring() = runTest {
        val harness = Harness(this) { throw IOException("offline") }
        harness.tokenStore.save(AuthTokens("access-1", "refresh-1"))

        val result = harness.session.restore()

        assertEquals(AppResult.Error(DataError.Network.NoInternet), result)
        assertEquals(SessionState.Restoring, harness.session.session.value)
        assertEquals(AuthTokens("access-1", "refresh-1"), harness.tokenStore.get())
    }

    @Test
    fun refusedRefresh_signsOutAsExpired() = runTest {
        val harness = Harness(this) { request ->
            when (request.url.encodedPath) {
                "/v1/auth/refresh" -> apiError(HttpStatusCode.Unauthorized, "REFRESH_TOKEN_REUSED")
                else -> apiError(HttpStatusCode.Unauthorized, "TOKEN_EXPIRED")
            }
        }
        harness.tokenStore.save(AuthTokens("access-1", "refresh-1"))
        runCurrent()

        harness.session.restore()
        runCurrent()

        assertEquals(SessionState.SignedOut(SignOutReason.SESSION_EXPIRED), harness.session.session.value)
        assertNull(harness.tokenStore.get())
    }

    @Test
    fun underAgeBirthDate_signsOutAndForgetsTheTokens() = runTest {
        val harness = Harness(this) { request ->
            when (request.url.encodedPath) {
                "/v1/me" -> if (request.method == HttpMethod.Patch) {
                    apiError(HttpStatusCode.UnprocessableEntity, "AGE_REQUIREMENT_NOT_MET")
                } else {
                    json(meJson(completed = false))
                }
                else -> error("unexpected ${request.url}")
            }
        }
        harness.tokenStore.save(AuthTokens("access-1", "refresh-1"))
        harness.session.restore()

        val result = harness.account.updateProfile(ProfileUpdate(birthDate = LocalDate(2015, 1, 1)))

        assertEquals("AGE_REQUIREMENT_NOT_MET", ((result as AppResult.Error).error as DataError.Network.Api).code)
        assertEquals(SessionState.SignedOut(SignOutReason.UNDER_AGE), harness.session.session.value)
        assertNull(harness.tokenStore.get())
    }

    @Test
    fun acceptingDocuments_recordsEachThenReloadsTheProfile() = runTest {
        var accepted = 0
        val harness = Harness(this) { request ->
            when (request.url.encodedPath) {
                "/v1/me/consents" -> {
                    accepted++
                    json("""{"items":[],"missingRequired":[]}""")
                }
                "/v1/me" -> json(meJson(completed = accepted == 2))
                else -> error("unexpected ${request.url}")
            }
        }
        harness.tokenStore.save(AuthTokens("access-1", "refresh-1"))
        val documents = listOf(
            LegalDocument(LegalDocumentType.TERMS, "2026-09-01", "en", "https://t", required = true),
            LegalDocument(LegalDocumentType.PRIVACY, "2026-09-01", "en", "https://p", required = true),
        )

        harness.account.accept(documents)

        assertEquals(listOf("/v1/me/consents", "/v1/me/consents"), harness.paths(HttpMethod.Post))
        val first = (harness.requests.first().body as TextContent).text
        assertTrue(first.contains(""""documentType":"terms"""") && first.contains(""""granted":true"""))
        assertIs<SessionState.SignedIn>(harness.session.session.value)
    }

    @Test
    fun signOut_revokesTheSessionAndClearsTokens() = runTest {
        val harness = Harness(this) { respond("", HttpStatusCode.NoContent) }
        harness.tokenStore.save(AuthTokens("access-1", "refresh-1"))

        harness.session.signOut()

        assertEquals(listOf("/v1/auth/logout"), harness.paths(HttpMethod.Post))
        assertNull(harness.tokenStore.get())
        assertEquals(SessionState.SignedOut(), harness.session.session.value)
    }
}
