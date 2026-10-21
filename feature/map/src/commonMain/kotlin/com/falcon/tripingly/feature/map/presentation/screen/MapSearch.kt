package com.falcon.tripingly.feature.map.presentation.screen

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.trip.GeoPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The search in the header, like the destination search: asks the server once typing pauses, ranked near the
 * map's center. One of our places or an address opens its card; a city or larger area only moves the map there.
 */
internal class MapSearch(
    private val context: MapContext,
    private val camera: MapCamera,
    private val selection: MapSelection,
    private val placeRepository: PlaceRepository,
) {
    private var searchJob: Job? = null

    /** The field replaces the trip's name; an open stop closes. */
    fun open() {
        selection.closeStop()
        context.update { it.copy(search = it.search ?: SearchModel()) }
    }

    /** A cleared field clears the results at once. */
    fun onQueryChanged(query: String) {
        update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        searchJob = context.scope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            update { it.copy(isSearching = true) }
            val center = camera.target
            val results = (placeRepository.search(query, GeoPoint(center.latitude, center.longitude)) as? AppResult.Success)?.data
            update { it.copy(results = results ?: it.results, isSearching = false) }
        }
    }

    fun onResultClick(result: PlaceSearchResult) {
        close()
        when {
            result.place != null -> selection.openPlace(result.place!!, result.type.zoom())
            result.type in ADDRESS_TYPES -> selection.openAddress(result)
            else -> camera.moveTo(Coordinates(result.location.lat, result.location.lng), result.type.zoom())
        }
    }

    fun close() {
        searchJob?.cancel()
        context.update { it.copy(search = null) }
    }

    /** Changes the open search; nothing once it's closed. */
    private fun update(change: (SearchModel) -> SearchModel) = context.update { model ->
        model.search?.let { model.copy(search = change(it)) } ?: model
    }

    private companion object {
        /** Typing has paused; see the search rule in 07-architecture. */
        const val SEARCH_DEBOUNCE_MILLIS = 300L

        /** Search results small enough to add as a stop. */
        val ADDRESS_TYPES = setOf(SearchResultType.Place, SearchResultType.House, SearchResultType.Street, SearchResultType.Other)
    }
}
