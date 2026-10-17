package com.falcon.tripingly.core.model.trip

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** A trip with its days, markers and members (`GET /trips/{id}`). */
data class TripDetails(
    val trip: Trip,
    /** In order; a trip always has at least one day. */
    val days: List<TripDay>,
    val members: List<TripMember>,
    /** My request to join someone else's trip; null when I have none (or once I'm a member). */
    val myJoinRequest: JoinRequestStatus? = null,
    /** Requests waiting for the owner; 0 for everyone else. */
    val pendingJoinRequestCount: Int = 0,
    /** Where the trip goes, if the owner picked a place; the map opens there while the trip has no stops. */
    val destination: Destination? = null,
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
    /** The cover photo's ID: images are cached by it, since its signed URL changes. */
    val coverPhotoId: String? = null,
    val coverThumbUrl: String? = null,
    val photoCount: Int = 0,
    /** The place's likes: a stop's heart likes its place, so every stop there shows the same count. */
    val likeCount: Int = 0,
    val likedByMe: Boolean = false,
    /** False for a custom spot that is only in private trips: it has no heart. */
    val canLike: Boolean = true,
    val commentCount: Int = 0,
    /** Who added the stop; null after they deleted their account. Only they and the trip's owner may delete it. */
    val createdById: String? = null,
)

data class GeoPoint(val lat: Double, val lng: Double)

/** A trip's destination, e.g. "Paris", picked from a place search. */
data class Destination(val name: String, val location: GeoPoint)

data class TripMember(val user: UserSummary, val role: TripRole)

enum class JoinRequestStatus {
    /** Waiting for the owner. */
    PENDING,

    /** The owner said no; it can't be sent again. */
    DECLINED,
}

/** Someone asking the owner to join a trip as an editor. */
data class JoinRequest(
    val id: String,
    val tripId: String,
    val user: UserSummary,
    val createdAt: Instant,
)
