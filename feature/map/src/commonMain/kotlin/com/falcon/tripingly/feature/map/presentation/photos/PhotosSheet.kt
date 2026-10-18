package com.falcon.tripingly.feature.map.presentation.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.data.photos.PhotoUpload
import com.falcon.tripingly.core.designsystem.component.MessageBannerHost
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoLike
import com.falcon.tripingly.core.designsystem.component.PhotoViewer
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PhotoStatus
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.*
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
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
            )
            MessageBannerHost(Modifier.align(Alignment.TopCenter))
        }
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
    val onAction: (Action) -> Unit = { action ->
        viewModel.onAction(action)
        if (action is Action.OnClosePhoto) onDismiss()
    }
    val index = state.openPhotoIndex
    when {
        index != null -> GalleryViewer(
            state = state,
            initialIndex = index,
            coverPhotoId = null,
            canManage = false,
            onReport = onReport,
            onAction = onAction,
        )
        // The gallery is loading, or the photo is about to open; a photo that isn't in it (deleted meanwhile) closes.
        state.isLoading || state.viewable.any { it.id == photoId } -> Dialog(
            onDismissRequest = { onAction(Action.OnClosePhoto) },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        else -> LaunchedEffect(Unit) { onAction(Action.OnClosePhoto) }
    }
    if (state.photoToDelete != null) DeletePhotoDialog(viewModel::onAction)
}

@Composable
private fun DeletePhotoDialog(onAction: (Action) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(Action.OnDismissDelete) },
        title = { Text(stringResource(Res.string.map_photo_delete_title)) },
        text = { Text(stringResource(Res.string.map_photo_delete_message)) },
        confirmButton = {
            TextButton(onClick = { onAction(Action.OnConfirmDelete) }) {
                Text(stringResource(Res.string.map_confirm_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(Action.OnDismissDelete) }) {
                Text(stringResource(Res.string.map_cancel))
            }
        },
    )
}

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
    modifier: Modifier = Modifier,
) {
    ShowMessage(message = state.message?.asString(), onShown = { onAction(Action.OnDismissMessage) }, isError = state.message !is UiText.Note)
    val fullWidth: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(span = fullWidth) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.small),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canAdd) {
                    FilledTonalButton(onClick = onAddPhotos) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.map_stop_add_photos), modifier = Modifier.padding(start = MaterialTheme.spacing.small))
                    }
                    FilledTonalIconButton(
                        onClick = { onAction(Action.OnOpenCamera) },
                        modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = stringResource(Res.string.map_camera_take_photo))
                    }
                }
            }
        }
        if (state.isLoading && state.uploads.isEmpty()) {
            item(span = fullWidth) {
                Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
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
        items(state.photos, key = { it.id }) { photo ->
            GalleryPhoto(
                photo = photo,
                isCover = photo.id == coverPhotoId,
                canDelete = state.canDelete(photo, canManage),
                onAction = onAction,
            )
        }
        items(state.uploads, key = { it.key }) { upload ->
            GalleryUpload(upload = upload, onAction = onAction)
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

@Composable
private fun GalleryPhoto(photo: Photo, isCover: Boolean, canDelete: Boolean, onAction: (Action) -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = photo.status == PhotoStatus.Ready) { onAction(Action.OnOpenPhoto(photo.id)) },
    ) {
        PhotoImage(
            photoId = photo.id,
            url = photo.thumbUrl,
            size = PhotoSize.Thumb,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        when (photo.status) {
            PhotoStatus.Processing, PhotoStatus.Uploading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(24.dp),
                strokeWidth = 2.dp,
            )
            PhotoStatus.Failed -> {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = stringResource(Res.string.map_photo_failed),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (canDelete) {
                    IconButton(onClick = { onAction(Action.OnDeletePhoto(photo)) }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.map_photo_delete))
                    }
                }
            }
            PhotoStatus.Ready -> {
                if (isCover) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = stringResource(Res.string.map_photo_cover),
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .padding(2.dp)
                            .size(16.dp),
                    )
                }
                PhotoLike(photo = photo, onToggle = { onAction(Action.OnToggleLike(photo)) }, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
        }
    }
}

/** A stop's owners and editors delete any photo; everyone deletes their own. */
private fun State.canDelete(photo: Photo, canManage: Boolean): Boolean =
    canManage || (myUserId != null && photo.uploaderId == myUserId)

@Composable
private fun GalleryUpload(upload: PhotoUpload, onAction: (Action) -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when (val uploadState = upload.state) {
            PhotoUpload.State.Queued -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            is PhotoUpload.State.Sending -> CircularProgressIndicator(
                progress = { uploadState.progress },
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
            is PhotoUpload.State.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(
                        if (uploadState.error == null) Res.string.map_photo_unreadable else Res.string.map_photo_not_uploaded,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    // An unreadable file stays unreadable, so only a lost connection or a refusal gets Retry.
                    if (uploadState.error != null) {
                        IconButton(onClick = { onAction(Action.OnRetryUpload(upload.key)) }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.map_retry))
                        }
                    }
                    IconButton(onClick = { onAction(Action.OnRemoveUpload(upload.key)) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_photo_remove))
                    }
                }
            }
        }
    }
}

/**
 * The ready photos in the app's [PhotoViewer], with what this gallery adds for the photo on show: report
 * (not one's own), make it the cover (a stop's owners and editors), and delete (whoever may).
 */
@Composable
private fun GalleryViewer(
    state: State,
    initialIndex: Int,
    coverPhotoId: String?,
    canManage: Boolean,
    onReport: (Photo) -> Unit,
    onAction: (Action) -> Unit,
) {
    PhotoViewer(
        photos = state.viewable,
        initialIndex = initialIndex,
        onToggleLike = { onAction(Action.OnToggleLike(it)) },
        onDismiss = { onAction(Action.OnClosePhoto) },
    ) { current ->
        if (state.myUserId == null || current.uploaderId != state.myUserId) {
            IconButton(onClick = { onReport(current) }) {
                Icon(Icons.Outlined.Flag, contentDescription = stringResource(Res.string.map_place_image_report), tint = Color.White)
            }
        }
        // Only a stop's owners and editors manage, and only a stop has a cover.
        if (canManage) {
            if (current.id == coverPhotoId) {
                Text(
                    text = stringResource(Res.string.map_photo_cover),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                )
            } else {
                TextButton(onClick = { onAction(Action.OnMakeCover(current.id)) }, enabled = !state.isSaving) {
                    Text(stringResource(Res.string.map_photo_make_cover), color = Color.White)
                }
            }
        }
        if (state.canDelete(current, canManage)) {
            IconButton(onClick = { onAction(Action.OnDeletePhoto(current)) }, enabled = !state.isSaving) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.map_photo_delete), tint = Color.White)
            }
        }
    }
}
