package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.skeleton
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.ui.timeAgo
import com.falcon.tripingly.feature.trips.generated.resources.*
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One trip on the timeline: its start date in the left column, its [mark] on the rail (a solid dot for what's
 * coming, grey and dashed for the past, today's ring for trips on now, stretched over trips that overlap) and the
 * trip's card, lit up in the brand colour while the trip is on. Tapping opens the trip; ⋮ or a long press opens
 * its menu.
 */
@Composable
internal fun TimelineTripItem(
    trip: Trip,
    today: LocalDate?,
    past: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
    mark: TimelineMark = TimelineMark(),
) {
    val colors = MaterialTheme.colorScheme
    val railColor = if (past) colors.outline else colors.primary
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Overlapping trips share the first one's date.
        DateColumn(trip.startDate.takeIf { mark.isFirst }, Modifier.padding(top = CardTopToDate))
        Box(
            modifier = Modifier
                .width(TimelineDefaults.RailWidth)
                .fillMaxHeight()
                .rail(railColor, dashed = past)
                .mark(mark, fill = if (mark.isNow) colors.background else railColor, edge = if (mark.isNow) colors.primary else colors.background),
        )
        Spacer(Modifier.width(TimelineDefaults.CardGap))
        TripCard(
            trip = trip,
            whenLabel = today?.let { trip.whenRelativeTo(it) }?.label(),
            past = past,
            isOn = mark.isNow,
            onAction = onAction,
            modifier = Modifier.weight(1f).padding(vertical = TimelineDefaults.RowGap / 2),
        )
    }
}

/** "Today · Mon, Oct 26" above the first trip, with a ring on the rail. */
@Composable
internal fun TodayHeader(today: LocalDate, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = TimelineDefaults.DateWidth, bottom = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(TimelineDefaults.RailWidth), contentAlignment = Alignment.Center) {
            Box(Modifier.size(TimelineDefaults.Dot).border(TimelineDefaults.DotRing, MaterialTheme.colorScheme.primary, CircleShape))
        }
        Spacer(Modifier.width(TimelineDefaults.CardGap))
        Text(
            text = stringResource(Res.string.home_today, DateUtils.formatWeekday(today)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** "Past · 2 trips" between what's coming and what's been. */
@Composable
internal fun PastHeader(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = pluralStringResource(Res.plurals.home_past_count, count, count),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = TimelineDefaults.DateWidth + TimelineDefaults.RailWidth + TimelineDefaults.CardGap,
            top = MaterialTheme.spacing.medium,
            bottom = MaterialTheme.spacing.small,
        ),
    )
}

@Composable
private fun DateColumn(date: LocalDate?, modifier: Modifier = Modifier) {
    Column(modifier.width(TimelineDefaults.DateWidth), horizontalAlignment = Alignment.End) {
        if (date != null) {
            Text(date.day.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.skeletonText())
            Text(
                DateUtils.formatMonthShort(date).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.skeletonText(),
            )
        }
    }
}

/**
 * The trip's dot, [fill]ed and ringed with [edge]: a circle, or for trips that overlap one rounded bar from the
 * first trip's dot to the last one's, each row drawing its part of it.
 */
private fun Modifier.mark(mark: TimelineMark, fill: Color, edge: Color) = drawBehind {
    val radius = TimelineDefaults.Dot.toPx() / 2
    val stroke = TimelineDefaults.DotRing.toPx()
    val center = CardTopToDate.toPx() + radius
    // A bar that goes on past the row's edge is cut there, where the next row's part carries on.
    val top = if (mark.isFirst) center - radius else -size.height
    val bottom = if (mark.isLast) center + radius else size.height * 2
    val topLeft = Offset(size.width / 2 - radius + stroke / 2, top + stroke / 2)
    val barSize = Size(radius * 2 - stroke, bottom - top - stroke)
    val corner = CornerRadius(radius - stroke / 2)
    clipRect {
        drawRoundRect(fill, topLeft, barSize, corner)
        drawRoundRect(edge, topLeft, barSize, corner, style = Stroke(stroke))
    }
}

/** The rail through the whole row, so rows join into one line. */
private fun Modifier.rail(color: Color, dashed: Boolean) = drawBehind {
    val x = size.width / 2
    drawLine(
        color = color.copy(alpha = RAIL_ALPHA),
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = TimelineDefaults.RailStroke.toPx(),
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())) else null,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripCard(
    trip: Trip,
    whenLabel: String?,
    past: Boolean,
    isOn: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    // A trip on now is lit up like the brand's buttons, its text in their dark content colour.
    val content = if (isOn) colors.onPrimary else colors.onSurface
    val muted = if (isOn) colors.onPrimary.copy(alpha = ON_MUTED_ALPHA) else colors.onSurfaceVariant
    Surface(
        modifier = modifier
            // Keeps the ripple inside the card's rounded corners.
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = { onAction(Action.Trips.Click(trip.id)) }, onLongClick = { menuOpen = true }),
        shape = MaterialTheme.shapes.large,
        color = if (isOn) colors.primary else colors.surfaceContainerLowest,
        contentColor = content,
    ) {
        Row(Modifier.padding(start = TimelineDefaults.CardPadding, top = TimelineDefaults.CardPadding, bottom = TimelineDefaults.CardPadding)) {
            Column(
                modifier = Modifier.weight(1f).alpha(if (past) PAST_ALPHA else 1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (whenLabel != null) {
                    Text(
                        text = whenLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isOn -> content
                            past -> muted
                            else -> colors.primary
                        },
                        modifier = Modifier.skeletonText(),
                    )
                }
                Text(trip.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
                TripCardDetails(trip, muted)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.home_trip_more, trip.name), modifier = Modifier.skeleton(CircleShape))
                    }
                    AppDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, actions = tripActions(trip, onAction))
                }
                Icon(
                    imageVector = if (trip.visibility == TripVisibility.PRIVATE) Icons.Outlined.Lock else Icons.Outlined.Public,
                    contentDescription = if (trip.visibility == TripVisibility.PRIVATE) stringResource(Res.string.trip_private) else null,
                    tint = muted,
                    modifier = Modifier.size(TimelineDefaults.VisibilityIcon).skeleton(CircleShape),
                )
            }
        }
    }
}

