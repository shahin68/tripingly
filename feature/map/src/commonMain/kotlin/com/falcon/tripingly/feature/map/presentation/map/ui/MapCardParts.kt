package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.CountButton
import com.falcon.tripingly.core.designsystem.component.CountLabel
import com.falcon.tripingly.core.designsystem.component.HotButton
import com.falcon.tripingly.core.designsystem.component.JoinedButton
import com.falcon.tripingly.core.designsystem.component.JoinedButtonColors
import com.falcon.tripingly.core.designsystem.component.JoinedButtonDefaults
import com.falcon.tripingly.core.designsystem.component.JoinedButtonSlot
import com.falcon.tripingly.core.designsystem.component.JoinedButtons
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_add_to_my_trip
import com.falcon.tripingly.feature.map.generated.resources.map_card_delete
import com.falcon.tripingly.feature.map.generated.resources.map_card_details
import com.falcon.tripingly.feature.map.generated.resources.map_place_add_to_day
import com.falcon.tripingly.feature.map.generated.resources.map_route_from_here
import com.falcon.tripingly.feature.map.generated.resources.map_route_go_here
import com.falcon.tripingly.feature.map.generated.resources.map_route_to_here
import com.falcon.tripingly.feature.map.presentation.component.lineColor
import com.falcon.tripingly.feature.map.presentation.map.PinAdd
import com.falcon.tripingly.feature.map.presentation.map.PinRoutesState
import org.jetbrains.compose.resources.stringResource

/*
 * What the stop and place cards share (design: the "Final" board of the pin card canvas, Shahin, 2026-11-12): the photo
 * banner with the name and type, the credit line under it, and one row of buttons without words: a joined neutral group
 * on the left (what the pin offers), and on the right ⓘ for the details joined with Delete or the green +.
 */

/**
 * The big card: the [banner], the [credit] line gliding in under it when it comes late, and the [buttons] row. Rows are
 * counted from the pin for [PinCardScope.bloomRow]: the buttons are nearest.
 */
@Composable
internal fun PinCardScope.BigPinCard(
    banner: @Composable () -> Unit,
    credit: @Composable () -> Unit,
    buttons: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(MaterialTheme.spacing.small)) {
        Box(Modifier.bloomRow(fromPin = 2)) { banner() }
        // A place's credit arrives with its details: the buttons glide down to make room.
        Box(Modifier.animateContentSize().bloomRow(fromPin = 1)) { credit() }
        Box(Modifier.padding(top = MaterialTheme.spacing.small).bloomRow(fromPin = 0)) { buttons() }
    }
}

/**
 * The card's buttons: the [group] on the left, as wide as their content (scrolling sideways should a big count not fit),
 * and on the right ⓘ ([onDetails]) joined with Delete ([onDelete]) or + ([onAdd], read out as [addLabel]); a button that
 * doesn't apply is left out.
 */
@Composable
internal fun PinCardButtons(
    group: List<JoinedButtonSlot>,
    onDetails: () -> Unit,
    onDelete: (() -> Unit)?,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    addLabel: String = "",
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            JoinedButtons(group, Modifier.horizontalScroll(rememberScrollState()))
        }
        val details = stringResource(Res.string.map_card_details)
        val info: JoinedButtonSlot = { shape -> JoinedButton(Icons.Outlined.Info, details, shape, onClick = onDetails) }
        JoinedButtons(listOfNotNull(info, pinAction(onDelete, onAdd, addLabel)))
    }
}

/** What the green + is read out as: "Add to day 2", or "Add to my trip" on a trip I only view. */
@Composable
internal fun PinAdd.label(): String = when (this) {
    is PinAdd.ToDay -> stringResource(Res.string.map_place_add_to_day, dayNumber)
    PinAdd.ToMyTrip -> stringResource(Res.string.map_add_to_my_trip)
}

/**
 * Delete, red on red glass, or the green + (read out as [addLabel]): on the right of a card, its details sheet and the
 * slim pill.
 */
internal fun pinAction(onDelete: (() -> Unit)?, onAdd: (() -> Unit)?, addLabel: String = ""): JoinedButtonSlot? = when {
    onDelete != null -> { shape -> JoinedButton(Icons.Outlined.Delete, stringResource(Res.string.map_card_delete), shape, onClick = onDelete, colors = JoinedButtonDefaults.destructive()) }
    onAdd != null -> { shape -> JoinedButton(Icons.Outlined.Add, addLabel, shape, onClick = onAdd, colors = JoinedButtonDefaults.accent()) }
    else -> null
}

/**
 * Go here (dimmed until where I am is known; a tap then asks for it) and Route from, or Route to here while another pin
 * is the start. Each takes the way of travelling's colour, the route's, while its route shows (Tripinly Pro; the
 * paywall otherwise).
 */
@Composable
internal fun routeButtons(routes: PinRoutesState, onGoHere: () -> Unit, onRoute: () -> Unit): List<JoinedButtonSlot> {
    val goHere = stringResource(Res.string.map_route_go_here)
    val route = stringResource(if (routes.isRouteTo) Res.string.map_route_to_here else Res.string.map_route_from_here)
    val neutral = JoinedButtonDefaults.neutral()
    val color = routes.mode.lineColor
    val on = JoinedButtonColors(color.copy(alpha = ROUTE_TINT), color)
    val goHereColors = when {
        routes.isGoHereOn -> on
        routes.hasLocation -> neutral
        else -> neutral.copy(content = neutral.content.copy(alpha = DIMMED_ALPHA))
    }
    return listOf<JoinedButtonSlot>(
        { shape -> JoinedButton(Icons.Outlined.NearMe, goHere, shape, onClick = onGoHere, colors = goHereColors) },
        { shape -> JoinedButton(if (routes.isRouteTo) Icons.Outlined.Flag else Icons.Outlined.Directions, route, shape, onClick = onRoute, colors = if (routes.isRouteOn) on else neutral) },
    )
}

