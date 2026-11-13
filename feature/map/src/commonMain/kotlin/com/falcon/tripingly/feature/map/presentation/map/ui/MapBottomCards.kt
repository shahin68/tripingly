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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
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
import com.falcon.tripingly.feature.map.generated.resources.map_hide_places
import com.falcon.tripingly.feature.map.generated.resources.map_my_location
import com.falcon.tripingly.feature.map.generated.resources.map_show_places
import com.falcon.tripingly.feature.map.presentation.map.DayTabsState
import com.falcon.tripingly.feature.map.presentation.map.EditorMenu
import com.falcon.tripingly.feature.map.presentation.map.LocationState
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.PlanCardState
import com.falcon.tripingly.feature.map.presentation.map.PointRouteCardState
import com.falcon.tripingly.feature.map.presentation.map.StopsCardState
import org.jetbrains.compose.resources.stringResource

/**
 * The credits and the map buttons and the open day's [plan] (the stops, or a route from A to
 * B) in its sheet, over the day tabs at the bottom: one floating glass card while there's a plan, the tabs alone
 * without. [onTabsHeight] and [onCreditsHeight] report what always covers the map; the map buttons and a pulled-up
 * sheet only float over it. [editorMenu]: an owner's or editor's day actions, in the sheet's ⋯ and the tabs' +.
 */
@Composable
internal fun BottomCards(
    routeAttribution: String?,
    location: LocationState,
    arePlacesHidden: Boolean,
    dayTabs: DayTabsState,
    plan: PlanCardState?,
    editorMenu: EditorMenu?,
    planSheet: AnchoredDraggableState<PlanSheetValue>,
    isWide: Boolean,
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
                // In landscape they sit on the map half instead (MapScreen), so the plan has the panel's whole height.
                if (!isWide) {
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
                        MapButtons(location, arePlacesHidden, onAction, Modifier.padding(bottom = MaterialTheme.spacing.small))
                    }
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
internal fun MapCredits(routeAttribution: String?, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier, shape = MaterialTheme.shapes.extraSmall, shadowElevation = 0.dp, blur = false) {
        // The route's two credits on two lines, so the label stays narrow.
        if (routeAttribution != null) RouteAttribution(routeAttribution.replace(CreditsSeparator, "\n")) else OsmAttribution()
    }
}

/**
 * Hides or shows the places (crossed out while hidden), over the button that fits the camera to the open day (its
 * stops, else the whole trip's, else the destination), over the location button.
 */
@Composable
internal fun MapButtons(location: LocationState, arePlacesHidden: Boolean, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    GlassButtonColumn(modifier = modifier, blur = false) {
        IconButton(onClick = { onAction(MapAction.Days.TogglePlaces) }) {
            if (arePlacesHidden) {
                Icon(Icons.Outlined.LayersClear, contentDescription = stringResource(Res.string.map_show_places))
            } else {
                Icon(Icons.Outlined.Layers, contentDescription = stringResource(Res.string.map_hide_places))
            }
        }
        GlassButtonColumnDivider()
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

@Composable
private fun MapControlsPreviewContent() {
    Box(Modifier.fillMaxSize().padding(MaterialTheme.spacing.medium)) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            MapCredits(routeAttribution = null)
            MapCredits(routeAttribution = "© openrouteservice.org | © OpenStreetMap contributors")
        }
        Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            MapButtons(location = LocationState(), arePlacesHidden = false, onAction = {})
            MapButtons(location = LocationState(isLoading = true), arePlacesHidden = true, onAction = {})
        }
    }
}

/** The credits, and the map buttons idle and while the location is on its way. */
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

/** Between openrouteservice's and OpenStreetMap's credit in the route's attribution. */
private val CreditsSeparator = Regex("""\s*\|\s*""")
