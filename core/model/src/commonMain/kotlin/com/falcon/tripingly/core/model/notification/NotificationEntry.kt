package com.falcon.tripingly.core.model.notification

import com.falcon.tripingly.core.model.trip.UserSummary
import kotlin.time.Instant

/**
 * One entry of the notification center, as the server words it (the same text as its push). The server groups
 * them (a stop's comments, a trip's changes, likes on one item, follows…): a group comes back on top with each new
 * one ([at]) until it is tapped, naming the latest person ([actor]) and showing up to three ([actors]).
 */
data class NotificationEntry(
    val id: String,
    /** Who caused it; null for the system or a deleted account. */
    val actor: UserSummary?,
    /** The people of a group, latest first, at most three; empty when it's no one's. */
    val actors: List<UserSummary> = emptyList(),
    val title: String,
    val body: String,
    /** Someone followed me (or we became friends): they can be removed as a follower from here. */
    val isFollow: Boolean = false,
    /** Where a tap goes (`tripinly://…`). */
    val deepLink: String,
    val isRead: Boolean,
    val at: Instant,
)
