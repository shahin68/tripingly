package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
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
import com.falcon.tripingly.core.designsystem.component.JoinedButtonSlot
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceImage
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.map.generated.resources.*
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.presentation.map.AddedBy
import com.falcon.tripingly.feature.map.presentation.map.PinAdd
import com.falcon.tripingly.feature.map.presentation.map.PinRoutesState
import com.falcon.tripingly.feature.map.presentation.map.preview.previewCafe
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlace
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlaceDetails
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A place tapped on the map, in a card that floats above its pin: its images (a tap opens one full screen), name, type
 * and flames (only counted here: a place is marked hot as a trip's stop), the credit, and the buttons: its photos
 * (anyone can add one), Go here and Route from or to here ([routes]); on the right ⓘ for the details (hours, website)
 * and the green + ([add]: to the open day, or on a trip I only view to one of my trips).
 */
@Composable
internal fun PinCardScope.PlaceCard(
    place: MapPlace,
    details: PlaceDetails?,
    /** For an address from search: shown instead of the category. */
    address: String?,
    add: PinAdd?,
    /** Who made it, for a custom spot. */
    addedBy: AddedBy?,
    /** What its photo swipes through; null while they load, none for an address from search. */
    images: List<PlaceImage>?,
    onAdd: () -> Unit,
    onOpenImage: (index: Int) -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenDetails: () -> Unit,
    routes: PinRoutesState,
    onGoHere: () -> Unit,
    onRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                afterSubtitle = { addedBy?.let { AddedByInline(it, it.spotText()) } },
            )
        },
        credit = {
            CreditLine(
                // Our places come from OpenStreetMap or from people; an address from search comes from OpenStreetMap too.
                osm = address != null || details?.isFromOsm == true,
                credit = images?.getOrNull(pagerState.currentPage)?.credit,
                photoBy = images?.getOrNull(pagerState.currentPage)?.uploader?.username,
                cropped = true,
                maxLines = 2,
                modifier = Modifier.padding(top = MaterialTheme.spacing.small).padding(horizontal = MaterialTheme.spacing.extraSmall),
            )
        },
        buttons = {
            PinCardButtons(
                group = placeButtons(hasPhotos = address == null, onOpenPhotos, routes, onGoHere, onRoute),
                onDetails = onOpenDetails,
                onDelete = null,
                onAdd = onAdd.takeIf { add != null },
                addLabel = add?.label().orEmpty(),
            )
        },
    )
}

/** A place's group: its photos (not for an address from search), then Go here and Route. */
@Composable
internal fun placeButtons(hasPhotos: Boolean, onOpenPhotos: () -> Unit, routes: PinRoutesState, onGoHere: () -> Unit, onRoute: () -> Unit): List<JoinedButtonSlot> {
    val photos = stringResource(Res.string.map_place_photos)
    val photo: JoinedButtonSlot = { shape -> JoinedButton(Icons.Outlined.PhotoLibrary, photos, shape, onClick = onOpenPhotos) }
    return listOfNotNull(photo.takeIf { hasPhotos }) + routeButtons(routes, onGoHere, onRoute)
}

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

/** A place loading, loaded with images, hours and website, an address from search, one offering Route to here with Go here dimmed, and a custom spot crediting its creator. */
internal class PlaceCardPreviewStates : PreviewParameterProvider<PlaceCardPreview> {
    override val values = sequenceOf(
        PlaceCardPreview(previewPlace, details = null, address = null, add = PinAdd.ToDay(2), routes = PinRoutesState()),
        PlaceCardPreview(previewPlace, previewPlaceDetails, address = null, add = PinAdd.ToDay(2), routes = PinRoutesState()),
        PlaceCardPreview(previewCafe.copy(name = "12 Rue de Rivoli"), details = null, address = "12 Rue de Rivoli, 75004 Paris, France", add = PinAdd.ToDay(1), routes = PinRoutesState()),
        PlaceCardPreview(previewCafe, previewPlaceDetails.copy(images = emptyList(), openingHours = null, website = null), address = null, add = PinAdd.ToMyTrip, routes = PinRoutesState(hasLocation = false, isRouteTo = true)),
        PlaceCardPreview(previewCafe, previewPlaceDetails.copy(addedBy = UserSummary("u-1", "anna", "Anna")), address = null, add = PinAdd.ToDay(1), routes = PinRoutesState()),
    )
}

internal data class PlaceCardPreview(
    val place: MapPlace,
    val details: PlaceDetails?,
    val address: String?,
    val add: PinAdd?,
    val routes: PinRoutesState,
)

@Composable
private fun PlaceCardPreviewContent(card: PlaceCardPreview) {
    Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(MaterialTheme.spacing.medium)) {
        PinCard(card = card, key = { it.place.id }, pinClearance = 0.dp) { shown ->
            PlaceCard(
                place = shown.place,
                details = shown.details,
                address = shown.address,
                add = shown.add,
                addedBy = shown.details?.addedBy?.let { AddedBy(it, isMe = false) },
                images = if (shown.address != null) emptyList() else shown.details?.images,
                onAdd = {},
                onOpenImage = {},
                onOpenPhotos = {},
                onOpenDetails = {},
                routes = shown.routes,
                onGoHere = {},
                onRoute = {},
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
