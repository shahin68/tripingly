package com.falcon.tripingly.feature.map.presentation.photos

import com.falcon.tripingly.core.ui.photos.rememberPhotoPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.AppDialog
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.MessageBannerHost
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.SkeletonBox
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.*
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel.Action
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel.State
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The photos of a stop or a place ([owner]) in a sheet over the map, under [title]. [canAdd]: may add photos
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
            title = title,
            subtitle = stringResource(if (owner is PhotoOwner.Place) Res.string.map_place_photos else Res.string.map_stop_photos),
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

/**
 * One photo of [owner]'s gallery ([photoId]) full screen, without the sheet: the same viewer, likes and
 * gallery as [PhotosRoute], opened from elsewhere (a place card's images). Closing it ends [onDismiss].
 */
@Composable
fun PhotoViewerRoute(
    owner: PhotoOwner,
    photoId: String,
    onReport: (Photo) -> Unit,
    onDismiss: () -> Unit,
    viewModel: PhotosViewModel = koinViewModel(key = "photos:$owner") { parametersOf(owner) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(photoId) { viewModel.onAction(Action.OnOpenPhoto(photoId)) }
    // Left without closing (e.g. the card went away), the shared gallery must not stay on this photo.
    DisposableEffect(viewModel) { onDispose { viewModel.onAction(Action.OnClosePhoto) } }
    val onAction: (Action) -> Unit = { action ->
        viewModel.onAction(action)
        if (action is Action.OnClosePhoto) onDismiss()
    }
    // Until the gallery has taken this photo in, the state can still be an older one.
    val isAsked = state.openPhotoId == photoId
    val index = state.openPhotoIndex?.takeIf { isAsked }
    val message = state.message
    when {
        index != null -> GalleryViewer(
            state = state,
            initialIndex = index,
            coverPhotoId = null,
            canManage = false,
            onReport = onReport,
            onAction = onAction,
        )
        // The gallery is loading, or still looking for the photo.
        !isAsked || state.isLoading || state.isFindingPhoto -> Dialog(
            onDismissRequest = { onAction(Action.OnClosePhoto) },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            // The photo's place in the viewer, shimmering until it's found.
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                SkeletonBox(Modifier.fillMaxWidth().aspectRatio(1f), shape = RectangleShape)
            }
        }
        // Not there even after a fresh look (deleted, hidden, or the gallery didn't load): say why, then close.
        else -> {
            val text = message?.asString() ?: stringResource(Res.string.map_photo_gone)
            val closingText = remember { text }
            ShowMessage(
                message = closingText,
                onShown = {
                    onAction(Action.OnDismissMessage)
                    onAction(Action.OnClosePhoto)
                },
                isError = message !is UiText.Note,
            )
        }
    }
    // While it's still looking, a message waits: the close above says it.
    if (index != null) {
        ShowMessage(message = message?.asString(), onShown = { onAction(Action.OnDismissMessage) }, isError = message !is UiText.Note)
    }
    if (state.photoToDelete != null) DeletePhotoDialog(viewModel::onAction)
}

@Composable
private fun DeletePhotoDialog(onAction: (Action) -> Unit) {
    AppDialog(
        title = stringResource(Res.string.map_photo_delete_title),
        message = stringResource(Res.string.map_photo_delete_message),
        confirmLabel = stringResource(Res.string.map_confirm_delete),
        isDestructive = true,
        onConfirm = { onAction(Action.OnConfirmDelete) },
        onDismiss = { onAction(Action.OnDismissDelete) },
    )
}

/** The sheet's content: the header with Add photos and the camera, then the uploads and photos in a grid. */
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
    val fullWidth: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    Column(modifier.fillMaxWidth().navigationBarsPadding()) {
        SheetHeader(title = title, subtitle = subtitle, onClose = onClose)
        if (canAdd) {
            Row(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium).padding(bottom = MaterialTheme.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                PrimaryButton(
                    text = stringResource(Res.string.map_stop_add_photos),
                    onClick = onAddPhotos,
                    size = ButtonSize.Small,
                    icon = Icons.Default.AddPhotoAlternate,
                )
                SecondaryButton(
                    text = stringResource(Res.string.map_camera_take_photo),
                    onClick = { onAction(Action.OnOpenCamera) },
                    size = ButtonSize.Small,
                    icon = Icons.Default.PhotoCamera,
                )
            }
        }
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
            // Commons images are cropped in the grid; each shows its full credit in the viewer.
            if (state.photos.any { it.credit != null }) {
                item(span = fullWidth) {
                    Text(
                        text = stringResource(Res.string.map_photos_commons_note),
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
            title = "Eiffel Tower",
            subtitle = "Day 1 · Stop 1",
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
            title = "A very long place name that doesn't fit on one line of the gallery",
            subtitle = "5 photos",
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
private fun DeletePhotoDialogPreview() {
    TripinglyTheme { DeletePhotoDialog(onAction = {}) }
}

@Preview
@Composable
private fun DeletePhotoDialogDarkPreview() {
    TripinglyTheme(darkTheme = true) { DeletePhotoDialog(onAction = {}) }
}
