package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.feature.map.presentation.component.MapMarker
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
    /** Places on their way for a stop card; see [loadStopPlace]. */
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
        loadStopPlace(marker.placeId)
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
            hotCount = 0,
            prominence = 0,
        )
        val zoom = result.type.zoom()
        val isThere = camera.isAt(result.location, zoom)
        context.update { it.copy(selection = Selection.Place(place, address = result, isCardShown = isThere)) }
        camera.moveTo(Coordinates(result.location.lat, result.location.lng), zoom)
    }

    fun closePlace() {
        placeDetailsJob?.cancel()
        context.update { if (it.selection is Selection.Place) it.unselected() else it }
    }

    fun closeStop() = context.update { if (it.selection is Selection.Stop) it.unselected() else it }

    /** Closes the stop if it's [markerId]'s. */
    fun closeStop(markerId: String) = context.update { if ((it.selection as? Selection.Stop)?.markerId == markerId) it.unselected() else it }

    /** Nothing selected, so the details sheet about it closes too (a deleted stop, another day). */
    private fun MapModel.unselected() = copy(selection = null, sheet = sheet.takeUnless { it is Sheet.Details })

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

    /**
     * A stop's place: where it's from (OpenStreetMap is credited), whether its cover is one of its Commons images (shown
     * with its credit), and its hours and website for the details sheet. Asked for once per place.
     */
    private fun loadStopPlace(placeId: String?) {
        if (placeId == null || placeId in context.model.value.stopPlaces || !loadingStopPlaces.add(placeId)) return
        context.scope.launch {
            placeRepository.place(placeId).onSuccess { details ->
                context.update { it.copy(stopPlaces = it.stopPlaces + (placeId to details)) }
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
