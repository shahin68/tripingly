package com.falcon.tripingly.core.model.trip

import com.falcon.tripingly.core.model.account.TripVisibility
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** A trip as My Trips lists it (`GET /me/trips`). */
data class Trip(
    val id: String,
    val name: String,
    /** Null for a trip without dates; with a start date the server keeps the end date in step with the days. */
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val visibility: TripVisibility,
    /** The signed-in user's role in this trip. */
    val role: TripRole,
    val owner: UserSummary,
    val coverThumbUrl: String? = null,
    val dayCount: Int,
    val markerCount: Int,
    /** My Trips lists the most recently updated first. */
    val updatedAt: Instant,
)

enum class TripRole {
    OWNER,
    EDITOR,

    /** Someone else's public trip: read-only. */
    VIEWER,
    ;

    /** Owners and editors change days, markers and photos. */
    val canEdit: Boolean get() = this != VIEWER

    /** Only the owner renames, reschedules, changes visibility, deletes and manages members and invites. */
    val canManage: Boolean get() = this == OWNER
}

data class UserSummary(
    val id: String,
    val username: String,
    val displayName: String,
)
