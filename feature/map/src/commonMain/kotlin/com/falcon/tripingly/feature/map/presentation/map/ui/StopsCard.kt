package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.ToggleButton
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_add_day
import com.falcon.tripingly.feature.map.generated.resources.map_best_order
import com.falcon.tripingly.feature.map.generated.resources.map_clear_day
import com.falcon.tripingly.feature.map.generated.resources.map_day_menu
import com.falcon.tripingly.feature.map.generated.resources.map_delete_day
import com.falcon.tripingly.feature.map.generated.resources.map_remove_stop
import com.falcon.tripingly.feature.map.generated.resources.map_route
import com.falcon.tripingly.feature.map.generated.resources.map_route_between
import com.falcon.tripingly.feature.map.generated.resources.map_route_close
import com.falcon.tripingly.feature.map.generated.resources.map_stop_count
import com.falcon.tripingly.feature.map.presentation.component.MapMarker
import com.falcon.tripingly.feature.map.presentation.map.EditorMenu
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.PointRouteCardState
import com.falcon.tripingly.feature.map.presentation.map.RouteSummaryState
import com.falcon.tripingly.feature.map.presentation.map.StopsCardState
import com.falcon.tripingly.feature.map.presentation.map.preview.PointRouteCardPreviewStates
import com.falcon.tripingly.feature.map.presentation.map.preview.StopsCardPreviewStates
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The open day's plan: "6 stops" with Route and the day's ⋯ menu ([editorMenu], for owners and editors), the stops,
 * how to travel with the best order, and, with the route on, its totals and the places along the way.
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
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = TitleRowHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(Res.plurals.map_stop_count, card.stops.size, card.stops.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            card.isRouteOn?.let { isOn ->
                // Neutral like ⋯ while off; green once on.
                ToggleButton(
                    text = stringResource(Res.string.map_route),
                    checked = isOn,
                    onClick = { onAction(MapAction.Plan.ToggleRoute) },
                    icon = Icons.Outlined.Directions,
                )
            }
            DayMenuButton(dayNumber, canClear = card.canClear, editorMenu = editorMenu, onAction = onAction)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
            items(card.stops, key = { it.id }) { stop ->
                Row(
                    modifier = Modifier.animateItem(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The time from the stop before.
                    card.legLabels[stop.id]?.let { leg ->
                        Text(leg.asString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StopChip(stop = stop, isSelected = stop.id == card.selectedStopId, onAction = onAction)
                }
            }
        }
        val route = card.route
        // Free for everyone, with or without the route.
        if (route != null || card.canFindBestOrder) {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
                if (route != null) {
                    TravelModeToggle(route.mode, onModeSelected = { onAction(MapAction.Plan.SelectTravelMode(it)) }, modifier = Modifier.weight(1f))
                }
                if (card.canFindBestOrder) {
                    SecondaryButton(
                        text = stringResource(Res.string.map_best_order),
                        onClick = { onAction(MapAction.Plan.FindBestOrder) },
                        isLoading = card.isFindingBestOrder,
                        size = ButtonSize.Small,
                        icon = Icons.Outlined.AutoAwesome,
                    )
                }
            }
        }
        route?.let { RouteAlongTheWay(it, onAction) }
    }
}

/** A route from one place to another, with how to travel, its totals and the places along the way. */
@Composable
internal fun PointRouteCard(
    card: PointRouteCardState,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlanCardColumn(modifier) {
        Row(modifier = Modifier.heightIn(min = TitleRowHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.map_route_between, card.from.name, card.to.name),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onAction(MapAction.Plan.ClosePointRoute) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_route_close))
            }
        }
        TravelModeToggle(card.route.mode, onModeSelected = { onAction(MapAction.Plan.SelectTravelMode(it)) })
        RouteAlongTheWay(card.route, onAction)
    }
}

/** The sheet's content, inside its edges; the handle sits above. */
@Composable
private fun PlanCardColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        // The ⋯ button's touch area makes up the rest of the end, so its circle sits as far in as the text.
        modifier = modifier.fillMaxWidth().padding(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall, bottom = MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
    ) { content() }
}

@Composable
private fun RouteAlongTheWay(route: RouteSummaryState, onAction: (MapAction) -> Unit) {
    AlongTheWay(
        places = route.alongTheWay,
        dayNumber = route.addToDayNumber,
        onOpen = { onAction(MapAction.Plan.AlongTheWayClick(it)) },
        onAdd = { onAction(MapAction.Plan.AddAlongTheWay(it)) },
        totals = { RouteTotals(route.durationSeconds, route.distanceMeters, route.isEstimated, route.isLoading) },
    )
}

/** The day's ⋯: add a day, clear the open one ([canClear]), delete it. Hidden for viewers. */
@Composable
private fun DayMenuButton(dayNumber: Int, canClear: Boolean, editorMenu: EditorMenu?, onAction: (MapAction) -> Unit) {
    val actions = listOfNotNull(
        editorMenu?.let { DropdownAction(stringResource(Res.string.map_add_day), icon = Icons.Outlined.Add) { onAction(MapAction.Header.AddDay) } },
        DropdownAction(stringResource(Res.string.map_clear_day, dayNumber), icon = Icons.Outlined.CleaningServices) { onAction(MapAction.Plan.ClearAll) }
            .takeIf { canClear },
        DropdownAction(stringResource(Res.string.map_delete_day), isDestructive = true, icon = Icons.Outlined.Delete) { onAction(MapAction.Header.DeleteDay) }
            .takeIf { editorMenu?.canDeleteDay == true },
    )
    if (actions.isEmpty()) return
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.padding(start = MaterialTheme.spacing.small)) {
        FilledTonalIconButton(
            onClick = { isExpanded = true },
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(Res.string.map_day_menu))
        }
        AppDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }, actions = actions)
    }
}

/** A stop: its number in a pin-colored circle and its name; selected while its card is open; × removes it. */
@Composable
private fun StopChip(stop: MapMarker, isSelected: Boolean, onAction: (MapAction) -> Unit) {
    Surface(
        onClick = { onAction(MapAction.MapView.MarkerClick(stop)) },
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.heightIn(min = ChipHeight).padding(start = MaterialTheme.spacing.extraSmall, end = if (stop.canDelete) 0.dp else MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Box(Modifier.size(StopNumberSize).background(MapColors.Stop, CircleShape), contentAlignment = Alignment.Center) {
                Text(stop.orderNumber.toString(), style = MaterialTheme.typography.labelSmall, color = MapColors.OnStop)
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
private val ChipHeight = 36.dp
private val StopNumberSize = 24.dp

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
