package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.CountButton
import com.falcon.tripingly.core.designsystem.component.CountLabelSize
import com.falcon.tripingly.core.designsystem.component.CreditLine
import com.falcon.tripingly.core.designsystem.component.HotButton
import com.falcon.tripingly.core.designsystem.component.HotJoinedButton
import com.falcon.tripingly.core.designsystem.component.JoinedButton
import com.falcon.tripingly.core.designsystem.component.JoinedButtonDefaults
import com.falcon.tripingly.core.designsystem.component.JoinedButtonSlot
import com.falcon.tripingly.core.designsystem.component.JoinedButtons
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_place_image_position
import com.falcon.tripingly.feature.map.generated.resources.map_stop_collapse
import com.falcon.tripingly.feature.map.generated.resources.map_stop_comment
import com.falcon.tripingly.feature.map.generated.resources.map_stop_expand
import com.falcon.tripingly.feature.map.generated.resources.map_stop_number
import com.falcon.tripingly.feature.map.generated.resources.map_stop_photos
import com.falcon.tripingly.feature.map.presentation.component.MapMarker
import com.falcon.tripingly.feature.map.presentation.map.AddedBy
import com.falcon.tripingly.feature.map.presentation.map.PinAdd
import com.falcon.tripingly.feature.map.presentation.map.StopCardNews
import com.falcon.tripingly.feature.map.presentation.map.StopCardState
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlaceDetails
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlaceImages
import com.falcon.tripingly.feature.map.presentation.map.preview.previewStop
import org.jetbrains.compose.resources.stringResource

/**
 * A stop tapped on the map, floating above its pin: a slim pill (its image, name, "Stop 2 · Attraction", Delete or +,
 * and ⌃) that grows into the big card: the photo with the name and type, its flame and comments to tap on their right
 * (Shahin, 2026-11-14), ⌄ to go back, the credit, and the buttons. The photo swipes through the stop's images ("1 / 3")
 * when it has more than one, and a tap on it opens the stop's photos; ⓘ its details sheet. Delete shows for whoever may
 * delete the stop. Go here and Route follow its photos.
 */
@Composable
internal fun PinCardScope.StopCard(
    card: StopCardState,
    onToggleHot: () -> Unit,
    onHotSeen: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenDetails: () -> Unit,
    onDelete: () -> Unit,
    onAdd: () -> Unit,
    onGoHere: () -> Unit,
    onRoute: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val stop = card.stop
    var isExpanded by rememberSaveable(stop.id) { mutableStateOf(initiallyExpanded) }
    val onDeleteShown = onDelete.takeIf { stop.canDelete }
    val onAddShown = onAdd.takeIf { card.add != null }
    val addLabel = card.add?.label().orEmpty()
    AnimatedContent(targetState = isExpanded, modifier = modifier.widthIn(max = MapCardMaxWidth)) { expanded ->
        if (expanded) {
            val images = card.images
            val pagerState = rememberPagerState { images.size }
            // Flames count as seen once the flame shows (here or in the details), and only then leave the pin.
            val currentOnHotSeen by rememberUpdatedState(onHotSeen)
            LaunchedEffect(card.news.hot) { if (card.news.hot) currentOnHotSeen() }
            val description = stringResource(Res.string.map_stop_photos)
            BigPinCard(
                banner = {
                    PhotoBanner(
                        title = stop.title.asString(),
                        subtitle = stopSubtitle(card, position = imagePosition(images.size, pagerState.currentPage)),
                        afterSubtitle = { card.addedBy?.let { AddedByInline(it, it.text()) } },
                        image = if (images.isEmpty()) null else pagerImages(images, pagerState, description, onOpen = { onOpenPhotos() }),
                        onClick = onOpenPhotos,
                        mark = { PinMark(card.place?.category, isStop = true) },
                        topEnd = { BannerButton({ isExpanded = false }, Icons.Outlined.ExpandMore, stringResource(Res.string.map_stop_collapse)) },
                        trailing = {
                            // Bigger and brighter than a place's flames, which only count: these are buttons. Like a heart,
                            // the flame is white until marked, then its own colour; only as high as their icons, so they keep
                            // the text's inset from the photo's edge (Shahin, 2026-11-14).
                            val tight = Modifier.height(CountLabelSize.Large.iconSize)
                            if (stop.canMarkHot) {
                                HotButton(stop.hotByMe, stop.hotCount, onToggleHot, tight, tint = if (stop.hotByMe) MaterialTheme.extendedColors.attention else LocalContentColor.current)
                            }
                            CountButton(Icons.AutoMirrored.Outlined.Comment, stringResource(Res.string.map_stop_comment), stop.commentCount, onClick = onOpenComments, modifier = tight, isNew = card.news.comments)
                        },
                    )
                },
                credit = { StopCredit(card, page = pagerState.currentPage, modifier = Modifier.padding(top = MaterialTheme.spacing.small)) },
                buttons = { PinCardButtons(listOf(photoButton(card.news, onOpenPhotos)) + routeButtons(card.routes, onGoHere, onRoute), onOpenDetails, onDeleteShown, onAddShown, addLabel = addLabel) },
            )
        } else {
            StopPill(card, onDeleteShown, onAddShown, addLabel, onExpand = { isExpanded = true }, modifier = Modifier.bloomRow(fromPin = 0))
        }
    }
}

