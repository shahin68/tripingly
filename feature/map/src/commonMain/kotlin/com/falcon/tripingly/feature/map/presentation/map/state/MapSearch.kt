package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.trip.GeoPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The search in the header, like the destination search: asks the server once typing pauses, ranked near the
 * map's center. One of our places or an address opens its card; a city or larger area only moves the map there.
 * The keyboard's search key, no result picked, searches the map instead: only the places named so show, like Google Maps
 * (Shahin, 2026-11-17), until the field's ✕ clears it or back closes the search, which puts the map back as it was.
 * While a route's start is picked it is the header's "to" field, and a result is where the route goes.
 */
internal class MapSearch(
    private val context: MapContext,
    private val camera: MapCamera,
    private val selection: MapSelection,
    private val pins: MapPins,
    private val placeRepository: PlaceRepository,
    /** While a route's start is picked, a result is where to go. */
    private val routeTo: (PlaceSearchResult) -> Unit,
) {
    private var searchJob: Job? = null

    init {
        // However the search goes (back, a result picked, a route taking the field over), the map shows all places again.
        context.scope.launch {
            context.model.map { it.search == null || it.route.isPicking }.distinctUntilChanged().filter { it }.collect { pins.setName(null) }
        }
    }

    /** The field replaces the trip's name; an open stop closes. */
    fun open() {
        selection.closeStop()
        context.update { it.copy(search = it.search ?: SearchModel()) }
    }

    /** A cleared field clears the results, and the map searched by name, at once. */
    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            update { it.copy(results = emptyList(), isSearching = false) }
            pins.setName(null)
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
        if (context.model.value.route.isPicking) return routeTo(result)
        when {
            result.place != null -> selection.openPlace(result.place!!, result.type.zoom())
            result.type in ADDRESS_TYPES -> selection.openAddress(result)
            else -> camera.moveTo(Coordinates(result.location.lat, result.location.lng), result.type.zoom())
        }
    }

    /**
     * The keyboard's search key: the results list goes and the map shows only the places named so. A name the server
     * can't search (under [MIN_NAME_LENGTH] letters) and the route's "to" field only close the keyboard.
     */
    fun submit() {
        val search = context.model.value.search ?: return
        val name = search.query.text.toString().trim()
        if (name.length < MIN_NAME_LENGTH || context.model.value.route.isPicking) return
        searchJob?.cancel()
        update { it.copy(results = emptyList(), isSearching = false) }
        pins.setName(name)
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

        /** The server searches the map by names of two letters or more. */
        const val MIN_NAME_LENGTH = 2

        /** Search results small enough to add as a stop. */
        val ADDRESS_TYPES = setOf(SearchResultType.Place, SearchResultType.House, SearchResultType.Street, SearchResultType.Other)
    }
}
