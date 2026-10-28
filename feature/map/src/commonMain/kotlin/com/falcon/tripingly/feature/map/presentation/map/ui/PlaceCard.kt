package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.map.generated.resources.*
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.presentation.map.preview.previewCafe
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlace
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlaceDetails
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A place tapped on the map, in a card that floats above its pin: what the pin knows at once, its images,
 * opening hours and website once loaded, its photos (anyone can add one), and Add to the open day for owners
 * and editors ([dayNumber] is null for viewers). A tapped image opens full screen. Anyone can route from the
 * place, and to it once another place was picked to start from ([onRouteTo]).
 */
@Composable
fun PlaceCard(
    place: MapPlace,
    details: PlaceDetails?,
    /** For an address from search: shown instead of the category. */
    address: String?,
    dayNumber: Int?,
    onAdd: () -> Unit,
    onOpenImage: (index: Int) -> Unit,
    onOpenPhotos: () -> Unit,
    onRouteFrom: () -> Unit,
    /** Null until "Route from here" was tapped on another place. */
    onRouteTo: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier = modifier.widthIn(max = MapCardMaxWidth), shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            PlaceBanner(
                name = place.name,
                subtitle = address ?: stringResource(place.category.label()),
                hotCount = details?.hotCount ?: place.hotCount,
                // An address from search is no place of ours, so it has no images to wait for.
                images = if (address != null) emptyList() else details?.images,
                category = place.category,
                onOpen = onOpenImage,
            )
            val notes = listOfNotNull(
                details?.addedBy?.let { stringResource(Res.string.map_place_added_by, it.username) },
                details?.openingHours?.let { "${stringResource(Res.string.map_place_opening_hours)}: $it" },
            )
            notes.forEach { note ->
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = MaterialTheme.spacing.extraSmall))
            }
            PlaceActions(address == null, details?.website, dayNumber, onAdd, onOpenPhotos, onRouteFrom, onRouteTo)
            OsmAttribution(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.extraSmall))
        }
    }
}

/**
 * Photos (not for an address from search, which is no place of ours), route from here, route to here once another
 * place was picked, the website, and Add to the open day.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceActions(
    hasPhotos: Boolean,
    website: String?,
    dayNumber: Int?,
    onAdd: () -> Unit,
    onOpenPhotos: () -> Unit,
    onRouteFrom: () -> Unit,
    onRouteTo: (() -> Unit)?,
) {
    val uriHandler = LocalUriHandler.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasPhotos) ActionIconButton(onOpenPhotos, Icons.Outlined.PhotoLibrary, stringResource(Res.string.map_place_photos))
        ActionIconButton(onRouteFrom, Icons.Outlined.Directions, stringResource(Res.string.map_route_from_here))
        if (onRouteTo != null) {
            SecondaryButton(stringResource(Res.string.map_route_to_here), onRouteTo, size = ButtonSize.Small, icon = Icons.Outlined.Flag)
        }
        website?.let {
            ActionIconButton(
                onClick = {
                    val url = if ("://" in it) it else "https://$it"
                    // OpenStreetMap's value may not be a link a browser takes.
                    runCatching { uriHandler.openUri(url) }
                },
                icon = Icons.Outlined.Language,
                contentDescription = stringResource(Res.string.map_place_website),
            )
        }
        if (dayNumber != null) {
            PrimaryButton(stringResource(Res.string.map_place_add_to_day, dayNumber), onAdd, size = ButtonSize.Small, icon = Icons.Outlined.Add)
        }
    }
}

private fun PlaceCategory.label(): StringResource = when (this) {
    PlaceCategory.Cafe -> Res.string.map_place_category_cafe
    PlaceCategory.Restaurant -> Res.string.map_place_category_restaurant
    PlaceCategory.Bar -> Res.string.map_place_category_bar
    PlaceCategory.Attraction -> Res.string.map_place_category_attraction
    PlaceCategory.Museum -> Res.string.map_place_category_museum
    PlaceCategory.Historic -> Res.string.map_place_category_historic
    PlaceCategory.Park -> Res.string.map_place_category_park
    PlaceCategory.Nature -> Res.string.map_place_category_nature
    PlaceCategory.Landmark -> Res.string.map_place_category_landmark
    PlaceCategory.Other -> Res.string.map_place_category_other
}

/** A place loading, loaded with images, hours and website, an address from search, a viewer's offering "Route to here", and a custom spot crediting its creator. */
internal class PlaceCardPreviewStates : PreviewParameterProvider<PlaceCardPreview> {
    override val values = sequenceOf(
        PlaceCardPreview(previewPlace, details = null, address = null, dayNumber = 2, canRouteTo = false),
        PlaceCardPreview(previewPlace, previewPlaceDetails, address = null, dayNumber = 2, canRouteTo = false),
        PlaceCardPreview(previewCafe.copy(name = "12 Rue de Rivoli"), details = null, address = "12 Rue de Rivoli, 75004 Paris, France", dayNumber = 1, canRouteTo = false),
        PlaceCardPreview(previewCafe, previewPlaceDetails.copy(images = emptyList(), openingHours = null, website = null), address = null, dayNumber = null, canRouteTo = true),
        PlaceCardPreview(previewCafe, previewPlaceDetails.copy(addedBy = UserSummary("u-1", "anna", "Anna")), address = null, dayNumber = 1, canRouteTo = false),
    )
}

internal data class PlaceCardPreview(
    val place: MapPlace,
    val details: PlaceDetails?,
    val address: String?,
    val dayNumber: Int?,
    val canRouteTo: Boolean,
)

@Preview
@Composable
private fun PlaceCardPreview(@PreviewParameter(PlaceCardPreviewStates::class) card: PlaceCardPreview) {
    TripinglyTheme {
        PlaceCard(
            place = card.place,
            details = card.details,
            address = card.address,
            dayNumber = card.dayNumber,
            onAdd = {},
            onOpenImage = {},
            onOpenPhotos = {},
            onRouteFrom = {},
            onRouteTo = if (card.canRouteTo) ({}) else null,
        )
    }
}

@Preview
@Composable
private fun PlaceCardDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        PlaceCard(previewPlace, previewPlaceDetails, address = null, dayNumber = 2, onAdd = {}, onOpenImage = {}, onOpenPhotos = {}, onRouteFrom = {}, onRouteTo = null)
    }
}
