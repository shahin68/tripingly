package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.ScrambleText
import com.falcon.tripingly.core.designsystem.component.ToggleButton
import com.falcon.tripingly.core.designsystem.component.horizontalFadingEdge
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_add_day
import com.falcon.tripingly.feature.map.generated.resources.map_best_order
import com.falcon.tripingly.feature.map.generated.resources.map_clear_day
import com.falcon.tripingly.feature.map.generated.resources.map_day_menu
import com.falcon.tripingly.feature.map.generated.resources.map_delete_day
import com.falcon.tripingly.feature.map.generated.resources.map_ordering
import com.falcon.tripingly.feature.map.generated.resources.map_remove_stop
import com.falcon.tripingly.feature.map.generated.resources.map_route
import com.falcon.tripingly.feature.map.generated.resources.map_route_between
import com.falcon.tripingly.feature.map.generated.resources.map_route_close
import com.falcon.tripingly.feature.map.generated.resources.map_stop_count
import com.falcon.tripingly.feature.map.presentation.component.MapMarker
import com.falcon.tripingly.feature.map.presentation.component.lineColor
import com.falcon.tripingly.feature.map.presentation.map.EditorMenu
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.PointRouteCardState
import com.falcon.tripingly.feature.map.presentation.map.StopsCardState
import com.falcon.tripingly.feature.map.presentation.map.preview.PointRouteCardPreviewStates
import com.falcon.tripingly.feature.map.presentation.map.preview.StopsCardPreviewStates
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The open day's plan: "6 stops" (scrambling to "Ordering…" while the best order is found) with Route and the day's
 * ⋯ menu (Best order, and for owners and editors [editorMenu]), the stops with the travel time between them while the
 * route shows, and the route card growing under them.
 */
@Composable
internal fun StopsCard(
    card: StopsCardState,
    dayNumber: Int,
    editorMenu: EditorMenu?,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlanCardColumn(modifier) {
        val title = if (card.isFindingBestOrder) {
            stringResource(Res.string.map_ordering)
        } else {
            pluralStringResource(Res.plurals.map_stop_count, card.stops.size, card.stops.size)
        }
        PlanTitleRow(title = { ScrambleText(title, it, style = MaterialTheme.typography.titleMedium) }) {
            card.isRouteOn?.let { isOn ->
                // Neutral like ⋯ while off; green once on.
                ToggleButton(
                    text = stringResource(Res.string.map_route),
                    checked = isOn,
                    onClick = { onAction(MapAction.Plan.ToggleRoute) },
                    icon = Icons.Outlined.Directions,
                )
            }
            DayMenuButton(dayNumber, card, editorMenu = editorMenu, onAction = onAction)
        }
        StopChips(card, onAction)
        val gap by animateDpAsState(if (card.route != null) RowGap else 0.dp, tween(ROUTE_GROW_MILLIS, easing = RouteGrowEasing), label = "route gap")
        RouteCardSlot(card.route, onAction, Modifier.padding(top = gap).padding(horizontal = MaterialTheme.spacing.medium))
    }
}

/**
 * The stops in a row that scrolls to the sheet's edges and rests as far in as the title. While the route shows, the
 * time from the stop before grows into each gap, on a short line in the route's colour.
 */
@Composable
private fun StopChips(card: StopsCardState, onAction: (MapAction) -> Unit) {
    val listState = rememberLazyListState()
    val legColor = card.route?.mode?.lineColor ?: MaterialTheme.colorScheme.outlineVariant
    LazyRow(
        state = listState,
        modifier = Modifier.padding(top = RowGap).horizontalFadingEdge(listState),
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(card.stops, key = { _, stop -> stop.id }) { index, stop ->
            Row(modifier = Modifier.animateItem(), verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) LegConnector(card.legLabels[stop.id], legColor, index)
                StopChip(stop = stop, isSelected = stop.id == card.selectedStopId, onAction = onAction)
            }
        }
    }
}

/**
 * What joins a stop to the one before: a short grey line, or with the route on, the time from the stop before
 * ("12 min") between two lines in the route's colour, growing in one gap after another.
 */
@Composable
private fun LegConnector(leg: UiText?, color: Color, index: Int) {
    val delay = LEG_DELAY_MILLIS + index.coerceAtMost(LEG_STAGGERED) * LEG_STAGGER_MILLIS
    AnimatedContent(
        targetState = leg,
        contentKey = { it != null },
        transitionSpec = {
            fadeIn(tween(LEG_FADE_MILLIS, delay)) togetherWith fadeOut(tween(LEG_FADE_OUT_MILLIS)) using
                SizeTransform(clip = true) { _, _ -> tween(LEG_GROW_MILLIS, if (targetState != null) delay else 0, RouteGrowEasing) }
        },
        label = "leg",
    ) { shown ->
        if (shown != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegLine(color, LegLineLength)
                Text(
                    text = shown.asString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = LegTextPadding),
                )
                LegLine(color, LegLineLength)
            }
        } else {
            Box(Modifier.width(LegLineLength), contentAlignment = Alignment.Center) {
                LegLine(MaterialTheme.colorScheme.surfaceVariant, LegLineLength / 2)
            }
        }
    }
}

@Composable
private fun LegLine(color: Color, length: Dp) {
    Box(Modifier.size(length, LegLineWidth).background(color, CircleShape))
}

