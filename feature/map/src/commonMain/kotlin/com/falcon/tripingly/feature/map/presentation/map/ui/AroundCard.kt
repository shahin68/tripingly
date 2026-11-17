package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    // Like the route card: the box keeps showing what it had while it goes, its height easing with it.
    AnimatedContent(
        targetState = around,
        contentKey = { it != null },
        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform { _, _ -> tween(ROUTE_GROW_MILLIS, easing = RouteGrowEasing) } },
        contentAlignment = Alignment.TopStart,
        modifier = modifier,
        label = "around box",
    ) { shown ->
        // The gap above glides with the box.
        if (shown != null) Box(Modifier.padding(top = gap)) { AroundCard(shown, onAction) }
    }
}

@Composable
private fun AroundCard(around: AroundState, onAction: (MapAction) -> Unit) {
    val inset = MaterialTheme.spacing.small
    // The places arrive after the box opens (and change with the kinds and the ring): the box eases to their height
    // instead of jumping (Shahin 2026-11-17).
    Column(Modifier.fillMaxWidth().animateContentSize(tween(ROUTE_GROW_MILLIS, easing = RouteGrowEasing)).clip(MaterialTheme.shapes.large).background(planCardColor()).padding(vertical = inset)) {
        // The chips scroll under the fading edge (✕ is the plan's Route button now, Shahin 2026-11-17); a spinner glides
        // in at their end while the places load.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                CategoryChipRow(chips = around.chips, onToggle = { onAction(MapAction.Around.ToggleCategory(it)) })
            }
            AnimatedVisibility(
                visible = around.isLoading,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(end = inset).size(SpinnerBoxSize)) {
                    CircularProgressIndicator(modifier = Modifier.size(SpinnerSize), strokeWidth = 2.dp)
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
            val selectedIndex = around.places.indexOfFirst { it.place.id == around.selectedPlaceId }
            // A place picked on the map is the card in view, as along the way.
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
            val onScreen = remember { OnScreen() }
            LaunchedEffect(around.selectedPlaceId) { listState.reveal(selectedIndex, onScreen, toStart = true) }
            LazyRow(
                state = listState,
                // A little more room under the kinds than between the places, as along the way.
                modifier = Modifier.padding(top = inset + MaterialTheme.spacing.extraSmall).horizontalFadingEdge(listState).trackOnScreen(onScreen),
                contentPadding = PaddingValues(horizontal = inset),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                items(around.places, key = { it.place.id }) { item ->
                    NearPlaceCard(
                        place = item.place,
                        subtitle = distanceText(item.distanceMeters).asString(),
                        dayNumber = around.addToDayNumber,
                        isSelected = item.place.id == around.selectedPlaceId,
                        onOpen = { onAction(MapAction.Around.PlaceClick(it)) },
                        onAdd = { onAction(MapAction.Around.Add(it)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** The spinner's room, as tall as a chip. */
private val SpinnerBoxSize = 40.dp
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
