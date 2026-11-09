package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.routing.RouteErrorCodes
import com.falcon.tripingly.core.data.routing.RouteRepository
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_best_order_already
import com.falcon.tripingly.feature.map.generated.resources.map_error_best_order
import com.falcon.tripingly.feature.map.generated.resources.map_error_route
import com.falcon.tripingly.feature.map.generated.resources.map_route_my_location
import com.falcon.tripingly.feature.map.generated.resources.map_stop_title_format
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The route the map shows: the open day's (the Route button), or one from A to B, which takes its place while
 * it's open. Asked for again when the stops or their order change, here or from someone else, or another way to
 * travel is picked. Without a route the day keeps its own lines between the stops. Routes along streets are
 * Tripinly Pro ([hasRoutes]); the best order of a day's stops is free.
 */
internal class MapRouting(
    private val context: MapContext,
    private val camera: MapCamera,
    private val selection: MapSelection,
    private val routeRepository: RouteRepository,
    private val markerRepository: MarkerRepository,
    private val hasRoutes: () -> Boolean,
    /** Without Pro, a tap on a route opens the paywall. */
    private val showRoutesArePro: () -> Unit,
    /** A stop was added or a route asked for: the plan sheet opens all the way. */
    private val showWholePlan: () -> Unit,
) {
    /** What the route on the map was last asked for. */
    private var request: RouteRequest? = null

    init {
        context.scope.launch {
            combine(context.trip, context.model, ::requestOf).distinctUntilChanged().collectLatest(::load)
        }
    }

    fun toggleDayRoute() {
        val isShown = context.model.value.route.isDayRouteShown
        if (!isShown && !hasRoutes()) return showRoutesArePro()
        update { it.copy(isDayRouteShown = !isShown, isAnimated = true) }
        showWholePlan()
    }

    /** The Route button's Retry after the route failed: asks for it again at once, the route stays on. */
    fun retryRoute() = update { it.copy(attempt = it.attempt + 1) }

    fun selectTravelMode(mode: TravelMode) = update { it.copy(travelMode = mode) }

    /**
     * The route to the open place, from [startId] (one of [routeStarts]) or else the start guessed for the user (see
     * [guess]): both ends on screen.
     */
    fun routeToPlace(startId: String?) {
        val place = selection.selectedPlace?.place ?: return
        if (!hasRoutes()) return showRoutesArePro()
        val starts = routeStarts(place.location, context.model.value.location.knownPoint, context.activeDay()?.markers.orEmpty())
        val start = (if (startId != null) starts.firstOrNull { it.id == startId } else starts.guess()) ?: return
        val from = MapPlace(
            id = start.stop?.placeId ?: start.id,
            name = start.stop?.name.orEmpty(),
            category = PlaceCategory.Other,
            location = start.location,
            isTripinly = false,
            hotCount = 0,
            prominence = 0,
        )
        val fromLabel = when {
            start.stop == null -> UiText.Resource(Res.string.map_route_my_location)
            start.stop.name.isBlank() -> UiText.Resource(Res.string.map_stop_title_format, start.number ?: 0)
            else -> null
        }
        selection.closePlace()
        update { it.copy(between = PointRoute(from, place, fromLabel)) }
        showWholePlan()
        camera.frame(listOf(from.location, place.location).map { Coordinates(it.lat, it.lng) })
    }

    fun closePointRoute() = update { it.copy(between = null) }

    /** A place along the way becomes the open day's last stop; shows at once like a tap on the map. */
    fun addAlongTheWay(place: MapPlace) {
        val day = context.activeDay() ?: return
        if (!context.canEdit()) return
        context.scope.launch { markerRepository.addMarker(context.tripId, day.id, NewMarker(place.name, place.location, placeId = place.id)) }
        showWholePlan()
    }

    /** Asks the server for the best order of the open day's stops; it shows for the user to apply. */
    fun findBestOrder() {
        val day = context.activeDay() ?: return
        if (!context.canEdit() || context.model.value.route.isFindingBestOrder) return
        update { it.copy(isFindingBestOrder = true) }
        context.scope.launch {
            val result = routeRepository.bestOrder(context.tripId, day, context.model.value.route.travelMode)
            val current = context.activeDay()?.markers?.map { it.id }
            context.update {
                val model = it.copy(route = it.route.copy(isFindingBestOrder = false))
                when {
                    result is AppResult.Error -> model.copy(message = result.error.toUiText(Res.string.map_error_best_order))
                    (result as AppResult.Success).data.markerIds == current ->
                        model.copy(message = UiText.Note(UiText.Resource(Res.string.map_best_order_already)))
                    else -> model.copy(dialog = Dialog.BestOrder(result.data))
                }
            }
        }
    }

    /**
     * Saves the proposed order like any reorder: shown at once, sent in the background. A stop added since keeps
     * its place at the end.
     */
    fun applyBestOrder() {
        val order = (context.model.value.dialog as? Dialog.BestOrder)?.order ?: return
        val day = context.activeDay() ?: return
        context.update { it.copy(dialog = null) }
        if (!context.canEdit()) return
        val current = day.markers.map { it.id }
        val ids = order.markerIds.filter { it in current } + current.filter { it !in order.markerIds }
        context.scope.launch { markerRepository.reorderMarkers(context.tripId, day.id, ids) }
    }

    private fun requestOf(details: TripDetails?, model: MapModel): RouteRequest? {
        val route = model.route
        route.between?.let { return RouteRequest.Between(it.from.location, it.to.location, route.travelMode, route.attempt) }
        if (!route.isDayRouteShown) return null
        val days = details?.days.orEmpty()
        val day = days.getOrNull(activeDayIndex(days.size, model.activeDayIndex)) ?: return null
        if (day.markers.size < 2) return null
        return RouteRequest.Day(day.id, day.markers.map { it.id to it.location }, route.travelMode, route.attempt)
    }

    private suspend fun load(request: RouteRequest?) {
        val before = this.request
        this.request = request
        if (request == null) {
            update { it.copy(plan = null, isUnavailable = false, isLoading = false) }
            return
        }
        // The line stays while the same day or the same two places load again; another one's goes at once.
        val isSameRoute = before != null && before.subject == request.subject
        update { it.copy(isLoading = true, plan = it.plan.takeIf { isSameRoute }, isUnavailable = it.isUnavailable && isSameRoute) }
        val result = when (request) {
            is RouteRequest.Day -> {
                val day = context.days().firstOrNull { it.id == request.dayId } ?: return update { it.copy(isLoading = false) }
                routeRepository.dayRoute(context.tripId, day, request.mode)
            }
            is RouteRequest.Between -> routeRepository.route(request.from, request.to, request.mode, context.tripId)
        }
        context.update {
            when (result) {
                is AppResult.Success -> it.copy(
                    route = it.route.copy(
                        plan = result.data,
                        planMode = request.mode,
                        planStops = request.stops,
                        isUnavailable = false,
                        isLoading = false,
                    ),
                )
                // Not Pro after all (the account changed): the route closes with the server's message.
                is AppResult.Error -> if (result.error.hasCode(RouteErrorCodes.PREMIUM_REQUIRED)) {
                    it.copy(
                        route = it.route.copy(plan = null, isDayRouteShown = false, between = null, isLoading = false),
                        message = result.error.toUiText(Res.string.map_error_route),
                    )
                } else {
                    it.copy(
                        route = it.route.copy(plan = null, isUnavailable = true, isLoading = false),
                        // The note on the route says it already; anything else (the connection) is explained too.
                        message = if (result.error.hasCode(RouteErrorCodes.ROUTING_UNAVAILABLE)) it.message else result.error.toUiText(Res.string.map_error_route),
                    )
                }
            }
        }
    }

    private fun update(change: (RouteModel) -> RouteModel) = context.update { it.copy(route = change(it.route)) }

    /** What a route is asked for; [subject] is the same for the same day or the same two places; a new [attempt] asks again. */
    private sealed interface RouteRequest {
        val subject: Any
        val mode: TravelMode
        /** The stops the route goes through in order. */
        val stops: List<GeoPoint>

        data class Day(val dayId: String, val markers: List<Pair<String, GeoPoint>>, override val mode: TravelMode, val attempt: Int) : RouteRequest {
            override val subject get() = dayId
            override val stops get() = markers.map { it.second }
        }

        data class Between(val from: GeoPoint, val to: GeoPoint, override val mode: TravelMode, val attempt: Int) : RouteRequest {
            override val subject get() = from to to
            override val stops get() = listOf(from, to)
        }
    }
}
