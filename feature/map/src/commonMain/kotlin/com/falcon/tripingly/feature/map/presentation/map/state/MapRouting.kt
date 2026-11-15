package com.falcon.tripingly.feature.map.presentation.map.state

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.routing.RouteErrorCodes
import com.falcon.tripingly.core.data.routing.RouteRepository
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.config.TransitArea
import com.falcon.tripingly.core.model.config.covers
import com.falcon.tripingly.core.model.route.TransitTime
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
import com.falcon.tripingly.feature.map.presentation.component.PinEnd
import com.falcon.tripingly.feature.map.presentation.map.TransitNote
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

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
    /** The cities with public transport routes, from the server's settings, which may arrive after the map opens. */
    private val transitAreas: StateFlow<List<TransitArea>>,
    private val clock: Clock = Clock.System,
) {
    /** What the route on the map was last asked for. */
    private var request: RouteRequest? = null

    init {
        context.scope.launch {
            combine(context.trip, context.model, transitAreas, ::requestOf).distinctUntilChanged().collectLatest(::load)
        }
        // A place tapped while routing keeps its pin until the route closes; then they all go back to what they were.
        context.scope.launch {
            context.model.map { model -> (model.selection as? Selection.Place)?.place?.takeIf { model.route.isActive } }
                .distinctUntilChanged()
                .collect { place -> if (place != null) update { route -> if (route.kept.any { it.id == place.id }) route else route.copy(kept = route.kept + place) } }
        }
        context.scope.launch {
            context.model.map { it.route.isActive }.distinctUntilChanged().collect { isActive -> if (!isActive) update { it.copy(kept = emptyList(), editing = null) } }
        }
        // The steps sheet goes with the public transport route it shows.
        context.scope.launch {
            context.model.map { it.route.isActive && it.route.travelMode == TravelMode.Transit }.distinctUntilChanged().collect { isTransit ->
                if (!isTransit) context.update { it.copy(sheet = it.sheet.takeUnless { sheet -> sheet == Sheet.TransitSteps }) }
            }
        }
    }

    fun toggleDayRoute() {
        val isShown = context.model.value.route.isDayRouteShown
        if (!isShown && !hasRoutes()) return showRoutesArePro()
        // One route at a time: the day's replaces a route between two pins, and a start picked waits no more.
        context.update {
            it.copy(
                route = if (isShown) it.route.copy(isDayRouteShown = false, isAnimated = true) else it.route.copy(isDayRouteShown = true, isAnimated = true, start = null, startLabel = null, between = null, editing = null),
                search = if (isShown || !it.route.isPicking) it.search else null,
            )
        }
        // Only a route coming on opens the plan; switching it off leaves the sheet as it is (Shahin, 2026-11-10).
        if (!isShown) showWholePlan()
    }

    /** The Route button's Retry after the route failed: asks for it again at once, the route stays on. */
    fun retryRoute() = update { it.copy(attempt = it.attempt + 1) }

    /**
     * Another way to travel. Public transport only routes inside one covered city (Vienna for now): elsewhere its dimmed
     * option only shows a note in the card, and the way picked stays (Shahin, 2026-11-15).
     */
    fun selectTravelMode(mode: TravelMode) {
        if (mode == TravelMode.Transit && !isTransitCovered(context.model.value)) return update { it.copy(transitNote = TransitNote.NotHere) }
        update { it.copy(travelMode = mode, transitNote = null) }
    }

    /** Public transport goes between the ends of the route that shows, or would show. */
    private fun isTransitCovered(model: MapModel): Boolean =
        requestOf(context.trip.value, model.copy(route = model.route.copy(travelMode = TravelMode.Transit)), transitAreas.value)?.transit?.isCovered == true

    /** "Leave at": the time picker opens on the time the route was asked for. */
    fun openLeaveAt() {
        if (context.model.value.route.travelMode == TravelMode.Transit) context.update { it.copy(dialog = Dialog.LeaveAt) }
    }

    /** The time picked: the route is asked for again, leaving then (or arriving by then) on the open day. */
    fun pickTransitTime(time: LocalTime, isArriveBy: Boolean) =
        context.update { it.copy(dialog = null, route = it.route.copy(transitPick = TransitPick(time, isArriveBy && it.route.between != null))) }

    /**
     * Go here on a pin's card: the route from where I am to the pin (on again: off). Without my location yet, the
     * permission is asked for where it can be and a note says why; tapping again then routes.
     */
    fun goHere() {
        val end = selectedEnd() ?: return
        if (!hasRoutes()) return showRoutesArePro()
        if (context.model.value.route.isGoingTo(end.place.location)) return closeRoute()
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
            route.isRouteEnd(at) -> closeRoute()
            route.isRouteTo(at) || route.isRouteFrom(at) -> pick(end.place, end.label)
            !hasRoutes() -> showRoutesArePro()
            else -> {
                selection.closePlace()
                selection.closeStop()
                // One route between two points at a time: a new start replaces the route before (Shahin, 2026-11-12).
                context.update {
                    it.copy(
                        route = it.route.copy(start = end.place, startLabel = end.label, between = null, editing = null),
                        // An empty "to" field, not a query searched before.
                        search = SearchModel(),
                    )
                }
            }
        }
    }

    /** A search result picked as the end being picked. */
    fun routeToResult(result: PlaceSearchResult) {
        // An address or a city isn't one of our places: it goes by its name.
        pick(result.place ?: stopPlace(addressId(result.location), result.location).copy(name = result.name), label = null)
    }

    /**
     * The keyboard's search in an end's field: the first result is picked. The name it opened with, untouched, keeps the
     * end as it was; nothing found yet keeps the field for another try.
     */
    fun submitEnd() {
        val model = context.model.value
        val search = model.search ?: return
        val query = search.query.text.toString()
        when {
            model.route.editing != null && query == editedName(model.route) -> cancelEdit()
            query.isNotBlank() -> search.results.firstOrNull()?.let(::routeToResult)
        }
    }

    /**
     * A tap on one end's pill in the route bar (Shahin, 2026-11-13): its pill becomes the search field, holding its name,
     * while the route, its line and both pins stay. A place typed and picked, or a pin's Route from/to here, replaces that
     * end; ✕ or back leaves it as it was.
     */
    fun editEnd(isFrom: Boolean) {
        val route = context.model.value.route
        if (route.between == null) return
        val editing = if (isFrom) RouteSide.From else RouteSide.To
        val name = editedName(route.copy(editing = editing))
        context.update {
            it.copy(
                route = it.route.copy(editing = editing),
                search = SearchModel(query = TextFieldState(name, TextRange(name.length))),
            )
        }
    }

    /** ✕ or back while an end is picked again: the end stays as it was and the keyboard goes. */
    private fun cancelEdit() = context.update { it.copy(route = it.route.copy(editing = null), search = null) }

    /**
     * The name the edited end's field opens with: a stop's title or the place's name; "Your location" is no name to search
     * by, so its field opens empty.
     */
    private fun editedName(route: RouteModel): String {
        val between = route.between ?: return ""
        val (place, label) = if (route.editing == RouteSide.From) between.from to between.fromLabel else between.to to between.toLabel
        return if (place.id == MY_LOCATION_ID) "" else (label as? UiText.Server)?.text ?: place.name
    }

    /**
     * A place picked for the end being picked: where the route goes from the start, or the end of a route picked again,
     * the other end staying.
     */
    private fun pick(place: MapPlace, label: UiText?) {
        val route = context.model.value.route
        val between = route.between
        when {
            between != null && route.editing == RouteSide.From -> showRoute(between.copy(from = place, fromLabel = label))
            between != null && route.editing == RouteSide.To -> showRoute(between.copy(to = place, toLabel = label))
            route.start == null -> Unit
            else -> showRoute(PointRoute(route.start, place, route.startLabel, label))
        }
    }

    /** The route bar's ⇅: the same two ends the other way round. */
    fun swapEnds() {
        val route = context.model.value.route.between ?: return
        showRoute(PointRoute(route.to, route.from, route.toLabel, route.fromLabel))
    }

    /**
     * The header's ✕ or back: while an end is picked again, that ends (the end stays as it was); otherwise the start, the
     * route between two pins and the "to" search close.
     */
    fun closeRouteBar() {
        if (context.model.value.route.editing != null) cancelEdit() else closeRoute()
    }

    /** The start, the route between two pins and the bar's search close. */
    private fun closeRoute() =
        context.update { it.copy(route = it.route.copy(start = null, startLabel = null, between = null, editing = null), search = null) }

    /** The open pin as an end of a route: a place as it is, a stop as the place it stands at, with its title. */
    private fun selectedEnd(): RouteEnd? = when (val selected = context.model.value.selection) {
        is Selection.Place -> RouteEnd(selected.place, null)
        is Selection.Stop -> context.activeDay()?.markers?.firstOrNull { it.id == selected.markerId }?.let { stop ->
            RouteEnd(stopPlace(stop.placeId ?: stop.id, stop.location), UiText.Server(stop.name))
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
        context.update { it.copy(route = it.route.copy(start = null, startLabel = null, between = route, editing = null), search = null) }
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

    /** An address from search as an end: no place of ours, so an id of its own from where it is. */
    private fun addressId(at: GeoPoint) = "$ADDRESS_ID_PREFIX${at.lat},${at.lng}"

    /**
     * The place at a route's end, for the server to leave out of the places along the way (Shahin, 2026-11-13): none for
     * where I am or an address, nor for a stop, as the trip's own places are left out anyway.
     */
    private fun MapPlace.endPlaceId(details: TripDetails?): String? = id.takeUnless {
        id == MY_LOCATION_ID || id.startsWith(ADDRESS_ID_PREFIX) || details?.days.orEmpty().any { day -> day.markers.any { it.id == id || it.placeId == id } }
    }

        private fun myLocation(me: Coordinates) = stopPlace(MY_LOCATION_ID, GeoPoint(me.latitude, me.longitude))

    private fun PinEnd.label(): UiText? = (this as? PinEnd.Stop)?.stop?.title

    /** The plan's ✕ on a route between two places. */
    fun closePointRoute() = context.update { it.copy(route = it.route.copy(between = null, editing = null), search = if (it.route.editing != null) null else it.search) }

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
            // Public transport has no travel times between every two stops: walking times order them.
            val mode = context.model.value.route.travelMode.takeUnless { it == TravelMode.Transit } ?: TravelMode.Walking
            val result = routeRepository.bestOrder(context.tripId, day, mode)
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

    private fun requestOf(details: TripDetails?, model: MapModel, areas: List<TransitArea>): RouteRequest? {
        val route = model.route
        val days = details?.days.orEmpty()
        val openDay = days.getOrNull(activeDayIndex(days.size, model.activeDayIndex))
        // By public transport: the time picked and the open day's date, which the route leaves on when it's ahead.
        // Covered is part of the request: the cities arriving after the map opened ask again.
        fun time(stops: List<GeoPoint>) = TransitRequest(openDay?.date, route.transitPick, areas.covers(stops)).takeIf { route.travelMode == TravelMode.Transit }
        route.between?.let {
            val ends = listOf(it.from.location, it.to.location)
            return RouteRequest.Between(it.from.location, it.to.location, route.travelMode, route.attempt, it.from.endPlaceId(details), it.to.endPlaceId(details), time(ends))
        }
        if (!route.isDayRouteShown) return null
        val day = openDay ?: return null
        if (day.markers.size < 2) return null
        return RouteRequest.Day(day.id, day.markers.map { it.id to it.location }, route.travelMode, route.attempt, time(day.markers.map { it.location }))
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
        // Public transport outside a covered city isn't asked for: a note says so, the day keeps its own lines.
        if (request.transit?.isCovered == false) {
            return update { it.copy(plan = null, isUnavailable = false, isLoading = false, transitAt = null, transitNote = TransitNote.NotHere) }
        }
        update { it.copy(isLoading = true, plan = it.plan.takeIf { isSameRoute }, isUnavailable = it.isUnavailable && isSameRoute, transitNote = null) }
        val time = request.transit?.let { transitTime(it, isBetween = request is RouteRequest.Between) }
        val result = when (request) {
            is RouteRequest.Day -> {
                val day = context.days().firstOrNull { it.id == request.dayId } ?: return update { it.copy(isLoading = false) }
                routeRepository.dayRoute(context.tripId, day, request.mode, time?.at)
            }
            is RouteRequest.Between -> routeRepository.route(request.from, request.to, request.mode, context.tripId, request.fromPlaceId, request.toPlaceId, time)
        }
        val transitNote = (result as? AppResult.Error)?.error?.let { error ->
            when {
                error.hasCode(RouteErrorCodes.TRANSIT_NO_COVERAGE) -> TransitNote.NotHere
                error.hasCode(RouteErrorCodes.TRANSIT_NO_CONNECTION) -> TransitNote.NoConnection
                else -> null
            }
        }
        context.update {
            when {
                result is AppResult.Success -> it.copy(
                    route = it.route.copy(
                        plan = result.data,
                        planMode = request.mode,
                        planStops = request.stops,
                        isUnavailable = false,
                        isLoading = false,
                        transitAt = time,
                    ),
                )
                // No public transport here or at this time: a note in the card, never a banner (Shahin, 2026-11-15).
                transitNote != null -> it.copy(route = it.route.copy(plan = null, isUnavailable = false, isLoading = false, transitAt = time, transitNote = transitNote))
                // Not Pro after all (the account changed): the route closes with the server's message.
                result is AppResult.Error -> if (result.error.hasCode(RouteErrorCodes.PREMIUM_REQUIRED)) {
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
                else -> it
            }
        }
    }

    /**
     * When public transport leaves (or arrives by, only from A to B): at the time picked, or the clock's time now, on the
     * open day when it's ahead, else today; nothing picked on a day not ahead leaves now (Shahin, 2026-11-15).
     */
    private fun transitTime(request: TransitRequest, isBetween: Boolean): TransitTime {
        val zone = TimeZone.currentSystemDefault()
        val now = clock.now()
        val local = now.toLocalDateTime(zone)
        val day = request.date?.takeIf { it > local.date }
        if (request.pick == null && day == null) return TransitTime(now)
        val at = LocalDateTime(day ?: local.date, request.pick?.time ?: local.time).toInstant(zone)
        return TransitTime(at, isArriveBy = isBetween && request.pick?.isArriveBy == true)
    }

    private fun update(change: (RouteModel) -> RouteModel) = context.update { it.copy(route = change(it.route)) }

    /** What a route is asked for; [subject] is the same for the same day or the same two places; a new [attempt] asks again. */
    private sealed interface RouteRequest {
        val subject: Any
        val mode: TravelMode
        /** By public transport: when it goes; null by any other way. */
        val transit: TransitRequest?
        /** The stops the route goes through in order. */
        val stops: List<GeoPoint>

        data class Day(
            val dayId: String,
            val markers: List<Pair<String, GeoPoint>>,
            override val mode: TravelMode,
            val attempt: Int,
            override val transit: TransitRequest?,
        ) : RouteRequest {
            override val subject get() = dayId
            override val stops get() = markers.map { it.second }
        }

        data class Between(
            val from: GeoPoint,
            val to: GeoPoint,
            override val mode: TravelMode,
            val attempt: Int,
            val fromPlaceId: String?,
            val toPlaceId: String?,
            override val transit: TransitRequest?,
        ) : RouteRequest {
            override val subject get() = from to to
            override val stops get() = listOf(from, to)
        }
    }
}

/**
 * The open day's [date] and the time picked: what a public transport route's time is worked out from as it loads; [isCovered]:
 * its stops are in one covered city.
 */
private data class TransitRequest(val date: LocalDate?, val pick: TransitPick?, val isCovered: Boolean)

/** How an address picked from search starts its id as a route's end. */
private const val ADDRESS_ID_PREFIX = "address:"
