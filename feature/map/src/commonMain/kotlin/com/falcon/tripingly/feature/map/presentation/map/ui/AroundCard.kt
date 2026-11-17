package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.horizontalFadingEdge
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceAround
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_around_close
import com.falcon.tripingly.feature.map.generated.resources.map_around_none
import com.falcon.tripingly.feature.map.presentation.map.AroundState
import com.falcon.tripingly.feature.map.presentation.map.CategoryChip
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/**
 * The places around a pin in the plan sheet, where the route card would be: the kinds as chips with ✕ at their end,
 * then the places as the along-the-way cards show them, with how far each is from the pin (the canvas, Shahin
 * 2026-11-17). It glides open and shut like the sheet's other rows.
 */
@Composable
internal fun AroundCardSlot(around: AroundState?, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier, gap: Dp = 0.dp) {
    val kept = remember { KeptAround() }
    if (around != null) kept.state = around
    AnimatedVisibility(
        visible = around != null,
        modifier = modifier,
        enter = expandVertically(tween(ROUTE_GROW_MILLIS, easing = RouteGrowEasing), expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(tween(ROUTE_GROW_MILLIS, easing = RouteGrowEasing), shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        // The gap above glides with the box.
        kept.state?.let { Box(Modifier.padding(top = gap)) { AroundCard(it, onAction) } }
    }
}

/** The last state the box showed, kept while it glides shut; not state, as the box recomposes with every new one anyway. */
private class KeptAround(var state: AroundState? = null)

@Composable
private fun AroundCard(around: AroundState, onAction: (MapAction) -> Unit) {
    val inset = MaterialTheme.spacing.small
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(planCardColor()).padding(vertical = inset)) {
        // The chips scroll under the fading edge up to ✕, which stays put at the end, as tall as a chip.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = inset)) {
            Box(Modifier.weight(1f)) {
                CategoryChipRow(chips = around.chips, onToggle = { onAction(MapAction.Around.ToggleCategory(it)) })
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(CloseSize)) {
                if (around.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(SpinnerSize), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { onAction(MapAction.Around.Close) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_around_close))
                    }
                }
            }
        }
        if (around.places.isEmpty()) {
            if (!around.isLoading) {
                Text(
                    text = stringResource(Res.string.map_around_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = inset, end = inset, top = inset),
                )
            }
        } else {
            val listState = rememberLazyListState()
            LazyRow(
                state = listState,
                // A little more room under the kinds than between the places, as along the way.
                modifier = Modifier.padding(top = inset + MaterialTheme.spacing.extraSmall).horizontalFadingEdge(listState),
                contentPadding = PaddingValues(horizontal = inset),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                items(around.places, key = { it.place.id }) { item ->
                    NearPlaceCard(
                        place = item.place,
                        subtitle = distanceText(item.distanceMeters).asString(),
                        dayNumber = around.addToDayNumber,
                        onOpen = { onAction(MapAction.Around.PlaceClick(it)) },
                        onAdd = { onAction(MapAction.Around.Add(it)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** ✕ as tall as a chip. */
private val CloseSize = 40.dp
private val SpinnerSize = 20.dp

@Composable
private fun AroundCardPreviewContent() {
    val place = { id: String, name: String, category: PlaceCategory ->
        MapPlace(id, name, category, GeoPoint(48.2, 16.37), isTripinly = false, hotCount = 0, prominence = 0)
    }
    PlanPreviewSheet {
        Column(Modifier.padding(MaterialTheme.spacing.medium)) {
            AroundCardSlot(
                AroundState(
                    chips = persistentListOf(CategoryChip(null, 7, isSelected = true), CategoryChip(PlaceCategory.Cafe, 4, isSelected = false), CategoryChip(PlaceCategory.Museum, 3, isSelected = false)),
                    places = persistentListOf(PlaceAround(place("p1", "Café Sperl", PlaceCategory.Cafe), 120), PlaceAround(place("p2", "Albertina", PlaceCategory.Museum), 450)),
                    isLoading = false,
                    addToDayNumber = 2,
                    selectedPlaceId = null,
                ),
                onAction = {},
            )
        }
    }
}

@Preview
@Composable
private fun AroundCardPreview() {
    TripinglyTheme { AroundCardPreviewContent() }
}

@Preview
@Composable
private fun AroundCardDarkPreview() {
    TripinglyTheme(darkTheme = true) { AroundCardPreviewContent() }
}
