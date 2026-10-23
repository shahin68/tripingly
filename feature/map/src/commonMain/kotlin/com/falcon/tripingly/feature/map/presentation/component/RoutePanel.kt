package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.NumberUtils
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.route.TravelMode
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
import kotlinx.collections.immutable.ImmutableList
import com.falcon.tripingly.core.ui.UiText
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** How to travel, and the route's time and length (or that routes aren't available right now). */
@Composable
fun RouteSummary(
    mode: TravelMode,
    durationSeconds: Int?,
    distanceMeters: Int?,
    isEstimated: Boolean,
    isLoading: Boolean,
    onModeSelected: (TravelMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
            TravelMode.entries.forEach { entry ->
                FilterChip(
                    selected = entry == mode,
                    onClick = { onModeSelected(entry) },
                    label = { Text(stringResource(entry.label())) },
                    leadingIcon = { Icon(entry.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    // The same color as this way's route line on the map.
                    colors = FilterChipDefaults.filterChipColors(
                        iconColor = entry.lineColor,
                        selectedContainerColor = entry.lineColor,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White,
                    ),
                )
            }
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        if (durationSeconds != null && distanceMeters != null) {
            Text(
                text = stringResource(Res.string.map_route_total, durationLabel(durationSeconds), distanceText(distanceMeters).asString()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        if (isEstimated && !isLoading) {
            Text(
                text = stringResource(Res.string.map_route_estimated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Places near the route, in the order the route passes them: when the route gets there and how far off it
 * they are. A tap opens the place; owners and editors add it to the open day ([dayNumber] is null otherwise).
 */
@Composable
fun AlongTheWay(
    places: ImmutableList<AlongTheWayPlace>,
    dayNumber: Int?,
    onOpen: (MapPlace) -> Unit,
    onAdd: (MapPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (places.isEmpty()) return
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.map_along_the_way),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            contentPadding = PaddingValues(vertical = MaterialTheme.spacing.extraSmall),
        ) {
            items(places, key = { it.place.id }) { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.widthIn(max = 240.dp).clickable { onOpen(item.place) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                        Icon(item.place.category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Column(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small, vertical = 6.dp).weight(1f, fill = false)) {
                            Text(item.place.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = stringResource(Res.string.map_along_the_way_item, durationLabel(item.etaFromStartSeconds), distanceText(item.distanceFromRouteMeters).asString()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        if (dayNumber != null) {
                            IconButton(onClick = { onAdd(item.place) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.map_along_add, dayNumber), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

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
