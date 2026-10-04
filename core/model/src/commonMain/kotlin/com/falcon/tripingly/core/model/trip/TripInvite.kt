package com.falcon.tripingly.core.model.trip

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** A link that lets whoever opens it join the trip as an editor until it expires or is revoked. */
data class TripInvite(
    val id: String,
    val url: String,
    val expiresAt: Instant,
)

/** What an invite link shows before joining. */
data class InvitePreview(
    val tripId: String,
    val tripName: String,
    val owner: UserSummary,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val expiresAt: Instant,
    val alreadyMember: Boolean,
)
