package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.*
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.presentation.map.RouteStartOption
import com.falcon.tripingly.feature.map.presentation.map.RouteStartState
import com.falcon.tripingly.feature.map.presentation.map.preview.previewCafe
import com.falcon.tripingly.feature.map.presentation.map.state.MY_LOCATION_START
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlace
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlaceDetails
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A place tapped on the map, in a card that floats above its pin: what the pin knows at once, its images,
 * opening hours and website once loaded, its photos (anyone can add one), and Add to the open day for owners
 * and editors ([dayNumber] is null for viewers). A tapped image opens full screen. [routeStart]: how far the place
 * is from the start the app guessed; tapping it routes from there, its arrow lists the other starts ([onRoute] gets
 * the picked start's id, null for the guess).
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
    /** Null while there's nowhere to start from: no stops on the open day and no known location. */
    routeStart: RouteStartState?,
    onRoute: (startId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier = modifier.widthIn(max = MapCardMaxWidth), shape = MaterialTheme.shapes.extraLarge, blur = false) {
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
            PlaceActions(address == null, details?.website, dayNumber, onAdd, onOpenPhotos, routeStart, onRoute)
            OsmAttribution(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.extraSmall))
        }
    }
}

/**
 * The route from the guessed start (with how far it is), photos (not for an address from search, which is no place
 * of ours), the website, and Add to the open day.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceActions(
    hasPhotos: Boolean,
    website: String?,
    dayNumber: Int?,
    onAdd: () -> Unit,
    onOpenPhotos: () -> Unit,
    routeStart: RouteStartState?,
    onRoute: (startId: String?) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        routeStart?.let { RouteStartPill(it, onRoute) }
        if (hasPhotos) ActionIconButton(onOpenPhotos, Icons.Outlined.PhotoLibrary, stringResource(Res.string.map_place_photos))
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

/**
 * "1.2 km from stop 1": tapping it routes from that start; the arrow opens the menu of every start (the user, then
 * the open day's stops nearest first), and picking one routes from it at once.
 */
@Composable
private fun RouteStartPill(routeStart: RouteStartState, onRoute: (startId: String?) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val start = routeStart.start
    val distance = distanceText(start.distanceMeters).asString()
    Box {
        Row(
            modifier = Modifier.heightIn(min = ActionHeight).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.heightIn(min = ActionHeight).clickable { onRoute(null) }
                    .padding(start = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.extraSmall),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = start.stopNumber?.let { stringResource(Res.string.map_route_from_stop, distance, it) }
                        ?: stringResource(Res.string.map_route_from_you, distance),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier.size(ActionHeight).clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (menuOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(Res.string.map_route_choose_start),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        AppDropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            actions = routeStart.options.map { option ->
                DropdownAction(
                    label = option.stopNumber?.let { number -> "$number · ${option.stopName?.asString().orEmpty()}" }
                        ?: stringResource(Res.string.map_route_my_location),
                    icon = if (option.stopNumber == null) Icons.Outlined.MyLocation else Icons.Outlined.Place,
                    badge = distanceText(option.distanceMeters).asString(),
                    selected = option.id == start.id,
                    onClick = {
                        menuOpen = false
                        onRoute(option.id)
                    },
                )
            },
        )
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

/** A place loading, loaded with images, hours and website, an address from search, a viewer's with the route start, and a custom spot crediting its creator. */
internal class PlaceCardPreviewStates : PreviewParameterProvider<PlaceCardPreview> {
    override val values = sequenceOf(
        PlaceCardPreview(previewPlace, details = null, address = null, dayNumber = 2, routeStart = null),
        PlaceCardPreview(previewPlace, previewPlaceDetails, address = null, dayNumber = 2, routeStart = null),
        PlaceCardPreview(previewCafe.copy(name = "12 Rue de Rivoli"), details = null, address = "12 Rue de Rivoli, 75004 Paris, France", dayNumber = 1, routeStart = null),
        PlaceCardPreview(previewCafe, previewPlaceDetails.copy(images = emptyList(), openingHours = null, website = null), address = null, dayNumber = null, routeStart = previewRouteStart),
        PlaceCardPreview(previewCafe, previewPlaceDetails.copy(addedBy = UserSummary("u-1", "anna", "Anna")), address = null, dayNumber = 1, routeStart = null),
    )
}

internal data class PlaceCardPreview(
    val place: MapPlace,
    val details: PlaceDetails?,
    val address: String?,
    val dayNumber: Int?,
    val routeStart: RouteStartState?,
)

private val previewRouteStart = RouteStartState(
    start = RouteStartOption("m-1", stopNumber = 1, stopName = UiText.Server("Saint Stephen's"), distanceMeters = 1200),
    options = persistentListOf(
        RouteStartOption(MY_LOCATION_START, stopNumber = null, stopName = null, distanceMeters = 2300),
        RouteStartOption("m-1", stopNumber = 1, stopName = UiText.Server("Saint Stephen's"), distanceMeters = 1200),
        RouteStartOption("m-2", stopNumber = 2, stopName = UiText.Server("Wilhelminenberg"), distanceMeters = 3400),
    ),
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
            routeStart = card.routeStart,
            onRoute = {},
        )
    }
}

@Preview
@Composable
private fun PlaceCardDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        PlaceCard(previewPlace, previewPlaceDetails, address = null, dayNumber = 2, onAdd = {}, onOpenImage = {}, onOpenPhotos = {}, routeStart = previewRouteStart, onRoute = {})
    }
}