/** The details' group: the stop's flame (where its place can be marked hot), its comments and its photos, with their [news]. */
@Composable
internal fun stopButtons(
    stop: MapMarker,
    news: StopCardNews,
    onToggleHot: () -> Unit,
    onHotSeen: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenPhotos: () -> Unit,
): List<JoinedButtonSlot> {
    val comments = stringResource(Res.string.map_stop_comment)
    // News waits on the comments and photos until they open (Shahin, 2026-11-13). Flames have no dot on the button:
    // they count as seen once the flame shows (the expanded card or the details), and only then leave the pin.
    val hot: JoinedButtonSlot = { shape ->
        val currentOnHotSeen by rememberUpdatedState(onHotSeen)
        LaunchedEffect(news.hot) { if (news.hot) currentOnHotSeen() }
        HotJoinedButton(hot = stop.hotByMe, count = stop.hotCount, shape = shape, onToggle = onToggleHot)
    }
    val comment: JoinedButtonSlot = { shape ->
        JoinedButton(Icons.AutoMirrored.Outlined.Comment, comments, shape, onClick = onOpenComments, count = stop.commentCount, isNew = news.comments)
    }
    return listOfNotNull(hot.takeIf { stop.canMarkHot }, comment, photoButton(news, onOpenPhotos))
}

/** The stop's photos, with a dot while some are new, and no count: it says little (Shahin, 2026-11-14). */
@Composable
private fun photoButton(news: StopCardNews, onOpenPhotos: () -> Unit): JoinedButtonSlot {
    val photos = stringResource(Res.string.map_stop_photos)
    return { shape -> JoinedButton(Icons.Outlined.PhotoLibrary, photos, shape, onClick = onOpenPhotos, isNew = news.photos) }
}

/** Which of [count] images shows ("2 / 3"), for more than one. */
@Composable
internal fun imagePosition(count: Int, page: Int): String? =
    if (count > 1) stringResource(Res.string.map_place_image_position, page + 1, count) else null

/**
 * "**Stop 2** · Attraction", the type once the stop's place is loaded, then which image shows ([position]). The stop's
 * number has no other mark, so it is extra bold, in [numberColor] where the line itself is a quieter grey (on the photo
 * banner the line's own colour).
 */
@Composable
internal fun stopSubtitle(card: StopCardState, numberColor: Color = Color.Unspecified, position: String? = null): AnnotatedString {
    val number = stringResource(Res.string.map_stop_number, card.stop.orderNumber)
    val type = card.place?.category?.let { stringResource(it.label()) }
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = numberColor)) { append(number) }
        type?.let { append(" · $it") }
        position?.let { append(" · $it") }
    }
}

