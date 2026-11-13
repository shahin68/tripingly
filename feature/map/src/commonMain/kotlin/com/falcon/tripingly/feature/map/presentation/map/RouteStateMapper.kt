package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.presentation.map.state.MY_LOCATION_ID
import com.falcon.tripingly.feature.map.presentation.map.state.RouteModel
import com.falcon.tripingly.feature.map.presentation.map.state.isGoingTo
import com.falcon.tripingly.feature.map.presentation.map.state.isRouteEnd
import com.falcon.tripingly.feature.map.presentation.map.state.isRouteTo
import kotlinx.collections.immutable.toImmutableList

/** The route card's summary of [plan] (a real one, or null while there is none). */
internal fun RouteModel.summary(plan: RoutePlan?, alongTheWay: List<AlongTheWayPlace>, selectedPlaceId: String?, addToDayNumber: Int?) = RouteSummaryState(
    mode = travelMode,
    durationSeconds = plan?.route?.durationSeconds,
    distanceMeters = plan?.route?.distanceMeters,
    isEstimated = isUnavailable || this.plan?.isEstimated == true,
    isLoading = isLoading,
    alongTheWay = alongTheWay.toImmutableList(),
    selectedPlaceId = selectedPlaceId,
    addToDayNumber = addToDayNumber,
)

/** The Go here and Route buttons of the pin [at]. */
internal fun RouteModel.pinRoutes(at: GeoPoint, hasLocation: Boolean) = PinRoutesState(
    isGoHereOn = isGoingTo(at),
    hasLocation = hasLocation,
    isRouteTo = isRouteTo(at),
    isRouteOn = isRouteEnd(at),
    mode = travelMode,
)

/**
 * The from/to bar: one end kept while the other is picked ("to" after Route from, "from" after its pill was tapped), or
 * the route between two points that shows.
 */
internal fun RouteModel.routeBar(): RouteBarState? {
    start?.let {
        val label = startLabel ?: UiText.Server(it.name)
        return if (isPickingFrom) RouteBarState(from = null, to = label, mode = travelMode) else RouteBarState(label, to = null, mode = travelMode)
    }
    val between = between ?: return null
    return RouteBarState(between.fromLabel ?: UiText.Server(between.from.name), between.toLabel ?: UiText.Server(between.to.name), travelMode)
}

/** The route's ends on the map (where I am has my own dot instead): the end kept while the other is picked, or both. */
internal fun RouteModel.endPlaces(): List<Pair<MapPlace, Boolean>> {
    val ends = start?.let { listOf(it to !isPickingFrom) } ?: between?.let { listOf(it.from to true, it.to to false) }.orEmpty()
    return ends.filter { (place, _) -> place.id != MY_LOCATION_ID }
}
