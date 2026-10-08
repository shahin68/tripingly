package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.trip.MarkerUpdate
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.TripMarker
import kotlinx.coroutines.flow.Flow

/**
 * Markers on a trip's days (owners and editors).
 *
 * Adding, changing, deleting, clearing a day and reordering show in [TripRepository.observeTrip]
 * at once and are sent to the server in the background, in order. A change the
 * server refuses, or that can't reach it, is undone on the device and reported
 * on [failures].
 */
interface MarkerRepository {
    /** Changes that were undone. Nothing is replayed to late collectors. */
    val failures: Flow<MarkerChangeFailure>

    /** Adds the marker at the end of the day with an ID chosen here, and returns it. */
    suspend fun addMarker(tripId: String, dayId: String, marker: NewMarker): TripMarker

    suspend fun updateMarker(markerId: String, update: MarkerUpdate)

    suspend fun deleteMarker(markerId: String)

    /** Deletes all of the day's markers, sent as one request. */
    suspend fun clearDay(tripId: String, dayId: String)

    /** [markerIds] is the day's complete marker list in the new order. */
    suspend fun reorderMarkers(tripId: String, dayId: String, markerIds: List<String>)

    /** Shows a failed change again and sends it once more. */
    suspend fun retry(failure: MarkerChangeFailure)

    /** Copies someone's marker into a day of one of my trips; waits for the server. */
    suspend fun copyMarker(markerId: String, targetDayId: String): AppResult<TripMarker, DataError.Network>
}
