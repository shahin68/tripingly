package com.falcon.tripingly.core.model.trip

import kotlinx.datetime.LocalDate

/** A trip with its days, markers and members (`GET /trips/{id}`). */
data class TripDetails(
    val trip: Trip,
    /** In order; a trip always has at least one day. */
    val days: List<TripDay>,
    val members: List<TripMember>,
    /** Set when this trip was copied from another user's trip that still exists. */
    val copiedFrom: CopiedFrom? = null,
)

data class TripDay(
    val id: String,
    /** 0-based. */
    val position: Int,
    /** Null when the trip has no dates. */
    val date: LocalDate?,
    /** In visiting order. */
    val markers: List<TripMarker>,
)

data class TripMarker(
    val id: String,
    val tripId: String,
    val dayId: String,
    val placeId: String,
    val name: String,
    val location: GeoPoint,
    /** `HH:mm`, or null. */
    val time: String? = null,
    /** 0-based within its day. */
    val position: Int,
    val coverThumbUrl: String? = null,
    val photoCount: Int = 0,
)

data class GeoPoint(val lat: Double, val lng: Double)

data class TripMember(val user: UserSummary, val role: TripRole)

data class CopiedFrom(val tripId: String, val owner: UserSummary)
