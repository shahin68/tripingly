package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.presentation.map.state.MY_LOCATION_ID
import com.falcon.tripingly.feature.map.presentation.map.state.RouteModel
import com.falcon.tripingly.feature.map.presentation.map.state.RouteSide
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
 * The from/to bar: the start while "to" is picked after Route from, or the route between two points that shows, the end
 * whose pill was tapped being its field.
 */
internal fun RouteModel.routeBar(): RouteBarState? {
    start?.let {
        val label = startLabel ?: UiText.Server(it.name)
        return RouteBarState(label, to = null, mode = travelMode)
    }
    val between = between ?: return null
    // The end picked again is its field; its place stays an end on the map until another is picked.
    return RouteBarState(
        from = (between.fromLabel ?: UiText.Server(between.from.name)).takeIf { editing != RouteSide.From },
        to = (between.toLabel ?: UiText.Server(between.to.name)).takeIf { editing != RouteSide.To },
        mode = travelMode,
        isEditing = editing != null,
    )
}

/** The route's ends on the map (where I am has my own dot instead): the end kept while the other is picked, or both. */
internal fun RouteModel.endPlaces(): List<Pair<MapPlace, Boolean>> {
    val ends = start?.let { listOf(it to true) } ?: between?.let { listOf(it.from to true, it.to to false) }.orEmpty()
    return ends.filter { (place, _) -> place.id != MY_LOCATION_ID }
}
