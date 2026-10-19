package com.falcon.tripingly.core.model.route

import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.trip.GeoPoint

/** The entitlement in `GET /me` that unlocks routes along streets: Tripinly Pro. */
const val ROUTES_ENTITLEMENT = "routes"

/** How the route is travelled. No public transport. */
enum class TravelMode { Walking, Cycling, Driving }

/** A route through a day's stops in order, or from one point to another. */
data class Route(
    /** The line to draw, from the start to the end. */
    val points: List<GeoPoint>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    /** One per pair of stops in a row; one leg without stop IDs from A to B. */
    val legs: List<RouteLeg>,
)

data class RouteLeg(
    val fromMarkerId: String?,
    val toMarkerId: String?,
    val distanceMeters: Int,
    val durationSeconds: Int,
)

/** A place close to the route, not one of the trip's stops. */
data class AlongTheWayPlace(
    val place: MapPlace,
    val distanceFromRouteMeters: Int,
    /** Travel time from the start to the point of the route nearest the place. */
    val etaFromStartSeconds: Int,
)

/** What the map shows for a route. */
data class RoutePlan(
    /** Null for a day with fewer than two stops. */
    val route: Route?,
    /** Ordered along the route. */
    val alongTheWay: List<AlongTheWayPlace>,
    /** Real routing wasn't available: [route] is straight lines with estimated times, which the app doesn't draw. */
    val isEstimated: Boolean,
    /** "© openrouteservice.org | © OpenStreetMap contributors", shown with the route. */
    val attribution: String,
)

/** The best order for a day's stops; the first stop stays first. */
data class BestOrder(
    val markerIds: List<String>,
    /** By real travel times (Tripinly Pro); otherwise by straight-line distance. */
    val byTravelTime: Boolean,
    /** Minutes saved against the current order, by travel times only. */
    val savedMinutes: Int?,
)
