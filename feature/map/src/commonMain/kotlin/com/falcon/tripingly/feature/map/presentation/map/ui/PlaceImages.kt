package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceImage
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_place_image_position
import com.falcon.tripingly.feature.map.generated.resources.map_place_images
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlaceImages
import org.jetbrains.compose.resources.stringResource

/**
 * The top of the place card: its images swiped through in a photo banner ([pagerState]), with its name, its [subtitle]
 * and which image shows ("1 / 2"), and its flames, only counted. A tapped image opens full screen ([onOpen]). [images]
 * is null while the place loads; a place without images is a tinted panel, whose tap opens its photos ([onOpenPhotos]).
 */
@Composable
internal fun PlaceBanner(
    name: String,
    subtitle: String,
    category: PlaceCategory,
    hotCount: Int,
    images: List<PlaceImage>?,
    pagerState: PagerState,
    onOpen: (index: Int) -> Unit,
    onOpenPhotos: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val position = images?.takeIf { it.size > 1 }?.let { stringResource(Res.string.map_place_image_position, pagerState.currentPage + 1, it.size) }
    val description = stringResource(Res.string.map_place_images)
    PhotoBanner(
        title = name,
        subtitle = listOfNotNull(subtitle, position).joinToString(" · "),
        modifier = modifier,
        // Loading, the photo's grey waits; a place without images has none to wait for.
        image = if (images?.isEmpty() == true) null else pagerImages(images, pagerState, description, onOpen),
        onClick = onOpenPhotos.takeIf { images?.isEmpty() == true },
        mark = { PinMark(category, stopNumber = null) },
        trailing = { if (hotCount > 0) BannerCount(Icons.Outlined.LocalFireDepartment, hotCount) },
    )
}

/** The place's [images] swiped through; while they load (null), the photo's grey waits. */
private fun pagerImages(images: List<PlaceImage>?, pagerState: PagerState, description: String, onOpen: (index: Int) -> Unit): @Composable BoxScope.() -> Unit = {
    if (images != null) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { images[it].id }) { page ->
            // The display size: a thumbnail is too small for the card, and the viewer opens from cache.
            PhotoImage(
                photoId = images[page].id,
                url = images[page].displayUrl,
                size = PhotoSize.Display,
                contentDescription = description,
                modifier = Modifier.fillMaxSize().clickable { onOpen(page) },
            )
        }
    }
}

/** Loading, a place without images (its category icon), and a place with images. */
internal class PlaceImageStripPreviewStates : PreviewParameterProvider<List<PlaceImage>?> {
    override val values = sequenceOf(null, emptyList(), previewPlaceImages)
}

@Preview
@Composable
private fun PlaceBannerPreview(@PreviewParameter(PlaceImageStripPreviewStates::class) images: List<PlaceImage>?) {
    TripinglyTheme {
        PlaceBanner("Peterskirche", "Attraction · Innere Stadt", PlaceCategory.Attraction, 2_300, images, rememberPagerState { images.orEmpty().size }, onOpen = {}, onOpenPhotos = {}, modifier = Modifier.width(320.dp))
    }
}

@Preview
@Composable
private fun PlaceBannerDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        PlaceBanner("Peterskirche", "Attraction", PlaceCategory.Attraction, 0, emptyList(), rememberPagerState { 0 }, onOpen = {}, onOpenPhotos = {}, modifier = Modifier.width(320.dp))
    }
}
