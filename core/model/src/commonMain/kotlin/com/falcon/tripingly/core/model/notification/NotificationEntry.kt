package com.falcon.tripingly.core.model.notification

import com.falcon.tripingly.core.model.trip.UserSummary
import kotlin.time.Instant

/**
 * One entry of the notification center, as the server words it (the same text as its push). Likes and flames on
 * one item share an entry that comes back on top with each new one ([at]), naming the latest person ([actor]).
 */
data class NotificationEntry(
    val id: String,
    /** Who caused it; null for the system or a deleted account. */
    val actor: UserSummary?,
    val title: String,
    val body: String,
    /** Where a tap goes (`tripinly://…`). */
    val deepLink: String,
    val isRead: Boolean,
    val at: Instant,
)
