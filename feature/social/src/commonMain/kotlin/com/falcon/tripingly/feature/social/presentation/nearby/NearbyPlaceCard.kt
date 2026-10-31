package com.falcon.tripingly.feature.social.presentation.nearby

import androidx.compose.foundation.shape.CircleShape
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.component.skeleton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.NumberUtils
import com.falcon.tripingly.core.designsystem.component.HotButton
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_add_place
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_distance_km
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_distance_m
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_open_images
import com.falcon.tripingly.feature.social.presentation.component.previewNearbyPlaces
import org.jetbrains.compose.resources.stringResource

/**
 * A place near me: its cover (or category icon), name and distance; below them the flame and, at the bottom end,
 * its photos and adding it to a trip. Tapping the card opens the photos too.
 */
@Composable
internal fun NearbyPlaceCard(
    place: NearbyPlace,
    onOpenImage: () -> Unit,
    onMarkHot: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cover = place.coverImageId
    val openImages = stringResource(Res.string.social_nearby_open_images)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            // A tap opens the place's images, where a Wikimedia Commons image shows its credit.
            .clickable(enabled = cover != null, onClickLabel = openImages, onClick = onOpenImage),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        // Symmetric: the cover sits the same distance from the top, bottom and start, and the end button's circle as far
        // from the end (its 48 dp touch area reaches past it). Title, distance and the button row are as tall as the cover.
        val inset = MaterialTheme.spacing.small
        Row(
            modifier = Modifier.padding(start = inset, top = inset, bottom = inset, end = inset - TouchInset),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumb = Modifier.size(CoverSize).skeleton(MaterialTheme.shapes.medium).clip(MaterialTheme.shapes.medium)
            if (cover != null) {
                PhotoImage(photoId = cover, url = place.coverThumbUrl, size = PhotoSize.Thumb, contentDescription = null, modifier = thumb)
            } else {
                Box(thumb.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(place.category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
                Text(
                    text = distanceText(place.distanceMeters).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.skeletonText(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HotButton(hot = place.hotByMe, count = place.hotCount, onToggle = onMarkHot)
                    Spacer(Modifier.weight(1f))
                    if (cover != null) {
                        FilledTonalIconButton(
                            onClick = onOpenImage,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = openImages, modifier = Modifier.size(ActionIconSize))
                        }
                    }
                    FilledIconButton(onClick = onAdd, modifier = Modifier.skeleton(CircleShape)) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.social_add_place), modifier = Modifier.size(ActionIconSize))
                    }
                }
            }
        }
    }
}

/** "350 m away" up to a kilometre, then "2.4 km away" ("2,4 km" in German and Hungarian). */
internal fun distanceText(meters: Int): UiText =
    if (meters < 1000) {
        UiText.Resource(Res.string.social_nearby_distance_m, meters)
    } else {
        UiText.Resource(Res.string.social_nearby_distance_km, NumberUtils.kilometers(meters))
    }

private val CoverSize = 88.dp

/** Between an icon button's 48 dp touch area and its 40 dp circle. */
private val TouchInset = 4.dp
private val ActionIconSize = 20.dp

/** A hot Tripinly place close by, and a long-named one further away. */
@Composable
private fun NearbyPlaceCardPreviewContent() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        previewNearbyPlaces.forEach { place -> NearbyPlaceCard(place = place, onOpenImage = {}, onMarkHot = {}, onAdd = {}) }
    }
}

@Preview
@Composable
private fun NearbyPlaceCardPreview() {
    TripinglyTheme { NearbyPlaceCardPreviewContent() }
}

@Preview
@Composable
private fun NearbyPlaceCardDarkPreview() {
    TripinglyTheme(darkTheme = true) { NearbyPlaceCardPreviewContent() }
}
