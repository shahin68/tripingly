package com.falcon.tripingly.feature.map.presentation.photos

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.data.photos.PhotoUpload
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PhotoStatus
import com.falcon.tripingly.core.model.place.ImageCredit
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel.State
import kotlinx.collections.immutable.persistentListOf
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_delete_photo
import com.falcon.tripingly.core.model.trip.UserSummary

private val owner = PhotoOwner.Stop("s1")

private fun previewPhoto(id: String, status: PhotoStatus = PhotoStatus.Ready, likes: Int = 0, credit: ImageCredit? = null) = Photo(
    id = id,
    owner = owner,
    status = status,
    thumbUrl = null,
    displayUrl = null,
    width = 1600,
    height = 1200,
    likeCount = likes,
    likedByMe = likes > 0,
    uploader = UserSummary(id = "u1", username = "anna", displayName = "Anna"),
    credit = credit,
)

internal val previewGalleryPhotos = persistentListOf(
    previewPhoto("ph1", likes = 7),
    previewPhoto("ph2"),
    previewPhoto("ph3", status = PhotoStatus.Processing),
    previewPhoto("ph4", status = PhotoStatus.Failed),
    previewPhoto(
        id = "ph5",
        likes = 2,
        credit = ImageCredit("Benh Lieu Song", null, "CC BY-SA 3.0", null, "https://commons.wikimedia.org/wiki/File:Tour_Eiffel.jpg"),
    ),
)

internal val previewUploads = persistentListOf(
    PhotoUpload("q1", owner, PhotoUpload.State.Queued),
    PhotoUpload("q2", owner, PhotoUpload.State.Sending(0.4f)),
    PhotoUpload("q3", owner, PhotoUpload.State.Failed(DataError.Network.NoInternet)),
)

/** A gallery with photos in every status and uploads, loading, no photos yet, and an error. */
internal class PhotosPreviewStates : PreviewParameterProvider<State> {
    override val values = sequenceOf(
        State(photos = previewGalleryPhotos, uploads = previewUploads, isLoading = false, myUserId = "u1"),
        State(),
        State(isLoading = false),
        State(photos = persistentListOf(previewGalleryPhotos[0], previewGalleryPhotos[1]), isLoading = false, message = UiText.Resource(Res.string.map_error_delete_photo)),
    )
}
