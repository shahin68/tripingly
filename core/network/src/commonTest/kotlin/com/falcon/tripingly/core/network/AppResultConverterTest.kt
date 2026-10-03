package com.falcon.tripingly.core.network

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import de.jensklingenberg.ktorfit.converter.Converter
import de.jensklingenberg.ktorfit.converter.KtorfitResult
import de.jensklingenberg.ktorfit.converter.TypeData
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.util.reflect.typeInfo
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppResultConverterTest {

    @Serializable
    private data class Ping(val status: String)

    private val api = TestApi { request ->
        when (request.url.encodedPath) {
            "/v1/pings" -> json("""[{"status":"ok"},{"status":"late"}]""")
            else -> apiError(HttpStatusCode.NotFound, "NOT_FOUND")
        }
    }
    private val ktorfit = createKtorfit(api.client, testConfig)

    @Suppress("UNCHECKED_CAST")
    private fun converter(): Converter.SuspendResponseConverter<*, AppResult<List<Ping>, DataError.Network>> =
        AppResultConverterFactory().suspendResponseConverter(
            TypeData.createTypeData(
                "com.falcon.tripingly.core.common.result.AppResult<kotlin.collections.List<Ping>, DataError.Network>",
                typeInfo<AppResult<List<Ping>, DataError.Network>>(),
            ),
            ktorfit,
        ) as Converter.SuspendResponseConverter<*, AppResult<List<Ping>, DataError.Network>>

    @Test
    fun success_decodesTheBodyType() = runTest {
        val result = converter().convert(KtorfitResult.Success(api.client.get("pings")))

        assertEquals(AppResult.Success(listOf(Ping("ok"), Ping("late"))), result)
    }

    @Test
    fun errorResponse_mapsTheEnvelope() = runTest {
        val result = converter().convert(KtorfitResult.Success(api.client.get("trips/missing")))

        val error = (result as AppResult.Error).error as DataError.Network.Api
        assertEquals(404, error.status)
        assertEquals("NOT_FOUND", error.code)
    }

    @Test
    fun failure_mapsTheException() = runTest {
        val result = converter().convert(KtorfitResult.Failure(IOException("offline")))

        assertEquals(AppResult.Error(DataError.Network.NoInternet), result)
    }

    @Test
    fun otherReturnTypes_areLeftToKtorfit() {
        val typeData = TypeData.createTypeData("kotlin.String", typeInfo<String>())

        assertNull(AppResultConverterFactory().suspendResponseConverter(typeData, ktorfit))
    }
}
