package com.falcon.tripingly.core.network.push

import com.falcon.tripingly.core.network.model.UserSummaryDto
import kotlinx.serialization.Serializable

/**
 * A page of `GET /notifications`. Written by hand, unlike the generated models, because it reads `type` as plain text:
 * the server renders the text and the link, so a kind added later never stops this app from reading the page.
 */
@Serializable
data class NotificationCenterPageDto(
    val items: List<NotificationEntryDto>,
    val nextCursor: String? = null,
    /** Entries new or back on top since the center was last opened. */
    val unseenCount: Int = 0,
)

/** One entry of the notification center, also what `notification.created` carries. */
@Serializable
data class NotificationEntryDto(
    val id: String,
    /** Read only to refresh a trip's join count on `join_requested`; any other kind reads the same. */
    val type: String? = null,
    val actor: UserSummaryDto? = null,
    /** The people of a group, latest first, at most three. */
    val actors: List<UserSummaryDto> = emptyList(),
    val title: String,
    val body: String,
    val deepLink: String,
    val read: Boolean = false,
    /** ISO 8601; when it last came back on top. */
    val createdAt: String,
)
