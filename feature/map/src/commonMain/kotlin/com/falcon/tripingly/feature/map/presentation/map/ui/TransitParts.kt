package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDialog
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.designsystem.component.SegmentedToggle
import com.falcon.tripingly.core.designsystem.component.SegmentedToggleOption
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.horizontalFadingEdge
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.route.Route
import com.falcon.tripingly.core.model.route.RouteLeg
import com.falcon.tripingly.core.model.route.TransitLine
import com.falcon.tripingly.core.model.route.TransitStep
import com.falcon.tripingly.core.model.route.TransitStop
import com.falcon.tripingly.core.model.route.TransitVehicle
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_transit_arrive
import com.falcon.tripingly.feature.map.generated.resources.map_transit_arrive_by
import com.falcon.tripingly.feature.map.generated.resources.map_transit_leave
import com.falcon.tripingly.feature.map.generated.resources.map_transit_leave_at
import com.falcon.tripingly.feature.map.generated.resources.map_transit_no_connection
import com.falcon.tripingly.feature.map.generated.resources.map_transit_not_here
import com.falcon.tripingly.feature.map.generated.resources.map_transit_show_steps
import com.falcon.tripingly.feature.map.generated.resources.map_transit_time_done
import com.falcon.tripingly.feature.map.generated.resources.map_transit_time_title
import com.falcon.tripingly.feature.map.generated.resources.map_transit_typical
import com.falcon.tripingly.feature.map.presentation.map.LeaveAtState
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.MapDialog
import com.falcon.tripingly.feature.map.presentation.map.MapSheet
import com.falcon.tripingly.feature.map.presentation.map.TransitChip
import com.falcon.tripingly.feature.map.presentation.map.TransitEnd
import com.falcon.tripingly.feature.map.presentation.map.TransitPlaceIcon
import com.falcon.tripingly.feature.map.presentation.map.TransitMark
import com.falcon.tripingly.feature.map.presentation.map.TransitNote
import com.falcon.tripingly.feature.map.presentation.map.TransitPath
import com.falcon.tripingly.feature.map.presentation.map.TransitStepRow
import com.falcon.tripingly.feature.map.presentation.map.transitStepRows
import com.falcon.tripingly.feature.map.presentation.map.transitStepsHeader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * The route's walks and rides in one rounded button, as the canvas's live demo draws them: walk minutes, line badges with
 * their ride minutes, › between them and a chevron at the end. A tap opens them step by step.
 */
@Composable
internal fun TransitLegsButton(legs: ImmutableList<TransitChip>, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(LegsHeight / 2)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LegsHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = stringResource(Res.string.map_transit_show_steps), role = Role.Button) { onAction(MapAction.Plan.OpenTransitSteps) }
            .padding(start = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A hand-drawn container: its text and icons take its colour.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            val listState = rememberLazyListState()
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f).horizontalFadingEdge(listState),
                horizontalArrangement = Arrangement.spacedBy(LegGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // One per walk or ride, in order; a route's chips only change with the route, so the index is their key.
                itemsIndexed(legs, key = { index, _ -> index }) { index, chip -> TransitChipItem(chip, isFirst = index == 0) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** A walk as its icon and minutes ("🚶 4"), a ride as its line's badge and minutes ("U3 6 min"), after a › but the first. */
@Composable
private fun TransitChipItem(chip: TransitChip, isFirst: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LegGap)) {
        if (!isFirst) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
        when (chip) {
            is TransitChip.Walk -> {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(wholeMinutes(chip.durationSeconds).toString(), style = BoldLabel, maxLines = 1)
            }
            is TransitChip.Ride -> {
                LineBadge(chip.line)
                Text(durationLabel(chip.durationSeconds), style = BoldLabel, maxLines = 1)
            }
        }
    }
}

/** "Leave at 14:05 ⌄" (never "Leave now"; Shahin, 2026-11-15), or "Arrive by 14:05 ⌄": opens the time picker. */
@Composable
internal fun LeaveAtButton(leaveAt: LeaveAtState, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = { onAction(MapAction.Plan.OpenLeaveAt) },
        shape = RoundedCornerShape(LeaveAtHeight / 2),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.height(LeaveAtHeight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LegGap),
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(if (leaveAt.isArriveBy) Res.string.map_transit_arrive_by else Res.string.map_transit_leave_at, leaveAt.time), style = BoldLabel, maxLines = 1)
            Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/** A line's name on its own colour (U1 in red), or on slate when the timetable gives none. */
