package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.NumberUtils
import com.falcon.tripingly.core.designsystem.component.SegmentedToggle
import com.falcon.tripingly.core.designsystem.component.SegmentedToggleOption
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_along_add
import com.falcon.tripingly.feature.map.generated.resources.map_along_the_way
import com.falcon.tripingly.feature.map.generated.resources.map_along_the_way_item
import com.falcon.tripingly.feature.map.generated.resources.map_mode_cycling
import com.falcon.tripingly.feature.map.generated.resources.map_mode_driving
import com.falcon.tripingly.feature.map.generated.resources.map_mode_walking
import com.falcon.tripingly.feature.map.generated.resources.map_route_estimated
import com.falcon.tripingly.feature.map.generated.resources.map_route_hours
import com.falcon.tripingly.feature.map.generated.resources.map_route_km
import com.falcon.tripingly.feature.map.generated.resources.map_route_meters
import com.falcon.tripingly.feature.map.generated.resources.map_route_minutes
import com.falcon.tripingly.feature.map.generated.resources.map_route_total
import com.falcon.tripingly.feature.map.presentation.map.preview.previewAlongTheWay
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/** How to travel: walk, bike or car, uncolored, the picked one named. */
@Composable
internal fun TravelModeToggle(
    mode: TravelMode,
    onModeSelected: (TravelMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedToggle(modifier) {
        TravelMode.entries.forEach { entry ->
            SegmentedToggleOption(
                label = stringResource(entry.label()),
                selected = entry == mode,
                onClick = { onModeSelected(entry) },
                icon = entry.icon(),
                showLabel = entry == mode,
            )
        }
    }
}

/** The route's time and length ("2 h 34 min · 12.8 km"), a spinner while it loads, or that times are estimated. */
@Composable
internal fun RouteTotals(
    durationSeconds: Int?,
    distanceMeters: Int?,
    isEstimated: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            isEstimated -> Text(
                text = stringResource(Res.string.map_route_estimated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (durationSeconds != null && distanceMeters != null) {
            Text(
                text = stringResource(Res.string.map_route_total, durationLabel(durationSeconds), distanceText(distanceMeters).asString()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * "Along the way" with the route's [totals] beside it, then the places near the route in the order the route passes
 * them: when the route gets there and how far off it they are. A tap opens the place; owners and editors add it to
 * the open day with + ([dayNumber] is null otherwise). Without places, only the totals show.
 */
@Composable
internal fun AlongTheWay(
    places: ImmutableList<AlongTheWayPlace>,
    dayNumber: Int?,
    onOpen: (MapPlace) -> Unit,
    onAdd: (MapPlace) -> Unit,
    modifier: Modifier = Modifier,
    totals: @Composable () -> Unit = {},
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (places.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.map_along_the_way),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            totals()
        }
        if (places.isEmpty()) return@Column
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            items(places, key = { it.place.id }) { item ->
                AlongTheWayCard(item, dayNumber, onOpen, onAdd, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun AlongTheWayCard(item: AlongTheWayPlace, dayNumber: Int?, onOpen: (MapPlace) -> Unit, onAdd: (MapPlace) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = { onOpen(item.place) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.widthIn(max = AlongCardMaxWidth),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            modifier = Modifier.padding(start = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.small),
        ) {
            Icon(item.place.category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.padding(vertical = MaterialTheme.spacing.small).weight(1f, fill = false)) {
                Text(item.place.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(Res.string.map_along_the_way_item, durationLabel(item.etaFromStartSeconds), distanceText(item.distanceFromRouteMeters).asString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (dayNumber != null) {
                FilledIconButton(onClick = { onAdd(item.place) }, modifier = Modifier.size(AddButtonSize)) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.map_along_add, dayNumber), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private val AlongCardMaxWidth = 240.dp
private val AddButtonSize = 36.dp

/** The routes' credits ("© openrouteservice.org | © OpenStreetMap contributors"), tappable like the map's. */
@Composable
fun RouteAttribution(text: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clickable { runCatching { uriHandler.openUri(COPYRIGHT_URL) } }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** "45 min", or "1 h 20 min" from an hour on; at least a minute. */
@Composable
fun durationLabel(seconds: Int): String = durationText(seconds).asString()

/** [durationLabel] for state built outside composition. */
internal fun durationText(seconds: Int): UiText {
    val minutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    return if (minutes < 60) {
        UiText.Resource(Res.string.map_route_minutes, minutes)
    } else {
        UiText.Resource(Res.string.map_route_hours, minutes / 60, minutes % 60)
    }
}

/** "350 m" up to a kilometre, then "2.4 km" ("2,4 km" in German and Hungarian), as on Nearby. */
internal fun distanceText(meters: Int): UiText =
    if (meters < 1000) {
        UiText.Resource(Res.string.map_route_meters, meters)
    } else {
        UiText.Resource(Res.string.map_route_km, NumberUtils.kilometers(meters))
    }

private fun TravelMode.label() = when (this) {
    TravelMode.Walking -> Res.string.map_mode_walking
    TravelMode.Cycling -> Res.string.map_mode_cycling
    TravelMode.Driving -> Res.string.map_mode_driving
}

private fun TravelMode.icon() = when (this) {
    TravelMode.Walking -> Icons.AutoMirrored.Filled.DirectionsWalk
    TravelMode.Cycling -> Icons.AutoMirrored.Filled.DirectionsBike
    TravelMode.Driving -> Icons.Default.DirectionsCar
}

private const val COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

/** A loaded route, one loading, estimated times, and one over an hour by car. */
internal class RouteSummaryPreviewStates : PreviewParameterProvider<RouteSummaryPreview> {
    override val values = sequenceOf(
        RouteSummaryPreview(TravelMode.Walking, durationSeconds = 2_700, distanceMeters = 3_400, isEstimated = false, isLoading = false),
        RouteSummaryPreview(TravelMode.Cycling, durationSeconds = null, distanceMeters = null, isEstimated = false, isLoading = true),
        RouteSummaryPreview(TravelMode.Walking, durationSeconds = 2_900, distanceMeters = 3_100, isEstimated = true, isLoading = false),
        RouteSummaryPreview(TravelMode.Driving, durationSeconds = 4_800, distanceMeters = 86_000, isEstimated = false, isLoading = false),
    )
}

internal data class RouteSummaryPreview(
    val mode: TravelMode,
    val durationSeconds: Int?,
    val distanceMeters: Int?,
    val isEstimated: Boolean,
    val isLoading: Boolean,
)

@Preview
@Composable
private fun RouteSummaryPreview(@PreviewParameter(RouteSummaryPreviewStates::class) route: RouteSummaryPreview) {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            TravelModeToggle(mode = route.mode, onModeSelected = {}, modifier = Modifier.width(220.dp))
            RouteTotals(route.durationSeconds, route.distanceMeters, route.isEstimated, route.isLoading)
        }
    }
}

@Composable
private fun AlongTheWayPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        // An owner's or editor's, with Add; a viewer's, without.
        AlongTheWay(places = previewAlongTheWay, dayNumber = 2, onOpen = {}, onAdd = {}, totals = { RouteTotals(9_240, 12_800, isEstimated = false, isLoading = false) })
        AlongTheWay(places = previewAlongTheWay, dayNumber = null, onOpen = {}, onAdd = {})
    }
}

@Preview
@Composable
private fun AlongTheWayPreview() {
    TripinglyTheme { AlongTheWayPreviewContent() }
}

@Preview
@Composable
private fun AlongTheWayDarkPreview() {
    TripinglyTheme(darkTheme = true) { AlongTheWayPreviewContent() }
}

@Preview
@Composable
private fun RouteAttributionPreview() {
    TripinglyTheme {
        RouteAttribution(
            text = "© openrouteservice.org | © OpenStreetMap contributors",
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}
