package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.request.ImageRequest
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import kotlinx.coroutines.launch

/** The sizes the server makes of every photo. */
enum class PhotoSize(internal val key: String) {
    /** 256 px square: pins, strips and lists. */
    Thumb("thumb"),

    /** At most 1600 px on the long edge: the gallery. */
    Display("display"),
}

/**
 * One of our photos. A photo's pixels never change while its signed [url] does (about every hour), so it's
 * cached in memory and on disk by [photoId] and [size]: once loaded, a new URL never downloads it again. A
 * photo still processing has no [url] and shows the grey placeholder; a loading one shimmers until it's there.
 */
@Composable
fun PhotoImage(
    photoId: String,
    url: String?,
    size: PhotoSize,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val placeholder = ColorPainter(placeholderColor)
    if (url == null) {
        Box(modifier.background(placeholderColor))
        return
    }
    val context = LocalPlatformContext.current
    val request = remember(context, photoId, url, size) { photoRequest(context, photoId, url, size) }
    // Shimmers like the photo lists' skeletons until the image is there (Shahin, 2026-11-14), then the photo fades in
    // over the shimmer fading out (Shahin, 2026-11-23); one already in memory shows at once. A failed one stays grey.
    val scope = rememberCoroutineScope()
    val reveal = remember(request) { Animatable(0f) }
    val isRevealed by remember(reveal) { derivedStateOf { reveal.value >= 1f } }
    Box(modifier, propagateMinConstraints = true) {
        // The alphas are read while drawing, so the fade redraws without recomposing.
        if (!isRevealed) SkeletonBox(Modifier.graphicsLayer { alpha = 1f - reveal.value }, shape = RectangleShape, base = placeholderColor)
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            error = placeholder,
            contentScale = contentScale,
            modifier = Modifier.graphicsLayer { alpha = reveal.value },
            onSuccess = { state ->
                scope.launch {
                    if (state.result.dataSource == DataSource.MEMORY_CACHE) reveal.snapTo(1f) else reveal.animateTo(1f, tween(PHOTO_FADE_MILLIS))
                }
            },
            onError = { scope.launch { reveal.snapTo(1f) } },
        )
    }
}

/** How long a photo takes to fade in over its shimmer. */
private const val PHOTO_FADE_MILLIS = 250

/** The request for a photo, keyed by what it shows rather than its signed URL; see [PhotoImage]. */
fun photoRequest(context: coil3.PlatformContext, photoId: String, url: String, size: PhotoSize): ImageRequest {
    val key = "photo:$photoId:${size.key}"
    return ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey(key)
        .diskCacheKey(key)
        .build()
}

/** Previews have no network: a photo still processing (grey), and one loading (the skeleton, still in previews). */
@Composable
private fun PhotoImagePreviewContent() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhotoImage(photoId = "1", url = null, size = PhotoSize.Thumb, contentDescription = null, modifier = Modifier.size(64.dp))
        PhotoImage(photoId = "2", url = "https://example.com/2.jpg", size = PhotoSize.Thumb, contentDescription = null, modifier = Modifier.size(64.dp))
    }
}

@Preview
@Composable
private fun PhotoImagePreview() {
    TripinglyTheme { PhotoImagePreviewContent() }
}

@Preview
@Composable
private fun PhotoImageDarkPreview() {
    TripinglyTheme(darkTheme = true) { PhotoImagePreviewContent() }
}
