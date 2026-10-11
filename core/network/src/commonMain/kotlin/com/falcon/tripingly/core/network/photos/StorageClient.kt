package com.falcon.tripingly.core.network.photos

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.apiCall
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders

/**
 * Talks to the photo storage (Cloudflare R2) through signed URLs, which carry their own authorization:
 * no bearer token and no API headers, which the storage would refuse. The image loader downloads
 * photos with the same [httpClient].
 */
class StorageClient(engine: HttpClientEngine) {
    val httpClient = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = TIMEOUT_MILLIS
            socketTimeoutMillis = TIMEOUT_MILLIS
        }
    }

    /** Uploads [bytes] to a signed [url] with exactly the [headers] it was signed for. */
    suspend fun upload(
        url: String,
        headers: Map<String, String>,
        bytes: ByteArray,
        onProgress: (Float) -> Unit,
    ): AppResult<Unit, DataError.Network> = apiCall<Unit> {
        httpClient.put(url) {
            // Ktor sets the length from the body itself.
            headers.filterKeys { !it.equals(HttpHeaders.ContentLength, ignoreCase = true) }
                .forEach { (name, value) -> header(name, value) }
            setBody(bytes)
            onUpload { sent, total -> if (total != null && total > 0) onProgress(sent.toFloat() / total) }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
    }
}
