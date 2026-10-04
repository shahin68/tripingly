package com.falcon.tripingly.core.data.trips

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

    /** Inserts or updates one marker, e.g. the server's answer to a change, until the trip is next reloaded. */
    suspend fun saveMarker(marker: TripMarker)

    /** The marker's trip, or null when it isn't cached. */
    suspend fun tripIdOfMarker(markerId: String): String?

    suspend fun deleteMarker(markerId: String)

    /** Applies a day's marker order ([markerIds] in the new order). */
    suspend fun reorderMarkers(markerIds: List<String>)

    suspend fun clear()
}
