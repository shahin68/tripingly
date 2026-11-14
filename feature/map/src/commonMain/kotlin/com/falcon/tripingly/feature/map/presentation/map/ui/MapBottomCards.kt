package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.GpsOff
import com.falcon.tripingly.feature.map.generated.resources.map_location_turn_on
import com.falcon.tripingly.feature.map.generated.resources.map_location_off
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.remember
import com.falcon.tripingly.core.designsystem.component.bottomSystemBar
import com.falcon.tripingly.core.designsystem.component.floatingBottom
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falcon.tripingly.core.designsystem.component.GlassButtonColumn
import com.falcon.tripingly.core.designsystem.component.GlassButtonDefaults
import com.falcon.tripingly.core.designsystem.component.GlassButtonColumnDivider
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_fit_day
import com.falcon.tripingly.feature.map.generated.resources.map_follow_location
import com.falcon.tripingly.feature.map.generated.resources.map_gps_lost
import com.falcon.tripingly.feature.map.generated.resources.map_hide_places
import com.falcon.tripingly.feature.map.generated.resources.map_show_places
import com.falcon.tripingly.feature.map.generated.resources.map_stop_following
import com.falcon.tripingly.feature.map.presentation.map.DayTabsState
import com.falcon.tripingly.feature.map.presentation.map.EditorMenu
import com.falcon.tripingly.feature.map.presentation.map.LocationState
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.PlanCardState
import com.falcon.tripingly.feature.map.presentation.map.PointRouteCardState
import com.falcon.tripingly.feature.map.presentation.map.StopsCardState
import org.jetbrains.compose.resources.stringResource

/**
 * The credits and the map buttons over one floating glass card: the open day's [plan] (the stops, or a route from A
 * to B) in its sheet on top, the day tabs at the bottom. The card grows up from the tabs when a plan comes and shrinks
 * back when it goes, so its corners stay round (Shahin, 2026-11-14, D2a). [onTabsHeight] and [onCreditsHeight] report
 * what always covers the map; the map buttons and a pulled-up sheet only float over it. [editorMenu]: an owner's or
 * editor's day actions, in the sheet's ⋯ and the tabs' +.
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
        // They ride on the card's top. In landscape they sit on the map half instead (MapScreen), so the plan has the
        // panel's whole height.
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
        // What always covers the map: the tabs (none without days) and the gap under the card, each measured.
        val covered = remember { IntArray(2) }
        val reportCovered = { onTabsHeight(covered.sum()) }
        val hasCard = dayTabs.days.isNotEmpty() || plan != null
        // Without the card nothing of it covers the map; the gap below changes then and reports this.
        if (!hasCard) covered[0] = 0
        if (hasCard) {
            GlassSurface(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .windowInsetsPadding(WindowInsets.floatingBottom.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = MaterialTheme.spacing.medium)
                    .fillMaxWidth(),
                blur = false,
            ) {
                Column {
                    PlanSheet(plan = plan, state = planSheet, modifier = Modifier.weight(1f, fill = false)) { shown ->
                        // A route from A to B takes the stops' place until it's closed.
                        when (shown) {
                            is PointRouteCardState -> PointRouteCard(card = shown, onAction = onAction)
                            is StopsCardState -> StopsCard(card = shown, dayNumber = dayNumber, editorMenu = editorMenu, onAction = onAction)
                        }
                    }
                    // The day tabs stay at the bottom; the sheet opens above them.
                    Box(Modifier.onSizeChanged { covered[0] = it.height; reportCovered() }) {
                        if (dayTabs.days.isNotEmpty()) DayTabs(tabs = dayTabs, canAddDay = editorMenu != null, onAction = onAction)
                    }
                }
            }
        }
        // Under the card: the navigation bar's room and a small gap; without days or a plan, the navigation bar's only.
        val gap = if (hasCard) {
            WindowInsets.floatingBottom.add(WindowInsets(bottom = MaterialTheme.spacing.small))
        } else {
            WindowInsets.bottomSystemBar
        }
        Spacer(Modifier.windowInsetsBottomHeight(gap).onSizeChanged { covered[1] = it.height; reportCovered() })
    }
}

/** OpenStreetMap's credit, or the route's while one shows, on a small glass label. */
@Composable
internal fun MapCredits(routeAttribution: String?, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.labelSmall) {
    GlassSurface(modifier = modifier, shape = MaterialTheme.shapes.extraSmall, shadowElevation = 0.dp, blur = false) {
        // The route's two credits on two lines, so the label stays narrow.
        if (routeAttribution != null) {
            RouteAttribution(routeAttribution.replace(CreditsSeparator, "\n"), style = style)
        } else {
            OsmAttribution(style = style)
        }
    }
}