@Composable
private fun TripCardDetails(trip: Trip, muted: Color) {
    val dates = DateUtils.formatRange(trip.startDate, trip.endDate) ?: stringResource(Res.string.trip_no_dates)
    Text(
        text = "$dates · ${pluralStringResource(Res.plurals.trip_day_count, trip.dayCount, trip.dayCount)}",
        style = MaterialTheme.typography.bodySmall,
        color = muted,
        modifier = Modifier.skeletonText(),
    )
    Text(
        text = pluralStringResource(Res.plurals.trip_stop_count, trip.markerCount, trip.markerCount) + " · " +
            stringResource(Res.string.trip_created, timeAgo(trip.createdAt)),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
        modifier = Modifier.skeletonText(),
    )
    val sharing = when {
        trip.role == TripRole.EDITOR -> stringResource(Res.string.trip_shared_by, trip.owner.username)
        trip.role == TripRole.VIEWER -> stringResource(Res.string.trip_saved_by, trip.owner.username)
        trip.editorCount > 0 -> pluralStringResource(Res.plurals.trip_shared_with, trip.editorCount, trip.editorCount)
        else -> null
    }
    if (sharing != null) {
        Row(
            modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            if (trip.role != TripRole.OWNER) AvatarCircle(trip.owner.displayName, size = AvatarSize.Small)
            Text(sharing, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
        }
    }
}

/** The ⋮ and long-press menu; which entries a trip has is decided by [menu]. */
@Composable
private fun tripActions(trip: Trip, onAction: (Action) -> Unit): List<DropdownAction> = trip.menu().map { item ->
    when (item) {
        TripMenuItem.Rename -> DropdownAction(stringResource(Res.string.trip_action_rename)) { onAction(Action.Trips.Rename(trip.id)) }
        TripMenuItem.Reschedule -> DropdownAction(stringResource(Res.string.trip_action_reschedule)) { onAction(Action.Trips.Reschedule(trip.id)) }
        TripMenuItem.MakePrivate -> DropdownAction(stringResource(Res.string.trip_action_make_private)) { onAction(Action.Trips.ToggleVisibility(trip.id)) }
        TripMenuItem.MakePublic -> DropdownAction(stringResource(Res.string.trip_action_make_public)) { onAction(Action.Trips.ToggleVisibility(trip.id)) }
        TripMenuItem.Members -> DropdownAction(stringResource(Res.string.trip_action_members)) { onAction(Action.Trips.ManageMembers(trip.id)) }
        TripMenuItem.Share -> DropdownAction(stringResource(Res.string.trip_action_share)) { onAction(Action.Trips.Share(trip.id)) }
        TripMenuItem.RemoveSaved -> DropdownAction(stringResource(Res.string.trip_action_remove_saved), isDestructive = true) {
            onAction(Action.Trips.RemoveSaved(trip.id))
        }
        TripMenuItem.Delete -> DropdownAction(stringResource(Res.string.trip_action_delete), isDestructive = true) { onAction(Action.Trips.Delete(trip.id)) }
        TripMenuItem.Leave -> DropdownAction(stringResource(Res.string.trip_action_leave), isDestructive = true) { onAction(Action.Trips.Leave(trip.id)) }
    }
}

private object TimelineDefaults {
    val DateWidth = 32.dp
    val RailWidth = 28.dp
    val CardGap = 12.dp
    val RowGap = 10.dp
    val Dot = 12.dp
    val DotRing = 3.dp
    val RailStroke = 2.dp
    val CardPadding = 14.dp
    val VisibilityIcon = 16.dp
}

/** Lines the date and the dot up with the card's first line. */
private val CardTopToDate = 20.dp
private const val RAIL_ALPHA = 0.45f
private const val PAST_ALPHA = 0.75f
/** Secondary text on a lit-up card. */
private const val ON_MUTED_ALPHA = 0.75f

@Composable
private fun TimelinePreviewContent() {
    val today = PreviewToday
    Column(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium)) {
        val timeline = tripsOnNowPreviewData.toTimeline(today)
        if (timeline.showsTodayRow) TodayHeader(today)
        timeline.upcoming.forEach { TimelineTripItem(it.trip, today, past = false, onAction = {}, mark = it.mark) }
        PastHeader(1)
        TimelineTripItem(tripsPreviewData.first(), today, past = true, onAction = {})
    }
}

@Preview
@Composable
private fun TimelineTripItemPreview() {
    TripinglyTheme { TimelinePreviewContent() }
}

@Preview
@Composable
private fun TimelineTripItemDarkPreview() {
    TripinglyTheme(darkTheme = true) { TimelinePreviewContent() }
}
