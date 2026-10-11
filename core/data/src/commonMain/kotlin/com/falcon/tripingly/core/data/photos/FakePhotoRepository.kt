package com.falcon.tripingly.core.data.photos

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.photo.Photo
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
    private val galleries = MutableStateFlow<Map<String, List<Photo>>>(emptyMap())

    override fun photos(markerId: String): Flow<List<Photo>?> = galleries.map { it[markerId] }

    override fun uploads(markerId: String): Flow<List<PhotoUpload>> = flowOf(emptyList())

    override suspend fun loadPhotos(markerId: String): AppResult<List<Photo>, DataError.Network> {
        galleries.update { if (markerId in it) it else it + (markerId to emptyList()) }
        return AppResult.Success(galleries.value.getValue(markerId))
    }

    override fun upload(markerId: String, images: List<PickedImage>) {
        galleries.update { all ->
            val gallery = all[markerId].orEmpty()
            all + (markerId to gallery + images.indices.map { i -> photo(markerId, gallery.size + i) })
        }
    }

    override fun retryUpload(key: String) = Unit

    override fun dismissUpload(key: String) = Unit

    override suspend fun setCover(markerId: String, photoId: String): AppResult<Unit, DataError.Network> = AppResult.Success(Unit)

    override suspend fun deletePhoto(photo: Photo): AppResult<Unit, DataError.Network> {
        galleries.update { all -> all + (photo.markerId to all[photo.markerId].orEmpty().filterNot { it.id == photo.id }) }
        return AppResult.Success(Unit)
    }

    override suspend fun reorderPhotos(markerId: String, photoIds: List<String>): AppResult<Unit, DataError.Network> {
        galleries.update { all ->
            val byId = all[markerId].orEmpty().associateBy { it.id }
            all + (markerId to photoIds.mapIndexedNotNull { i, id -> byId[id]?.copy(position = i) })
        }
        return AppResult.Success(Unit)
    }

    private fun photo(markerId: String, position: Int) = Photo(
        id = Uuid.random().toString(),
        markerId = markerId,
        status = PhotoStatus.Ready,
        thumbUrl = null,
        displayUrl = null,
        width = null,
        height = null,
        position = position,
        likeCount = 0,
        likedByMe = false,
        uploaderId = null,
    )
}
