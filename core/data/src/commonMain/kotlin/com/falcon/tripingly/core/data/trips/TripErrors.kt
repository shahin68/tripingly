package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.common.error.DataError

/** API error codes the trip screens handle specifically. */
object TripErrorCodes {
    const val NOT_FOUND = "NOT_FOUND"
    const val FORBIDDEN = "FORBIDDEN"
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val LIMIT_REACHED = "LIMIT_REACHED"
    const val USER_BLOCKED = "USER_BLOCKED"
    const val INVITE_EXPIRED = "INVITE_EXPIRED"
    /** The owner declined my request to join; it can't be sent again. */
    const val JOIN_REQUEST_DECLINED = "JOIN_REQUEST_DECLINED"

    /** A marker with the app-chosen ID exists already. */
    const val ID_CONFLICT = "ID_CONFLICT"

    /** `fields.endDate` when new dates would remove days that still have markers. */
    const val DAYS_NOT_EMPTY = "daysNotEmpty"

    /** `fields.memberUsernames.<index>` (new trip) or `fields.usernames.<index>` (adding members) when the user doesn't exist. */
    const val MEMBER_NOT_FOUND = "notFound"

    /** Like [MEMBER_NOT_FOUND], when the user blocked that person. */
    const val MEMBER_BLOCKED = "blocked"

    /** `fields.id` when deleting a trip's only day. */
    const val LAST_DAY = "lastDay"
}

/** True when the API answered with [code]. */
fun DataError.Network.hasCode(code: String): Boolean = this is DataError.Network.Api && this.code == code

/** True when a `VALIDATION_FAILED` error names [reason] for [field] (`details.fields.<field>`). */
fun DataError.Network.hasFieldError(field: String, reason: String): Boolean =
    this is DataError.Network.Api &&
        code == TripErrorCodes.VALIDATION_FAILED &&
        details["fields.$field"]?.split(',')?.contains(reason) == true