/** A route from one place to another, with how to travel, its totals and the places along the way. */
@Composable
internal fun PointRouteCard(
    card: PointRouteCardState,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlanCardColumn(modifier) {
        PlanTitleRow(title = { Text(stringResource(Res.string.map_route_between, card.from.name, card.to.name), it, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }) {
            IconButton(onClick = { onAction(MapAction.Plan.ClosePointRoute) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_route_close))
            }
        }
        RouteCardSlot(card.route, onAction, Modifier.padding(top = RowGap).padding(horizontal = MaterialTheme.spacing.medium))
    }
}

/** The sheet's content under its handle; each row keeps its own sides. */
@Composable
private fun PlanCardColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacing.small), content = content)
}

/**
 * The plan's [title] (given the modifier that lets it take the room left) with its buttons at the end, all one
 * height. The end button's 48 dp touch area makes up the rest of the end padding, so its circle sits as far in as
 * the title.
 */
@Composable
private fun PlanTitleRow(title: @Composable (Modifier) -> Unit, buttons: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TitleRowHeight)
            .padding(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        title(Modifier.weight(1f))
        buttons()
    }
}

/**
 * The day's ⋯: Best order (anyone who can reorder, 3 to 25 stops), add a day, clear the open one, delete it. Hidden
 * when none of them is open to this user.
 */
@Composable
private fun DayMenuButton(dayNumber: Int, card: StopsCardState, editorMenu: EditorMenu?, onAction: (MapAction) -> Unit) {
    val actions = listOfNotNull(
        DropdownAction(stringResource(Res.string.map_best_order), icon = Icons.Outlined.AutoAwesome) { onAction(MapAction.Plan.FindBestOrder) }
            .takeIf { card.canFindBestOrder && !card.isFindingBestOrder },
        editorMenu?.let { DropdownAction(stringResource(Res.string.map_add_day), icon = Icons.Outlined.Add) { onAction(MapAction.Header.AddDay) } },
        DropdownAction(stringResource(Res.string.map_clear_day, dayNumber), icon = Icons.Outlined.CleaningServices) { onAction(MapAction.Plan.ClearAll) }
            .takeIf { card.canClear },
        DropdownAction(stringResource(Res.string.map_delete_day), isDestructive = true, icon = Icons.Outlined.Delete) { onAction(MapAction.Header.DeleteDay) }
            .takeIf { editorMenu?.canDeleteDay == true },
    )
    if (actions.isEmpty()) return
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(
            onClick = { isExpanded = true },
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(Res.string.map_day_menu))
        }
        AppDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }, actions = actions)
    }
}

/** A stop: its number in a pin-coloured circle and its name; selected while its card is open; × removes it (owners, and editors on their own stops). */
@Composable
private fun StopChip(stop: MapMarker, isSelected: Boolean, onAction: (MapAction) -> Unit) {
    Surface(
        onClick = { onAction(MapAction.MapView.MarkerClick(stop)) },
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            // The number sits as far from the chip's start as from its top and bottom.
            modifier = Modifier.heightIn(min = ChipHeight).padding(start = (ChipHeight - StopNumberSize) / 2, end = if (stop.canDelete) 0.dp else MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Box(Modifier.size(StopNumberSize).background(MaterialTheme.extendedColors.stop, CircleShape), contentAlignment = Alignment.Center) {
                Text(stop.orderNumber.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.extendedColors.onStop)
            }
            Text(stop.title.asString(), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (stop.canDelete) {
                IconButton(onClick = { onAction(MapAction.Plan.RemoveStop(stop.id)) }, modifier = Modifier.size(ChipHeight)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_remove_stop), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private val TitleRowHeight = 48.dp
private val RowGap = 12.dp

private val ChipHeight = 40.dp

private val LegLineLength = 8.dp
private val LegLineWidth = 2.dp
private val LegTextPadding = 3.dp

// The canvas's timings: the times grow into the gaps one after another, just after the route card starts growing.
private const val LEG_DELAY_MILLIS = 80
private const val LEG_STAGGER_MILLIS = 60
// Gaps further on are off screen anyway, so they don't wait.
private const val LEG_STAGGERED = 5
private const val LEG_GROW_MILLIS = 420
private const val LEG_FADE_MILLIS = 220
private const val LEG_FADE_OUT_MILLIS = 120
private val StopNumberSize = 28.dp

@Preview
@Composable
private fun StopsCardPreview(@PreviewParameter(StopsCardPreviewStates::class) card: StopsCardState) {
    TripinglyTheme {
        StopsCard(card = card, dayNumber = 1, editorMenu = EditorMenu(canDeleteDay = true), onAction = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surface))
    }
}

@Preview
@Composable
private fun StopsCardDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        StopsCard(card = StopsCardPreviewStates().values.elementAt(1), dayNumber = 2, editorMenu = null, onAction = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surface))
    }
}

@Preview
@Composable
private fun PointRouteCardPreview(@PreviewParameter(PointRouteCardPreviewStates::class) card: PointRouteCardState) {
    TripinglyTheme { PointRouteCard(card = card, onAction = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) }
}

@Preview
@Composable
private fun PointRouteCardDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        PointRouteCard(card = PointRouteCardPreviewStates().values.first(), onAction = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surface))
    }
}