@Composable
private fun LineBadge(line: TransitLine, modifier: Modifier = Modifier) {
    Text(
        text = line.name,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = line.textColor?.let { Color(it) } ?: Color.White,
        maxLines = 1,
        modifier = modifier.clip(BadgeShape).background(lineColor(line.color)).padding(horizontal = 6.dp, vertical = 1.dp),
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

/**
 * The public transport route step by step, as the canvas's timeline: "24 min · 1 change" over its times at the top (the
 * back arrow on the right, like every sheet's), then a row per walk or ride: its time, its mark on the line, the place in
 * bold and what happens there, the line on to the next row dotted for walks and solid in the line's colour for rides; a
 * flag where it ends, the timetables' credit at the foot.
 */
@Composable
internal fun TransitStepsSheet(sheet: MapSheet.TransitSteps, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(bottom = MaterialTheme.spacing.large)) {
        SheetHeader(title = sheet.title.asString(), subtitle = sheet.subtitle?.asString(), onClose = onClose)
        LazyColumn(contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small)) {
            items(sheet.rows, key = { it.key }) { row -> TransitTimelineRow(row, Modifier.animateItem()) }
            item(key = "foot") {
                Column(Modifier.padding(top = MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
                    if (sheet.isTypicalTimes) {
                        Text(stringResource(Res.string.map_transit_typical), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    sheet.attribution?.let { RouteAttribution(it) }
                }
            }
        }
    }
}

/** One row of the timeline: the time, the mark with the line down to the next row, then the place and what happens. */
@Composable
private fun TransitTimelineRow(row: TransitStepRow, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Text(row.time, style = BoldTitle, maxLines = 1, modifier = Modifier.width(TimeWidth))
        Column(Modifier.width(TrackWidth).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            TimelineMark(row.mark, Modifier.padding(top = MarkTop))
            row.path?.let { TimelinePath(it, Modifier.weight(1f).padding(top = PathGap)) }
        }
        Column(
            modifier = Modifier.weight(1f).padding(bottom = if (row.path != null) MaterialTheme.spacing.medium else 0.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            row.place?.let { place ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    row.icon?.let { PlaceCircle(it) }
                    Text(place.asString(), style = BoldTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            row.ride?.let { ride ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    ride.line?.let { LineBadge(it) }
                    ride.towards?.let { Text(it.asString(), style = BoldLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            // How this stretch goes by its minutes: walking, or the ride's tram, bus, U-Bahn… (Shahin, 2026-11-15).
            row.detail?.let { detail ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
                    Icon(
                        imageVector = row.ride?.vehicle?.icon ?: Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(DetailIconSize),
                    )
                    Text(detail.asString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Where a row sits on the line: a ring (walk green, the line's colour, the text colour at a change), a dot for a day's stop, a flag at the end. */
@Composable
private fun TimelineMark(mark: TransitMark, modifier: Modifier = Modifier) {
    val ring = when (mark) {
        TransitMark.Walk -> MapColors.Walk
        TransitMark.Change -> MaterialTheme.colorScheme.onSurface
        is TransitMark.Line -> lineColor(mark.color)
        TransitMark.Stop -> MaterialTheme.colorScheme.primary
        // In the stops' colour, like the route's end on the map.
        TransitMark.End -> return Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.extendedColors.stop, modifier = modifier.size(TrackWidth))
    }
    val fill = if (mark == TransitMark.Stop) ring else MaterialTheme.colorScheme.surface
    Box(modifier.size(MarkSize).clip(CircleShape).background(fill).border(RingWidth, ring, CircleShape))
}

/** A place of the route next to its name: its photo in a circle, or its kind of place (or where I am) on a tint. */
@Composable
private fun PlaceCircle(icon: TransitPlaceIcon) {
    val modifier = Modifier.size(PlaceCircleSize).clip(CircleShape)
    if (icon.photoId != null && icon.photoUrl != null) {
        PhotoImage(photoId = icon.photoId, url = icon.photoUrl, size = PhotoSize.Thumb, contentDescription = null, modifier = modifier)
        return
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = when {
                icon.isMyLocation -> Icons.Default.MyLocation
                else -> icon.category?.icon ?: Icons.Default.Place
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(PlaceIconSize),
        )
    }
}

/** The line down to the next row: dotted for walks (quiet at a change), a solid bar in the line's colour for rides. */
@Composable
private fun TimelinePath(path: TransitPath, modifier: Modifier = Modifier) {
    when (path) {
        is TransitPath.Ride -> Box(modifier.width(BarWidth).clip(RoundedCornerShape(BarWidth / 2)).background(lineColor(path.color)))
        TransitPath.Walk, TransitPath.Change -> {
            val color = if (path == TransitPath.Walk) MapColors.Walk else MaterialTheme.colorScheme.outlineVariant
            Canvas(modifier.width(RingWidth)) {
                val dot = RingWidth.toPx()
                // Round caps on dashes of no length draw dots, one dot wide and two apart.
                drawLine(
                    color = color,
                    start = Offset(size.width / 2, dot / 2),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = dot,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, dot * 2)),
                )
            }
        }
    }
}

/** The way public transport travels, as its icon. */
private val TransitVehicle.icon: ImageVector
    get() = when (this) {
        TransitVehicle.Tram -> Icons.Default.Tram
        TransitVehicle.Subway -> Icons.Default.DirectionsSubway
        TransitVehicle.Bus -> Icons.Default.DirectionsBus
        TransitVehicle.Train -> Icons.Default.Train
        TransitVehicle.Ferry -> Icons.Default.DirectionsBoat
        TransitVehicle.CableCar, TransitVehicle.Other -> Icons.Default.DirectionsTransit
    }

private fun lineColor(color: Long?): Color = color?.let { Color(it) } ?: MapColors.Transit

/** A walk's minutes as a bare number, as the legs row shows them; at least one. */
private fun wholeMinutes(seconds: Int): Int = (seconds / 60.0).roundToInt().coerceAtLeast(1)

/**
 * "Leave at": a time on the open day (the date stays the day's; Shahin, 2026-11-15), leaving then or, from A to B,
 * arriving by then.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LeaveAtDialog(dialog: MapDialog.LeaveAt, onAction: (MapAction) -> Unit) {
    val time = rememberTimePickerState(initialHour = dialog.hour, initialMinute = dialog.minute, is24Hour = true)
    var isArriveBy by rememberSaveable { mutableStateOf(dialog.isArriveBy) }
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

/** The canvas's bold labels: 14 sp, bold. */
private val BoldLabel @Composable get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)

/** The canvas's boldest text: times and places, 14 sp, extra bold. */
private val BoldTitle @Composable get() = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)

private val LegsHeight = 40.dp
private val LeaveAtHeight = 36.dp
private val LegGap = 4.dp
private val BadgeShape = RoundedCornerShape(6.dp)
private val TimeWidth = 44.dp
private val TrackWidth = 18.dp
private val MarkSize = 12.dp
private val MarkTop = 4.dp
private val RingWidth = 3.dp
private val BarWidth = 5.dp
private val PathGap = 2.dp
private val PlaceCircleSize = 24.dp
private val PlaceIconSize = 14.dp
private val DetailIconSize = 16.dp

private val previewSteps = listOf(
    TransitStep(false, null, null, null, previewStop(null, 12), previewStop("Neubaugasse", 16), 0, 300, 240, emptyList()),
    TransitStep(true, TransitVehicle.Subway, TransitLine("U3", 0xFFEF7C00L, 0xFFFFFFFFL), "Simmering", previewStop("Neubaugasse", 16), previewStop("Stephansplatz", 22), 4, 2100, 360, emptyList()),
    TransitStep(false, null, null, null, previewStop("Stephansplatz", 22), previewStop("Stephansplatz", 25), 0, 120, 180, emptyList()),
    TransitStep(true, TransitVehicle.Subway, TransitLine("U1", 0xFFE3000FL, 0xFFFFFFFFL), "Leopoldau", previewStop("Stephansplatz", 25), previewStop("Praterstern", 30), 3, 1900, 300, emptyList()),
    TransitStep(false, null, null, null, previewStop("Praterstern", 30), previewStop(null, 36), 0, 450, 360, emptyList()),
)

private fun previewStop(name: String?, minute: Int) =
    TransitStop(name, GeoPoint(48.2, 16.37), Instant.parse("2026-11-16T08:${minute.toString().padStart(2, '0')}:00Z"))

private val previewRoute = Route(emptyList(), 5000, 1440, listOf(RouteLeg(null, null, 5000, 1440, previewSteps)), previewSteps.first().from.time, previewSteps.last().to.time, transfers = 1)

@Composable
private fun TransitPreviewContent() {
    PlanPreviewSheet {
        Column(Modifier.padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            TransitLegsButton(
                persistentListOf(TransitChip.Walk(240), TransitChip.Ride(previewSteps[1].line!!, 360), TransitChip.Ride(previewSteps[3].line!!, 300), TransitChip.Walk(360)),
                onAction = {},
            )
            LeaveAtButton(LeaveAtState("09:12", isArriveBy = false), onAction = {})
            TransitNoteText(TransitNote.NotHere)
            val (title, subtitle) = transitStepsHeader(previewRoute)
            val rows = transitStepRows(
                previewRoute.legs,
                fromEnd = { TransitEnd(UiText.Server("Mariahilfer Kirche"), TransitPlaceIcon(category = PlaceCategory.Historic)) },
                toEnd = { TransitEnd(UiText.Server("Wiener Riesenrad"), TransitPlaceIcon(category = PlaceCategory.Attraction)) },
            )
            TransitStepsSheet(MapSheet.TransitSteps(title, subtitle, rows.toImmutableList(), isTypicalTimes = true, attribution = "Timetables: Wiener Linien · © OpenStreetMap contributors"), onClose = {})
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
