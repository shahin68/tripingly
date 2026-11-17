package com.falcon.tripingly.core.model.route

import com.falcon.tripingly.core.model.place.CategoryCount
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.trip.GeoPoint
import kotlin.time.Instant

/** The entitlement in `GET /me` that unlocks routes along streets: Tripinly Pro. */
const val ROUTES_ENTITLEMENT = "routes"

/** How the route is travelled; [Transit] (public transport) only where the server covers it ([TransitArea]). */
enum class TravelMode { Walking, Cycling, Driving, Transit }

/** A route through a day's stops in order, or from one point to another. */
data class Route(
    /** The line to draw, from the start to the end. */
    val points: List<GeoPoint>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    /** One per pair of stops in a row; one leg without stop IDs from A to B. */
    val legs: List<RouteLeg>,
    /** By public transport: when it sets off and arrives; null along streets. */
    val departure: Instant? = null,
    val arrival: Instant? = null,
    /** By public transport: changes in all. */
    val transfers: Int = 0,
    /** By public transport: the date is past the published timetable, so the times are the same weekday's this week. */
    val isTypicalTimes: Boolean = false,
)

data class RouteLeg(
    val fromMarkerId: String?,
    val toMarkerId: String?,
    val distanceMeters: Int,
    val durationSeconds: Int,
    /** By public transport: its walks and rides in order; empty along streets. */
    val steps: List<TransitStep> = emptyList(),
)

/** One walk or ride of a public transport route. */
data class TransitStep(
    val isRide: Boolean,
    /** Null on foot. */
    val vehicle: TransitVehicle?,
    /** The line ridden, null on foot. */
    val line: TransitLine?,
    /** Where the ride is heading. */
    val headsign: String?,
    val from: TransitStop,
    val to: TransitStop,
    /** Stops passed between getting on and off. */
    val stops: Int,
    val distanceMeters: Int,
    val durationSeconds: Int,
    /** Its part of the route's line. */
    val points: List<GeoPoint>,
)

enum class TransitVehicle { Tram, Subway, Bus, Train, Ferry, CableCar, Other }

/** A line's name and its own colours as ARGB, null when the timetable has none. */
data class TransitLine(val name: String, val color: Long?, val textColor: Long?)

/** Where a step starts or ends; [name] is null at the route's own ends. */
data class TransitStop(val name: String?, val location: GeoPoint, val time: Instant)

/** When a public transport route leaves, or arrives by. */
data class TransitTime(val at: Instant, val isArriveBy: Boolean = false)

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
    /** "© openrouteservice.org | © OpenStreetMap contributors" (or the timetables' credit), shown with the route. */
    val attribution: String,
    /** How many places of each kind the route passes, whatever kinds were asked for, most first: the filter chips. */
    val alongTheWayCounts: List<CategoryCount> = emptyList(),
)

/** The best order for a day's stops; the first stop stays first. */
data class BestOrder(
    val markerIds: List<String>,
    /** By real travel times (Tripinly Pro); otherwise by straight-line distance. */
    val byTravelTime: Boolean,
    /** Minutes saved against the current order, by travel times only. */
    val savedMinutes: Int?,
)