/** OpenStreetMap's credit for a stop at one of its places, then the shown image's ([page]): a Commons credit or "Photo by @handle". */
@Composable
internal fun StopCredit(card: StopCardState, modifier: Modifier = Modifier, page: Int = 0) {
    CreditLine(
        osm = card.place?.isFromOsm == true,
        credit = card.images.getOrNull(page)?.credit,
        photoBy = card.images.getOrNull(page)?.uploader?.username,
        cropped = true,
        maxLines = 2,
        modifier = modifier.padding(horizontal = MaterialTheme.spacing.extraSmall),
    )
}

/**
 * The slim pill: as wide as its content, at most the card's width (the screen's minus its margins), the name wrapping
 * onto a second line before it ends. It glides to its new size when the place's type comes in late (Shahin,
 * 2026-11-13), and a tap anywhere on it grows it into the big card, like ⌃.
 */
@Composable
private fun StopPill(card: StopCardState, onDelete: (() -> Unit)?, onAdd: (() -> Unit)?, addLabel: String, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val stop = card.stop
    Row(
        modifier = modifier
            .clickable(interactionSource = null, indication = null, onClickLabel = stringResource(Res.string.map_stop_expand), onClick = onExpand)
            .animateContentSize()
            .padding(PillInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        StopImage(card, Modifier.size(PillImageSize).clip(CircleShape))
        Column(Modifier.weight(1f, fill = false).padding(end = MaterialTheme.spacing.extraSmall)) {
            Text(stop.title.asString(), style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(stopSubtitle(card, MaterialTheme.colorScheme.onSurface), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        // Delete is for whoever added the stop or owns the trip; a viewer's + puts it on one of their trips.
        pinAction(onDelete, onAdd, addLabel)?.let { action -> JoinedButtons(listOf(action)) }
        FilledTonalIconButton(onClick = onExpand, modifier = Modifier.size(PillImageSize)) {
            Icon(Icons.Outlined.ExpandLess, contentDescription = stringResource(Res.string.map_stop_expand), modifier = Modifier.size(JoinedButtonDefaults.IconSize))
        }
    }
}

/** The stop's image in the pill: the big card's first, or its place's icon on stop green while it has none. */
@Composable
private fun StopImage(card: StopCardState, modifier: Modifier) {
    val image = card.images.firstOrNull()
    if (image != null) {
        PhotoImage(photoId = image.id, url = image.thumbUrl, size = PhotoSize.Thumb, contentDescription = null, modifier = modifier)
    } else {
        PinMark(card.place?.category, isStop = true, modifier = modifier)
    }
}

private val PillInset = 6.dp
// The image and ⌃ are as high as the joined Delete between them.
private val PillImageSize = JoinedButtonDefaults.Height

/** An editor's stop, a viewer's, one with a long name and no counts, opened, and the pill of a stop I can't delete. */
internal class StopCardPreviewStates : PreviewParameterProvider<StopCardPreview> {
    override val values = sequenceOf(
        StopCardPreview(StopCardState(previewStop, images = previewPlaceImages, place = previewPlaceDetails, canEdit = true, canReport = false, addedBy = AddedBy(null, isMe = true), news = StopCardNews(comments = true, photos = true)), expanded = true),
        StopCardPreview(StopCardState(previewStop.copy(canDelete = false), images = previewPlaceImages.take(1), place = previewPlaceDetails, canEdit = false, canReport = true, add = PinAdd.ToMyTrip, addedBy = AddedBy(UserSummary("u-2", "anna", "Anna"), isMe = false)), expanded = true),
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
                images = emptyList(),
                place = null,
                canEdit = true,
                canReport = false,
            ),
            expanded = true,
        ),
        StopCardPreview(StopCardState(previewStop, images = previewPlaceImages, place = previewPlaceDetails, canEdit = true, canReport = false), expanded = false),
        StopCardPreview(StopCardState(previewStop.copy(canDelete = false), images = emptyList(), place = null, canEdit = false, canReport = true, add = PinAdd.ToMyTrip), expanded = false),
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
                onHotSeen = {},
                onOpenComments = {},
                onOpenPhotos = {},
                onOpenDetails = {},
                onDelete = {},
                onAdd = {},
                onGoHere = {},
                onRoute = {},
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
