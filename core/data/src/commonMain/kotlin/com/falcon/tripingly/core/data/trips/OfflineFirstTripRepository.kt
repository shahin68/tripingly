package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.JoinRequest
import com.falcon.tripingly.core.model.trip.JoinRequestStatus
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripUpdate
import com.falcon.tripingly.core.network.model.AddMemberDto
import com.falcon.tripingly.core.network.model.AddMembersDto
import com.falcon.tripingly.core.network.model.CreateTripDto
import com.falcon.tripingly.core.network.model.TripDto
import com.falcon.tripingly.core.network.model.UpdateTripDto
import com.falcon.tripingly.core.network.newIdempotencyKey
import com.falcon.tripingly.core.network.realtime.RealtimeClient
import com.falcon.tripingly.core.network.trips.TripsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

internal class OfflineFirstTripRepository(
    private val api: TripsApi,
    private val local: TripLocalDataSource,
    /** Marker changes still on their way, which a reload must not overwrite. */
    private val pending: PendingTripWrites,
    /** Watches an observed trip, so changes from others come in live. */
    private val realtime: RealtimeClient,
    /** The signed-in user's id, for leaving a trip. */
    private val currentUserId: () -> String?,
) : TripRepository {

    override fun observeMyTrips(): Flow<List<Trip>> = local.observeMyTrips()

    override suspend fun refreshMyTrips(): AppResult<Unit, DataError.Network> {
        val trips = mutableListOf<Trip>()
        var cursor: String? = null
        do {
            val page = when (val result = api.myTrips(cursor, PAGE_SIZE)) {
                is AppResult.Success -> result.data
                is AppResult.Error -> return result
            }
            page.items.mapTo(trips) { it.toTrip() }
            cursor = page.nextCursor
        } while (cursor != null)
        local.replaceMyTrips(trips)
        return AppResult.Success(Unit)
    }

    override fun observeTrip(tripId: String): Flow<TripDetails?> = local.observeTrip(tripId)
        .onStart { realtime.watchTrip(tripId) }
        .onCompletion { realtime.unwatchTrip(tripId) }

    override suspend fun refreshTrip(tripId: String): AppResult<TripDetails, DataError.Network> {
        val since = pending.version(tripId)
        return when (val result = api.trip(tripId)) {
            is AppResult.Success -> {
                val details = result.data.toDetails()
                // With a marker change still unsent, the server's copy is older than the device's;
                // the marker queue reloads the trip once it has sent everything.
                if (!pending.saveIfQuiet(tripId, since) { local.saveTrip(details) }) pending.reloadWhenSent(tripId)
                AppResult.Success(details)
            }
            is AppResult.Error -> {
                if (result.error.hasCode(TripErrorCodes.NOT_FOUND)) local.deleteTrip(tripId)
                result
            }
        }
    }

    override suspend fun createTrip(trip: NewTrip): AppResult<TripDetails, DataError.Network> =
        api.create(
            CreateTripDto(
                title = trip.name.trim(),
                startDate = trip.startDate?.toString(),
                endDate = trip.endDate?.toString(),
                visibility = trip.visibility?.let {
                    if (it == TripVisibility.PRIVATE) CreateTripDto.Visibility.PRIVATE else CreateTripDto.Visibility.PUBLIC
                },
                destination = trip.destination?.toDto(),
                memberUsernames = trip.memberUsernames.ifEmpty { null },
            ),
            newIdempotencyKey(),
        ).saved()

    override suspend fun updateTrip(tripId: String, update: TripUpdate): AppResult<TripDetails, DataError.Network> =
        api.update(
            tripId,
            UpdateTripDto(
                title = update.name?.trim(),
                startDate = update.dates?.startDate?.toString(),
                endDate = update.dates?.endDate?.toString(),
                visibility = update.visibility?.let {
                    if (it == TripVisibility.PRIVATE) UpdateTripDto.Visibility.PRIVATE else UpdateTripDto.Visibility.PUBLIC
                },
            ),
        ).saved()

    override suspend fun deleteTrip(tripId: String): AppResult<Unit, DataError.Network> {
        val result = api.delete(tripId)
        if (result is AppResult.Success || result.isNotFound()) local.deleteTrip(tripId)
        return result
    }

    override suspend fun setSaved(trip: Trip, saved: Boolean): AppResult<Unit, DataError.Network> {
        val result = if (saved) api.save(trip.id) else api.unsave(trip.id)
        if (result is AppResult.Success) local.saveSavedByMe(trip, saved)
        return result
    }

    override suspend fun requestToJoin(tripId: String): AppResult<Unit, DataError.Network> =
        when (val result = api.requestJoin(tripId)) {
            is AppResult.Success -> AppResult.Success(local.saveMyJoinRequest(tripId, JoinRequestStatus.PENDING))
            is AppResult.Error -> {
                if (result.error.hasCode(TripErrorCodes.JOIN_REQUEST_DECLINED)) {
                    local.saveMyJoinRequest(tripId, JoinRequestStatus.DECLINED)
                }
                result
            }
        }

    override suspend fun cancelJoinRequest(tripId: String): AppResult<Unit, DataError.Network> {
        val result = api.cancelJoinRequest(tripId)
        if (result is AppResult.Success) local.saveMyJoinRequest(tripId, null)
        return result
    }

    override suspend fun joinRequests(tripId: String, cursor: String?): AppResult<Page<JoinRequest>, DataError.Network> =
        when (val result = api.joinRequests(tripId, cursor, PAGE_SIZE)) {
            is AppResult.Success -> AppResult.Success(Page(result.data.items.map { it.toJoinRequest() }, result.data.nextCursor))
            is AppResult.Error -> result
        }

    override suspend fun acceptJoinRequest(tripId: String, requestId: String): AppResult<TripDetails, DataError.Network> =
        when (val result = api.acceptJoinRequest(requestId)) {
            // The members and the request count changed; reload the trip like adding a member does.
            is AppResult.Success -> refreshTrip(tripId)
            is AppResult.Error -> result
        }

    override suspend fun declineJoinRequest(tripId: String, requestId: String): AppResult<Unit, DataError.Network> {
        val result = api.declineJoinRequest(requestId)
        if (result is AppResult.Success) local.removePendingJoinRequest(tripId)
        return result
    }

    override suspend fun addDay(tripId: String): AppResult<TripDetails, DataError.Network> =
        when (val result = api.addDay(tripId, newIdempotencyKey())) {
            // The trip's end date moved too; reload it.
            is AppResult.Success -> refreshTrip(tripId)
            is AppResult.Error -> result
        }

    override suspend fun deleteDay(tripId: String, dayId: String, confirm: Boolean): AppResult<TripDetails, DataError.Network> =
        when (val result = api.deleteDay(dayId, confirm)) {
            is AppResult.Success -> refreshTrip(tripId)
            is AppResult.Error -> result
        }

    override suspend fun addMember(tripId: String, username: String): AppResult<TripDetails, DataError.Network> =
        when (val result = api.addMember(tripId, AddMemberDto(username = username.trim().removePrefix("@").lowercase()))) {
            is AppResult.Success -> refreshTrip(tripId)
            is AppResult.Error -> result
        }

    override suspend fun addMembers(tripId: String, usernames: List<String>): AppResult<TripDetails, DataError.Network> =
        when (val result = api.addMembers(tripId, AddMembersDto(usernames = usernames))) {
            is AppResult.Success -> refreshTrip(tripId)
            is AppResult.Error -> result
        }

    override suspend fun removeMember(tripId: String, userId: String): AppResult<TripDetails, DataError.Network> =
        when (val result = api.removeMember(tripId, userId)) {
            is AppResult.Success -> refreshTrip(tripId)
            is AppResult.Error -> result
        }

    override suspend fun leaveTrip(tripId: String): AppResult<Unit, DataError.Network> {
        val userId = currentUserId() ?: return AppResult.Error(DataError.Network.Unauthorized)
        val result = api.removeMember(tripId, userId)
        if (result is AppResult.Success || result.isNotFound()) local.deleteTrip(tripId)
        return result
    }

    /** Caches the server's answer. */
    private suspend fun AppResult<TripDto, DataError.Network>.saved(): AppResult<TripDetails, DataError.Network> =
        when (this) {
            is AppResult.Success -> AppResult.Success(data.toDetails().also { local.saveTrip(it) })
            is AppResult.Error -> this
        }

    private fun AppResult<*, DataError.Network>.isNotFound() =
        this is AppResult.Error && error.hasCode(TripErrorCodes.NOT_FOUND)

    private companion object {
        const val PAGE_SIZE = 50
    }
}
