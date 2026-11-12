package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.CreditLine
import com.falcon.tripingly.core.designsystem.component.JoinedButtonSlot
import com.falcon.tripingly.core.designsystem.component.JoinedButtons
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.bottomSystemBar
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_details_comments
import com.falcon.tripingly.feature.map.generated.resources.map_place_added_by
import com.falcon.tripingly.feature.map.generated.resources.map_place_opening_hours
import com.falcon.tripingly.feature.map.generated.resources.map_stop_report
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.PinAdd
import com.falcon.tripingly.feature.map.presentation.map.PinRoutesState
import com.falcon.tripingly.feature.map.presentation.map.PlaceCardState
import com.falcon.tripingly.feature.map.presentation.map.StopCardState
import com.falcon.tripingly.feature.map.presentation.map.preview.previewCafe
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlaceDetails
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlaceImages
import com.falcon.tripingly.feature.map.presentation.map.preview.previewStop
import org.jetbrains.compose.resources.stringResource

/*
 * The details sheet a pin card's ⓘ opens, half high (design: the "Final" board of the pin card canvas, Shahin,
 * 2026-11-12): the name and type with Delete or + joined with ✕, the card's buttons, the first photos (one tapped opens
 * full screen, "+N" or the photos button switches the sheet to the photos), then the facts, the comments and the credit.
 */

/** A stop's details. [photoStrip]: its first photos. */
@Composable
internal fun StopDetailsContent(card: StopCardState, onAction: (MapAction) -> Unit, onClose: () -> Unit, photoStrip: @Composable () -> Unit) {
    val stop = card.stop
    val showPhotos = { onAction(MapAction.StopCard.OpenPhotos) }
    PinDetails(
        title = stop.title.asString(),
        subtitle = stopSubtitle(card),
        action = pinAction(
            onDelete = { onAction(MapAction.Plan.RemoveStop(stop.id)) }.takeIf { stop.canDelete },
            onAdd = { onAction(MapAction.StopCard.Add) }.takeIf { card.add != null },
            addLabel = card.add?.label().orEmpty(),
        ),
        onClose = onClose,
        buttons = {
            val group = stopButtons(stop, { onAction(MapAction.StopCard.ToggleHot(stop.id)) }, { onAction(MapAction.StopCard.OpenComments) }, showPhotos) +
                routeButtons(card.routes, { onAction(MapAction.PinRoute.GoHere) }, { onAction(MapAction.PinRoute.Route) })
            JoinedButtons(group, Modifier.horizontalScroll(rememberScrollState()))
        },
        photoStrip = photoStrip,
    ) {
        PlaceFacts(card.place)
        Fact(
            icon = Icons.AutoMirrored.Outlined.Comment,
            text = stringResource(Res.string.map_details_comments, stop.commentCount),
            onClick = { onAction(MapAction.StopCard.OpenComments) },
        )
        if (card.canReport) {
            Fact(icon = Icons.Outlined.Flag, text = stringResource(Res.string.map_stop_report), onClick = { onAction(MapAction.StopCard.Report) }, color = MaterialTheme.colorScheme.error)
        }
        StopCredit(card, Modifier.padding(top = MaterialTheme.spacing.medium))
    }
}

/** A place's details. [photoStrip]: its first photos (an address from search has none). */
@Composable
internal fun PlaceDetailsContent(card: PlaceCardState, onAction: (MapAction) -> Unit, onClose: () -> Unit, photoStrip: @Composable () -> Unit) {
    PinDetails(
        title = card.place.name,
        subtitle = card.address ?: stringResource(card.place.category.label()),
        action = pinAction(
            onDelete = null,
            onAdd = { onAction(MapAction.PlaceCard.Add) }.takeIf { card.add != null },
            addLabel = card.add?.label().orEmpty(),
        ),
        onClose = onClose,
        buttons = {
            JoinedButtons(
                placeButtons(
                    hasPhotos = card.address == null,
                    onOpenPhotos = { onAction(MapAction.PlaceCard.OpenPhotos) },
                    routes = card.routes,
                    onGoHere = { onAction(MapAction.PinRoute.GoHere) },
                    onRoute = { onAction(MapAction.PinRoute.Route) },
                ),
                Modifier.horizontalScroll(rememberScrollState()),
            )
        },
        photoStrip = photoStrip,
    ) {
        PlaceFacts(card.details)
        CreditLine(
            osm = card.address != null || card.details?.isFromOsm == true,
            credit = null,
            cropped = false,
            modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
        )
    }
}

@Composable
private fun PinDetails(
    title: String,
    subtitle: String,
    action: JoinedButtonSlot?,
    onClose: () -> Unit,
    buttons: @Composable () -> Unit,
    photoStrip: @Composable () -> Unit,
    facts: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.bottomSystemBar)) {
        SheetHeader(title = title, subtitle = subtitle, onClose = onClose, joinedActions = listOfNotNull(action))
        Column(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large).padding(bottom = MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            buttons()
            photoStrip()
            Column { facts() }
        }
    }
}

/** What a place's details say: opening hours, website (opens it), and who made a custom spot. */
@Composable
private fun PlaceFacts(details: PlaceDetails?) {
    val uriHandler = LocalUriHandler.current
    details?.openingHours?.let { Fact(Icons.Outlined.Schedule, "${stringResource(Res.string.map_place_opening_hours)}: $it") }
    details?.website?.let { website ->
        Fact(Icons.Outlined.Language, website, onClick = {
            val url = if ("://" in website) website else "https://$website"
            // OpenStreetMap's value may not be a link a browser takes.
            runCatching { uriHandler.openUri(url) }
        })
    }
    details?.addedBy?.let { Fact(Icons.Outlined.Person, stringResource(Res.string.map_place_added_by, it.username)) }
}

/** One line of the details with a line above it; a tappable one ends with ›. */
@Composable
private fun Fact(icon: ImageVector, text: String, onClick: (() -> Unit)? = null, color: Color = MaterialTheme.colorScheme.onSurface) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FactHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (color == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurfaceVariant else color, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
        if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val FactHeight = 48.dp

@Composable
private fun PinDetailsPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        StopDetailsContent(
            card = StopCardState(previewStop.copy(canDelete = false), coverCredit = previewPlaceImages.first().credit, place = previewPlaceDetails, canEdit = false, canReport = true, add = PinAdd.ToMyTrip),
            onAction = {},
            onClose = {},
            photoStrip = { Text("Photos") },
        )
        PlaceDetailsContent(
            card = PlaceCardState(previewCafe, previewPlaceDetails.copy(isFromOsm = true), address = null, add = PinAdd.ToDay(2), routes = PinRoutesState(isRouteOn = true)),
            onAction = {},
            onClose = {},
            photoStrip = {},
        )
    }
}

@Preview(heightDp = 1000)
@Composable
private fun PinDetailsPreview() {
    TripinglyTheme { PinDetailsPreviewContent() }
}

@Preview(heightDp = 1000)
@Composable
private fun PinDetailsDarkPreview() {
    TripinglyTheme(darkTheme = true) { PinDetailsPreviewContent() }
}
