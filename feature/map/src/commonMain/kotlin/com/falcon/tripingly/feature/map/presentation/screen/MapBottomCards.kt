package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_best_order
import com.falcon.tripingly.feature.map.generated.resources.map_itinerary_clear_all
import com.falcon.tripingly.feature.map.generated.resources.map_itinerary_title
import com.falcon.tripingly.feature.map.generated.resources.map_remove_stop
import com.falcon.tripingly.feature.map.generated.resources.map_route
import com.falcon.tripingly.feature.map.generated.resources.map_route_between
import com.falcon.tripingly.feature.map.generated.resources.map_route_close
import com.falcon.tripingly.feature.map.generated.resources.map_route_start
import com.falcon.tripingly.feature.map.presentation.component.AlongTheWay
import com.falcon.tripingly.feature.map.presentation.component.RouteSummary
import org.jetbrains.compose.resources.stringResource

internal val DayTabsHeight = 48.dp

/** The credits and the location button above the plan: the button's height and its margins. */
internal val MapControlsHeight = 88.dp

@Composable
internal fun DayTabs(
    tabs: DayTabsState,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.days.isEmpty()) return Spacer(modifier.navigationBarsPadding())
    // A fixed height, part of what the map keeps out of; the background reaches down under the navigation bar.
    SecondaryScrollableTabRow(
        selectedTabIndex = tabs.activeIndex,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .height(DayTabsHeight),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 16.dp,
        divider = {},
    ) {
        tabs.days.forEachIndexed { index, day ->
            Tab(
                selected = tabs.activeIndex == index,
                onClick = { onAction(MapAction.OnDaySelected(index)) },
                text = {
                    Text(day.label.asString())
                },
            )
        }
    }
}

@Composable
internal fun LocationFab(
    location: LocationState,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = { onAction(MapAction.CenterOnUserLocation) },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    ) {
        if (location.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = "📍",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** "Route from here" was tapped on [placeName]: the next place's card offers "Route to here". */
@Composable
internal fun RouteStartChip(placeName: String, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = { onAction(MapAction.OnCancelRouteStart) },
        label = { Text(stringResource(Res.string.map_route_start, placeName)) },
        leadingIcon = { Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_route_close), modifier = Modifier.size(18.dp)) },
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    )
}

/** The open day's stops and the best order; with the Route button on, the route's times and the places along the way. */
@Composable
internal fun StopsCard(
    card: StopsCardState,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomCard(modifier) {
        StopsCardTitle(stopCount = card.stops.size, canClear = card.canClear, isRouteOn = card.isRouteOn, onAction = onAction)
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            card.stops.forEach { stop ->
                // The time from the stop before.
                card.legLabels[stop.id]?.let { leg ->
                    item(key = "leg:${stop.id}") {
                        Text(
                            text = leg.asString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = stop.id) {
                    StopChip(stop = stop, isSelected = stop.id == card.selectedStopId, onAction = onAction)
                }
            }
        }
        // Free for everyone, with or without the route.
        if (card.canFindBestOrder) {
            TextButton(onClick = { onAction(MapAction.OnFindBestOrder) }, enabled = !card.isFindingBestOrder) {
                Text(stringResource(Res.string.map_best_order))
                if (card.isFindingBestOrder) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp).size(14.dp), strokeWidth = 2.dp)
                }
            }
        }
        card.route?.let { RouteDetails(it, onAction, Modifier.padding(top = MaterialTheme.spacing.small)) }
    }
}

/** A route from one place to another, with its time, how to travel and the places along the way. */
@Composable
internal fun PointRouteCard(
    card: PointRouteCardState,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.map_route_between, card.from.name, card.to.name),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onAction(MapAction.OnClosePointRoute) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_route_close))
            }
        }
        RouteDetails(card.route, onAction)
    }
}

@Composable
private fun BottomCard(modifier: Modifier, content: @Composable () -> Unit) {
    // The plan sheet is the card; its handle sits above.
    Column(modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) { content() }
}

@Composable
private fun RouteDetails(route: RouteSummaryState, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        RouteSummary(
            mode = route.mode,
            durationSeconds = route.durationSeconds,
            distanceMeters = route.distanceMeters,
            isEstimated = route.isEstimated,
            isLoading = route.isLoading,
            onModeSelected = { onAction(MapAction.OnTravelModeSelected(it)) },
        )
        AlongTheWay(
            places = route.alongTheWay,
            dayNumber = route.addToDayNumber,
            onOpen = { onAction(MapAction.OnAlongTheWayClick(it)) },
            onAdd = { onAction(MapAction.OnAddAlongTheWay(it)) },
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        )
    }
}

@Composable
private fun StopChip(
    stop: MapMarker,
    isSelected: Boolean,
    onAction: (MapAction) -> Unit,
) {
    InputChip(
        selected = isSelected,
        onClick = { onAction(MapAction.OnMarkerClick(stop)) },
        label = {
            Text(
                text = stop.title.asString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stop.orderNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        trailingIcon = if (stop.canDelete) {
            {
                IconButton(
                    onClick = { onAction(MapAction.OnRemoveMarker(stop.id)) },
                    modifier = Modifier.size(16.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.map_remove_stop),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        } else {
            null
        },
        colors = InputChipDefaults.inputChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

@Composable
private fun StopsCardTitle(
    stopCount: Int,
    canClear: Boolean,
    /** The Route button's state; null hides it. */
    isRouteOn: Boolean?,
    onAction: (MapAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.map_itinerary_title, stopCount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (isRouteOn != null) {
            FilterChip(
                selected = isRouteOn,
                onClick = { onAction(MapAction.OnToggleDayRoute) },
                label = { Text(stringResource(Res.string.map_route)) },
                leadingIcon = { Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        if (canClear) {
            TextButton(onClick = { onAction(MapAction.ClearAllMarkers) }) {
                Text(stringResource(Res.string.map_itinerary_clear_all), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
