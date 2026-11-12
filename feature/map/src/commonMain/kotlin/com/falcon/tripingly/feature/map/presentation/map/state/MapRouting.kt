package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.routing.RouteErrorCodes
import com.falcon.tripingly.core.data.routing.RouteRepository
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceSearchResult
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
import com.falcon.tripingly.feature.map.generated.resources.map_route_need_location
import com.falcon.tripingly.feature.map.generated.resources.map_stop_title_format
import com.falcon.tripingly.feature.map.presentation.component.PinEnd
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
    /** Go here without my location: asks for the permission where it can, without moving the camera. */
    private val askForLocation: () -> Unit,
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
        // One route at a time: the day's replaces a route between two pins, and a start picked waits no more.
        context.update {
            it.copy(
                route = if (isShown) it.route.copy(isDayRouteShown = false) else it.route.copy(isDayRouteShown = true, isAnimated = true, start = null, startLabel = null, between = null),
                search = if (isShown || it.route.start == null) it.search else null,
            )
        }
        // Only a route coming on opens the plan; switching it off leaves the sheet as it is (Shahin, 2026-11-10).
        if (!isShown) showWholePlan()
    }

    /** The Route button's Retry after the route failed: asks for it again at once, the route stays on. */
    fun retryRoute() = update { it.copy(attempt = it.attempt + 1) }

    fun selectTravelMode(mode: TravelMode) = update { it.copy(travelMode = mode) }

    /**
     * Go here on a pin's card: the route from where I am to the pin (on again: off). Without my location yet, the
     * permission is asked for where it can be and a note says why; tapping again then routes.
     */
    fun goHere() {
        val end = selectedEnd() ?: return
        if (!hasRoutes()) return showRoutesArePro()
        if (context.model.value.route.isGoingTo(end.place.location)) return closeRouteBar()
        val me = context.model.value.location.current
        if (me == null) {
            askForLocation()
            return context.showMessage(UiText.Note(UiText.Resource(Res.string.map_route_need_location)))
        }
        showRoute(PointRoute(myLocation(me), end.place, UiText.Resource(Res.string.map_route_my_location), end.label))
    }

    /**
     * The Route button on a pin's card. Off: Route from makes the pin the start, closes its card and opens the
     * header's "to" search; while another pin is the start it says Route to here and shows the route. On (this pin is an
     * end of the route or the start): it closes them.
     */
    fun onRouteButton() {
        val end = selectedEnd() ?: return
        val route = context.model.value.route
        val at = end.place.location
        when {
            route.isRouteEnd(at) -> closeRouteBar()
            route.isRouteTo(at) -> route.start?.let { showRoute(PointRoute(it, end.place, route.startLabel, end.label)) }
            !hasRoutes() -> showRoutesArePro()
            else -> {
                selection.closePlace()
                selection.closeStop()
                // One route at a time: the newest replaces the one before (Shahin, 2026-11-12).
                context.update {
                    it.copy(
                        route = it.route.copy(start = end.place, startLabel = end.label, between = null, isDayRouteShown = false),
                        search = it.search ?: SearchModel(),
                    )
                }
            }
        }
    }

    /** A search result picked as where to go from the start. */
    fun routeToResult(result: PlaceSearchResult) {
        val route = context.model.value.route
        val from = route.start ?: return
        // An address or a city isn't one of our places: it goes by its name.
        val to = result.place ?: stopPlace("", result.location).copy(name = result.name)
        showRoute(PointRoute(from, to, route.startLabel))
    }

    /** The header's ✕ or back: the start, the route between two pins and the "to" search close. */
    fun closeRouteBar() = context.update { it.copy(route = it.route.copy(start = null, startLabel = null, between = null), search = null) }

    /** The open pin as an end of a route: a place as it is, a stop as the place it stands at, with its title. */
    private fun selectedEnd(): RouteEnd? = when (val selected = context.model.value.selection) {
        is Selection.Place -> RouteEnd(selected.place, null)
        is Selection.Stop -> context.activeDay()?.markers?.let { markers ->
            val index = markers.indexOfFirst { it.id == selected.markerId }
            markers.getOrNull(index)?.let { stop ->
                val label = if (stop.name.isBlank()) UiText.Resource(Res.string.map_stop_title_format, index + 1) else UiText.Server(stop.name)
                RouteEnd(stopPlace(stop.placeId ?: stop.id, stop.location), label)
            }
        }
        null -> null
    }

    private data class RouteEnd(val place: MapPlace, val label: UiText?)

    /** A pin pulled onto another: the route from the first to the second, both on screen. */
    fun routeBetweenPins(from: PinEnd, to: PinEnd) {
        if (!hasRoutes()) return showRoutesArePro()
        showRoute(PointRoute(from.toPlace(), to.toPlace(), from.label(), to.label()))
    }

    private fun showRoute(route: PointRoute) {
        selection.closePlace()
        selection.closeStop()
        context.update { it.copy(route = it.route.copy(start = null, startLabel = null, between = route, isDayRouteShown = false), search = null) }
        showWholePlan()
        camera.frame(listOf(route.from.location, route.to.location).map { Coordinates(it.lat, it.lng) })
    }

    private fun PinEnd.toPlace(): MapPlace = when (this) {
        is PinEnd.Place -> place
        is PinEnd.Stop -> stopPlace(stop.placeId ?: stop.id, GeoPoint(stop.position.latitude, stop.position.longitude))
    }

    /** A stop, or where I am, as an end of a route; its name comes with the route as a label. */
    private fun stopPlace(id: String, location: GeoPoint) =
        MapPlace(id = id, name = "", category = PlaceCategory.Other, location = location, isTripinly = false, hotCount = 0, prominence = 0)

    private fun myLocation(me: Coordinates) = stopPlace(MY_LOCATION_ID, GeoPoint(me.latitude, me.longitude))

    private fun PinEnd.label(): UiText? = (this as? PinEnd.Stop)?.stop?.title

    /** The plan's ✕ on a route between two places. */
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
