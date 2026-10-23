package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.CommonsCredit
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceImage
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_place_image_position
import com.falcon.tripingly.feature.map.generated.resources.map_place_images
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.foundation.layout.width

/**
 * The top of the place card: its images swiped through, cropped to the card, each Commons image with its
 * credit. [images] is null while the place loads; a place without images shows its category icon.
 */
@Composable
internal fun PlaceImageStrip(
    images: List<PlaceImage>?,
    category: PlaceCategory,
    onOpen: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val frame = Modifier.fillMaxWidth().height(StripHeight).clip(shape)
    when {
        images == null -> Box(modifier.then(frame).background(MaterialTheme.colorScheme.surfaceVariant))
        images.isEmpty() -> Box(
            modifier = modifier.then(frame).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val pagerState = rememberPagerState { images.size }
            val description = stringResource(Res.string.map_place_images)
            Box(frame) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { images[it].id }) { page ->
                    val image = images[page]
                    // The display size: a thumbnail is too small for the card, and the viewer opens from cache.
                    PhotoImage(
                        photoId = image.id,
                        url = image.displayUrl,
                        size = PhotoSize.Display,
                        contentDescription = description,
                        modifier = Modifier.fillMaxSize().clickable { onOpen(page) },
                    )
                }
                if (images.size > 1) {
                    Text(
                        text = stringResource(Res.string.map_place_image_position, pagerState.currentPage + 1, images.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(MaterialTheme.spacing.small)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            images.getOrNull(pagerState.currentPage)?.credit?.let { credit ->
                CommonsCredit(credit = credit, cropped = true, maxLines = 2)
            }
        }
    }
}

private val StripHeight = 140.dp

/** Loading, a place without images (its category icon), and a place with images. */
internal class PlaceImageStripPreviewStates : PreviewParameterProvider<List<PlaceImage>?> {
    override val values = sequenceOf(null, emptyList(), previewPlaceImages)
}

@Preview
@Composable
private fun PlaceImageStripPreview(@PreviewParameter(PlaceImageStripPreviewStates::class) images: List<PlaceImage>?) {
    TripinglyTheme {
        PlaceImageStrip(images = images, category = PlaceCategory.Museum, onOpen = {}, modifier = Modifier.width(320.dp))
    }
}
