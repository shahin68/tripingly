package com.falcon.tripingly.feature.map.presentation.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.component.SkeletonBox
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_photos_more
import com.falcon.tripingly.feature.map.generated.resources.map_stop_no_photos
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel.Action
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel.State
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The first photos of [owner] in a row, for its details sheet: a tapped one opens full screen in the same viewer as the
 * Photos sheet's, the last shows how many more there are and opens them all ([onShowAll]). [onReport] reports the photo
 * open in the viewer.
 */
@Composable
fun PhotoStripRoute(
    owner: PhotoOwner,
    onShowAll: () -> Unit,
    onReport: (Photo) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotosViewModel = koinViewModel(key = "photos:$owner") { parametersOf(owner) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PhotoStrip(state, onShowAll = onShowAll, onAction = viewModel::onAction, modifier = modifier)
    state.openPhotoIndex?.let { index ->
        GalleryViewer(state = state, initialIndex = index, coverPhotoId = null, canManage = false, onReport = onReport, onAction = viewModel::onAction)
    }
}

/** Up to [STRIP_TILES] ready photos of [state] side by side, the last "+N" when there are more; skeletons while loading. */
@Composable
internal fun PhotoStrip(state: State, onShowAll: () -> Unit, onAction: (Action) -> Unit, modifier: Modifier = Modifier) {
    val photos = state.viewable
    when {
        state.isLoading && photos.isEmpty() -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            repeat(STRIP_TILES) { SkeletonBox(Modifier.weight(1f).height(TileHeight)) }
        }
        photos.isEmpty() -> Text(
            stringResource(Res.string.map_stop_no_photos),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        else -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            val shown = photos.take(STRIP_TILES)
            val more = photos.size - STRIP_TILES + 1
            shown.forEachIndexed { index, photo ->
                val isMore = index == STRIP_TILES - 1 && more > 1
                Box(
                    Modifier
                        .weight(1f)
                        .height(TileHeight)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { if (isMore) onShowAll() else onAction(Action.OnOpenPhoto(photo.id)) },
                ) {
                    PhotoImage(photoId = photo.id, url = photo.thumbUrl, size = PhotoSize.Thumb, contentDescription = null, modifier = Modifier.fillMaxSize())
                    if (isMore) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                            Text(stringResource(Res.string.map_photos_more, more), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
            // A short row keeps its tiles the same size as a full one's.
            repeat(STRIP_TILES - shown.size) { Box(Modifier.weight(1f)) }
        }
    }
}

private const val STRIP_TILES = 4
private val TileHeight = 74.dp
private val TileGap = 6.dp

@Composable
private fun PhotoStripPreviewContent() {
    val states = PhotosPreviewStates().values.toList()
    Column(
        Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        states.forEach { PhotoStrip(it, onShowAll = {}, onAction = {}) }
    }
}

@Preview
@Composable
private fun PhotoStripPreview() {
    TripinglyTheme { PhotoStripPreviewContent() }
}

@Preview
@Composable
private fun PhotoStripDarkPreview() {
    TripinglyTheme(darkTheme = true) { PhotoStripPreviewContent() }
}