/** The credits in landscape, at the map's top end: a little smaller than elsewhere, still legible (Shahin, 2026-11-14). */
internal val SmallCreditsStyle: TextStyle
    @Composable get() = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp)

/**
 * Hides or shows the places (crossed out while hidden), over the button that fits the camera to the open day (its
 * stops, else the whole trip's, else the destination), over the location button, which follows me while lit (filled,
 * in the accent colour). Following without a signal, a quiet note beside it says it waits for one.
 */
@Composable
internal fun MapButtons(location: LocationState, arePlacesHidden: Boolean, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.Bottom) {
        AnimatedVisibility(location.isSignalLost, enter = fadeIn(), exit = fadeOut()) { GpsLostNote() }
        AnimatedVisibility(location.isSwitchedOff, enter = fadeIn(), exit = fadeOut()) {
            LocationOffNote(onTurnOn = { onAction(MapAction.Location.TurnOn) })
        }
        MapButtonColumn(location, arePlacesHidden, onAction)
    }
}

@Composable
private fun MapButtonColumn(location: LocationState, arePlacesHidden: Boolean, onAction: (MapAction) -> Unit) {
    GlassButtonColumn(blur = false) {
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
            when {
                location.isLoading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                location.isFollowing -> Icon(Icons.Filled.NearMe, contentDescription = stringResource(Res.string.map_stop_following), tint = MaterialTheme.colorScheme.primary)
                else -> Icon(Icons.Outlined.NearMe, contentDescription = stringResource(Res.string.map_follow_location))
            }
        }
    }
}

/** Following me, no location for a while (a tunnel, underground): a small glass note, as high as one button. */
@Composable
private fun GpsLostNote() {
    GlassSurface(shape = CircleShape, blur = false) {
        Row(
            modifier = Modifier.height(GlassButtonDefaults.Size).padding(horizontal = MaterialTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.GpsOff, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(Res.string.map_gps_lost), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The button is on but the device's location is switched off: a glass note, as high as one button, that turns it on. */
@Composable
private fun LocationOffNote(onTurnOn: () -> Unit) {
    GlassSurface(shape = CircleShape, blur = false) {
        Row(
            modifier = Modifier.height(GlassButtonDefaults.Size).padding(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.LocationOff, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(Res.string.map_location_off), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onTurnOn) { Text(stringResource(Res.string.map_location_turn_on)) }
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
        Column(
            Modifier.align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                MapButtons(location = LocationState(), arePlacesHidden = false, onAction = {})
                MapButtons(location = LocationState(isLoading = true), arePlacesHidden = true, onAction = {})
            }
            MapButtons(location = LocationState(isFollowing = true, isSignalLost = true), arePlacesHidden = false, onAction = {})
            MapButtons(location = LocationState(isFollowing = true, isSwitchedOff = true), arePlacesHidden = false, onAction = {})
        }
    }
}

/** The credits, and the map buttons idle, while the location is on its way, without signal and with location off. */
@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun MapControlsPreview() {
    TripinglyTheme { MapControlsPreviewContent() }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun MapControlsDarkPreview() {
    TripinglyTheme(darkTheme = true) { MapControlsPreviewContent() }
}

/** Between openrouteservice's and OpenStreetMap's credit in the route's attribution. */
private val CreditsSeparator = Regex("""\s*\|\s*""")
