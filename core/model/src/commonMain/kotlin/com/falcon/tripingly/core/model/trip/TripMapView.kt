package com.falcon.tripingly.core.model.trip

import com.falcon.tripingly.core.model.route.TravelMode

/**
 * How a trip's map was left: the open day, the Route button and the travel mode. The trip opens like this again,
 * also after the app was closed, framed on that day. The camera, the selected stop and open sheets are not part of it.
 */
data class TripMapView(
    val dayId: String,
    val isRouteShown: Boolean,
    val travelMode: TravelMode,
)
