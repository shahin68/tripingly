package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.feature.map.presentation.component.placeClusters
import com.falcon.tripingly.feature.map.presentation.component.placePins
import kotlin.math.floor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Place pins load by map square once the camera rests: liked places first, then the best known, without the
 * trip's stops, at most as many as the server allows. The map loads the squares it shows by itself; the ring
 * of squares around the view loads too (the ones not loaded yet, in one request), so a pan finds its places ready.
 */
internal class MapPins(
    private val context: MapContext,
    private val placeRepository: PlaceRepository,
    private val appConfigRepository: AppConfigRepository,
    /** Where the pins are worked out, off the main thread. */
    private val computation: CoroutineDispatcher,
) {
    private var pinsJob: Job? = null
    private var prefetchJob: Job? = null
    /** Where the camera last rested, loaded when hidden places show again. */
    private var lastRest: Pair<GeoBounds, Float>? = null

    init {
        // Pins change when a square in view reloads with new places, and when stops come or go (a stop hides its place).
        context.scope.launch {
            placeRepository.changedSquares.collect { square ->
                if (context.model.value.pins.area?.squares?.contains(square) == true) show()
            }
        }
        context.scope.launch {
            context.trip.map(::stopPlaceIds).distinctUntilChanged().drop(1).collect { show() }
        }
    }

    /** While places are hidden nothing loads; the camera's last rest loads once they show again. */
    fun setHidden(hidden: Boolean) {
        context.update { it.copy(arePlacesHidden = hidden) }
        if (!hidden) lastRest?.let { (bounds, zoom) -> onCameraRest(bounds, zoom) }
    }

    fun onCameraRest(bounds: GeoBounds, zoom: Float) {
        lastRest = bounds to zoom
        if (context.model.value.arePlacesHidden) return
        val squares = PlaceSquare.covering(bounds, floor(zoom).toInt())
        pinsJob?.cancel()
        pinsJob = context.scope.launch {
            // Loaded together with the squares already kept; null while offline, so the pins stay.
            placeRepository.placesIn(squares)
            update { it.copy(area = PinArea(bounds, squares)) }
            show()
        }
        prefetchJob?.cancel()
        prefetchJob = context.scope.launch {
            delay(PREFETCH_DELAY_MILLIS)
            placeRepository.placesIn(PlaceSquare.covering(bounds, floor(zoom).toInt(), ring = 1))
        }
    }

    /** The pins of the squares the camera rests on, from what the repository keeps. */
    private suspend fun show() {
        val area = context.model.value.pins.area ?: return
        val squares = placeRepository.loadedPlacesIn(area.squares).values
        val hidden = stopPlaceIds(context.trip.value)
        val config = appConfigRepository.config.value
        val shown = context.model.value.pins.pins
        val (pins, clusters) = withContext(computation) {
            placePins(squares, area.bounds, hidden, config, shown) to placeClusters(squares, area.bounds)
        }
        update { it.copy(pins = pins, clusters = clusters) }
    }

    private fun update(change: (PinsModel) -> PinsModel) = context.update { it.copy(pins = change(it.pins)) }

    private companion object {
        /** Same wait as for search: the camera has rested. */
        const val PREFETCH_DELAY_MILLIS = 300L
    }
}

/** A place that is already a stop shows as the stop only. */
internal fun stopPlaceIds(details: TripDetails?): Set<String> =
    details?.days.orEmpty().flatMapTo(HashSet()) { day -> day.markers.map { it.placeId } }
