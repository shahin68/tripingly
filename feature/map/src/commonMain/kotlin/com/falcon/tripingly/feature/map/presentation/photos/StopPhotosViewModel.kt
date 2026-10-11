package com.falcon.tripingly.feature.map.presentation.photos

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.photos.PhotoRepository
import com.falcon.tripingly.core.data.photos.PhotoUpload
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoStatus
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One stop's photos in the sheet: the gallery, the photos picked on this device still going up, and the
 * full-screen viewer. The gallery loads when the sheet first opens for the stop. Owners and editors add
 * photos, pick the cover and delete; the server checks that too.
 */
class StopPhotosViewModel(
    private val markerId: String,
    private val photoRepository: PhotoRepository,
) : ViewModel() {

    private val ui = MutableStateFlow(UiState())

    val uiState: StateFlow<State> = combine(
        photoRepository.photos(markerId),
        photoRepository.uploads(markerId),
        ui,
        ::toState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    init {
        load()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnAddPhotos -> if (action.images.isNotEmpty()) photoRepository.upload(markerId, action.images)
            is Action.OnRetryUpload -> photoRepository.retryUpload(action.key)
            is Action.OnRemoveUpload -> photoRepository.dismissUpload(action.key)
            is Action.OnOpenPhoto -> ui.update { it.copy(openPhotoId = action.photoId) }
            is Action.OnClosePhoto -> ui.update { it.copy(openPhotoId = null) }
            is Action.OnMakeCover -> makeCover(action.photoId)
            is Action.OnDeletePhoto -> ui.update { it.copy(photoToDelete = action.photo) }
            is Action.OnConfirmDelete -> deletePhoto()
            is Action.OnDismissDelete -> ui.update { it.copy(photoToDelete = null) }
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
            viewable = viewable.toImmutableList(),
            openPhotoIndex = viewable.indexOfFirst { it.id == ui.openPhotoId }.takeIf { it >= 0 },
            photoToDelete = ui.photoToDelete,
            isSaving = ui.isSaving,
            message = ui.message,
        )
    }

    private fun load() {
        viewModelScope.launch {
            val result = photoRepository.loadPhotos(markerId)
            ui.update { it.copy(isLoading = false, message = (result as? AppResult.Error)?.error?.toUiText()) }
        }
    }

    private fun makeCover(photoId: String) {
        if (ui.value.isSaving) return
        ui.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = photoRepository.setCover(markerId, photoId)
            ui.update { it.copy(isSaving = false, message = (result as? AppResult.Error)?.error?.toUiText()) }
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
                    message = (result as? AppResult.Error)?.error?.toUiText(),
                    openPhotoId = if (result is AppResult.Success && state.openPhotoId == photo.id) next else state.openPhotoId,
                )
            }
        }
    }

    private data class UiState(
        val isLoading: Boolean = true,
        val openPhotoId: String? = null,
        val photoToDelete: Photo? = null,
        val isSaving: Boolean = false,
        val message: UiText? = null,
    )

    @Immutable
    data class State(
        /** The gallery in order: processing and failed photos too, for trip members. */
        val photos: ImmutableList<Photo> = persistentListOf(),
        /** Photos picked on this device that the server doesn't have yet. */
        val uploads: ImmutableList<PhotoUpload> = persistentListOf(),
        /** The gallery hasn't loaded yet. */
        val isLoading: Boolean = true,
        /** The photos the viewer pages through: the ready ones. */
        val viewable: ImmutableList<Photo> = persistentListOf(),
        /** The viewer is open on this index of [viewable]. */
        val openPhotoIndex: Int? = null,
        /** Waiting for the user to confirm. */
        val photoToDelete: Photo? = null,
        /** A cover change or delete is waiting for the server. */
        val isSaving: Boolean = false,
        val message: UiText? = null,
    )

    sealed interface Action {
        data class OnAddPhotos(val images: List<PickedImage>) : Action
        data class OnRetryUpload(val key: String) : Action
        data class OnRemoveUpload(val key: String) : Action
        data class OnOpenPhoto(val photoId: String) : Action
        data object OnClosePhoto : Action
        data class OnMakeCover(val photoId: String) : Action
        /** Asks first; see [OnConfirmDelete]. */
        data class OnDeletePhoto(val photo: Photo) : Action
        data object OnConfirmDelete : Action
        data object OnDismissDelete : Action
        data object OnDismissMessage : Action
    }
}
