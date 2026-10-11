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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.data.photos.PhotoUpload
import com.falcon.tripingly.core.designsystem.component.ErrorBanner
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoStatus
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.*
import com.falcon.tripingly.feature.map.presentation.photos.StopPhotosViewModel.Action
import com.falcon.tripingly.feature.map.presentation.photos.StopPhotosViewModel.State
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The photos of [marker] in a sheet over the map; owners and editors ([canEdit]) add and manage them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopPhotosRoute(
    marker: MapMarker,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    viewModel: StopPhotosViewModel = koinViewModel(key = "photos:${marker.id}") { parametersOf(marker.id) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickPhotos = rememberPhotoPicker { images -> viewModel.onAction(Action.OnAddPhotos(images)) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        StopPhotosScreen(
            state = state,
            marker = marker,
            canEdit = canEdit,
            onAddPhotos = pickPhotos,
            onAction = viewModel::onAction,
        )
    }

    state.openPhotoIndex?.let { index ->
        PhotoViewer(
            state = state,
            initialIndex = index,
            coverPhotoId = marker.coverPhotoId,
            canEdit = canEdit,
            onAction = viewModel::onAction,
        )
    }

    state.photoToDelete?.let {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(Action.OnDismissDelete) },
            title = { Text(stringResource(Res.string.map_photo_delete_title)) },
            text = { Text(stringResource(Res.string.map_photo_delete_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(Action.OnConfirmDelete) }) {
                    Text(stringResource(Res.string.map_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(Action.OnDismissDelete) }) {
                    Text(stringResource(Res.string.map_cancel))
                }
            },
        )
    }
}

@Composable
fun StopPhotosScreen(
    state: State,
    marker: MapMarker,
    canEdit: Boolean,
    onAddPhotos: () -> Unit,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        text = marker.title.ifBlank { stringResource(Res.string.map_stop_title_format, marker.orderNumber) },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(Res.string.map_stop_photos),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canEdit) {
                    FilledTonalButton(onClick = onAddPhotos) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.map_stop_add_photos), modifier = Modifier.padding(start = MaterialTheme.spacing.small))
                    }
                }
            }
        }
        state.message?.let { message ->
            item(span = fullWidth) {
                ErrorBanner(errorMessage = message.asString(), onDismiss = { onAction(Action.OnDismissMessage) })
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
                isCover = photo.id == marker.coverPhotoId,
                canEdit = canEdit,
                onAction = onAction,
            )
        }
        items(state.uploads, key = { it.key }) { upload ->
            GalleryUpload(upload = upload, onAction = onAction)
        }
    }
}

@Composable
private fun GalleryPhoto(photo: Photo, isCover: Boolean, canEdit: Boolean, onAction: (Action) -> Unit) {
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
                if (canEdit) {
                    IconButton(onClick = { onAction(Action.OnDeletePhoto(photo)) }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.map_photo_delete))
                    }
                }
            }
            PhotoStatus.Ready -> if (isCover) {
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
        }
    }
}

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

/** The ready photos full screen, swiped through; owners and editors make one the cover or delete it. */
@Composable
private fun PhotoViewer(
    state: State,
    initialIndex: Int,
    coverPhotoId: String?,
    canEdit: Boolean,
    onAction: (Action) -> Unit,
) {
    val viewable = state.viewable
    val pagerState = rememberPagerState(initialPage = initialIndex) { viewable.size }
    Dialog(
        onDismissRequest = { onAction(Action.OnClosePhoto) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { viewable[it].id }) { page ->
                val photo = viewable[page]
                PhotoImage(
                    photoId = photo.id,
                    url = photo.displayUrl,
                    size = PhotoSize.Display,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            val current = viewable.getOrNull(pagerState.currentPage)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onAction(Action.OnClosePhoto) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_close), tint = Color.White)
                }
                Box(Modifier.weight(1f))
                if (canEdit && current != null) {
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
                    IconButton(onClick = { onAction(Action.OnDeletePhoto(current)) }, enabled = !state.isSaving) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.map_photo_delete), tint = Color.White)
                    }
                }
            }
        }
    }
}
