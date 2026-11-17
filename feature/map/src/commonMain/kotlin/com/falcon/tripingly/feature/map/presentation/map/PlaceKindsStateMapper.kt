package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.feature.map.presentation.map.state.MapModel
import com.falcon.tripingly.feature.map.presentation.map.state.Selection
import com.falcon.tripingly.feature.map.presentation.map.state.isActive
import kotlinx.collections.immutable.toImmutableList

/*
 * The screen states of finding places by kind (Shahin, 2026-11-17): the map filter and Around a pin. The route card's
 * chips are built with the route ([categoryChips], RouteStateMapper.kt).
 */

/** The places around a pin for the plan sheet; [addToDayNumber] for an owner or editor. Null while Around is closed. */
internal fun MapModel.aroundState(addToDayNumber: Int?): AroundState? {
    val around = around ?: return null
    return AroundState(
        chips = categoryChips(around.result?.counts.orEmpty(), around.categories),
        places = around.result?.places.orEmpty().toImmutableList(),
        isLoading = around.isLoading,
        addToDayNumber = addToDayNumber,
        selectedPlaceId = (selection as? Selection.Place)?.place?.id,
    )
}

/** The filter circle, hidden while a route, Around or search shows (their own places are what counts then). */
internal fun MapModel.placeFilterState(): PlaceFilterState? {
    if (route.isActive || around != null || search != null) return null
    val picked = placeFilter.categories
    val chips = listOf(CategoryChip(null, null, isSelected = picked.isEmpty())) +
        FILTER_CATEGORIES.map { CategoryChip(it, null, isSelected = it in picked) }
    return PlaceFilterState(placeFilter.isOpen, isActive = picked.isNotEmpty(), chips.toImmutableList(), isLoading = placeFilter.isLoading)
}

/** The map filter's kinds, as the places' kinds are listed; Other is no kind to look for. */
private val FILTER_CATEGORIES = PlaceCategory.entries - PlaceCategory.Other
