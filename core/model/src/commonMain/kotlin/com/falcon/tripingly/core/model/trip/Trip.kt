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
    /** People the trip is shared with as editors; the owner isn't counted. */
    val editorCount: Int = 0,
    /** Someone else's public trip I saved: it shows in My Trips, read-only, as the live original. */
    val savedByMe: Boolean = false,
    /** Shown on trip cards ("3 days ago") and as a date on the trip screen; Explore's time chips filter by it. */
    val createdAt: Instant,
    /** My Trips lists the most recently updated first. */
    val updatedAt: Instant,
)

enum class TripRole {
    OWNER,
    EDITOR,

    /** Someone else's public trip (saved or just opened): read-only. */
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
