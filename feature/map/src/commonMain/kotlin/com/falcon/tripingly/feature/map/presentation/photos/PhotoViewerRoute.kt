package com.falcon.tripingly.feature.map.presentation.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.SkeletonBox
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.*
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel.Action
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
