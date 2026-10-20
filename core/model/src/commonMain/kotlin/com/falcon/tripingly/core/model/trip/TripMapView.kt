package com.falcon.tripingly.core.model.trip

import com.falcon.tripingly.core.model.route.TravelMode

/**
 * How a trip's map was left: the open day, where the camera was and the Route button. The trip opens
 * like this again, also after the app was closed. The selected stop and open sheets are not part of it.
 */
data class TripMapView(
    val dayId: String,
    val latitude: Double,
    val longitude: Double,
    val zoom: Float,
    val isRouteShown: Boolean,
    val travelMode: TravelMode,
)
