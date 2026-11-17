package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_around
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Around (Tripinly Pro, Shahin 2026-11-17): the places within a ring around the open pin, listed in the plan sheet and
 * purple on the map while the other places turn to dots. The ring's handle sets its size; chips pick kinds. One thing
 * at a time: Around closes the route and a route closes Around. Without Pro the paywall opens.
 */
internal class MapAround(
    private val context: MapContext,
    private val placeRepository: PlaceRepository,
    private val hasRoutes: () -> Boolean,
    private val showAroundIsPro: () -> Unit,
    private val showWholePlan: () -> Unit,
) {
    private var loadJob: Job? = null

    init {
        context.scope.launch {
            context.model.map { it.route.isActive }.distinctUntilChanged().drop(1).collect { isActive -> if (isActive) dismiss() }
        }
    }

    /**
     * The Around button on a pin's card or details: the ring opens around it (on another pin: it moves there), and the
     * card and details close so the ring can be reached. Tapped again on the pin it is around, it closes as ✕ does, the
     * card staying (Shahin, 2026-11-17).
     */
    fun open() {
        if (!hasRoutes()) return showAroundIsPro()
        val (center, pinPlaceId) = selectedPin() ?: return
        if (context.model.value.around?.center == center) return close()
        context.update {
            it.copy(
                // Moved to another pin, it keeps what was before it first opened.
                around = AroundModel(
                    center = center,
                    pinPlaceId = pinPlaceId,
                    radiusMeters = it.around?.radiusMeters ?: it.fittingRadius(),
                    categories = it.around?.categories.orEmpty(),
                    routeBefore = it.around?.routeBefore ?: it.route,
                    wasFilterOpen = it.around?.wasFilterOpen ?: it.placeFilter.isOpen,
                ),
                route = it.route.copy(isDayRouteShown = false, start = null, startLabel = null, between = null, editing = null),
                placeFilter = it.placeFilter.copy(isOpen = false),
                selection = null,
                sheet = it.sheet.takeUnless { sheet -> sheet is Sheet.Details },
            )
        }
        load(waitForMore = false)
        showWholePlan()
    }

    /** The ✕ of the Around box: the map goes back to what it showed before, a route as it was too (its plan is cached). */
    fun close() {
        loadJob?.cancel()
        context.update { model ->
            val around = model.around ?: return@update model
            model.copy(around = null, route = around.routeBefore, placeFilter = model.placeFilter.copy(isOpen = around.wasFilterOpen))
        }
    }

    /** A route came on: Around goes, the route stays. */
    private fun dismiss() {
        loadJob?.cancel()
        context.update { it.copy(around = null) }
    }

    /** A chip: All ([category] null) lists every kind again; a kind is added or taken off. */
    fun toggleCategory(category: PlaceCategory?) {
        update { it.copy(categories = if (category == null) emptySet() else it.categories.toggled(category)) }
        load(waitForMore = false)
    }

    /** The ring's handle dragged: the ring follows at once, the places once the drag rests a moment. */
    fun setRadius(meters: Int) {
        update { it.copy(radiusMeters = meters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)) }
        load(waitForMore = true)
    }

    private fun load(waitForMore: Boolean) {
        loadJob?.cancel()
        update { it.copy(isLoading = true) }
        loadJob = context.scope.launch {
            if (waitForMore) delay(RADIUS_SETTLE_MILLIS)
            // What the ring is now, after the wait.
            val around = context.model.value.around ?: return@launch
            val result = placeRepository.around(around.center, around.radiusMeters, around.categories, around.pinPlaceId)
            context.update { model ->
                val current = model.around ?: return@update model
                when (result) {
                    is AppResult.Success -> model.copy(around = current.copy(result = result.data, isLoading = false))
                    // The last places stay; a note says the new ones didn't come.
                    is AppResult.Error -> model.copy(around = current.copy(isLoading = false), message = result.error.toUiText(Res.string.map_error_around))
                }
            }
        }
    }

    /** Where the open pin is, and its place: a stop of the open day, or a place (an address from search has none). */
    private fun selectedPin(): Pair<GeoPoint, String?>? = when (val selected = context.model.value.selection) {
        is Selection.Stop -> context.activeDay()?.markers?.firstOrNull { it.id == selected.markerId }?.let { it.location to it.placeId.ifEmpty { null } }
        is Selection.Place -> selected.place.location to selected.place.id.ifEmpty { null }
        is Selection.NewSpot, null -> null
    }

    /**
     * How big the ring first opens: [DEFAULT_RADIUS_METERS] at most, smaller when the map is zoomed in so the whole ring
     * and its handle are on screen (a quarter of the view's shorter side, which the pins' area widens by a little).
     */
    private fun MapModel.fittingRadius(): Int {
        val view = pins.area?.bounds ?: return DEFAULT_RADIUS_METERS
        val metersPerDegree = 111_320.0
        val height = (view.north - view.south) * metersPerDegree
        val width = (view.east - view.west) * metersPerDegree * cos((view.north + view.south) / 2 * PI / 180)
        val fits = (minOf(width, height) * FIT_SHARE).roundToInt() / ROUND_TO_METERS * ROUND_TO_METERS
        return fits.coerceIn(MIN_RADIUS_METERS, DEFAULT_RADIUS_METERS)
    }

    private fun update(change: (AroundModel) -> AroundModel) = context.update { model -> model.around?.let { model.copy(around = change(it)) } ?: model }

    companion object {
        /** The server's limits for the ring (GET /v1/places/around). */
        const val MIN_RADIUS_METERS = 50
        const val MAX_RADIUS_METERS = 5000
        /** A short walk; the ring opens this big the first time. */
        const val DEFAULT_RADIUS_METERS = 500
        /** The share of the view's shorter side the ring first spans as its radius, with room for its handle and tag. */
        const val FIT_SHARE = 0.25
        const val ROUND_TO_METERS = 10
        /** How long the handle rests before the places for its new size load. */
        const val RADIUS_SETTLE_MILLIS = 300L
    }
}
