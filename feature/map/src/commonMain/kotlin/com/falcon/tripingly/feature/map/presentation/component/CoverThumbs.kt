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
import coil3.compose.LocalPlatformContext
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
 * The thumbnails of [covers] (image ID to signed URL) by ID, null for one that failed to load. Loaded through
 * the app's image loader, so an image already seen comes from its cache, whatever its signed URL is now. The new
 * ones load together and arrive at once: on Android each change of the pins can make the map blink. A change of
 * [covers] never cancels loads on their way: while the camera moves the pins change every 250 ms, and restarting
 * then kept photo pins (which wait for their photo) off the map until it rested (Shahin, 2026-11-10).
 */
@Composable
private fun rememberThumbs(covers: Map<String, String>): Map<String, ImageBitmap?> {
    val context = LocalPlatformContext.current
    var thumbs by remember { mutableStateOf(emptyMap<String, ImageBitmap?>()) }
    val currentCovers by rememberUpdatedState(covers)
    LaunchedEffect(Unit) {
        val loader = SingletonImageLoader.get(context)
        val asked = HashSet<String>()
        snapshotFlow { currentCovers }.collect { now ->
            val new = now.filterKeys { it !in asked }
            if (new.isEmpty()) return@collect
            asked += new.keys
            launch {
                val loaded = new.map { (id, url) ->
                    async { id to loader.execute(photoRequest(context, id, url, PhotoSize.Thumb)).image?.toPinBitmap() }
                }.awaitAll()
                thumbs = thumbs + loaded
            }
        }
    }
    return thumbs
}

/** A bitmap that can be drawn into a pin's own (software) canvas. */
internal expect fun Image.toPinBitmap(): ImageBitmap
