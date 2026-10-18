package com.falcon.tripingly.core.data.photos

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PhotoStatus
import com.falcon.tripingly.core.model.photo.PickedImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

/** Photos in memory, ready at once and without images, for building and demoing without the server. */
class FakePhotoRepository : PhotoRepository {
    private val galleries = MutableStateFlow<Map<PhotoOwner, List<Photo>>>(emptyMap())

    override fun photos(owner: PhotoOwner): Flow<List<Photo>?> = galleries.map { it[owner] }

    override fun uploads(owner: PhotoOwner): Flow<List<PhotoUpload>> = flowOf(emptyList())

    override suspend fun loadPhotos(owner: PhotoOwner): AppResult<List<Photo>, DataError.Network> {
        galleries.update { if (owner in it) it else it + (owner to emptyList()) }
        return AppResult.Success(galleries.value.getValue(owner))
    }

    override fun upload(owner: PhotoOwner, images: List<PickedImage>) {
        galleries.update { all ->
            val gallery = all[owner].orEmpty()
            all + (owner to gallery + images.indices.map { i -> photo(owner, gallery.size + i) })
        }
    }

    override fun retryUpload(key: String) = Unit

    override fun dismissUpload(key: String) = Unit

    override suspend fun setCover(markerId: String, photoId: String): AppResult<Unit, DataError.Network> = AppResult.Success(Unit)

    override suspend fun deletePhoto(photo: Photo): AppResult<Unit, DataError.Network> {
        galleries.update { all -> all + (photo.owner to all[photo.owner].orEmpty().filterNot { it.id == photo.id }) }
        return AppResult.Success(Unit)
    }

    override suspend fun setLiked(photo: Photo, liked: Boolean): AppResult<Unit, DataError.Network> {
        galleries.update { all ->
            all + (
                photo.owner to all[photo.owner].orEmpty().map {
                    if (it.id != photo.id || it.likedByMe == liked) it
                    else it.copy(likedByMe = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0))
                }
                )
        }
        return AppResult.Success(Unit)
    }

    override suspend fun reorderPhotos(markerId: String, photoIds: List<String>): AppResult<Unit, DataError.Network> {
        val owner = PhotoOwner.Stop(markerId)
        galleries.update { all ->
            val byId = all[owner].orEmpty().associateBy { it.id }
            all + (owner to photoIds.mapIndexedNotNull { i, id -> byId[id]?.copy(position = i) })
        }
        return AppResult.Success(Unit)
    }

    private fun photo(owner: PhotoOwner, position: Int) = Photo(
        id = Uuid.random().toString(),
        owner = owner,
        status = PhotoStatus.Ready,
        thumbUrl = null,
        displayUrl = null,
        width = null,
        height = null,
        position = position,
        likeCount = 0,
        likedByMe = false,
        uploaderId = null,
        credit = null,
    )
}
