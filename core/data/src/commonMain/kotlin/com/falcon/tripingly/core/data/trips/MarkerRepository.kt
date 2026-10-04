package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.trip.MarkerUpdate
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.TripMarker

/**
 * Markers on a trip's days (owners and editors). Changes show up in
 * [TripRepository.observeTrip] once the server has accepted them; a reorder
 * shows at once and rolls back if the server refuses it.
 */
interface MarkerRepository {
    /** Adds the marker at the end of the day. */
    suspend fun addMarker(dayId: String, marker: NewMarker): AppResult<TripMarker, DataError.Network>

    suspend fun updateMarker(markerId: String, update: MarkerUpdate): AppResult<TripMarker, DataError.Network>

    suspend fun deleteMarker(markerId: String): AppResult<Unit, DataError.Network>

    /** [markerIds] is the day's complete marker list in the new order. */
    suspend fun reorderMarkers(tripId: String, dayId: String, markerIds: List<String>): AppResult<Unit, DataError.Network>

    /** Copies someone's marker into a day of one of my trips. */
    suspend fun copyMarker(markerId: String, targetDayId: String): AppResult<TripMarker, DataError.Network>
}
