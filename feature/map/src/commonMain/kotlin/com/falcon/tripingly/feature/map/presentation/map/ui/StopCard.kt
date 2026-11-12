package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.CreditLine
import com.falcon.tripingly.core.designsystem.component.HotJoinedButton
import com.falcon.tripingly.core.designsystem.component.JoinedButton
import com.falcon.tripingly.core.designsystem.component.JoinedButtonDefaults
import com.falcon.tripingly.core.designsystem.component.JoinedButtonSlot
import com.falcon.tripingly.core.designsystem.component.JoinedButtons
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_stop_collapse
import com.falcon.tripingly.feature.map.generated.resources.map_stop_comment
import com.falcon.tripingly.feature.map.generated.resources.map_stop_expand
import com.falcon.tripingly.feature.map.generated.resources.map_stop_number
import com.falcon.tripingly.feature.map.generated.resources.map_stop_photos
import com.falcon.tripingly.feature.map.presentation.component.MapMarker
import com.falcon.tripingly.feature.map.presentation.map.StopCardState
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlaceDetails
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlaceImages
import com.falcon.tripingly.feature.map.presentation.map.preview.previewStop
import org.jetbrains.compose.resources.stringResource

/**
 * A stop tapped on the map, floating above its pin: a slim pill (its image, name, "Stop 2 · Attraction", Delete or +,
 * and ⌃) that grows into the big card: the photo with the name and type, ⌄ to go back, the credit, and the buttons.
 * Tapping the photo opens the stop's photos; ⓘ its details sheet. Delete shows for whoever may delete the stop.
 */
@Composable
internal fun PinCardScope.StopCard(
    card: StopCardState,
    onToggleHot: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenDetails: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val stop = card.stop
    var isExpanded by rememberSaveable(stop.id) { mutableStateOf(initiallyExpanded) }
    val onDeleteShown = onDelete.takeIf { stop.canDelete }
    AnimatedContent(targetState = isExpanded, modifier = modifier.widthIn(max = MapCardMaxWidth)) { expanded ->
        if (expanded) {
            BigPinCard(
                banner = {
                    PhotoBanner(
                        title = stop.title.asString(),
                        subtitle = stopSubtitle(card),
                        image = stop.coverPhotoId?.let { coverId -> coverImage(coverId, stop.coverThumbUrl) },
                        onClick = onOpenPhotos,
                        topEnd = { BannerButton({ isExpanded = false }, Icons.Outlined.ExpandMore, stringResource(Res.string.map_stop_collapse)) },
                    )
                },
                credit = { StopCredit(card, Modifier.padding(top = MaterialTheme.spacing.small)) },
                buttons = { PinCardButtons(stopButtons(stop, onToggleHot, onOpenComments, onOpenPhotos), onOpenDetails, onDeleteShown, onAdd = null) },
            )
        } else {
            StopPill(card, onDeleteShown, onExpand = { isExpanded = true }, modifier = Modifier.bloomRow(fromPin = 0))
        }
    }
}

/** The stop's group: its flame (where its place can be marked hot), its comments and its photos. */
@Composable
internal fun stopButtons(stop: MapMarker, onToggleHot: () -> Unit, onOpenComments: () -> Unit, onOpenPhotos: () -> Unit): List<JoinedButtonSlot> {
    val comments = stringResource(Res.string.map_stop_comment)
    val photos = stringResource(Res.string.map_stop_photos)
    val hot: JoinedButtonSlot = { shape -> HotJoinedButton(hot = stop.hotByMe, count = stop.hotCount, shape = shape, onToggle = onToggleHot) }
    val comment: JoinedButtonSlot = { shape -> JoinedButton(Icons.AutoMirrored.Outlined.Comment, comments, shape, onClick = onOpenComments, count = stop.commentCount) }
    val photo: JoinedButtonSlot = { shape -> JoinedButton(Icons.Outlined.PhotoLibrary, photos, shape, onClick = onOpenPhotos, count = stop.photoCount) }
    return listOfNotNull(hot.takeIf { stop.canMarkHot }, comment, photo)
}

/** A cover filling the banner. */
private fun coverImage(coverId: String, url: String?): @Composable BoxScope.() -> Unit = {
    PhotoImage(photoId = coverId, url = url, size = PhotoSize.Thumb, contentDescription = null, modifier = Modifier.fillMaxSize())
}

/** "Stop 2 · Attraction", the type once the stop's place is loaded. */
@Composable
internal fun stopSubtitle(card: StopCardState): String = listOfNotNull(
    stringResource(Res.string.map_stop_number, card.stop.orderNumber),
    card.place?.category?.let { stringResource(it.label()) },
).joinToString(" · ")

