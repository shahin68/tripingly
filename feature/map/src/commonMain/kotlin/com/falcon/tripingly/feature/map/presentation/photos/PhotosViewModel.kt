package com.falcon.tripingly.feature.map.presentation.photos

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.photos.PhotoRepository
import com.falcon.tripingly.core.data.photos.PhotoUpload
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PhotoStatus
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_cover
import com.falcon.tripingly.feature.map.generated.resources.map_error_delete_photo
import com.falcon.tripingly.feature.map.generated.resources.map_error_like_photo
import com.falcon.tripingly.feature.map.generated.resources.map_error_load_photos
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The photos of a stop or a place in the sheet: the gallery, the photos picked on this device still going
 * up, and the full-screen viewer. The gallery loads when the sheet first opens. A stop's owners and editors
 * add photos (picked or taken with the in-app camera), pick the cover and delete; anyone adds photos to a
 * place and deletes their own. Everyone likes photos. The server checks all of it too.
 */
class PhotosViewModel(
    private val owner: PhotoOwner,
    private val photoRepository: PhotoRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    private val ui = MutableStateFlow(UiState())

    private val myUserId = (sessionRepository.session.value as? SessionState.SignedIn)?.account?.id

    val uiState: StateFlow<State> = combine(
        photoRepository.photos(owner),
        photoRepository.uploads(owner),
        ui,
        ::toState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    init {
        load()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnAddPhotos -> addPhotos(action.images)
            is Action.OnOpenCamera -> ui.update { it.copy(isCameraOpen = true) }
            is Action.OnCloseCamera -> ui.update { it.copy(isCameraOpen = false) }
            is Action.OnRetryUpload -> photoRepository.retryUpload(action.key)
            is Action.OnRemoveUpload -> photoRepository.dismissUpload(action.key)
            is Action.OnOpenPhoto -> openPhoto(action.photoId)
            is Action.OnClosePhoto -> ui.update { it.copy(openPhotoId = null) }
            is Action.OnMakeCover -> makeCover(action.photoId)
            is Action.OnDeletePhoto -> ui.update { it.copy(photoToDelete = action.photo) }
            is Action.OnConfirmDelete -> deletePhoto()
            is Action.OnDismissDelete -> ui.update { it.copy(photoToDelete = null) }
            is Action.OnToggleLike -> toggleLike(action.photo)
            is Action.OnDismissMessage -> ui.update { it.copy(message = null) }
        }
    }

    private fun toState(photos: List<Photo>?, uploads: List<PhotoUpload>, ui: UiState): State {
        // A photo still "uploading" is either one this device is sending (shown from its upload) or one
        // another device never finished; neither has anything to show yet.
        val gallery = photos.orEmpty().filter { it.status != PhotoStatus.Uploading }
        val viewable = gallery.filter { it.status == PhotoStatus.Ready }
        return State(
            photos = gallery.toImmutableList(),
            uploads = uploads.toImmutableList(),
            isLoading = photos == null && ui.isLoading,
            isFindingPhoto = ui.isFindingPhoto,
            viewable = viewable.toImmutableList(),
            openPhotoId = ui.openPhotoId,
            openPhotoIndex = viewable.indexOfFirst { it.id == ui.openPhotoId }.takeIf { it >= 0 },
            photoToDelete = ui.photoToDelete,
            myUserId = myUserId,
            isSaving = ui.isSaving,
            isCameraOpen = ui.isCameraOpen,
            message = ui.message,
        )
    }

    private fun addPhotos(images: List<PickedImage>) {
        ui.update { it.copy(isCameraOpen = false) }
        if (images.isNotEmpty()) photoRepository.upload(owner, images)
    }

    private fun load() {
        viewModelScope.launch {
            val result = photoRepository.loadPhotos(owner)
            ui.update { it.copy(isLoading = false, message = (result as? AppResult.Error)?.error?.toUiText(Res.string.map_error_load_photos)) }
        }
    }

    /**
     * Opened from outside the sheet (the place card), the photo can be newer than the gallery this device
     * already has, so a photo the gallery doesn't know reloads it once. The first load counts as that reload.
     */
    private fun openPhoto(photoId: String) {
        val wasLoading = ui.value.isLoading
        ui.update { it.copy(openPhotoId = photoId, isFindingPhoto = true) }
        viewModelScope.launch {
            ui.first { !it.isLoading }
            val isKnown = photoRepository.photos(owner).first().orEmpty().any { it.id == photoId }
            val result = if (isKnown || wasLoading) null else photoRepository.loadPhotos(owner)
            ui.update { state ->
                state.copy(
                    isFindingPhoto = false,
                    message = (result as? AppResult.Error)?.error?.toUiText(Res.string.map_error_load_photos) ?: state.message,
                )
            }
        }
    }

    private fun makeCover(photoId: String) {
        val stop = owner as? PhotoOwner.Stop ?: return
        if (ui.value.isSaving) return
        ui.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = photoRepository.setCover(stop.markerId, photoId)
            ui.update { it.copy(isSaving = false, message = (result as? AppResult.Error)?.error?.toUiText(Res.string.map_error_cover)) }
        }
    }

    private fun deletePhoto() {
        val photo = ui.value.photoToDelete ?: return
        // Deleted in the viewer, the viewer moves on to the photo that takes its place (or the one before);
        // it closes with the last one.
        val viewable = uiState.value.viewable
        val index = viewable.indexOfFirst { it.id == photo.id }
        val remaining = viewable.filterNot { it.id == photo.id }
        val next = remaining.getOrNull(index.coerceAtMost(remaining.size - 1))?.id
        ui.update { it.copy(photoToDelete = null, isSaving = true) }
        viewModelScope.launch {
            val result = photoRepository.deletePhoto(photo)
            ui.update { state ->
                state.copy(
                    isSaving = false,
                    message = (result as? AppResult.Error)?.error?.toUiText(Res.string.map_error_delete_photo),
                    openPhotoId = if (result is AppResult.Success && state.openPhotoId == photo.id) next else state.openPhotoId,
                )
            }
        }
    }

    /** Shows at once; see [PhotoRepository.setLiked]. */
    private fun toggleLike(photo: Photo) {
        val current = uiState.value.photos.firstOrNull { it.id == photo.id } ?: photo
        viewModelScope.launch {
            val result = photoRepository.setLiked(current, !current.likedByMe)
            if (result is AppResult.Error) ui.update { it.copy(message = result.error.toUiText(Res.string.map_error_like_photo)) }
        }
    }

    private data class UiState(
        val isLoading: Boolean = true,
        val openPhotoId: String? = null,
        val isFindingPhoto: Boolean = false,
        val photoToDelete: Photo? = null,
        val isSaving: Boolean = false,
        val isCameraOpen: Boolean = false,
        val message: UiText? = null,
    )

    @Immutable
    data class State(
        /** The gallery: a stop's in order (processing and failed photos too, for trip members), a place's most liked first. */
        val photos: ImmutableList<Photo> = persistentListOf(),
        /** Photos picked on this device that the server doesn't have yet. */
        val uploads: ImmutableList<PhotoUpload> = persistentListOf(),
        /** The gallery hasn't loaded yet. */
        val isLoading: Boolean = true,
        /** The photo asked for is being looked up, see [Action.OnOpenPhoto]. */
        val isFindingPhoto: Boolean = false,
        /** The photos the viewer pages through: the ready ones. */
        val viewable: ImmutableList<Photo> = persistentListOf(),
        /** The photo asked for with [Action.OnOpenPhoto], until it closes. */
        val openPhotoId: String? = null,
        /** The viewer is open on this index of [viewable]. */
        val openPhotoIndex: Int? = null,
        /** Waiting for the user to confirm. */
        val photoToDelete: Photo? = null,
        /** Whose own photos can be deleted by them alone (a place's). */
        val myUserId: String? = null,
        /** A cover change or delete is waiting for the server. */
        val isSaving: Boolean = false,
        /** The in-app camera is open; its photo comes back as [Action.OnAddPhotos]. */
        val isCameraOpen: Boolean = false,
        val message: UiText? = null,
    )

    sealed interface Action {
        /** Picked, or taken with the camera (which then closes). */
        data class OnAddPhotos(val images: List<PickedImage>) : Action
        data object OnOpenCamera : Action
        data object OnCloseCamera : Action
        data class OnRetryUpload(val key: String) : Action
        data class OnRemoveUpload(val key: String) : Action
        data class OnOpenPhoto(val photoId: String) : Action
        data object OnClosePhoto : Action
        data class OnMakeCover(val photoId: String) : Action
        /** Asks first; see [OnConfirmDelete]. */
        data class OnDeletePhoto(val photo: Photo) : Action
        data object OnConfirmDelete : Action
        data class OnToggleLike(val photo: Photo) : Action
        data object OnDismissDelete : Action
        data object OnDismissMessage : Action
    }
}
