package com.falcon.tripingly.core.data.photos

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PickedImage
import kotlinx.coroutines.flow.Flow

/**
 * Photos on stops and on places. Uploads run from the app scope, so leaving a screen doesn't stop them: each
 * picked image is shrunk and stripped of its metadata on the phone, sent straight to storage, then processed
 * by the server. A stop's cover and photo count in the trips cache follow its photos.
 */
interface PhotoRepository {
    /**
     * The gallery as the server last sent it, null until loaded. A stop's is in order (editors also get
     * processing and failed photos); a place's is most liked first (with my own processing and failed ones).
     */
    fun photos(owner: PhotoOwner): Flow<List<Photo>?>

    /** Images picked for [owner] that the server doesn't have yet. */
    fun uploads(owner: PhotoOwner): Flow<List<PhotoUpload>>

    suspend fun loadPhotos(owner: PhotoOwner): AppResult<List<Photo>, DataError.Network>

    /** Queues the picked [images] for [owner]. */
    fun upload(owner: PhotoOwner, images: List<PickedImage>)

    fun retryUpload(key: String)

    fun dismissUpload(key: String)

    suspend fun setCover(markerId: String, photoId: String): AppResult<Unit, DataError.Network>

    suspend fun deletePhoto(photo: Photo): AppResult<Unit, DataError.Network>

    /** Likes or unlikes; the heart and count change in the gallery at once and go back on a refusal. */
    suspend fun setLiked(photo: Photo, liked: Boolean): AppResult<Unit, DataError.Network>

    /** [photoIds]: the marker's processing and ready photos in the new order. */
    suspend fun reorderPhotos(markerId: String, photoIds: List<String>): AppResult<Unit, DataError.Network>
}

/** Turns a picked image into the JPEG that's uploaded; see [prepareJpeg]. */
fun interface PhotoPreparer {
    fun prepare(image: ByteArray, maxEdge: Int, quality: Int): ByteArray?
}

internal val PlatformPhotoPreparer = PhotoPreparer(::prepareJpeg)