/** OpenStreetMap's credit for a stop at one of its places, then the cover's when it's a Commons image. */
@Composable
internal fun StopCredit(card: StopCardState, modifier: Modifier = Modifier) {
    CreditLine(
        osm = card.place?.isFromOsm == true,
        credit = card.coverCredit,
        cropped = true,
        maxLines = 2,
        modifier = modifier.padding(horizontal = MaterialTheme.spacing.extraSmall),
    )
}

/**
 * The slim pill: as wide as its content, at most the card's width (the screen's minus its margins), the name wrapping
 * onto a second line before it ends.
 */
@Composable
private fun StopPill(card: StopCardState, onDelete: (() -> Unit)?, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val stop = card.stop
    Row(
        modifier = modifier.padding(PillInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        StopImage(stop, Modifier.size(PillImageSize).clip(CircleShape))
        Column(Modifier.weight(1f, fill = false).padding(end = MaterialTheme.spacing.extraSmall)) {
            Text(stop.title.asString(), style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(stopSubtitle(card), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        // Delete is for whoever added the stop or owns the trip; a viewer's + comes with copying stops.
        pinAction(onDelete, onAdd = null)?.let { action -> JoinedButtons(listOf(action)) }
        FilledTonalIconButton(onClick = onExpand, modifier = Modifier.size(PillImageSize)) {
            Icon(Icons.Outlined.ExpandLess, contentDescription = stringResource(Res.string.map_stop_expand), modifier = Modifier.size(JoinedButtonDefaults.IconSize))
        }
    }
}

/** The stop's image in the pill: its cover, or the pin icon while it has none. */
@Composable
private fun StopImage(marker: MapMarker, modifier: Modifier) {
    val coverId = marker.coverPhotoId
    if (coverId != null) {
        PhotoImage(photoId = coverId, url = marker.coverThumbUrl, size = PhotoSize.Thumb, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(JoinedButtonDefaults.IconSize))
        }
    }
}

private val PillInset = 6.dp
// The image and ⌃ are as high as the joined Delete between them.
private val PillImageSize = JoinedButtonDefaults.Height

/** An editor's stop, a viewer's, one with a long name and no counts, opened, and the pill of a stop I can't delete. */
internal class StopCardPreviewStates : PreviewParameterProvider<StopCardPreview> {
    override val values = sequenceOf(
        StopCardPreview(StopCardState(previewStop, coverCredit = previewPlaceImages.first().credit, place = previewPlaceDetails, canEdit = true, canReport = false), expanded = true),
        StopCardPreview(StopCardState(previewStop.copy(canDelete = false), coverCredit = null, place = previewPlaceDetails, canEdit = false, canReport = true), expanded = true),
        StopCardPreview(
            StopCardState(
                previewStop.copy(
                    title = UiText.Server("Musée national des Arts asiatiques – Guimet, the long name of a museum"),
                    orderNumber = 12,
                    coverPhotoId = null,
                    photoCount = 0,
                    hotCount = 0,
                    hotByMe = false,
                    commentCount = 0,
                ),
                coverCredit = null,
                place = null,
                canEdit = true,
                canReport = false,
            ),
            expanded = true,
        ),
        StopCardPreview(StopCardState(previewStop, coverCredit = null, place = previewPlaceDetails, canEdit = true, canReport = false), expanded = false),
        StopCardPreview(StopCardState(previewStop.copy(canDelete = false), coverCredit = null, place = null, canEdit = false, canReport = true), expanded = false),
    )
}

internal data class StopCardPreview(val card: StopCardState, val expanded: Boolean)

@Composable
private fun StopCardPreviewContent(preview: StopCardPreview) {
    Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(MaterialTheme.spacing.medium)) {
        PinCard(card = preview, key = { it.card.stop.id }, pinClearance = 0.dp) { shown ->
            StopCard(
                card = shown.card,
                onToggleHot = {},
                onOpenComments = {},
                onOpenPhotos = {},
                onOpenDetails = {},
                onDelete = {},
                initiallyExpanded = shown.expanded,
            )
        }
    }
}

@Preview(widthDp = 392, heightDp = 360)
@Composable
private fun StopCardPreview(@PreviewParameter(StopCardPreviewStates::class) preview: StopCardPreview) {
    TripinglyTheme { StopCardPreviewContent(preview) }
}

@Preview(widthDp = 392, heightDp = 360)
@Composable
private fun StopCardDarkPreview() {
    TripinglyTheme(darkTheme = true) { StopCardPreviewContent(StopCardPreviewStates().values.first()) }
}
