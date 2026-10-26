package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.GlassButtonColumn
import com.falcon.tripingly.core.designsystem.component.GlassButtonColumnDivider
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_fit_day
import com.falcon.tripingly.feature.map.generated.resources.map_my_location
import com.falcon.tripingly.feature.map.generated.resources.map_route_close
import com.falcon.tripingly.feature.map.generated.resources.map_route_start
import com.falcon.tripingly.feature.map.presentation.map.DayTabsState
import com.falcon.tripingly.feature.map.presentation.map.EditorMenu
import com.falcon.tripingly.feature.map.presentation.map.LocationState
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.PlanCardState
import com.falcon.tripingly.feature.map.presentation.map.PointRouteCardState
import com.falcon.tripingly.feature.map.presentation.map.StopsCardState
import org.jetbrains.compose.resources.stringResource

/**
 * The credits and the map buttons, the route-start chip and the open day's [plan] (the stops, or a route from A to
 * B) in its sheet, over the day tabs at the bottom: one floating glass card while there's a plan, the tabs alone
 * without. [onTabsHeight] and [onCreditsHeight] report what always covers the map; the map buttons and a pulled-up
 * sheet only float over it. [editorMenu]: an owner's or editor's day actions, in the sheet's ⋯ and the tabs' +.
 */
@Composable
internal fun BottomCards(
    routeAttribution: String?,
    location: LocationState,
    routeStartName: String?,
    dayTabs: DayTabsState,
    plan: PlanCardState?,
    editorMenu: EditorMenu?,
    planSheet: AnchoredDraggableState<PlanSheetValue>,
    onAction: (MapAction) -> Unit,
    onTabsHeight: (Int) -> Unit,
    onCreditsHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayNumber = dayTabs.days.getOrNull(dayTabs.activeIndex)?.number ?: 1
    Column(modifier = modifier.fillMaxWidth()) {
        PlanSheet(
            plan = plan,
            state = planSheet,
            above = {
                routeStartName?.let { name ->
                    RouteStartChip(name, onAction, Modifier.padding(start = MaterialTheme.spacing.medium))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.spacing.medium),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Our places come from OpenStreetMap, routes from openrouteservice too. The credits and the gap
                    // under them are what this row covers of the map.
                    MapCredits(
                        routeAttribution = routeAttribution,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .onSizeChanged { onCreditsHeight(it.height) }
                            .padding(bottom = MaterialTheme.spacing.small),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    MapButtons(location, onAction, Modifier.padding(bottom = MaterialTheme.spacing.small))
                }
            },
            modifier = Modifier.weight(1f, fill = false),
        ) { shown ->
            // A route from A to B takes the stops' place until it's closed.
            when (shown) {
                is PointRouteCardState -> PointRouteCard(card = shown, onAction = onAction)
                is StopsCardState -> StopsCard(card = shown, dayNumber = dayNumber, editorMenu = editorMenu, onAction = onAction)
            }
        }
        // The day tabs stay at the bottom; everything else opens above them.
        DayTabs(
            tabs = dayTabs,
            isJoined = plan != null,
            canAddDay = editorMenu != null,
            onAction = onAction,
            modifier = Modifier.onSizeChanged { onTabsHeight(it.height) },
        )
    }
}

/** OpenStreetMap's credit, or the route's while one shows, on a small glass label. */
@Composable
private fun MapCredits(routeAttribution: String?, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier, shape = MaterialTheme.shapes.extraSmall, shadowElevation = 0.dp, blur = false) {
        if (routeAttribution != null) RouteAttribution(routeAttribution) else OsmAttribution()
    }
}

/** Fits the camera to the open day (its stops, else the whole trip's, else the destination), over the location button. */
@Composable
internal fun MapButtons(location: LocationState, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    GlassButtonColumn(modifier = modifier, blur = false) {
        IconButton(onClick = { onAction(MapAction.Days.FitDay) }) {
            Icon(Icons.Outlined.CenterFocusStrong, contentDescription = stringResource(Res.string.map_fit_day))
        }
        GlassButtonColumnDivider()
        IconButton(onClick = { onAction(MapAction.Location.CenterOnUser) }) {
            if (location.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.NearMe, contentDescription = stringResource(Res.string.map_my_location))
            }
        }
    }
}

/** "Route from here" was tapped on [placeName]: the next place's card offers "Route to here". Tapping it cancels. */
@Composable
internal fun RouteStartChip(placeName: String, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier.padding(bottom = MaterialTheme.spacing.small),
        shape = CircleShape,
        onClick = { onAction(MapAction.Plan.CancelRouteStart) },
        blur = false,
    ) {
        Row(
            modifier = Modifier.padding(start = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Icon(Icons.Outlined.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(Res.string.map_route_start, placeName),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(vertical = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            )
            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_route_close), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MapControlsPreviewContent() {
    Box(Modifier.fillMaxSize().padding(MaterialTheme.spacing.medium)) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            RouteStartChip(placeName = "Eiffel Tower", onAction = {})
            RouteStartChip(placeName = "Musée national des Arts asiatiques – Guimet", onAction = {})
            MapCredits(routeAttribution = null)
            MapCredits(routeAttribution = "© openrouteservice.org | © OpenStreetMap contributors")
        }
        Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            MapButtons(location = LocationState(), onAction = {})
            MapButtons(location = LocationState(isLoading = true), onAction = {})
        }
    }
}

/** The route-start chip, the credits, and the map buttons idle and while the location is on its way. */
@Preview(widthDp = 360, heightDp = 360)
@Composable
private fun MapControlsPreview() {
    TripinglyTheme { MapControlsPreviewContent() }
}

@Preview(widthDp = 360, heightDp = 360)
@Composable
private fun MapControlsDarkPreview() {
    TripinglyTheme(darkTheme = true) { MapControlsPreviewContent() }
}
