package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.model.trip.InvitePreview
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripInvite
import com.falcon.tripingly.core.network.newIdempotencyKey
import com.falcon.tripingly.core.network.trips.InvitesApi

internal class DefaultTripInviteRepository(
    private val api: InvitesApi,
    private val local: TripLocalDataSource,
) : TripInviteRepository {

    override suspend fun invites(tripId: String): AppResult<List<TripInvite>, DataError.Network> =
        api.list(tripId).map { dto -> dto.items.map { it.toInvite() } }

    override suspend fun createInvite(tripId: String): AppResult<TripInvite, DataError.Network> =
        api.create(tripId, newIdempotencyKey()).map { it.toInvite() }

    override suspend fun revokeInvite(tripId: String, inviteId: String): AppResult<Unit, DataError.Network> =
        api.revoke(tripId, inviteId)

    override suspend fun preview(token: String): AppResult<InvitePreview, DataError.Network> =
        api.preview(token).map { it.toPreview() }

    override suspend fun accept(token: String): AppResult<TripDetails, DataError.Network> =
        api.accept(token).map { dto -> dto.toDetails().also { local.saveTrip(it) } }
}
