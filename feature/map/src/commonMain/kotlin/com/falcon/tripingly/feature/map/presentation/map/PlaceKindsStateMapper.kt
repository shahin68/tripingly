package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.model.place.PlaceKinds
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
        pinName = around.pinName,
        chips = categoryChips(around.result?.counts.orEmpty(), around.categories),
        places = around.result?.places.orEmpty().toImmutableList(),
        isLoading = around.isLoading,
        addToDayNumber = addToDayNumber,
        selectedPlaceId = (selection as? Selection.Place)?.place?.id,
    )
}

/** The filter circle with the server's filter kinds ([kinds]), hidden while a route, Around or search shows (their own places are what counts then). */
internal fun MapModel.placeFilterState(kinds: PlaceKinds): PlaceFilterState? {
    if (route.isActive || around != null || search != null) return null
    val picked = placeFilter.categories
    val chips = kinds.forFilters.map { CategoryChip(it.category, null, isSelected = it.category in picked) }
    return PlaceFilterState(placeFilter.isOpen, isActive = picked.isNotEmpty(), chips.toImmutableList(), isLoading = placeFilter.isLoading)
}
