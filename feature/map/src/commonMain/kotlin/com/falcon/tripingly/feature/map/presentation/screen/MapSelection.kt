package com.falcon.tripingly.feature.map.presentation.screen

import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The tapped stop or place: the camera brings it to the middle, then its card opens (at once when it's already
 * there). A touch anywhere else closes it.
 */
internal class MapSelection(
    private val context: MapContext,
    private val camera: MapCamera,
    private val placeRepository: PlaceRepository,
) {
    private var placeDetailsJob: Job? = null
    /** Places whose first image is on its way for a stop card; see [loadStopPlaceImage]. */
    private val loadingStopPlaces = mutableSetOf<String>()
    /** The latest touch closed a place or stop, so its tap only closes it (see [onScreenTouch]). */
    var isClosingTouch = false
        private set

    val selectedMarkerId: String? get() = (context.model.value.selection as? Selection.Stop)?.markerId
    val selectedPlace: Selection.Place? get() = context.model.value.selection as? Selection.Place

    /** From the map or a chip. */
    fun selectMarker(marker: MapMarker) {
        closePlace()
        val zoom = camera.zoom
        val isThere = camera.isAt(GeoPoint(marker.position.latitude, marker.position.longitude), zoom)
        context.update { it.copy(selection = Selection.Stop(marker.id, isCardShown = isThere)) }
        camera.moveTo(marker.position, zoom)
        if (marker.coverPhotoId == null) loadStopPlaceImage(marker.placeId)
    }

    /** Shows the card with what the pin knows and adds the place's details. */
    fun openPlace(place: MapPlace, zoom: Float = camera.zoom) {
        val isThere = camera.isAt(place.location, zoom)
        context.update { it.copy(selection = Selection.Place(place, isCardShown = isThere)) }
        camera.moveTo(Coordinates(place.location.lat, place.location.lng), zoom)
        placeDetailsJob?.cancel()
        placeDetailsJob = context.scope.launch {
            placeRepository.place(place.id).onSuccess { details ->
                context.update { model ->
                    val selection = model.selection as? Selection.Place
                    if (selection?.place?.id == place.id) model.copy(selection = selection.copy(details = details)) else model
                }
            }
        }
    }

    /** An address from search has no place of ours yet: its card shows the address and adds it by name. */
    fun openAddress(result: PlaceSearchResult) {
        placeDetailsJob?.cancel()
        val place = MapPlace(
            id = "",
            name = result.name,
            category = PlaceCategory.Other,
            location = result.location,
            isTripinly = false,
            likeCount = 0,
            prominence = 0,
        )
        val zoom = result.type.zoom()
        val isThere = camera.isAt(result.location, zoom)
        context.update { it.copy(selection = Selection.Place(place, address = result, isCardShown = isThere)) }
        camera.moveTo(Coordinates(result.location.lat, result.location.lng), zoom)
    }

    fun closePlace() {
        placeDetailsJob?.cancel()
        context.update { if (it.selection is Selection.Place) it.copy(selection = null) else it }
    }

    fun closeStop() = context.update { if (it.selection is Selection.Stop) it.copy(selection = null) else it }

    /** Closes the stop if it's [markerId]'s. */
    fun closeStop(markerId: String) = context.update { if ((it.selection as? Selection.Stop)?.markerId == markerId) it.copy(selection = null) else it }

    fun openPlaceImage(index: Int?) = context.update { model ->
        val selection = model.selection as? Selection.Place ?: return@update model
        model.copy(selection = selection.copy(imageIndex = index))
    }

    /** The camera came to rest: the tapped place's or stop's card opens. Moving the map closes it already (see [onScreenTouch]). */
    fun onCameraRest() = context.update { model ->
        when (val selection = model.selection) {
            is Selection.Place -> model.copy(selection = selection.copy(isCardShown = true))
            is Selection.Stop -> model.copy(selection = selection.copy(isCardShown = true))
            null -> model
        }
    }

    /**
     * A finger went down anywhere but on the open card: an open place or stop closes. A tap that closes one
     * still opens another pin, but doesn't add a stop.
     */
    fun onScreenTouch() {
        isClosingTouch = context.model.value.selection != null
        closePlace()
        closeStop()
    }

    /** A stop without a cover shows its place's first image; asked for once per place. */
    private fun loadStopPlaceImage(placeId: String?) {
        if (placeId == null || placeId in context.model.value.stopPlaceImages || !loadingStopPlaces.add(placeId)) return
        context.scope.launch {
            placeRepository.place(placeId).onSuccess { details ->
                context.update { it.copy(stopPlaceImages = it.stopPlaceImages + (placeId to details.images.firstOrNull())) }
            }
            loadingStopPlaces -= placeId
        }
    }
}

/** Close enough to see what was picked: a country whole, a café with its street. */
internal fun SearchResultType.zoom(): Float = when (this) {
    SearchResultType.Country -> 5f
    SearchResultType.State -> 7f
    SearchResultType.County -> 9f
    SearchResultType.City -> 12f
    SearchResultType.District, SearchResultType.Locality -> 14f
    SearchResultType.Place, SearchResultType.House, SearchResultType.Street, SearchResultType.Other -> 17f
}
