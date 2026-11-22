package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.PlaceSearch
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.feature.map.presentation.map.metersTo
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
    private var fitJob: Job? = null

    /** Where the map searched by name was fitted to, for the fit button while it shows. */
    private var namedTargets: List<Coordinates> = emptyList()

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
        fitJob?.cancel()
        if (query.isBlank()) {
            update { it.copy(results = emptyList(), isSearching = false) }
            pins.setName(null)
            return
        }
        searchJob = context.scope.launch {
            delay(SEARCH_DEBOUNCE)
            update { it.copy(isSearching = true) }
            val center = camera.target
            val found = (placeRepository.search(query, GeoPoint(center.latitude, center.longitude)) as? AppResult.Success)?.data
            update { it.copy(results = found?.results ?: it.results, city = found?.city ?: it.city, isSearching = false) }
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
     * The keyboard's search key: the results list goes and the map shows only the places named so. Once they've loaded,
     * the camera fits them and the listed places, zooming in or out, but only those in the city the map is in (the
     * server's box; without one, [CITY_RADIUS_METERS] around the map's centre). With nothing named so there it goes to
     * another city: the result nearest the map and the others within [CITY_RADIUS_METERS] of it (Shahin, 2026-11-20).
     * With no results at all it stays where it is. A finger moving the map first leaves it be
     * too. A name the server can't search (under [MIN_NAME_LENGTH] letters) and the route's "to" field only close the
     * keyboard.
     */
    fun submit() {
        val search = context.model.value.search ?: return
        val name = search.query.text.toString().trim()
        if (name.length < MIN_NAME_LENGTH || context.model.value.route.isPicking) return
        searchJob?.cancel()
        fitJob?.cancel()
        val center = camera.target.let { GeoPoint(it.latitude, it.longitude) }
        val listed = PlaceSearch(search.results, search.city).takeIf { search.results.isNotEmpty() }
        update { it.copy(results = emptyList(), isSearching = false) }
        pins.setName(name)
        namedTargets = emptyList()
        fitJob = context.scope.launch {
            val found = listed ?: (placeRepository.search(name, center) as? AppResult.Success)?.data
            context.model.first { !isNamed(name) || !it.placeFilter.isLoading }
            if (!isNamed(name)) return@launch
            val city = found?.city
            val inCity = { point: GeoPoint -> if (city != null) point in city else point.metersTo(center) <= CITY_RADIUS_METERS }
            val places = found?.results.orEmpty().filter { it.place != null }.map { it.location }
            val all = namedPins() + places
            val targets = all.filter(inCity).ifEmpty {
                val nearest = all.minByOrNull { it.metersTo(center) } ?: return@ifEmpty emptyList()
                all.filter { it.metersTo(nearest) <= CITY_RADIUS_METERS }
            }
            namedTargets = targets.map { Coordinates(it.lat, it.lng) }
            camera.frame(namedTargets)
        }
    }

    /**
     * The fit button while the map is searched by name: the camera fits the named places again, as the latest thing
     * done on the map (Shahin, 2026-11-22). False when no named search shows or its places haven't come yet.
     */
    fun frameNamed(): Boolean {
        val name = context.model.value.placeFilter.name ?: return false
        if (!isNamed(name) || namedTargets.isEmpty()) return false
        camera.frame(namedTargets)
        return true
    }

    /** The search is still open on the map searched by [name]. */
    private fun isNamed(name: String) = context.model.value.let { it.search != null && it.placeFilter.name == name }

    /** Where the search's results are on the map (the other pins stay, dimmed). */
    private fun namedPins() = context.model.value.pins.pins.filter { it.isSearchResult }.map { it.place.location }

    /** A finger moved the map before the results came: it stays where the finger left it. */
    fun onUserMove() {
        fitJob?.cancel()
    }

    fun close() {
        searchJob?.cancel()
        fitJob?.cancel()
        context.update { it.copy(search = null) }
    }

    /** Changes the open search; nothing once it's closed. */
    private fun update(change: (SearchModel) -> SearchModel) = context.update { model ->
        model.search?.let { model.copy(search = change(it)) } ?: model
    }

    private companion object {
        /** Typing has paused; see the search rule in 07-architecture. */
        val SEARCH_DEBOUNCE = 300.milliseconds

        /** About a big city's reach: the map's city when the server doesn't know it, and the other city's results. */
        const val CITY_RADIUS_METERS = 25_000

        /** The server searches the map by names of two letters or more. */
        const val MIN_NAME_LENGTH = 2

        /** Search results small enough to add as a stop. */
        val ADDRESS_TYPES = setOf(SearchResultType.Place, SearchResultType.House, SearchResultType.Street, SearchResultType.Other)
    }
}
