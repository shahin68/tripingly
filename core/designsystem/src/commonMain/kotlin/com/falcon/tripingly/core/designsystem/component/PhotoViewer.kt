package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.photo_viewer_close
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.photo.Photo
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PhotoStatus
import com.falcon.tripingly.core.model.place.ImageCredit
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import androidx.compose.material3.Text
import com.falcon.tripingly.core.designsystem.generated.resources.photo_viewer_uploaded_by
import com.falcon.tripingly.core.model.trip.UserSummary

/**
 * The app's one full-screen photo viewer: [photos] uncropped on black, swiped through from [initialIndex] (one
 * photo works too). Every photo shows its like ([onToggleLike]), and a Wikimedia Commons image its credit.
 * [actions] adds the caller's buttons for the photo on show, after the like (report, make cover, delete).
 */
@Composable
fun PhotoViewer(
    photos: List<Photo>,
    initialIndex: Int,
    onToggleLike: (Photo) -> Unit,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.(Photo) -> Unit = {},
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { photos[it].id }) { page ->
                val photo = photos[page]
                PhotoImage(
                    photoId = photo.id,
                    url = photo.displayUrl,
                    size = PhotoSize.Display,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            // Full screen, so the viewer shows the app's messages itself, below its top bar.
            MessageBannerHost(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 56.dp))
            val current = photos.getOrNull(pagerState.currentPage)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.photo_viewer_close), tint = Color.White)
                }
                Box(Modifier.weight(1f))
                if (current != null) {
                    PhotoLike(photo = current, onToggle = { onToggleLike(current) }, modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small))
                    actions(current)
                }
            }
            // A Commons image credits its author and licence; a photo, who uploaded it.
            val bottom = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(MaterialTheme.spacing.medium)
            current?.uploader?.takeIf { current.credit == null }?.let { uploader ->
                Text(
                    text = stringResource(Res.string.photo_viewer_uploaded_by, uploader.username),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = bottom,
                )
            }
            current?.credit?.let { credit ->
                CommonsCredit(
                    credit = credit,
                    cropped = false,
                    color = Color.White,
                    modifier = bottom,
                )
            }
        }
    }
}

/** A photo's like on a small pill so it reads on any photo; the same in a gallery grid and in [PhotoViewer]. */
@Composable
fun PhotoLike(photo: Photo, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), modifier = modifier) {
        LikeButton(liked = photo.likedByMe, count = photo.likeCount, onToggle = onToggle)
    }
}

private fun previewPhoto(id: String, likes: Int, liked: Boolean, credit: ImageCredit? = null, uploader: UserSummary? = null) = Photo(
    id = id,
    owner = PhotoOwner.Place("place"),
    status = PhotoStatus.Ready,
    thumbUrl = null,
    displayUrl = null,
    width = 1600,
    height = 1200,
    position = 0,
    likeCount = likes,
    likedByMe = liked,
    uploader = uploader,
    credit = credit,
)

private val previewViewerPhotos = listOf(
    previewPhoto(
        id = "1",
        likes = 12,
        liked = true,
        credit = ImageCredit(
            author = "Benh Lieu Song",
            title = null,
            license = "CC BY-SA 3.0",
            licenseUrl = null,
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Tour_Eiffel.jpg",
        ),
    ),
    previewPhoto(id = "2", likes = 0, liked = false, uploader = UserSummary(id = "u1", username = "anna", displayName = "Anna")),
)

@Preview
@Composable
private fun PhotoViewerPreview() {
    TripinglyTheme { PhotoViewer(photos = previewViewerPhotos, initialIndex = 0, onToggleLike = {}, onDismiss = {}) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun PhotoViewerLandscapePreview() {
    TripinglyTheme(darkTheme = true) {
        PhotoViewer(photos = previewViewerPhotos, initialIndex = 1, onToggleLike = {}, onDismiss = {})
    }
}

@Composable
private fun PhotoLikePreviewContent() {
    Row(Modifier.background(Color.DarkGray).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhotoLike(photo = previewViewerPhotos[0], onToggle = {})
        PhotoLike(photo = previewViewerPhotos[1], onToggle = {})
    }
}

@Preview
@Composable
private fun PhotoLikePreview() {
    TripinglyTheme { PhotoLikePreviewContent() }
}

@Preview
@Composable
private fun PhotoLikeDarkPreview() {
    TripinglyTheme(darkTheme = true) { PhotoLikePreviewContent() }
}
