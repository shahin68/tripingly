package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.trip.JoinRequest
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripUpdate
import kotlinx.coroutines.flow.Flow

/**
 * Trips, their days and members. The server is the source of truth: flows read
 * the local cache, writes go to the API and update the cache once the server
 * has answered.
 */
interface TripRepository {
    /** My Trips (my own, shared with me and saved) from the cache, most recently updated first. */
    fun observeMyTrips(): Flow<List<Trip>>

    /** Reloads My Trips from the server and replaces the cached list. */
    suspend fun refreshMyTrips(): AppResult<Unit, DataError.Network>

    /**
     * One trip from the cache; it has no days until [refreshTrip] has loaded it once. Null when not cached.
     * While it's collected, changes others make come in over the live connection.
     */
    fun observeTrip(tripId: String): Flow<TripDetails?>

    /** Reloads one trip. `NOT_FOUND` (deleted, or no longer visible) also drops it from the cache. */
    suspend fun refreshTrip(tripId: String): AppResult<TripDetails, DataError.Network>

    suspend fun createTrip(trip: NewTrip): AppResult<TripDetails, DataError.Network>

    /** Owner only. New dates that would remove days with markers fail with `VALIDATION_FAILED` on `fields.endDate`. */
    suspend fun updateTrip(tripId: String, update: TripUpdate): AppResult<TripDetails, DataError.Network>

    /** Owner only. */
    suspend fun deleteTrip(tripId: String): AppResult<Unit, DataError.Network>

    /**
     * Saves someone else's public [trip] to My Trips, or removes it ([saved] false). It stays the
     * owner's live trip, read-only; there are no copies.
     */
    suspend fun setSaved(trip: Trip, saved: Boolean): AppResult<Unit, DataError.Network>

    /**
     * Asks the owner of someone else's public trip to let me edit it with them. Once the owner said
     * no, it fails with [TripErrorCodes.JOIN_REQUEST_DECLINED] (and the trip shows it).
     */
    suspend fun requestToJoin(tripId: String): AppResult<Unit, DataError.Network>

    /** Withdraws my pending request. */
    suspend fun cancelJoinRequest(tripId: String): AppResult<Unit, DataError.Network>

    /** Owner only: who asked to join, newest first. */
    suspend fun joinRequests(tripId: String, cursor: String? = null): AppResult<Page<JoinRequest>, DataError.Network>

    /** Owner only: the person becomes an editor (`LIMIT_REACHED` when the trip is full). */
    suspend fun acceptJoinRequest(tripId: String, requestId: String): AppResult<TripDetails, DataError.Network>

    /** Owner only: the person can't ask again. */
    suspend fun declineJoinRequest(tripId: String, requestId: String): AppResult<Unit, DataError.Network>

    /** Appends an empty day; a dated trip ends one day later. */
    suspend fun addDay(tripId: String): AppResult<TripDetails, DataError.Network>

    /**
     * Deletes a day with its markers. The last day can't be deleted. Editors may only delete a day
     * whose stops are all theirs. Without [confirm], a day with other people's photos, comments or
     * likes answers `STOP_HAS_OTHERS_CONTENT`.
     */
    suspend fun deleteDay(tripId: String, dayId: String, confirm: Boolean = false): AppResult<TripDetails, DataError.Network>

    /**
     * Owner only: adds friends as editors, several at once, all or nothing; the only way to add someone directly.
     * Refused usernames come back as `VALIDATION_FAILED` with `fields.usernames.<index>` = [TripErrorCodes.MEMBER_NOT_FOUND],
     * [TripErrorCodes.MEMBER_BLOCKED] or [TripErrorCodes.MEMBER_NOT_FRIEND]; a full trip as `LIMIT_REACHED`.
     */
    suspend fun addMembers(tripId: String, usernames: List<String>): AppResult<TripDetails, DataError.Network>

    /** Owner only: removes an editor. */
    suspend fun removeMember(tripId: String, userId: String): AppResult<TripDetails, DataError.Network>

    /** An editor leaves the trip; it disappears from My Trips. */
    suspend fun leaveTrip(tripId: String): AppResult<Unit, DataError.Network>
}
