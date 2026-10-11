package com.falcon.tripingly.core.data.photos

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.data.config.FakeAppConfigRepository
import com.falcon.tripingly.core.data.trips.InMemoryTripLocalDataSource
import com.falcon.tripingly.core.data.trips.TripJson
import com.falcon.tripingly.core.data.trips.toDetails
import com.falcon.tripingly.core.model.photo.PhotoStatus
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.NetworkJson
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
import com.falcon.tripingly.core.network.model.TripDto
import com.falcon.tripingly.core.network.photos.StorageClient
import com.falcon.tripingly.core.network.photos.createPhotosApi
import com.falcon.tripingly.core.network.trips.createMarkersApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PhotoRepositoryTest {

    private val config = ApiConfig(baseUrl = "https://api.test/v1", environment = "test", client = "android/1.0")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sent = MutableStateFlow<List<HttpRequestData>>(emptyList())
    private val requests get() = sent.value
    private val local = InMemoryTripLocalDataSource()
    private val prepared = mutableListOf<Pair<Int, Int>>()

    @AfterTest
    fun cancelUploads() = scope.cancel()

    private fun repository(
        preparer: PhotoPreparer = PhotoPreparer { _, maxEdge, quality -> prepared += maxEdge to quality; JPEG },
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): DefaultPhotoRepository {
        val engine = MockEngine { request ->
            sent.update { it + request }
            handler(request)
        }
        val ktorfit = createKtorfit(
            createHttpClient(engine, config, InMemoryTokenStore(), SessionEvents(), languageTag = { "en" }),
            config,
        )
        return DefaultPhotoRepository(
            api = ktorfit.createPhotosApi(),
            markersApi = ktorfit.createMarkersApi(),
            storage = StorageClient(engine),
            local = local,
            config = FakeAppConfigRepository(),
            preparer = preparer,
            scope = scope,
        )
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun uploadIsPrepared_stored_processed_andBecomesTheMarkersCover() = runTest {
        local.saveTrip(NetworkJson.decodeFromString<TripDto>(TripJson.trip("t1", days = listOf(TripJson.day("d0", 0, "2026-06-01", TripJson.marker("m1", "t1", "d0", 0))))).toDetails())
        val repository = repository { request ->
            when {
                request.url.encodedPath.endsWith("markers/m1/photos/upload-url") -> json(
                    """{"photo":${photo("pending_upload")},"uploadUrl":"https://storage.test/put/p1",
                       "uploadHeaders":{"Content-Type":"image/jpeg","Content-Length":"3"},"expiresAt":"2026-10-11T10:00:00.000Z"}""",
                    HttpStatusCode.Created,
                )
                request.url.host == "storage.test" -> respond("", HttpStatusCode.OK)
                request.url.encodedPath.endsWith("photos/p1/complete") -> json(photo("processing"), HttpStatusCode.Accepted)
                request.url.encodedPath.endsWith("markers/m1/photos") -> json("[${photo("ready")}]")
                request.url.encodedPath.endsWith("markers/m1") ->
                    json(TripJson.marker("m1", "t1", "d0", 0).replace("\"coverPhotoId\":null", "\"coverPhotoId\":\"p1\"").replace("\"photoCount\":0", "\"photoCount\":1"))
                else -> error("unexpected ${request.method.value} ${request.url}")
            }
        }

        repository.upload("m1", listOf(PickedImage { byteArrayOf(9, 9, 9, 9) }))

        val marker = realTime {
            local.trips.first { it["t1"]?.days?.single()?.markers?.single()?.coverPhotoId == "p1" }
        }["t1"]!!.days.single().markers.single()
        assertEquals(1, marker.photoCount)
        assertEquals(listOf(2560 to 85), prepared)
        assertEquals(emptyList(), repository.uploads("m1").first())
        assertEquals(PhotoStatus.Ready, repository.photos("m1").first()?.single()?.status)
        val put = requests.single { it.method == HttpMethod.Put }
        assertEquals("image/jpeg", put.body.contentType?.toString())
        assertEquals(3L, (put.body as OutgoingContent).contentLength)
        // The signed URL carries its own authorization.
        assertNull(put.headers[HttpHeaders.Authorization])
    }

    @Test
    fun unreadableImage_failsWithoutAskingTheServer() = runTest {
        val repository = repository(preparer = { _, _, _ -> null }) { error("no request expected") }

        repository.upload("m1", listOf(PickedImage { byteArrayOf(1) }))

        val upload = realTime { repository.uploads("m1").first { list -> list.any { it.state is PhotoUpload.State.Failed } } }.single()
        assertNull((upload.state as PhotoUpload.State.Failed).error)
        assertTrue(requests.isEmpty())
        repository.dismissUpload(upload.key)
        assertEquals(emptyList(), repository.uploads("m1").first())
    }

    @Test
    fun refusedUpload_failsWithTheServersError_andCanBeRetried() = runTest {
        var refuse = true
        val repository = repository { request ->
            if (refuse) {
                json(TripJson.error("PHOTO_LIMIT_REACHED", "This stop has 30 photos"), HttpStatusCode.UnprocessableEntity)
            } else {
                when {
                    request.url.encodedPath.endsWith("upload-url") -> json(
                        """{"photo":${photo("pending_upload")},"uploadUrl":"https://storage.test/put/p1",
                           "uploadHeaders":{"Content-Type":"image/jpeg"},"expiresAt":"2026-10-11T10:00:00.000Z"}""",
                        HttpStatusCode.Created,
                    )
                    request.url.host == "storage.test" -> respond("", HttpStatusCode.OK)
                    request.url.encodedPath.endsWith("complete") -> json(photo("processing"), HttpStatusCode.Accepted)
                    else -> json("[${photo("processing")}]")
                }
            }
        }

        repository.upload("m1", listOf(PickedImage { byteArrayOf(1) }))
        val failed = realTime { repository.uploads("m1").first { list -> list.any { it.state is PhotoUpload.State.Failed } } }.single()
        val error = assertIs<DataError.Network.Api>((failed.state as PhotoUpload.State.Failed).error)
        assertEquals("PHOTO_LIMIT_REACHED", error.code)

        refuse = false
        repository.retryUpload(failed.key)
        realTime { repository.uploads("m1").first { it.isEmpty() } }
    }

    private fun photo(status: String) = """
        {"id":"p1","markerId":"m1","status":"$status","thumbUrl":${if (status == "ready") "\"https://storage.test/t\"" else "null"},
         "displayUrl":null,"width":null,"height":null,"position":0,"likeCount":0,"likedByMe":false,
         "uploader":${TripJson.OWNER},"createdAt":"2026-10-11T09:00:00.000Z"}
    """.trimIndent()

    private companion object {
        val JPEG = byteArrayOf(1, 2, 3)
    }
}

private suspend fun <T> realTime(block: suspend () -> T): T =
    withContext(Dispatchers.Default) { withTimeout(10.seconds) { block() } }
