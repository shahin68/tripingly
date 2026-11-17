package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.place.PlacesInView
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.feature.map.presentation.component.placeClusters
import com.falcon.tripingly.feature.map.presentation.component.placePins
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Place pins load by map square once the camera rests: liked places first, then the best known, without the
 * open day's stops, at most as many as the server allows. A place that is a stop on another day stays a place here,
 * so it can be added to this day too (Shahin, 2026-11-14: "visit the Eiffel tower multiple times in a trip"). The map loads the squares it shows by itself; the ring
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
    /** When the pins last followed a moving camera. */
    private var lastMoving: TimeSource.Monotonic.ValueTimeMark? = null
    /**
     * The closest zoom the camera rested at since the pins were last tidied. Until it rests a whole step farther out
     * than that, every pin still in view stays, also one the squares of this zoom don't list.
     */
    private var closestZoom: Float? = null
    /** Whether shown pins the squares no longer list stay: not once zoomed out a whole step. */
    private var keepUnlisted = false

    init {
        // Pins change when a square in view reloads with new places, and when the open day's stops come or go or another
        // day opens (a stop hides its place on its own day only).
        context.scope.launch {
            placeRepository.changedSquares.collect { square ->
                if (context.model.value.pins.area?.squares?.contains(square) == true) show()
            }
        }
        context.scope.launch {
            combine(context.trip, context.model.map { it.activeDayIndex }) { trip, _ -> trip }
                .map { trip -> context.activeDay().let { day -> stopPlaceIds(day) to tripStopPlaces(trip?.days.orEmpty(), day) } }
                .distinctUntilChanged().drop(1).collect { show() }
        }
    }

    /** While places are hidden nothing loads; the camera's last rest loads once they show again. */
    fun setHidden(hidden: Boolean) {
        context.update { it.copy(arePlacesHidden = hidden) }
        if (!hidden) lastRest?.let { (bounds, zoom) -> onCameraRest(bounds, zoom) }
    }

    /**
     * The map searched by [name] (null: the search closed): only places named so load and show, wherever the camera
     * goes, also while places are hidden; the kinds picked wait meanwhile and come back with the map as it was.
     */
    fun setName(name: String?) {
        if (context.model.value.placeFilter.name == name) return
        refilter({ it.copy(name = name) }) { place -> name == null || place.name.contains(name, ignoreCase = true) }
    }

    /**
     * The map's filter picked [categories]: only places of those kinds load and show (none: every kind), other days'
     * stops too by their kind; the open day's stops always stay. At once the pins shown so far keep only those kinds, so
     * the map answers the tap (Shahin, 2026-11-17); the squares of the camera's last rest load again for the kinds
     * picked, the filter's circle turning meanwhile.
     */
    fun setFilter(categories: Set<PlaceCategory>) =
        refilter({ it.copy(categories = categories) }) { place -> categories.isEmpty() || place.category in categories }

    /** The filter changes: the pins shown keep what it [keeps] at once, the camera's last rest loads for the rest. */
    private fun refilter(change: (PlaceFilterModel) -> PlaceFilterModel, keeps: (MapPlace) -> Boolean) {
        val rest = lastRest
        context.update { model ->
            val filter = change(model.placeFilter)
            val isAll = filter.name == null && filter.categories.isEmpty()
            model.copy(
                placeFilter = filter.copy(isLoading = rest != null && !(model.arePlacesHidden && filter.name == null)),
                // A cluster counts every place: it waits for the filter's own squares. The area goes until they come, so a
                // square of the old filter arriving late (a prefetch, a reload) can't bring its pins back for a moment.
                pins = PinsModel(pins = model.pins.pins.filter { keeps(it.place) }, clusters = if (isAll) model.pins.clusters else emptyList()),
            )
        }
        closestZoom = null
        if (rest == null) return
        if (isHidden) {
            // Places hidden again (the search closed): only the route's places stay, nothing loads.
            pinsJob?.cancel()
            prefetchJob?.cancel()
            context.update { it.copy(placeFilter = it.placeFilter.copy(isLoading = false)) }
        } else {
            onCameraRest(rest.first, rest.second)
        }
    }

    private val categories get() = context.model.value.placeFilter.categories
    private val name get() = context.model.value.placeFilter.name

    /** Places are hidden and nothing loads, unless the map was searched by name. */
    private val isHidden get() = context.model.value.let { it.arePlacesHidden && it.placeFilter.name == null }

    fun onCameraRest(view: GeoBounds, zoom: Float) {
        lastRest = view to zoom
        if (isHidden) return
        val bounds = view.withPinMargin()
        val closest = closestZoom
        keepUnlisted = closest != null && zoom > closest - TIDY_ZOOM_OUT
        closestZoom = if (keepUnlisted && closest != null) maxOf(closest, zoom) else zoom
        val squares = PlaceSquare.covering(bounds, floor(zoom).toInt(), categories = categories, name = name)
        pinsJob?.cancel()
        pinsJob = context.scope.launch {
            // Loaded together with the squares already kept; null while offline, so the pins stay.
            placeRepository.placesIn(squares)
            update { it.copy(area = PinArea(bounds, squares)) }
            show()
            context.update { it.copy(placeFilter = it.placeFilter.copy(isLoading = false)) }
        }
        prefetchJob?.cancel()
        prefetchJob = context.scope.launch {
            delay(PREFETCH_DELAY_MILLIS)
            placeRepository.placesIn(PlaceSquare.covering(bounds, floor(zoom).toInt(), ring = 1, categories = categories, name = name))
        }
    }

    /**
     * The camera moves: new places show while it does, not only once it rests. At most every [MOVING_PINS_MILLIS], the
     * pins follow the view; squares not loaded yet load, without cancelling a load still on its way (a quick pan would
     * otherwise never finish one). While it moves pins only come, never go, so few markers change; the rest tidies up
     * and loads the ring around the view.
     */
    fun onCameraMoving(view: GeoBounds, zoom: Float) {
        if (isHidden) return
        val bounds = view.withPinMargin()
        val now = TimeSource.Monotonic.markNow()
        if (lastMoving?.let { now - it < MOVING_PINS_MILLIS.milliseconds } == true) return
        lastMoving = now
        if (pinsJob?.isActive == true) return
        val squares = PlaceSquare.covering(bounds, floor(zoom).toInt(), categories = categories, name = name)
        pinsJob = context.scope.launch {
            placeRepository.placesIn(squares)
            update { it.copy(area = PinArea(bounds, squares)) }
            show(addOnly = true)
        }
    }

    /** The pins of the squares the camera rests on, from what the repository keeps. */
    private suspend fun show(addOnly: Boolean = false) {
        val area = context.model.value.pins.area ?: return
        val day = context.activeDay()
        // Other days' stops come along as places: a custom spot only on this private trip is in no square. With their
        // own kind, they follow the map's filter like any place (Shahin, 2026-11-17).
        val square = area.squares.firstOrNull()
        val picked = square?.categories.orEmpty()
        val named = square?.name
        val otherStops = tripStopPlaces(context.days(), day)
            .filter { (picked.isEmpty() || it.category in picked) && (named == null || it.name.contains(named, ignoreCase = true)) }
        val squares = placeRepository.loadedPlacesIn(area.squares).values + PlacesInView(otherStops, emptyList())
        val hidden = stopPlaceIds(day)
        val config = appConfigRepository.config.value
        val shown = context.model.value.pins
        val (pins, clusters) = withContext(computation) {
            val pins = placePins(squares, area.bounds, hidden, config, shown = shown.pins, keepUnlisted = keepUnlisted)
            val clusters = placeClusters(squares, area.bounds)
            if (!addOnly) return@withContext pins to clusters
            val pinIds = pins.mapTo(HashSet()) { it.place.id }
            val clusterSpots = clusters.mapTo(HashSet()) { it.location }
            (pins + shown.pins.filter { it.place.id !in pinIds && it.place.id !in hidden }) to
                (clusters + shown.clusters.filter { it.location !in clusterSpots })
        }
        update { it.copy(pins = pins, clusters = clusters) }
    }

    private fun update(change: (PinsModel) -> PinsModel) = context.update { it.copy(pins = change(it.pins)) }

    private companion object {
        /** Same wait as for search: the camera has rested. */
        const val PREFETCH_DELAY_MILLIS = 300L

        /** How often the pins follow a moving camera. */
        const val MOVING_PINS_MILLIS = 250L

        /**
         * How far out the camera zooms before the pins the farther squares don't list leave. A whole step, so a pinch
         * that ends a little below a whole zoom (14.9 after 15) keeps them (Shahin, 2026-11-10).
         */
        const val TIDY_ZOOM_OUT = 1f
    }
}

