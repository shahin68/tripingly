package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.routing.RouteErrorCodes
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_around
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Around (Tripinly Pro, Shahin 2026-11-17): the places within a ring around the open pin, listed in the plan sheet and
 * purple and clear on the map while everything else dims, the map otherwise left as it was: a route stays on, so ✕ has
 * nothing to bring back (Shahin, 2026-11-21). The ring's handle sets its size; chips pick kinds. A route coming on
 * closes Around. Without Pro the paywall opens.
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
            // A route coming on closes Around, also when another route was already on (Around leaves it as it is): the
            // route's day line, start or ends changing while one shows.
            context.model.map { Triple(it.route.isDayRouteShown, it.route.start, it.route.between) }.distinctUntilChanged().drop(1)
                .collect { if (context.model.value.route.isActive) dismiss() }
        }
    }

    /**
     * The Around button on a pin's card or details: the ring opens around it (on another pin: it moves there), and the
     * card and details close so the ring can be reached. Tapped again on the pin it is around, it closes as ✕ does, the
     * card staying (Shahin, 2026-11-17).
     */
    fun open() {
        if (!hasRoutes()) return showAroundIsPro()
        val (center, pinPlaceId, pinPlace) = selectedPin() ?: return
        if (context.model.value.around?.center == center) return close()
        context.update {
            it.copy(
                // Moved to another pin, it keeps what was before it first opened.
                around = AroundModel(
                    center = center,
                    pinPlaceId = pinPlaceId,
                    pinPlace = pinPlace,
                    pinName = selectedPinName(),
                    radiusMeters = it.around?.radiusMeters ?: it.fittingRadius(),
                    categories = it.around?.categories.orEmpty(),
                    wasFilterOpen = it.around?.wasFilterOpen ?: it.placeFilter.isOpen,
                ),
                placeFilter = it.placeFilter.copy(isOpen = false),
                selection = null,
                sheet = it.sheet.takeUnless { sheet -> sheet is Sheet.Details },
            )
        }
        load()
        showWholePlan()
    }

    /** The ✕ of the Around box: the map shows as before, untouched meanwhile but for the filter. */
    fun close() {
        loadJob?.cancel()
        asked = null
        context.update { model ->
            val around = model.around ?: return@update model
            model.copy(around = null, placeFilter = model.placeFilter.copy(isOpen = around.wasFilterOpen))
        }
    }

    /** A route came on: Around goes, the route stays. */
    private fun dismiss() {
        loadJob?.cancel()
        asked = null
        context.update { it.copy(around = null) }
    }

    /** A chip: the clear button ([category] null) lists every kind again; a kind tapped is the only one picked. */
    fun toggleCategory(category: PlaceCategory?) {
        update { it.copy(categories = if (category == null) emptySet() else it.categories.pickedAlone(category)) }
        load()
    }

    /**
     * The ring's handle dragged: the ring follows at once; its places are asked for only once the finger lets go
     * ([isFinal]), so a drag is one request however far it goes (Shahin, 2026-11-17).
     */
    fun setRadius(meters: Int, isFinal: Boolean) {
        update { it.copy(radiusMeters = meters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)) }
        if (isFinal) load()
    }

    /** What the places shown (or on their way) were asked for: the same ask again isn't sent. */
    private var asked: AroundAsk? = null

    private data class AroundAsk(val center: GeoPoint, val radiusMeters: Int, val categories: Set<PlaceCategory>, val pinPlaceId: String?)

    /**
     * Asks for the places in the ring as it is now, unless they were just asked for the same; a newer ask cancels the
     * one still on its way (its cancellation ends quietly, see the API call).
     */
    private fun load() {
        val around = context.model.value.around ?: return
        val ask = AroundAsk(around.center, around.radiusMeters, around.categories, around.pinPlaceId)
        if (ask == asked) return
        asked = ask
        loadJob?.cancel()
        update { it.copy(isLoading = true) }
        loadJob = context.scope.launch {
            val result = placeRepository.around(ask.center, ask.radiusMeters, ask.categories, ask.pinPlaceId)
            val isNotPro = result is AppResult.Error && result.error.hasCode(RouteErrorCodes.PREMIUM_REQUIRED)
            context.update { model ->
                val current = model.around ?: return@update model
                when (result) {
                    is AppResult.Success -> model.copy(around = current.copy(result = result.data, isLoading = false))
                    is AppResult.Error -> {
                        asked = null
                        if (isNotPro) {
                            // Not Pro after all (Pro ended): the ring closes, and as every ask here follows a tap, the
                            // paywall opens (Shahin, 2026-11-21).
                            model.copy(around = null, placeFilter = model.placeFilter.copy(isOpen = current.wasFilterOpen))
                        } else {
                            // The last places stay; a note says the new ones didn't come. The same ask may be sent again.
                            model.copy(around = current.copy(isLoading = false), message = result.error.toUiText(Res.string.map_error_around))
                        }
                    }
                }
            }
            if (isNotPro) showAroundIsPro()
        }
    }

    /** Where the open pin is, its place's ID, and the place when the pin is one (an address from search has no ID). */
    private fun selectedPin(): Triple<GeoPoint, String?, MapPlace?>? = when (val selected = context.model.value.selection) {
        is Selection.Stop -> context.activeDay()?.markers?.firstOrNull { it.id == selected.markerId }?.let { Triple(it.location, it.placeId.ifEmpty { null }, null) }
        is Selection.Place -> Triple(selected.place.location, selected.place.id.ifEmpty { null }, selected.place.takeIf { it.id.isNotEmpty() })
        is Selection.NewSpot, null -> null
    }

    private fun selectedPinName(): String = when (val selected = context.model.value.selection) {
        is Selection.Stop -> context.activeDay()?.markers?.firstOrNull { it.id == selected.markerId }?.name.orEmpty()
        is Selection.Place -> selected.place.name
        is Selection.NewSpot, null -> ""
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
    }
}
