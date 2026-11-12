package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.CreditLine
import com.falcon.tripingly.core.designsystem.component.JoinedButton
import com.falcon.tripingly.core.designsystem.component.JoinedButtonColors
import com.falcon.tripingly.core.designsystem.component.JoinedButtonSlot
import com.falcon.tripingly.core.designsystem.theme.MapColors
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
 * A place tapped on the map, in a card that floats above its pin: its images (a tap opens one full screen), name, type
 * and flames (only counted here: a place is marked hot as a trip's stop), the credit, and the buttons: its photos
 * (anyone can add one), route from here, and route to here once another place was picked to start from ([onRouteTo]);
 * on the right ⓘ for the details (hours, website) and the green + that adds it to the open day ([dayNumber], null for
 * viewers).
 */
@Composable
internal fun PinCardScope.PlaceCard(
    place: MapPlace,
    details: PlaceDetails?,
    /** For an address from search: shown instead of the category. */
    address: String?,
    dayNumber: Int?,
    onAdd: () -> Unit,
    onOpenImage: (index: Int) -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenDetails: () -> Unit,
    onRouteFrom: () -> Unit,
    /** Null until "Route from here" was tapped on another place. */
    onRouteTo: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // An address from search is no place of ours, so it has no images to wait for and no photos.
    val images = if (address != null) emptyList() else details?.images
    val pagerState = rememberPagerState { images.orEmpty().size }
    BigPinCard(
        modifier = modifier.widthIn(max = MapCardMaxWidth),
        banner = {
            PlaceBanner(
                name = place.name,
                subtitle = address ?: stringResource(place.category.label()),
                category = place.category,
                hotCount = details?.hotCount ?: place.hotCount,
                images = images,
                pagerState = pagerState,
                onOpen = onOpenImage,
                onOpenPhotos = onOpenPhotos.takeIf { address == null },
            )
        },
        credit = {
            CreditLine(
                // Our places come from OpenStreetMap or from people; an address from search comes from OpenStreetMap too.
                osm = address != null || details?.isFromOsm == true,
                credit = images?.getOrNull(pagerState.currentPage)?.credit,
                cropped = true,
                maxLines = 2,
                modifier = Modifier.padding(top = MaterialTheme.spacing.small).padding(horizontal = MaterialTheme.spacing.extraSmall),
            )
        },
        buttons = {
            PinCardButtons(
                group = placeButtons(hasPhotos = address == null, onOpenPhotos, onRouteFrom, onRouteTo),
                onDetails = onOpenDetails,
                onDelete = null,
                onAdd = onAdd.takeIf { dayNumber != null },
                addLabel = dayNumber?.let { stringResource(Res.string.map_place_add_to_day, it) }.orEmpty(),
            )
        },
    )
}

/** A place's group: its photos (not for an address from search), route from here, and route to here once one was picked. */
@Composable
internal fun placeButtons(hasPhotos: Boolean, onOpenPhotos: () -> Unit, onRouteFrom: () -> Unit, onRouteTo: (() -> Unit)?): List<JoinedButtonSlot> {
    val photos = stringResource(Res.string.map_place_photos)
    val routeFrom = stringResource(Res.string.map_route_from_here)
    val routeTo = stringResource(Res.string.map_route_to_here)
    val photo: JoinedButtonSlot = { shape -> JoinedButton(Icons.Outlined.PhotoLibrary, photos, shape, onClick = onOpenPhotos) }
    val from: JoinedButtonSlot = { shape -> JoinedButton(Icons.Outlined.Directions, routeFrom, shape, onClick = onRouteFrom) }
    val to: JoinedButtonSlot? = onRouteTo?.let { { shape -> JoinedButton(Icons.Outlined.Flag, routeTo, shape, onClick = it, colors = routeColors()) } }
    return listOfNotNull(photo.takeIf { hasPhotos }, if (to != null) to else from)
}

/** A route button waiting for its other end: the blue of the routes on a see-through blue. */
private fun routeColors() = JoinedButtonColors(MapColors.Car.copy(alpha = ROUTE_TINT), MapColors.Car)

private const val ROUTE_TINT = 0.18f

internal fun PlaceCategory.label(): StringResource = when (this) {
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

@Composable
private fun PlaceCardPreviewContent(card: PlaceCardPreview) {
    Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(MaterialTheme.spacing.medium)) {
        PinCard(card = card, key = { it.place.id }, pinClearance = 0.dp) { shown ->
            PlaceCard(
                place = shown.place,
                details = shown.details,
                address = shown.address,
                dayNumber = shown.dayNumber,
                onAdd = {},
                onOpenImage = {},
                onOpenPhotos = {},
                onOpenDetails = {},
                onRouteFrom = {},
                onRouteTo = if (shown.canRouteTo) ({}) else null,
            )
        }
    }
}

@Preview(widthDp = 392, heightDp = 380)
@Composable
private fun PlaceCardPreview(@PreviewParameter(PlaceCardPreviewStates::class) card: PlaceCardPreview) {
    TripinglyTheme { PlaceCardPreviewContent(card) }
}

@Preview(widthDp = 392, heightDp = 380)
@Composable
private fun PlaceCardDarkPreview() {
    TripinglyTheme(darkTheme = true) { PlaceCardPreviewContent(PlaceCardPreviewStates().values.elementAt(1)) }
}
