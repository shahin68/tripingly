package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.model.account.NotificationType

/**
 * The phone's own notification switches, one channel per [NotificationType] (Android). Apps can't turn a
 * channel on, so Settings only reads them and opens the phone's page for one. Android binds it where push
 * is set up; iOS has no channels, so there is no binding there.
 */
interface SystemNotificationChannels {
    /** The kinds the person turned off in the phone's settings (all of them when the app's notifications are off). */
    fun blocked(): Set<NotificationType>

    /** Opens the phone's settings page for [type]'s channel. */
    fun open(type: NotificationType)
}

/** The channel each kind of push arrives in; the same ids the backend sends (`android.notification.channelId`). */
val NotificationType.channelId: String
    get() = when (this) {
        NotificationType.COMMENTS -> "comments"
        NotificationType.LIKES -> "likes"
        NotificationType.TRIP_CHANGES -> "trip_changes"
        NotificationType.ADDED_TO_TRIP -> "added_to_trips"
        NotificationType.JOIN_REQUESTS -> "join_requests"
        NotificationType.NEW_FOLLOWERS -> "followers"
    }
