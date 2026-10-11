package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

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
 * photo still processing has no [url] and shows the placeholder.
 */
@Composable
fun PhotoImage(
    photoId: String,
    url: String?,
    size: PhotoSize,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    if (url == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    val context = LocalPlatformContext.current
    val request = remember(context, photoId, url, size) { photoRequest(context, photoId, url, size) }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        placeholder = placeholder,
        error = placeholder,
        contentScale = contentScale,
    )
}

/** The request for a photo, keyed by what it shows rather than its signed URL; see [PhotoImage]. */
fun photoRequest(context: coil3.PlatformContext, photoId: String, url: String, size: PhotoSize): ImageRequest {
    val key = "photo:$photoId:${size.key}"
    return ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey(key)
        .diskCacheKey(key)
        .build()
}
