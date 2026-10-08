package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.trip.InvitePreview
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripInvite

/** Invite links: the owner creates, lists and revokes them; whoever opens one can join as an editor. */
interface TripInviteRepository {
    suspend fun invites(tripId: String): AppResult<List<TripInvite>, DataError.Network>

    suspend fun createInvite(tripId: String): AppResult<TripInvite, DataError.Network>

    suspend fun revokeInvite(tripId: String, inviteId: String): AppResult<Unit, DataError.Network>

    /** `INVITE_EXPIRED` for an expired link, `NOT_FOUND` for a revoked or unknown one. */
    suspend fun preview(token: String): AppResult<InvitePreview, DataError.Network>

    /** Joins the trip; it appears in My Trips. */
    suspend fun accept(token: String): AppResult<TripDetails, DataError.Network>
}
