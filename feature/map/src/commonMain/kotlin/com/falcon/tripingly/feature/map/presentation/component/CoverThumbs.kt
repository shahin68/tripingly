package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.component.photoRequest
import com.falcon.tripingly.core.model.place.MapPlace
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** The cover thumbnails of [markers] by photo ID, for drawing into their pins (null when one failed to load). */
@Composable
internal fun rememberCoverThumbs(markers: List<MapMarker>): Map<String, ImageBitmap?> =
    rememberThumbs(
        markers.mapNotNull { marker ->
            val id = marker.coverPhotoId ?: return@mapNotNull null
            marker.coverThumbUrl?.let { id to it }
        }.toMap(),
    )

/** The photos of the photo pins in [pins] by image ID ([MapPlace.coverImageId]), null when one failed to load. */
@Composable
internal fun rememberPinPhotos(pins: List<PlacePin>): Map<String, ImageBitmap?> =
    rememberThumbs(
        pins.filter { it.photo }.mapNotNull { pin ->
            val id = pin.place.coverImageId ?: return@mapNotNull null
            pin.place.coverThumbUrl?.let { id to it }
        }.toMap(),
    )

/**
 * The brand logos of the pins in [pins] by URL ([com.falcon.tripingly.core.model.place.Brand.logoUrl]), null when one
 * failed to load. A logo never changes at its URL, so the image loader keeps it for good under that URL.
 */
@Composable
internal fun rememberBrandLogos(pins: List<PlacePin>): Map<String, ImageBitmap?> =
    rememberThumbs(pins.mapNotNull { it.place.brand?.logoUrl }.associateWith { it }) { context, url, _ ->
        val key = "brand-logo:$url"
        ImageRequest.Builder(context).data(url).memoryCacheKey(key).diskCacheKey(key).build()
    }

/** The brand logo [this] pin shows: its URL once the logo has loaded, else null (round pin). */
internal fun MapPlace.shownLogo(logos: Map<String, ImageBitmap?>): String? = brand?.logoUrl?.takeIf { logos[it] != null }

/** Whether [this] pin is ready: its photo or brand logo has loaded (or failed to), so its look won't change at once. */
internal fun PlacePin.isReady(photos: Map<String, ImageBitmap?>, logos: Map<String, ImageBitmap?>): Boolean =
    (!photo || place.coverImageId in photos) && place.brand?.logoUrl.let { it == null || it in logos }

/**
 * The thumbnails of [covers] (image ID to signed URL) by ID, null for one that failed to load. Loaded through
 * the app's image loader, so an image already seen comes from its cache, whatever its signed URL is now. The new
 * ones load together and arrive at once: on Android each change of the pins can make the map blink. A change of
 * [covers] never cancels loads on their way: while the camera moves the pins change every 250 ms, and restarting
 * then kept photo pins (which wait for their photo) off the map until it rested (Shahin, 2026-11-10).
 */
@Composable
private fun rememberThumbs(
    covers: Map<String, String>,
    request: (PlatformContext, String, String) -> ImageRequest = { context, id, url -> photoRequest(context, id, url, PhotoSize.Thumb) },
): Map<String, ImageBitmap?> {
    val context = LocalPlatformContext.current
    var thumbs by remember { mutableStateOf(emptyMap<String, ImageBitmap?>()) }
    val currentCovers by rememberUpdatedState(covers)
    LaunchedEffect(Unit) {
        val loader = SingletonImageLoader.get(context)
        val asked = HashSet<String>()
        snapshotFlow { currentCovers }.collect { now ->
            // Kept bitmaps stay bounded: past [MAX_KEPT_THUMBS] the ones no pin shows go (a pin that comes back finds its
            // image in the loader's memory cache), so a long session doesn't pile up hundreds of decoded photos.
            if (thumbs.size > MAX_KEPT_THUMBS) {
                thumbs = thumbs.filterKeys { it in now }
                asked.retainAll(now.keys)
            }
            val new = now.filterKeys { it !in asked }
            if (new.isEmpty()) return@collect
            asked += new.keys
            launch {
                val loaded = new.map { (id, url) ->
                    async { id to loader.execute(request(context, id, url)).image?.toPinBitmap() }
                }.awaitAll()
                thumbs = thumbs + loaded
            }
        }
    }
    return thumbs
}

/** A bitmap that can be drawn into a pin's own (software) canvas. */
internal expect fun Image.toPinBitmap(): ImageBitmap

/** About twice the photo pins a view shows at most (APP_MAP_MAX_PHOTO_PINS defaults to 100). */
private const val MAX_KEPT_THUMBS = 250

/**
 * [getOrPut] for the map's icon caches, which keep at most [max] entries: past that the oldest goes (a pin that needs it
 * again draws it anew), so looks seen over a long session (every photo, size and colour) don't pile up.
 */
internal inline fun <K, V> MutableMap<K, V>.getOrPutBounded(key: K, max: Int, make: () -> V): V =
    get(key) ?: make().also { value ->
        put(key, value)
        if (size > max) remove(keys.first())
    }

/** Place pin looks kept drawn: every pin a view shows, as pin and as dot, with room to spare. */
internal const val MAX_PIN_ICONS = 600
