package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.model.trip.JoinRequestStatus
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMarker
import kotlinx.coroutines.flow.Flow

/** The trips cache, in domain terms. Room in the app, in memory in tests. */
internal interface TripLocalDataSource {
    fun observeMyTrips(): Flow<List<Trip>>

    fun observeTrip(tripId: String): Flow<TripDetails?>

    suspend fun trip(tripId: String): TripDetails?

    /** Replaces My Trips; trips missing from [trips] are dropped. */
    suspend fun replaceMyTrips(trips: List<Trip>)

    /** Replaces one trip with its days, markers and members. */
    suspend fun saveTrip(details: TripDetails)

    suspend fun deleteTrip(tripId: String)

    /** Saves or unsaves someone else's trip; one that isn't cached yet is cached from [trip]. */
    suspend fun saveSavedByMe(trip: Trip, saved: Boolean)

    suspend fun saveMyJoinRequest(tripId: String, status: JoinRequestStatus?)

    /** The owner answered one of the trip's join requests. */
    suspend fun removePendingJoinRequest(tripId: String)

    /** Inserts or updates markers. */
    suspend fun saveMarkers(markers: List<TripMarker>)

    /**
     * Sets the place the server matched to a marker, if the marker is still cached, with the place's hot marks
     * and its image as the cover (a stop added at a hot place shows them at once), and the name the server gave
     * it when the app added it without one.
     */
    suspend fun savePlaceOfMarker(marker: TripMarker)

    /** The marker's trip, or null when it isn't cached. */
    suspend fun tripIdOfMarker(markerId: String): String?

    suspend fun marker(markerId: String): TripMarker?

    /** A cached stop at the place, or null. */
    suspend fun markerAtPlace(placeId: String): TripMarker?

    /** Sets the hot count of every cached stop at the place, and whether I marked it hot unless [hotByMe] is null. */
    suspend fun savePlaceHot(placeId: String, hotCount: Int, hotByMe: Boolean?)

    /** Counts comments added to (positive [delta]) or deleted from a cached marker. */
    suspend fun addToCommentCount(markerId: String, delta: Int)

    suspend fun deleteMarkers(markerIds: List<String>)

    /** Applies a day's marker order ([markerIds] in the new order). */
    suspend fun reorderMarkers(markerIds: List<String>)

    suspend fun clear()
}
