package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDialog
import com.falcon.tripingly.core.designsystem.component.PillChip
import com.falcon.tripingly.core.designsystem.component.SectionLabel
import com.falcon.tripingly.core.designsystem.component.SegmentedToggle
import com.falcon.tripingly.core.designsystem.component.SegmentedToggleOption
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.horizontalFadingEdge
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.route.TransitLine
import com.falcon.tripingly.core.model.route.TransitStep
import com.falcon.tripingly.core.model.route.TransitStop
import com.falcon.tripingly.core.model.route.TransitVehicle
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_transit_arrive
import com.falcon.tripingly.feature.map.generated.resources.map_transit_arrive_by
import com.falcon.tripingly.feature.map.generated.resources.map_transit_leave
import com.falcon.tripingly.feature.map.generated.resources.map_transit_leave_at
import com.falcon.tripingly.feature.map.generated.resources.map_transit_no_connection
import com.falcon.tripingly.feature.map.generated.resources.map_transit_not_here
import com.falcon.tripingly.feature.map.generated.resources.map_transit_ride_times
import com.falcon.tripingly.feature.map.generated.resources.map_transit_show_steps
import com.falcon.tripingly.feature.map.generated.resources.map_transit_steps_title
import com.falcon.tripingly.feature.map.generated.resources.map_transit_stops
import com.falcon.tripingly.feature.map.generated.resources.map_transit_time_done
import com.falcon.tripingly.feature.map.generated.resources.map_transit_time_title
import com.falcon.tripingly.feature.map.generated.resources.map_transit_to_stop
import com.falcon.tripingly.feature.map.generated.resources.map_transit_towards
import com.falcon.tripingly.feature.map.generated.resources.map_transit_typical
import com.falcon.tripingly.feature.map.generated.resources.map_transit_walk
import com.falcon.tripingly.feature.map.generated.resources.map_transit_walk_to
import com.falcon.tripingly.feature.map.presentation.map.LeaveAtState
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.MapDialog
import com.falcon.tripingly.feature.map.presentation.map.MapSheet
import com.falcon.tripingly.feature.map.presentation.map.TransitChip
import com.falcon.tripingly.feature.map.presentation.map.TransitLegSteps
import com.falcon.tripingly.feature.map.presentation.map.TransitNote
import com.falcon.tripingly.feature.map.presentation.map.clockTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * The public transport row of the route card: "Leave at 14:05" (never "Leave now"; Shahin, 2026-11-15), then the route's
 * walks and rides, which open step by step in their own sheet.
 */
@Composable
internal fun TransitRow(leaveAt: LeaveAtState, legs: ImmutableList<TransitChip>, onAction: (MapAction) -> Unit, inset: Dp, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PillChip(
            label = stringResource(if (leaveAt.isArriveBy) Res.string.map_transit_arrive_by else Res.string.map_transit_leave_at, leaveAt.time),
            onClick = { onAction(MapAction.Plan.OpenLeaveAt) },
            leading = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.padding(start = MaterialTheme.spacing.small).size(18.dp)) },
            modifier = Modifier.padding(start = inset),
        )
        if (legs.isNotEmpty()) {
            val listState = rememberLazyListState()
            val showSteps = stringResource(Res.string.map_transit_show_steps)
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f).horizontalFadingEdge(listState),
                contentPadding = PaddingValues(start = MaterialTheme.spacing.small, end = inset),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // One chip per walk or ride, in order; a route's chips only change with the route, so the index is their key.
                itemsIndexed(legs, key = { index, _ -> index }) { _, chip ->
                    TransitChipItem(chip, Modifier.animateItem().clip(MaterialTheme.shapes.small).clickable(onClickLabel = showSteps) { onAction(MapAction.Plan.OpenTransitSteps) })
                }
            }
        }
    }
}

