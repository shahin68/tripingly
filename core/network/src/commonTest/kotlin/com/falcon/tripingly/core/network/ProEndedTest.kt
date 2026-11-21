package com.falcon.tripingly.core.network

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class ProEndedTest {

    @Serializable
    private data class Route(val id: String)

    private fun TestApi.proEndedCount(scope: kotlinx.coroutines.CoroutineScope): () -> Int {
        var count = 0
        scope.launch(UnconfinedTestDispatcher()) { sessionEvents.proEnded.collect { count++ } }
        return { count }
    }

    @Test
    fun premiumRequired_tellsTheApp_andTheCallerStillGetsTheCode() = runTest {
        val api = TestApi { apiError(HttpStatusCode.Forbidden, "PREMIUM_REQUIRED") }
        val count = api.proEndedCount(backgroundScope)

        val result = apiCall<Route> { api.client.get("routes/day") }

        assertEquals(1, count())
        assertEquals("PREMIUM_REQUIRED", ((result as AppResult.Error).error as DataError.Network.Api).code)
    }

    @Test
    fun otherForbidden_isNotAboutPro() = runTest {
        val api = TestApi { apiError(HttpStatusCode.Forbidden, "TRIP_PRIVATE") }
        val count = api.proEndedCount(backgroundScope)

        apiCall<Route> { api.client.get("trips/t1") }

        assertEquals(0, count())
    }
}
