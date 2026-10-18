package com.falcon.tripingly.core.data.photos

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.data.realtime.Coalescer
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.TripLocalDataSource
import com.falcon.tripingly.core.data.trips.isConnectionProblem
import com.falcon.tripingly.core.data.trips.toMarker
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.place.ImageCredit
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PhotoStatus
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.model.social.LikeTarget
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
    private val social: SocialRepository,
    private val preparer: PhotoPreparer,
    private val scope: CoroutineScope,
) : PhotoRepository {

    private val galleries = MutableStateFlow<Map<PhotoOwner, List<Photo>>>(emptyMap())

    /** Picked images still on the phone, with where to read them from. */
    private val queue = MutableStateFlow<List<QueuedImage>>(emptyList())

    private class QueuedImage(val upload: PhotoUpload, val image: PickedImage)

    /** Created on the first upload, with the server's limit of parallel uploads. */
    private var sendPermits: Semaphore? = null

    /** Reloads a marker for its cover and photo count, once per burst of changes. */
    private val markerRefreshes = Coalescer(scope) { refreshMarker(it) }

    override fun photos(owner: PhotoOwner): Flow<List<Photo>?> =
        galleries.map { it[owner] }.distinctUntilChanged()

    override fun uploads(owner: PhotoOwner): Flow<List<PhotoUpload>> =
        queue.map { images -> images.map { it.upload }.filter { it.owner == owner } }.distinctUntilChanged()

    override suspend fun loadPhotos(owner: PhotoOwner): AppResult<List<Photo>, DataError.Network> {
        val result = when (owner) {
            is PhotoOwner.Stop -> api.photos(owner.markerId)
            is PhotoOwner.Place -> api.placePhotos(owner.placeId)
        }
        return result.map { photos ->
            // A place's photos come most liked first and keep that order.
            photos.map { it.toPhoto() }.sortedFor(owner).also { gallery -> galleries.update { it + (owner to gallery) } }
        }
    }

    override fun upload(owner: PhotoOwner, images: List<PickedImage>) {
        val added = images.map { QueuedImage(PhotoUpload(Uuid.random().toString(), owner, PhotoUpload.State.Queued), it) }
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
            removePhoto(photo.id)
            val stop = photo.owner as? PhotoOwner.Stop ?: return@map
            val marker = cachedMarker(stop.markerId)
            // The server picks a new cover when the cover goes, so only then is the marker asked for again.
            if (marker != null && marker.coverPhotoId != photo.id) {
                local.saveMarkers(listOf(marker.copy(photoCount = (marker.photoCount - 1).coerceAtLeast(0))))
            } else {
                refreshMarker(stop.markerId)
            }
        }

    override suspend fun setLiked(photo: Photo, liked: Boolean): AppResult<Unit, DataError.Network> {
        val before = galleries.value.values.firstNotNullOfOrNull { gallery -> gallery.firstOrNull { it.id == photo.id } } ?: photo
        if (before.likedByMe != liked) {
            updatePhoto(photo.id) {
                it.copy(likedByMe = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0))
            }
        }
        val target = if (photo.credit != null) LikeTarget.PlaceImage else LikeTarget.Photo
        return when (val result = social.setLiked(target, photo.id, liked)) {
            is AppResult.Success -> {
                updatePhoto(photo.id) { it.copy(likedByMe = result.data.liked, likeCount = result.data.likeCount) }
                AppResult.Success(Unit)
            }
            is AppResult.Error -> {
                updatePhoto(photo.id) { it.copy(likedByMe = before.likedByMe, likeCount = before.likeCount) }
                result
            }
        }
    }

    override suspend fun reorderPhotos(markerId: String, photoIds: List<String>): AppResult<Unit, DataError.Network> =
        api.reorder(markerId, PhotoOrderDto(photoIds)).map { photos ->
            galleries.update { it + (PhotoOwner.Stop(markerId) to photos.map { p -> p.toPhoto() }.sortedBy { p -> p.position }) }
        }

    private suspend fun send(key: String) {
        val permits = sendPermits ?: Semaphore(config.config.value.photoParallelUploads.coerceAtLeast(1)).also { sendPermits = it }
        permits.withPermit {
            // Dismissed while it waited.
            val queued = queue.value.firstOrNull { it.upload.key == key } ?: return
            val owner = queued.upload.owner
            setState(key, PhotoUpload.State.Sending(0f))
            val settings = config.config.value
            val jpeg = withContext(Dispatchers.Default) {
                queued.image.bytes()?.let { preparer.prepare(it, settings.photoMaxEdgePx, settings.photoJpegQuality) }
            } ?: return setState(key, PhotoUpload.State.Failed(null))
            when (val result = sendWithRetries(owner, jpeg) { progress -> setState(key, PhotoUpload.State.Sending(progress)) }) {
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
        owner: PhotoOwner,
        jpeg: ByteArray,
        onProgress: (Float) -> Unit,
    ): AppResult<Photo, DataError.Network> {
        var attempt = 0
        while (true) {
            val result = sendOnce(owner, jpeg, onProgress)
            if (result !is AppResult.Error || !result.error.isConnectionProblem() || attempt == RETRY_DELAYS.size) return result
            delay(RETRY_DELAYS[attempt++])
        }
    }

    private suspend fun sendOnce(
        owner: PhotoOwner,
        jpeg: ByteArray,
        onProgress: (Float) -> Unit,
    ): AppResult<Photo, DataError.Network> {
        val request = UploadUrlRequestDto(UploadUrlRequestDto.MimeType.IMAGE_SLASH_JPEG, jpeg.size.toDouble())
        val ticket = when (
            val result = when (owner) {
                is PhotoOwner.Stop -> api.uploadUrl(owner.markerId, request)
                is PhotoOwner.Place -> api.placeUploadUrl(owner.placeId, request)
            }
        ) {
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
                val stop = photo.owner as? PhotoOwner.Stop
                if (photo.status == PhotoStatus.Ready && stop != null) markerRefreshes.request(stop.markerId)
            }
            is RealtimeEvent.PhotoFailed ->
                updatePhoto(event.photoId) { it.copy(status = PhotoStatus.Failed) }
            is RealtimeEvent.PhotoDeleted -> {
                removePhoto(event.photoId)
                // deletePhoto has updated the stop already.
                if (!byMe) markerRefreshes.request(event.markerId)
            }
            is RealtimeEvent.PhotosReordered -> galleries.update { all ->
                val owner = PhotoOwner.Stop(event.markerId)
                val gallery = all[owner] ?: return@update all
                val order = event.photoIds.withIndex().associate { (index, id) -> id to index }
                all + (owner to gallery.map { it.copy(position = order[it.id] ?: it.position) }.sortedBy { it.position })
            }
            // setCover has saved the stop already; a cover set by processing comes with photo.ready.
            is RealtimeEvent.MarkerCoverChanged -> if (!byMe) markerRefreshes.request(event.markerId)
            // Other people's likes; mine came back with my own request (a late event could be older).
            is RealtimeEvent.LikeCountChanged ->
                if (!byMe && event.targetType == LikeTarget.Photo.wireName) updatePhoto(event.targetId) { it.copy(likeCount = event.count) }
            else -> Unit
        }
    }

    /** My place photo finished processing (or failed); it comes to me alone, since a place has no trip room. */
    internal fun applyLive(event: RealtimeEvent.ToMe) {
        when (event) {
            is RealtimeEvent.PlacePhotoSaved -> savePhoto(event.photo.toPhoto(), replace = true)
            is RealtimeEvent.PlacePhotoFailed ->
                updatePhoto(event.photoId) { it.copy(status = PhotoStatus.Failed) }
        }
    }

    /** After the live connection was down: reloads galleries whose photos were processing, and their stops. */
    internal fun catchUp() {
        galleries.value.filterValues { gallery -> gallery.any { it.status == PhotoStatus.Processing } }.keys.forEach { owner ->
            scope.launch {
                loadPhotos(owner)
                if (owner is PhotoOwner.Stop) markerRefreshes.request(owner.markerId)
            }
        }
    }

    /**
     * A new photo joins its own loaded gallery (a new place photo goes first); a known one is replaced
     * ([replace]) in every gallery that shows it, or kept (it may be newer).
     * A photo can show in more than one: a liked trip photo is in its stop's gallery and its place's.
     */
    private fun savePhoto(photo: Photo, replace: Boolean) {
        galleries.update { all ->
            val withPhoto = if (replace) all.mapValues { (_, gallery) -> gallery.map { if (it.id == photo.id) photo else it } } else all
            val gallery = withPhoto[photo.owner] ?: return@update withPhoto
            if (gallery.any { it.id == photo.id }) withPhoto
            else withPhoto + (photo.owner to (gallery + photo).sortedFor(photo.owner, newFirst = photo.id))
        }
    }

    /** Changes the photo in every gallery that shows it (see [savePhoto]). */
    private fun updatePhoto(photoId: String, change: (Photo) -> Photo) {
        galleries.update { all -> all.mapValues { (_, gallery) -> gallery.map { if (it.id == photoId) change(it) else it } } }
    }

    /** Takes the photo out of every gallery that shows it (see [savePhoto]). */
    private fun removePhoto(photoId: String) {
        galleries.update { all -> all.mapValues { (_, gallery) -> gallery.filterNot { it.id == photoId } } }
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

/** A stop's gallery goes by position; a place's keeps the server's order (most liked first), [newFirst] at the top. */
private fun List<Photo>.sortedFor(owner: PhotoOwner, newFirst: String? = null): List<Photo> = when (owner) {
    is PhotoOwner.Stop -> sortedBy { it.position }
    is PhotoOwner.Place -> sortedByDescending { it.id == newFirst }
}

internal fun PhotoDto.toPhoto() = Photo(
    id = id,
    owner = markerId?.let { PhotoOwner.Stop(it) } ?: PhotoOwner.Place(requireNotNull(placeId) { "A photo has a marker or a place" }),
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
    credit = credit?.let { ImageCredit(it.author, it.title, it.license, it.licenseUrl, it.sourceUrl) },
)