/**
 * A photo [image] filling a rounded banner (tapped: [onClick]) with the [title] (wrapping) and [subtitle] in white over
 * a dark fade at the bottom, [afterSubtitle] on its line (e.g. who added it), [trailing] on their right at the bottom
 * (a stop's flame and comments to tap, or a place's flames only counted; Shahin 2026-11-14), and a [topEnd] button.
 * Without an image ([image] null) it is a tinted panel with the pin icon at the top start and the text dark.
 */
@Composable
internal fun PhotoBanner(
    title: String,
    subtitle: AnnotatedString,
    image: (@Composable BoxScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    afterSubtitle: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    topEnd: @Composable () -> Unit = {},
    /** Without an image: the pin's own mark ([PinMark]), mirroring [topEnd]. */
    mark: @Composable () -> Unit = {},
) {
    val textColor = if (image != null) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier
            .fillMaxWidth()
            .height(BannerHeight)
            .clip(MaterialTheme.shapes.large)
            .background(if (image != null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (image != null) {
            image()
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
        } else {
            // The pin's mark, mirroring the top end button: the same size and inset.
            Box(Modifier.padding(BannerButtonInset)) { mark() }
        }
        CompositionLocalProvider(LocalContentColor provides textColor) {
            Box(Modifier.align(Alignment.TopEnd).padding(BannerButtonInset)) { topEnd() }
            // The same inset on every side the text has: start, end and bottom.
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(BannerInset),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall), verticalAlignment = Alignment.CenterVertically) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
                        afterSubtitle()
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium), verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
        }
    }
}

/**
 * The mark of a card without an image: its place's category icon, on the place colour or on stop green for a stop
 * ([isStop]; its number is in the bold "Stop 2" under the title, Shahin 2026-11-13).
 */
@Composable
internal fun PinMark(category: PlaceCategory?, isStop: Boolean, modifier: Modifier = Modifier.size(JoinedButtonDefaults.Height)) {
    val fill = if (isStop) MaterialTheme.extendedColors.stop else MaterialTheme.extendedColors.place
    Box(modifier.background(fill, CircleShape), contentAlignment = Alignment.Center) {
        Icon((category ?: PlaceCategory.Other).icon, contentDescription = null, tint = if (isStop) Color.White else MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(JoinedButtonDefaults.IconSize))
    }
}

/** A count on a banner that only shows (a place's flames before it's on a trip): quiet, not a button. */
@Composable
internal fun BannerCount(icon: ImageVector, count: Int) {
    CountLabel(icon, count, color = LocalContentColor.current.copy(alpha = 0.85f))
}

/**
 * A round button on a photo banner (⌄ collapse), in the banner's text colour on a see-through tint of it: white glass on a
 * photo, a dark tint on the panel without one, so it shows on both (Shahin, 2026-11-13).
 */
@Composable
internal fun BannerButton(onClick: () -> Unit, icon: ImageVector, contentDescription: String, modifier: Modifier = Modifier) {
    val content = LocalContentColor.current
    // White on a busy photo needs a stronger glass than a dark tint on the plain panel.
    val tint = content.copy(alpha = if (content.luminance() > 0.5f) ON_PHOTO_TINT else ON_PANEL_TINT)
    Surface(onClick = onClick, modifier = modifier.size(JoinedButtonDefaults.Height), shape = CircleShape, color = tint, contentColor = content) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(JoinedButtonDefaults.IconSize)) }
    }
}

/** The cards' widest, so they don't stretch across a tablet or a landscape phone. */
internal val MapCardMaxWidth = 360.dp
private val BannerHeight = 180.dp
private val BannerInset = 14.dp
private val BannerButtonInset = 8.dp
private const val ON_PHOTO_TINT = 0.26f
private const val ON_PANEL_TINT = 0.12f
private const val ROUTE_TINT = 0.18f
private const val DIMMED_ALPHA = 0.38f

@Composable
private fun MapCardPartsPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium).width(340.dp), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        PhotoBanner(title = "Hofburg", subtitle = AnnotatedString("Historic site"), image = null, mark = { PinMark(PlaceCategory.Historic, isStop = false) }, trailing = { BannerCount(Icons.Outlined.LocalFireDepartment, 40) })
        PhotoBanner(
            title = "Lunch at Anna's, a long name that wraps",
            subtitle = AnnotatedString("Custom spot"),
            image = null,
            mark = { PinMark(PlaceCategory.Restaurant, isStop = true) },
            topEnd = { BannerButton({}, Icons.Outlined.ExpandMore, "Collapse") },
            trailing = {
                HotButton(hot = true, count = 12, onToggle = {}, tint = MaterialTheme.extendedColors.attention)
                CountButton(Icons.AutoMirrored.Outlined.Comment, "Comments", 3, onClick = {}, isNew = true)
            },
        )
        PinCardButtons(
            group = listOf<JoinedButtonSlot>(
                { JoinedButton(Icons.Outlined.PhotoLibrary, "Photos", it, onClick = {}) },
            ),
            onDetails = {},
            onDelete = {},
            onAdd = null,
        )
    }
}

@Preview
@Composable
private fun MapCardPartsPreview() {
    TripinglyTheme { MapCardPartsPreviewContent() }
}

@Preview
@Composable
private fun MapCardPartsDarkPreview() {
    TripinglyTheme(darkTheme = true) { MapCardPartsPreviewContent() }
}
