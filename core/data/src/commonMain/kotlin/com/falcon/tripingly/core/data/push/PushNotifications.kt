package com.falcon.tripingly.core.data.push

/** Push notifications for the signed-in user. */
interface PushNotifications {
    /** Registers this device for the signed-in user, and again whenever its token changes. */
    fun start()

    /** The user opened a notification from a push. */
    suspend fun opened(notificationId: String)
}
