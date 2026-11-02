package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.CommonsCredit
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.component.compactCount
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceImage
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_place_image_position
import com.falcon.tripingly.feature.map.generated.resources.map_place_images
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlaceImages
import org.jetbrains.compose.resources.stringResource

/**
 * The top of the place card: its images swiped through in a photo banner, with its name, its [subtitle] and which
 * image shows ("1 / 2"), its hot count, and under it the shown Commons image's credit. [images] is null while the
 * place loads; a place without images shows its category icon.
 */
@Composable
internal fun PlaceBanner(
    name: String,
    subtitle: String,
    hotCount: Int,
    images: List<PlaceImage>?,
    category: PlaceCategory,
    onOpen: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { images.orEmpty().size }
    val position = images?.takeIf { it.size > 1 }?.let { stringResource(Res.string.map_place_image_position, pagerState.currentPage + 1, it.size) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        PhotoBanner(
            title = name,
            subtitle = listOfNotNull(subtitle, position).joinToString(" · "),
            image = {
                when {
                    images == null -> Unit
                    images.isEmpty() -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                        Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(40.dp))
                    }
                    else -> {
                        val description = stringResource(Res.string.map_place_images)
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
            },
        ) {
            if (hotCount > 0) BannerChip(compactCount(hotCount), icon = Icons.Outlined.LocalFireDepartment)
        }
        images?.getOrNull(pagerState.currentPage)?.credit?.let { credit ->
            CommonsCredit(credit = credit, cropped = true, maxLines = 2, modifier = Modifier.padding(horizontal = MaterialTheme.spacing.extraSmall))
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
        PlaceBanner("Peterskirche", "Attraction · Innere Stadt", 2_300, images, PlaceCategory.Museum, onOpen = {}, modifier = Modifier.width(320.dp))
    }
}

@Preview
@Composable
private fun PlaceBannerDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        PlaceBanner("Peterskirche", "Attraction", 0, emptyList(), PlaceCategory.Museum, onOpen = {}, modifier = Modifier.width(320.dp))
    }
}
