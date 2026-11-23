package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.em
import com.falcon.tripingly.core.designsystem.component.label
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.model.place.PlaceCategory
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.CreditLine
import com.falcon.tripingly.core.designsystem.component.JoinedButtonSlot
import com.falcon.tripingly.core.designsystem.component.JoinedButtons
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.bottomSystemBar
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_details_comments
import com.falcon.tripingly.feature.map.generated.resources.map_place_opening_hours
import com.falcon.tripingly.feature.map.generated.resources.map_stop_report
import com.falcon.tripingly.feature.map.presentation.map.AddedBy
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
        subtitle = stopSubtitle(card, MaterialTheme.colorScheme.onSurface, withIcon = true),
        category = card.category,
        action = pinAction(
            onDelete = { onAction(MapAction.Plan.RemoveStop(stop.id)) }.takeIf { stop.canDelete },
            onAdd = { onAction(MapAction.StopCard.Add) }.takeIf { card.add != null },
            addLabel = card.add?.label().orEmpty(),
        ),
        onClose = onClose,
        buttons = {
            // A visitor's map has no routes or Around from a stop (Shahin, 2026-11-22); when a join is accepted they
            // fade in and the row glides to fit.
            AnimatedContent(targetState = card.canRoute, transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform() }, label = "stop details route buttons") { canRoute ->
                val group = stopButtons(
                    stop,
                    card.news,
                    { onAction(MapAction.StopCard.ToggleHot(stop.id)) },
                    { onAction(MapAction.StopCard.HotSeen(stop.id)) },
                    { onAction(MapAction.StopCard.OpenComments) },
                    showPhotos,
                ) +
                    if (canRoute) routeButtons(card.routes, { onAction(MapAction.PinRoute.GoHere) }, { onAction(MapAction.PinRoute.Route) }, { onAction(MapAction.Around.Open) }) else emptyList()
                JoinedButtons(group, Modifier.horizontalScroll(rememberScrollState()))
            }
        },
        photoStrip = photoStrip,
    ) {
        card.addedBy?.let { AddedByFact(it, it.text(), onAction) }
        PlaceFacts(card.place)
        card.spotBy?.let { AddedByFact(it, it.spotText(), onAction) }
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
        subtitle = card.address?.let(::AnnotatedString) ?: buildAnnotatedString {
            appendCategoryIcon()
            append(card.place.category.label())
        },
        category = card.place.category.takeIf { card.address == null },
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
                    onAround = { onAction(MapAction.Around.Open) },
                ),
                Modifier.horizontalScroll(rememberScrollState()),
            )
        },
        photoStrip = photoStrip,
    ) {
        // A place off the trip: who made the spot, in the same words as on a stop at it.
        card.addedBy?.let { AddedByFact(it, it.spotText(), onAction) }
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
    subtitle: AnnotatedString,
    category: PlaceCategory?,
    action: JoinedButtonSlot?,
    onClose: () -> Unit,
    buttons: @Composable () -> Unit,
    photoStrip: @Composable () -> Unit,
    facts: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.bottomSystemBar)) {
        SheetHeader(title = title, subtitle = subtitle, onClose = onClose, subtitleInlineContent = categoryIconContent(category), joinedActions = listOfNotNull(action))
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

/** What a place's details say: opening hours and website (opens it). */
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
}

/** Who added a stop or made a spot (Shahin's D1, 2026-11-13): their face first; a tap opens their profile, not for me. */
@Composable
private fun AddedByFact(addedBy: AddedBy, text: String, onAction: (MapAction) -> Unit) {
    val user = addedBy.user
    Fact(Icons.Outlined.Person, text, avatar = user, onClick = user?.takeUnless { addedBy.isMe }?.let { { onAction(MapAction.Sheet.OpenProfile(it.username)) } })
}

/** One line of the details with a line above it, led by [icon] or a person's face ([avatar]); a tappable one ends with ›. */
@Composable
private fun Fact(icon: ImageVector, text: String, onClick: (() -> Unit)? = null, color: Color = MaterialTheme.colorScheme.onSurface, avatar: UserSummary? = null) {
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
        if (avatar != null) {
            AvatarCircle(avatar, size = AvatarSize.Small)
        } else {
            Icon(icon, contentDescription = null, tint = if (color == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurfaceVariant else color, modifier = Modifier.size(20.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
        if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val FactHeight = 48.dp

@Composable
private fun PinDetailsPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        StopDetailsContent(
            card = StopCardState(previewStop.copy(canDelete = false), images = previewPlaceImages, place = previewPlaceDetails, canEdit = false, canReport = true, add = PinAdd.ToMyTrip, addedBy = AddedBy(UserSummary("u-2", "anna", "Anna"), isMe = false)),
            onAction = {},
            onClose = {},
            photoStrip = { Text("Photos") },
        )
        PlaceDetailsContent(
            card = PlaceCardState(previewCafe, previewPlaceDetails.copy(isFromOsm = true), address = null, add = PinAdd.ToDay(2), routes = PinRoutesState(isRouteOn = true), addedBy = AddedBy(null, isMe = true)),
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

private const val CATEGORY_ICON = "categoryIcon"

/**
 * The place type's small icon before its name in a subtitle; [categoryIconContent] draws it. The placeholder keeps
 * Compose's default alternate text: an empty one throws when the text is built (the details-sheet crash, 2026-11-18).
 */
internal fun AnnotatedString.Builder.appendCategoryIcon() {
    appendInlineContent(CATEGORY_ICON)
    append("\u2009")
}

/** Draws [appendCategoryIcon]'s icon: [category]'s, in the default on-surface colour (light and dark). */
@Composable
private fun categoryIconContent(category: PlaceCategory?): Map<String, InlineTextContent> {
    category ?: return emptyMap()
    val tint = MaterialTheme.colorScheme.onSurface
    return mapOf(
        CATEGORY_ICON to InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(category.icon(), contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize())
        },
    )
}