/** A place that is already a stop of the open [day] shows as the stop only; on other days it is a place again. */
internal fun stopPlaceIds(day: TripDay?): Set<String> = day?.markers.orEmpty().mapTo(HashSet()) { it.placeId }

/** The places of the trip's stops on days other than [day], as places to add to it. */
internal fun tripStopPlaces(days: List<TripDay>, day: TripDay?): List<MapPlace> =
    days.asSequence()
        .filter { it.id != day?.id }
        .flatMap { it.markers }
        .distinctBy { it.placeId }
        .map { MapPlace(it.placeId, it.name, it.category ?: PlaceCategory.Other, it.location, isTripinly = false, hotCount = it.hotCount, prominence = 0) }
        .toList()

/**
 * Pins count as in view a little beyond the screen ([PIN_MARGIN_SHARE] of its width and height on each side), so a pin
 * comes before it shows and goes only once it's off screen, not at the edge (Shahin, 2026-11-10).
 */
internal fun GeoBounds.withPinMargin(): GeoBounds {
    val lat = (north - south) * PIN_MARGIN_SHARE
    val lng = (east - west) * PIN_MARGIN_SHARE
    return GeoBounds(maxOf(-90.0, south - lat), west - lng, minOf(90.0, north + lat), east + lng)
}

/** About a big pin's width beyond a phone screen's side, in portrait and landscape. */
private const val PIN_MARGIN_SHARE = 0.1
