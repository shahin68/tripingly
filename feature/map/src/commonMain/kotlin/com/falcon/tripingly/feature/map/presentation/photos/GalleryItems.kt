package com.falcon.tripingly.feature.map.presentation.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.data.photos.PhotoUpload
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoLike
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.component.PhotoViewer
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoStatus
import com.falcon.tripingly.feature.map.generated.resources.*
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel.Action
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel.State
import org.jetbrains.compose.resources.stringResource

/* The gallery's tiles (a photo, an upload) and its full-screen viewer. */

@Composable
internal fun GalleryPhoto(
    photo: Photo,
    isCover: Boolean,
    isThisTrip: Boolean,
    canDelete: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
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
                if (isThisTrip) {
                    Text(
                        text = stringResource(Res.string.map_photo_this_trip),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                PhotoLike(photo = photo, onToggle = { onAction(Action.OnToggleLike(photo)) }, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
        }
    }
}

/** A stop's owners and editors delete any of the stop's own photos; everyone deletes their own. */
internal fun State.canDelete(photo: Photo, canManage: Boolean): Boolean =
    (canManage && photo.owner == owner) || (myUserId != null && photo.uploader?.id == myUserId)

@Composable
internal fun GalleryUpload(upload: PhotoUpload, onAction: (Action) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
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
 * (not one's own), make it the cover (a stop's owners and editors, any photo of its grid), and delete (whoever may).
 */
@Composable
internal fun GalleryViewer(
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
        if (state.myUserId == null || current.uploader?.id != state.myUserId) {
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

/** A ready photo of this trip as the cover, one processing, one that failed (deletable), and each state of an upload. */
@Preview
@Composable
private fun GalleryItemsPreview() {
    TripinglyTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val item = Modifier.size(104.dp)
            Box(item) { GalleryPhoto(previewGalleryPhotos[0], isCover = true, isThisTrip = true, canDelete = true, onAction = {}) }
            Box(item) { GalleryPhoto(previewGalleryPhotos[2], isCover = false, isThisTrip = false, canDelete = true, onAction = {}) }
            Box(item) { GalleryPhoto(previewGalleryPhotos[3], isCover = false, isThisTrip = false, canDelete = true, onAction = {}) }
            previewUploads.forEach { upload -> Box(item) { GalleryUpload(upload, onAction = {}) } }
        }
    }
}

@Preview
@Composable
private fun GalleryItemsDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val item = Modifier.size(104.dp)
            Box(item) { GalleryPhoto(previewGalleryPhotos[0], isCover = true, isThisTrip = false, canDelete = true, onAction = {}) }
            Box(item) { GalleryUpload(previewUploads.last(), onAction = {}) }
        }
    }
}