/** A walk as its minutes, a ride as its vehicle and its line's badge. */
@Composable
private fun TransitChipItem(chip: TransitChip, modifier: Modifier = Modifier) {
    Row(modifier.heightIn(min = ChipHeight).padding(horizontal = MaterialTheme.spacing.extraSmall), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        when (chip) {
            is TransitChip.Walk -> {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(durationLabel(chip.durationSeconds), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            is TransitChip.Ride -> {
                Icon(chip.vehicle.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                LineBadge(chip.line)
            }
        }
    }
}

/** A line's name on its own colour (U1 in red), or on slate when the timetable gives none. */
@Composable
private fun LineBadge(line: TransitLine, modifier: Modifier = Modifier) {
    val color = line.color?.let { Color(it) } ?: MapColors.Transit
    val text = line.textColor?.let { Color(it) } ?: Color.White
    Text(
        text = line.name,
        style = MaterialTheme.typography.labelMedium,
        color = text,
        maxLines = 1,
        modifier = modifier.clip(MaterialTheme.shapes.extraSmall).background(color).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Why there is no public transport route: in the card, never a banner (Shahin, 2026-11-15). */
@Composable
internal fun TransitNoteText(note: TransitNote, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            when (note) {
                TransitNote.NotHere -> Res.string.map_transit_not_here
                TransitNote.NoConnection -> Res.string.map_transit_no_connection
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** The way public transport travels, as its icon. */
internal val TransitVehicle.icon: ImageVector
    get() = when (this) {
        TransitVehicle.Tram -> Icons.Default.Tram
        TransitVehicle.Subway -> Icons.Default.DirectionsSubway
        TransitVehicle.Bus -> Icons.Default.DirectionsBus
        TransitVehicle.Train -> Icons.Default.Train
        TransitVehicle.Ferry -> Icons.Default.DirectionsBoat
        TransitVehicle.CableCar, TransitVehicle.Other -> Icons.Default.DirectionsTransit
    }

/**
 * The public transport route step by step: each walk with its minutes and where it goes, each ride with its line, its
 * direction, where to get on and off and when. A day's legs each start with the stop they go to.
 */
@Composable
internal fun TransitStepsSheet(sheet: MapSheet.TransitSteps, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(bottom = MaterialTheme.spacing.large)) {
        SheetHeader(title = stringResource(Res.string.map_transit_steps_title), onClose = onClose)
        LazyColumn(contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.large), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            sheet.legs.forEachIndexed { legIndex, leg ->
                leg.toStop?.let { stop ->
                    item(key = "leg-$legIndex") { SectionLabel(stringResource(Res.string.map_transit_to_stop, stop.asString()), Modifier.animateItem()) }
                }
                itemsIndexed(leg.steps, key = { index, _ -> "step-$legIndex-$index" }) { _, step -> TransitStepRow(step, Modifier.animateItem()) }
            }
            if (sheet.isTypicalTimes) {
                item(key = "typical") { Text(stringResource(Res.string.map_transit_typical), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            sheet.attribution?.let { credit ->
                item(key = "credit") { RouteAttribution(credit) }
            }
        }
    }
}

@Composable
private fun TransitStepRow(step: TransitStep, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Box(Modifier.size(StepIconSize).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(if (step.isRide) (step.vehicle ?: TransitVehicle.Other).icon else Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (step.isRide) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    step.line?.let { LineBadge(it) }
                    step.headsign?.let { Text(stringResource(Res.string.map_transit_towards, it), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
                Text(
                    stringResource(Res.string.map_transit_ride_times, step.from.name.orEmpty(), clockTime(step.from.time), step.to.name.orEmpty(), clockTime(step.to.time)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${pluralStringResource(Res.plurals.map_transit_stops, step.stops, step.stops)} · ${durationLabel(step.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val minutes = durationLabel(step.durationSeconds)
                Text(
                    step.to.name?.let { stringResource(Res.string.map_transit_walk_to, minutes, it) } ?: stringResource(Res.string.map_transit_walk, minutes),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${distanceText(step.distanceMeters).asString()} · ${clockTime(step.from.time)} → ${clockTime(step.to.time)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * "Leave at": a time on the open day (the date stays the day's; Shahin, 2026-11-15), leaving then or, from A to B,
 * arriving by then.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LeaveAtDialog(dialog: MapDialog.LeaveAt, onAction: (MapAction) -> Unit) {
    val time = rememberTimePickerState(initialHour = dialog.hour, initialMinute = dialog.minute, is24Hour = true)
    var isArriveBy by remember { mutableStateOf(dialog.isArriveBy) }
    AppDialog(
        title = stringResource(Res.string.map_transit_time_title),
        confirmLabel = stringResource(Res.string.map_transit_time_done),
        onConfirm = { onAction(MapAction.Dialog.PickTransitTime(time.hour, time.minute, isArriveBy)) },
        onDismiss = { onAction(MapAction.Dialog.DismissLeaveAt) },
    ) {
        if (dialog.canArriveBy) {
            SegmentedToggle(Modifier.fillMaxWidth()) {
                SegmentedToggleOption(label = stringResource(Res.string.map_transit_leave), selected = !isArriveBy, onClick = { isArriveBy = false })
                SegmentedToggleOption(label = stringResource(Res.string.map_transit_arrive), selected = isArriveBy, onClick = { isArriveBy = true })
            }
        }
        TimePicker(time, Modifier.align(Alignment.CenterHorizontally))
    }
}

private val ChipHeight = 32.dp
private val StepIconSize = 36.dp

private val previewSteps = persistentListOf(
    TransitStep(false, null, null, null, previewStop(null, 0), previewStop("Stephansplatz", 4), 0, 320, 240, emptyList()),
    TransitStep(true, TransitVehicle.Subway, TransitLine("U1", 0xFFE3000FL, 0xFFFFFFFFL), "Leopoldau", previewStop("Stephansplatz", 6), previewStop("Praterstern", 10), 2, 2100, 240, emptyList()),
    TransitStep(true, TransitVehicle.Tram, TransitLine("O", null, null), "Raxstraße", previewStop("Praterstern", 13), previewStop("Hauptbahnhof", 30), 11, 4800, 1020, emptyList()),
    TransitStep(false, null, null, null, previewStop("Hauptbahnhof", 30), previewStop(null, 35), 0, 380, 300, emptyList()),
)

private fun previewStop(name: String?, minute: Int) =
    TransitStop(name, GeoPoint(48.2, 16.37), Instant.parse("2026-11-16T13:${minute.toString().padStart(2, '0')}:00Z"))

@Composable
private fun TransitPreviewContent() {
    PlanPreviewSheet {
        Column(Modifier.padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            TransitRow(
                LeaveAtState("14:00", isArriveBy = false),
                persistentListOf(TransitChip.Walk(240), TransitChip.Ride(previewSteps[1].line!!, TransitVehicle.Subway), TransitChip.Ride(previewSteps[2].line!!, TransitVehicle.Tram), TransitChip.Walk(300)),
                onAction = {},
                inset = MaterialTheme.spacing.small,
            )
            TransitNoteText(TransitNote.NotHere)
            TransitStepsSheet(MapSheet.TransitSteps(persistentListOf(TransitLegSteps(null, previewSteps)), isTypicalTimes = true, attribution = "© Wiener Linien"), onClose = {})
        }
    }
}

@Preview
@Composable
private fun TransitPreview() {
    TripinglyTheme { TransitPreviewContent() }
}

@Preview
@Composable
private fun TransitDarkPreview() {
    TripinglyTheme(darkTheme = true) { TransitPreviewContent() }
}
