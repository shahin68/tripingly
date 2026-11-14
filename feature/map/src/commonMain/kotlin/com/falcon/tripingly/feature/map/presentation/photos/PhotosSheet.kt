package com.falcon.tripingly.feature.map.presentation.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.data.photos.PhotoUpload
import com.falcon.tripingly.core.designsystem.component.AppDialog
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.MessageBannerHost
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.SkeletonBox
import com.falcon.tripingly.core.designsystem.component.bottomSystemBar
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.photos.rememberPhotoPicker
import com.falcon.tripingly.feature.map.generated.resources.*
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel.Action
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel.State
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The photos of a stop or a place ([owner], named [title]) in a sheet over the map. [canAdd]: may add photos
 * (a stop's owners and editors; anyone on a place). [canManage]: a stop's owners and editors, who pick the
 * cover ([coverPhotoId]) and delete any photo; everyone else deletes only their own. [onReport] reports the
 * photo open in the viewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosRoute(
    owner: PhotoOwner,
    title: String,
    canAdd: Boolean,
    canManage: Boolean,
    coverPhotoId: String?,
    onReport: (Photo) -> Unit,
    onDismiss: () -> Unit,
    viewModel: PhotosViewModel = koinViewModel(key = "photos:$owner") { parametersOf(owner) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickPhotos = rememberPhotoPicker { images -> viewModel.onAction(Action.OnAddPhotos(images)) }

    Box {
        PhotosScreen(
            state = state,
            title = stringResource(if (owner is PhotoOwner.Place) Res.string.map_place_photos else Res.string.map_stop_photos),
            subtitle = title,
            canAdd = canAdd,
            canManage = canManage,
            coverPhotoId = coverPhotoId,
            onAddPhotos = pickPhotos,
            onAction = viewModel::onAction,
            onClose = onDismiss,
        )
        MessageBannerHost(Modifier.align(Alignment.TopCenter))
    }

    state.openPhotoIndex?.let { index ->
        GalleryViewer(
            state = state,
            initialIndex = index,
            coverPhotoId = coverPhotoId,
            canManage = canManage,
            onReport = onReport,
            onAction = viewModel::onAction,
        )
    }

    if (state.isCameraOpen) {
        PhotoCameraDialog(
            onPhoto = { viewModel.onAction(Action.OnAddPhotos(listOf(it))) },
            onDismiss = { viewModel.onAction(Action.OnCloseCamera) },
        )
    }

    if (state.photoToDelete != null) DeletePhotoDialog(viewModel::onAction)
}

@Composable
internal fun DeletePhotoDialog(onAction: (Action) -> Unit) {
    AppDialog(
        title = stringResource(Res.string.map_photo_delete_title),
        message = stringResource(Res.string.map_photo_delete_message),
        confirmLabel = stringResource(Res.string.map_confirm_delete),
        isDestructive = true,
        onConfirm = { onAction(Action.OnConfirmDelete) },
        onDismiss = { onAction(Action.OnDismissDelete) },
    )
}

/**
 * The sheet's content: the header with the camera and Add (where photos can be added), then the uploads and photos in a
 * grid.
 */
@Composable
fun PhotosScreen(
    state: State,
    title: String,
    subtitle: String,
    canAdd: Boolean,
    canManage: Boolean,
    coverPhotoId: String?,
    onAddPhotos: () -> Unit,
    onAction: (Action) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShowMessage(message = state.message?.asString(), onShown = { onAction(Action.OnDismissMessage) }, isError = state.message !is UiText.Note)
    // A click when one of my photos finishes uploading: it leaves the uploads without having failed.
    val haptics = rememberHaptics()
    var sending by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(state.uploads) {
        val now = state.uploads.filter { it.state !is PhotoUpload.State.Failed }.mapTo(mutableSetOf()) { it.key }
        if (sending.any { key -> state.uploads.none { it.key == key } }) haptics.success()
        sending = now
    }
    val fullWidth: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    Column(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.bottomSystemBar)) {
        SheetHeader(
            title = title,
            subtitle = subtitle,
            onClose = onClose,
            actions = {
                if (canAdd) {
                    FilledTonalIconButton(onClick = { onAction(Action.OnOpenCamera) }) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = stringResource(Res.string.map_camera_take_photo))
                    }
                    PrimaryButton(
                        text = stringResource(Res.string.map_photos_add),
                        onClick = onAddPhotos,
                        size = ButtonSize.Small,
                        icon = Icons.Default.AddPhotoAlternate,
                    )
                }
            },
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = GalleryTileMinSize),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            if (state.isLoading && state.uploads.isEmpty()) {
                items(SKELETON_TILES, key = { "skeleton$it" }) { SkeletonBox(Modifier.aspectRatio(1f)) }
            } else if (state.photos.isEmpty() && state.uploads.isEmpty()) {
                item(span = fullWidth) {
                    Text(
                        text = stringResource(Res.string.map_stop_no_photos),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = MaterialTheme.spacing.large),
                    )
                }
            }
            // My uploads first, as the server puts the ones it is processing.
            items(state.uploads, key = { it.key }) { upload ->
                GalleryUpload(upload = upload, onAction = onAction, modifier = Modifier.animateItem())
            }
            // A stop's own photos are tagged once others' photos share its grid.
            val tagsTrip = state.owner is PhotoOwner.Stop && state.photos.any { it.owner != state.owner }
            items(state.photos, key = { it.id }) { photo ->
                GalleryPhoto(
                    modifier = Modifier.animateItem(),
                    photo = photo,
                    isCover = photo.id == coverPhotoId,
                    isThisTrip = tagsTrip && photo.owner == state.owner,
                    canDelete = state.canDelete(photo, canManage),
                    onAction = onAction,
                )
            }
            // Commons images are cropped in the grid; each shows its full credit in the viewer. "Some" once our own
            // photos share the grid.
            if (state.photos.any { it.credit != null }) {
                val allCommons = state.uploads.isEmpty() && state.photos.all { it.credit != null }
                item(span = fullWidth) {
                    Text(
                        text = stringResource(if (allCommons) Res.string.map_photos_commons_note else Res.string.map_photos_commons_note_some),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = MaterialTheme.spacing.small),
                    )
                }
            }
        }
    }
}

private val GalleryTileMinSize = 104.dp
private const val SKELETON_TILES = 6

@Preview
@Composable
private fun PhotosScreenPreview(@PreviewParameter(PhotosPreviewStates::class) state: State) {
    TripinglyTheme {
        PhotosScreen(
            state = state,
            title = "Photos",
            subtitle = "Eiffel Tower",
            canAdd = true,
            canManage = true,
            coverPhotoId = "ph1",
            onAddPhotos = {},
            onAction = {},
            onClose = {},
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}

/** A viewer's gallery of a place: no Add, nothing to manage. */
@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun PhotosScreenLandscapePreview() {
    TripinglyTheme(darkTheme = true) {
        PhotosScreen(
            state = PhotosPreviewStates().values.first(),
            title = "Photos",
            subtitle = "A very long place name that doesn't fit on one line of the gallery",
            canAdd = false,
            canManage = false,
            coverPhotoId = null,
            onAddPhotos = {},
            onAction = {},
            onClose = {},
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}

@Preview
@Composable
internal fun DeletePhotoDialogPreview() {
    TripinglyTheme { DeletePhotoDialog(onAction = {}) }
}

@Preview
@Composable
internal fun DeletePhotoDialogDarkPreview() {
    TripinglyTheme(darkTheme = true) { DeletePhotoDialog(onAction = {}) }
}
