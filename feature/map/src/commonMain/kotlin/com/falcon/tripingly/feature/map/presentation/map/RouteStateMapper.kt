package com.falcon.tripingly.feature.map.presentation.map

import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.presentation.map.state.RouteModel
import com.falcon.tripingly.feature.map.presentation.map.state.isGoingTo
import com.falcon.tripingly.feature.map.presentation.map.state.isRouteEnd
import com.falcon.tripingly.feature.map.presentation.map.state.isRouteTo
import kotlinx.collections.immutable.toImmutableList

/** The route card's summary of [plan] (a real one, or null while there is none). */
internal fun RouteModel.summary(plan: RoutePlan?, alongTheWay: List<AlongTheWayPlace>, addToDayNumber: Int?) = RouteSummaryState(
    mode = travelMode,
    durationSeconds = plan?.route?.durationSeconds,
    distanceMeters = plan?.route?.distanceMeters,
    isEstimated = isUnavailable || this.plan?.isEstimated == true,
    isLoading = isLoading,
    alongTheWay = alongTheWay.toImmutableList(),
    addToDayNumber = addToDayNumber,
)

/** The Go here and Route buttons of the pin [at]. */
internal fun RouteModel.pinRoutes(at: GeoPoint, hasLocation: Boolean) = PinRoutesState(
    isGoHereOn = isGoingTo(at),
    hasLocation = hasLocation,
    isRouteTo = isRouteTo(at),
    isRouteOn = isRouteEnd(at),
)

/** The from/to bar: a start waiting for where to go, or the route between two points that shows. */
internal fun RouteModel.routeBar(): RouteBarState? {
    start?.let { return RouteBarState(startLabel ?: UiText.Server(it.name), to = null) }
    val between = between ?: return null
    return RouteBarState(between.fromLabel ?: UiText.Server(between.from.name), between.toLabel ?: UiText.Server(between.to.name))
}

/** Where the ring under the start pin goes: the start picked, or a route's start that isn't where I am. */
internal fun RouteModel.startRing(): GeoPoint? = start?.location ?: between?.takeUnless { it.isFromMe }?.from?.location
