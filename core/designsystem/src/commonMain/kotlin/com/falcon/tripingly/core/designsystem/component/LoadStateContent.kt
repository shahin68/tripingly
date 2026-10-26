package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * The three faces of every screen that loads: a shimmering [loading] skeleton until the first answer, the
 * [ErrorScreen] when that answer is an error and there's nothing to show ([errorMessage] not null), otherwise
 * the [content]. Errors after something is shown go to the message banner instead, so content never vanishes.
 * Switches with a short crossfade.
 */
@Composable
fun LoadStateContent(
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    loading: @Composable () -> Unit = { SkeletonList(contentPadding = contentPadding) },
    error: @Composable (message: String) -> Unit = { ErrorScreen(onRetry = onRetry, message = it, contentPadding = contentPadding) },
    content: @Composable () -> Unit,
) {
    val face = when {
        errorMessage != null -> LoadFace.Error
        isLoading -> LoadFace.Loading
        else -> LoadFace.Content
    }
    Crossfade(targetState = face, modifier = modifier) {
        when (it) {
            LoadFace.Loading -> loading()
            LoadFace.Error -> error(errorMessage.orEmpty())
            LoadFace.Content -> content()
        }
    }
}

private enum class LoadFace { Loading, Error, Content }

@Composable
private fun LoadStatePreviewContent(face: Int) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LoadStateContent(
            isLoading = face == 0,
            errorMessage = if (face == 1) "Couldn't load your trips. No internet connection." else null,
            onRetry = {},
        ) {
            Text("Content", Modifier.padding(MaterialTheme.spacing.medium))
        }
    }
}

@Preview
@Composable
private fun LoadStateLoadingPreview() {
    TripinglyTheme { LoadStatePreviewContent(face = 0) }
}

@Preview
@Composable
private fun LoadStateErrorDarkPreview() {
    TripinglyTheme(darkTheme = true) { LoadStatePreviewContent(face = 1) }
}
