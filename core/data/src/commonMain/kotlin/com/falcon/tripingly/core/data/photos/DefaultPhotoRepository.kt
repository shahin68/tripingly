package com.falcon.tripingly.core.data.photos

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.data.realtime.Coalescer
import com.falcon.tripingly.core.data.trips.TripLocalDataSource
import com.falcon.tripingly.core.data.trips.isConnectionProblem
import com.falcon.tripingly.core.data.trips.toMarker
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoStatus
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.model.trip.TripMarker
import com.falcon.tripingly.core.network.model.CoverDto
import com.falcon.tripingly.core.network.model.PhotoDto
import com.falcon.tripingly.core.network.model.PhotoOrderDto
import com.falcon.tripingly.core.network.model.UploadUrlRequestDto
import com.falcon.tripingly.core.network.photos.PhotosApi
import com.falcon.tripingly.core.network.photos.StorageClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import com.falcon.tripingly.core.network.trips.MarkersApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

internal class DefaultPhotoRepository(
    private val api: PhotosApi,
    private val markersApi: MarkersApi,
    private val storage: StorageClient,
    private val local: TripLocalDataSource,
    private val config: AppConfigRepository,
    private val preparer: PhotoPreparer,
    private val scope: CoroutineScope,
) : PhotoRepository {

    private val galleries = MutableStateFlow<Map<String, List<Photo>>>(emptyMap())

    /** Picked images still on the phone, with where to read them from. */
    private val queue = MutableStateFlow<List<QueuedImage>>(emptyList())

    private class QueuedImage(val upload: PhotoUpload, val image: PickedImage)

    /** Created on the first upload, with the server's limit of parallel uploads. */
    private var sendPermits: Semaphore? = null

    /** Reloads a marker for its cover and photo count, once per burst of changes. */
    private val markerRefreshes = Coalescer(scope) { refreshMarker(it) }

    override fun photos(markerId: String): Flow<List<Photo>?> =
        galleries.map { it[markerId] }.distinctUntilChanged()

    override fun uploads(markerId: String): Flow<List<PhotoUpload>> =
        queue.map { images -> images.map { it.upload }.filter { it.markerId == markerId } }.distinctUntilChanged()

    override suspend fun loadPhotos(markerId: String): AppResult<List<Photo>, DataError.Network> =
        api.photos(markerId).map { photos ->
            photos.map { it.toPhoto() }.sortedBy { it.position }.also { gallery -> galleries.update { it + (markerId to gallery) } }
        }

    override fun upload(markerId: String, images: List<PickedImage>) {
        val added = images.map { QueuedImage(PhotoUpload(Uuid.random().toString(), markerId, PhotoUpload.State.Queued), it) }
        queue.update { it + added }
        added.forEach { image -> scope.launch { send(image.upload.key) } }
    }

    override fun retryUpload(key: String) {
        setState(key, PhotoUpload.State.Queued)
        scope.launch { send(key) }
    }

    override fun dismissUpload(key: String) {
        queue.update { images -> images.filterNot { it.upload.key == key } }
    }

    override suspend fun setCover(markerId: String, photoId: String): AppResult<Unit, DataError.Network> =
        api.setCover(markerId, CoverDto(photoId)).map { local.saveMarkers(listOf(it.toMarker())) }

    override suspend fun deletePhoto(photo: Photo): AppResult<Unit, DataError.Network> =
        api.delete(photo.id).map {
            removePhoto(photo.markerId, photo.id)
            val marker = cachedMarker(photo.markerId)
            // The server picks a new cover when the cover goes, so only then is the marker asked for again.
            if (marker != null && marker.coverPhotoId != photo.id) {
                local.saveMarkers(listOf(marker.copy(photoCount = (marker.photoCount - 1).coerceAtLeast(0))))
            } else {
                refreshMarker(photo.markerId)
            }
        }

    override suspend fun reorderPhotos(markerId: String, photoIds: List<String>): AppResult<Unit, DataError.Network> =
        api.reorder(markerId, PhotoOrderDto(photoIds)).map { photos ->
            galleries.update { it + (markerId to photos.map { p -> p.toPhoto() }.sortedBy { p -> p.position }) }
        }

    private suspend fun send(key: String) {
        val permits = sendPermits ?: Semaphore(config.config.value.photoParallelUploads.coerceAtLeast(1)).also { sendPermits = it }
        permits.withPermit {
            // Dismissed while it waited.
            val queued = queue.value.firstOrNull { it.upload.key == key } ?: return
            val markerId = queued.upload.markerId
            setState(key, PhotoUpload.State.Sending(0f))
            val settings = config.config.value
            val jpeg = withContext(Dispatchers.Default) {
                queued.image.bytes()?.let { preparer.prepare(it, settings.photoMaxEdgePx, settings.photoJpegQuality) }
            } ?: return setState(key, PhotoUpload.State.Failed(null))
            when (val result = sendWithRetries(markerId, jpeg) { progress -> setState(key, PhotoUpload.State.Sending(progress)) }) {
                is AppResult.Error -> setState(key, PhotoUpload.State.Failed(result.error))
                is AppResult.Success -> {
                    // Processing now; the live connection says when it's ready, and may have said so already.
                    // In the gallery before the upload goes, so it never disappears in between.
                    savePhoto(result.data, replace = false)
                    dismissUpload(key)
                }
            }
        }
    }

    /** Creates the photo, puts the file in storage and has the server process it; retries a dropped connection. */
    private suspend fun sendWithRetries(
        markerId: String,
        jpeg: ByteArray,
        onProgress: (Float) -> Unit,
    ): AppResult<Photo, DataError.Network> {
        var attempt = 0
        while (true) {
            val result = sendOnce(markerId, jpeg, onProgress)
            if (result !is AppResult.Error || !result.error.isConnectionProblem() || attempt == RETRY_DELAYS.size) return result
            delay(RETRY_DELAYS[attempt++])
        }
    }

    private suspend fun sendOnce(
        markerId: String,
        jpeg: ByteArray,
        onProgress: (Float) -> Unit,
    ): AppResult<Photo, DataError.Network> {
        val ticket = when (val result = api.uploadUrl(markerId, UploadUrlRequestDto(UploadUrlRequestDto.MimeType.IMAGE_SLASH_JPEG, jpeg.size.toDouble()))) {
            is AppResult.Error -> return result
            is AppResult.Success -> result.data
        }
        val stored = storage.upload(ticket.uploadUrl, ticket.uploadHeaders, jpeg, onProgress)
        if (stored is AppResult.Error) return stored
        return api.complete(ticket.photo.id).map { it.toPhoto() }
    }

    /** A photo change from the live connection; [byMe] when this user made it (their own upload too). */
    internal fun applyLive(event: RealtimeEvent.InTrip, byMe: Boolean) {
        when (event) {
            is RealtimeEvent.PhotoSaved -> {
                val photo = event.photo.toPhoto()
                savePhoto(photo, replace = true)
                // A ready photo counts on the stop, and the first one becomes its cover.
                if (photo.status == PhotoStatus.Ready) markerRefreshes.request(photo.markerId)
            }
            is RealtimeEvent.PhotoFailed -> galleries.update { all ->
                val gallery = all[event.markerId] ?: return@update all
                all + (event.markerId to gallery.map { if (it.id == event.photoId) it.copy(status = PhotoStatus.Failed) else it })
            }
            is RealtimeEvent.PhotoDeleted -> {
                removePhoto(event.markerId, event.photoId)
                // deletePhoto has updated the stop already.
                if (!byMe) markerRefreshes.request(event.markerId)
            }
            is RealtimeEvent.PhotosReordered -> galleries.update { all ->
                val gallery = all[event.markerId] ?: return@update all
                val order = event.photoIds.withIndex().associate { (index, id) -> id to index }
                all + (event.markerId to gallery.map { it.copy(position = order[it.id] ?: it.position) }.sortedBy { it.position })
            }
            // setCover has saved the stop already; a cover set by processing comes with photo.ready.
            is RealtimeEvent.MarkerCoverChanged -> if (!byMe) markerRefreshes.request(event.markerId)
            else -> Unit
        }
    }

    /** After the live connection was down: reloads galleries whose photos were processing, and their stops. */
    internal fun catchUp() {
        galleries.value.filterValues { gallery -> gallery.any { it.status == PhotoStatus.Processing } }.keys.forEach { markerId ->
            scope.launch {
                loadPhotos(markerId)
                markerRefreshes.request(markerId)
            }
        }
    }

    /** Into a loaded gallery; [replace] an existing copy, or keep it (it may be newer). */
    private fun savePhoto(photo: Photo, replace: Boolean) {
        galleries.update { all ->
            val gallery = all[photo.markerId] ?: return@update all
            val known = gallery.any { it.id == photo.id }
            when {
                !known -> all + (photo.markerId to (gallery + photo).sortedBy { it.position })
                replace -> all + (photo.markerId to gallery.map { if (it.id == photo.id) photo else it })
                else -> all
            }
        }
    }

    private fun removePhoto(markerId: String, photoId: String) {
        galleries.update { all -> all[markerId]?.let { all + (markerId to it.filterNot { p -> p.id == photoId }) } ?: all }
    }

    private suspend fun refreshMarker(markerId: String) {
        // Only stops of cached trips are kept.
        if (local.tripIdOfMarker(markerId) == null) return
        markersApi.get(markerId).onSuccess { local.saveMarkers(listOf(it.toMarker())) }
    }

    private suspend fun cachedMarker(markerId: String): TripMarker? {
        val tripId = local.tripIdOfMarker(markerId) ?: return null
        return local.trip(tripId)?.days?.flatMap { it.markers }?.firstOrNull { it.id == markerId }
    }

    private fun setState(key: String, state: PhotoUpload.State) {
        queue.update { images ->
            images.map { if (it.upload.key == key) QueuedImage(it.upload.copy(state = state), it.image) else it }
        }
    }

    /** Forgets everything; the next account starts empty. */
    fun clear() {
        galleries.value = emptyMap()
        queue.value = emptyList()
    }

    private companion object {
        val RETRY_DELAYS = listOf(1.seconds, 2.seconds)
    }
}

internal fun PhotoDto.toPhoto() = Photo(
    id = id,
    markerId = markerId,
    status = when (status) {
        PhotoDto.Status.PENDING_UPLOAD -> PhotoStatus.Uploading
        PhotoDto.Status.PROCESSING -> PhotoStatus.Processing
        PhotoDto.Status.READY -> PhotoStatus.Ready
        PhotoDto.Status.FAILED -> PhotoStatus.Failed
    },
    thumbUrl = thumbUrl,
    displayUrl = displayUrl,
    width = width?.toInt(),
    height = height?.toInt(),
    position = position.toInt(),
    likeCount = likeCount.toInt(),
    likedByMe = likedByMe,
    uploaderId = uploader?.id,
)
