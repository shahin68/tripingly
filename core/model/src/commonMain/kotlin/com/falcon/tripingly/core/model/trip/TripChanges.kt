package com.falcon.tripingly.core.model.trip

import com.falcon.tripingly.core.model.account.TripVisibility
import kotlinx.datetime.LocalDate

/** A new trip. Without [visibility] the server uses the user's default. */
data class NewTrip(
    val name: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val visibility: TripVisibility? = null,
    val destination: Destination? = null,
)

/** Owner changes to a trip; null fields stay unchanged. */
data class TripUpdate(
    val name: String? = null,
    val dates: TripDates? = null,
    val visibility: TripVisibility? = null,
)

/**
 * New trip dates. The server adds empty days or removes trailing ones; it refuses
 * to remove days that still have markers.
 */
data class TripDates(val startDate: LocalDate, val endDate: LocalDate)

/** A marker the user drops on the map or picks from a search result or a place on the map. */
data class NewMarker(
    val name: String,
    val location: GeoPoint,
    val time: String? = null,
    /** One of our places; the server then takes its location from the place. */
    val placeId: String? = null,
)

/** Changes to a marker; null fields stay unchanged. [dayId] moves it to another day of the same trip. */
data class MarkerUpdate(
    val name: String? = null,
    val location: GeoPoint? = null,
    val dayId: String? = null,
)
