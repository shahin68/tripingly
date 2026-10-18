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
            current?.credit?.let { credit ->
                CommonsCredit(
                    credit = credit,
                    cropped = false,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .safeDrawingPadding()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(MaterialTheme.spacing.medium),
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
