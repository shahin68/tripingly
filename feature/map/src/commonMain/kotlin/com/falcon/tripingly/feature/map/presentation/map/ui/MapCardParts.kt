package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.JoinedButton
import com.falcon.tripingly.core.designsystem.component.JoinedButtonColors
import com.falcon.tripingly.core.designsystem.component.JoinedButtonDefaults
import com.falcon.tripingly.core.designsystem.component.JoinedButtonSlot
import com.falcon.tripingly.core.designsystem.component.JoinedButtons
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_add_to_my_trip
import com.falcon.tripingly.feature.map.generated.resources.map_card_delete
import com.falcon.tripingly.feature.map.generated.resources.map_card_details
import com.falcon.tripingly.feature.map.generated.resources.map_place_add_to_day
import com.falcon.tripingly.feature.map.generated.resources.map_route_from_here
import com.falcon.tripingly.feature.map.generated.resources.map_around
import com.falcon.tripingly.feature.map.generated.resources.map_route_go_here
import com.falcon.tripingly.feature.map.generated.resources.map_route_to_here
import com.falcon.tripingly.feature.map.presentation.component.AlongTheWayPinColor
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
 * counted from the pin for [PinCardScope.bloomRow]: the buttons are nearest, else the credit.
 */
@Composable
internal fun PinCardScope.BigPinCard(
    banner: @Composable () -> Unit,
    credit: @Composable () -> Unit,
    /** None on the spot map, whose buttons are in its bottom bar. */
    buttons: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(MaterialTheme.spacing.small)) {
        Box(Modifier.bloomRow(fromPin = if (buttons != null) 2 else 1)) { banner() }
        // A place's credit arrives with its details: the buttons glide down to make room.
        Box(Modifier.animateContentSize().bloomRow(fromPin = if (buttons != null) 1 else 0)) { credit() }
        if (buttons != null) Box(Modifier.padding(top = MaterialTheme.spacing.small).bloomRow(fromPin = 0)) { buttons() }
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
    onDetails: (() -> Unit)?,
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
        // None on the spot map, which shows the details under its buttons.
        val info: JoinedButtonSlot? = if (onDetails == null) null else { shape -> JoinedButton(Icons.Outlined.Info, details, shape, onClick = onDetails) }
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
 * is the start ([onRoute]; none on the spot map, which has one pin). Each takes the way of travelling's colour, the
 * route's, while its route shows (Tripinly Pro; the paywall otherwise).
 */
@Composable
internal fun routeButtons(routes: PinRoutesState, onGoHere: () -> Unit, onRoute: (() -> Unit)?, onAround: (() -> Unit)? = null): List<JoinedButtonSlot> {
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
    val goHereButton: JoinedButtonSlot = { shape -> JoinedButton(Icons.Outlined.NearMe, goHere, shape, onClick = onGoHere, colors = goHereColors) }
    val buttons = mutableListOf(goHereButton)
    if (onRoute != null) {
        val routeButton: JoinedButtonSlot = { shape -> JoinedButton(if (routes.isRouteTo) Icons.Outlined.Flag else Icons.Outlined.Directions, route, shape, onClick = onRoute, colors = if (routes.isRouteOn) on else neutral) }
        buttons += routeButton
    }
    onAround?.let { buttons += aroundButton(routes.isAroundOn, it) }
    return buttons
}

/** Around (Tripinly Pro; the paywall otherwise): the places in a ring around the pin, its icon only (E1, Shahin 2026-11-17). */
@Composable
private fun aroundButton(isOn: Boolean, onClick: () -> Unit): JoinedButtonSlot {
    val label = stringResource(Res.string.map_around)
    val colors = if (isOn) JoinedButtonColors(AlongTheWayPinColor.copy(alpha = ROUTE_TINT), AlongTheWayPinColor) else JoinedButtonDefaults.neutral()
    return { shape -> JoinedButton(Icons.Outlined.TravelExplore, label, shape, onClick = onClick, colors = colors) }
}

/** The cards' widest, so they don't stretch across a tablet or a landscape phone. */
internal val MapCardMaxWidth = 360.dp
private const val ROUTE_TINT = 0.18f
private const val DIMMED_ALPHA = 0.38f

@Composable
private fun MapCardPartsPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium).width(340.dp), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
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
