package com.falcon.tripingly.core.network

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs one API request and maps the outcome; nothing throws across the call,
 * except cancellation. A `204` maps to `Unit`. `401` (no session, or the
 * refresh was refused) maps to [DataError.Network.Unauthorized]; any other error
 * with the API's envelope maps to [DataError.Network.Api].
 *
 * ```
 * suspend fun trip(id: String) = apiCall<TripDto> { client.get("trips/$id") }
 * ```
 */
suspend inline fun <reified T> apiCall(
    crossinline request: suspend () -> HttpResponse,
): AppResult<T, DataError.Network> = executeApiCall(typeInfo<T>()) { request() }

@PublishedApi
@Suppress("UNCHECKED_CAST")
internal suspend fun <T> executeApiCall(
    type: TypeInfo,
    request: suspend () -> HttpResponse,
): AppResult<T, DataError.Network> {
    val response = try {
        request()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return AppResult.Error(e.toNetworkError())
    }
    if (!response.status.isSuccess()) return AppResult.Error(response.toNetworkError())
    if (type.type == Unit::class) return AppResult.Success(Unit as T)
    return try {
        AppResult.Success(response.body<T>(type))
    } catch (e: CancellationException) {
        throw e
    } catch (e: SerializationException) {
        AppResult.Error(DataError.Network.Serialization)
    } catch (e: ContentConvertException) {
        AppResult.Error(DataError.Network.Serialization)
    } catch (e: NoTransformationFoundException) {
        AppResult.Error(DataError.Network.Serialization)
    }
}

internal fun Exception.toNetworkError(): DataError.Network = when (this) {
    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException ->
        DataError.Network.RequestTimeout
    is IOException -> DataError.Network.NoInternet
    is SerializationException -> DataError.Network.Serialization
    else -> DataError.Network.Unknown(this::class.simpleName)
}

internal suspend fun HttpResponse.toNetworkError(): DataError.Network {
    if (status == HttpStatusCode.Unauthorized) return DataError.Network.Unauthorized
    val envelope = errorEnvelope()
    return when {
        envelope != null -> DataError.Network.Api(
            status = status.value,
            code = envelope.code,
            message = envelope.message,
            details = envelope.details?.flatten().orEmpty(),
        )
        status.value >= 500 -> DataError.Network.ServerError
        else -> DataError.Network.Unknown("HTTP ${status.value}")
    }
}

/** The `error.code` of an error response, or null when it has no envelope. */
internal suspend fun HttpResponse.apiErrorCode(): String? = errorEnvelope()?.code

private suspend fun HttpResponse.errorEnvelope(): ErrorEnvelope.Body? = try {
    NetworkJson.decodeFromString<ErrorEnvelope>(bodyAsText()).error
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/** Read loosely (code as a string) so codes added on the server don't break parsing. */
@Serializable
private data class ErrorEnvelope(val error: Body) {
    @Serializable
    data class Body(val code: String, val message: String, val details: JsonObject? = null)
}

private fun JsonObject.flatten(prefix: String = ""): Map<String, String> = buildMap {
    for ((key, value) in this@flatten) {
        val path = if (prefix.isEmpty()) key else "$prefix.$key"
        when (value) {
            is JsonObject -> putAll(value.flatten(path))
            is JsonArray -> put(path, value.joinToString(",") { it.contentOrJson() })
            JsonNull -> Unit
            is JsonPrimitive -> put(path, value.content)
        }
    }
}

private fun JsonElement.contentOrJson(): String =
    if (this is JsonPrimitive) content else toString()
