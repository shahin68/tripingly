package com.falcon.tripingly.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.falcon.tripingly.MainActivity
import com.falcon.tripingly.R
import com.falcon.tripingly.core.data.push.PushMessaging
import com.falcon.tripingly.core.data.push.channelId
import com.falcon.tripingly.core.model.account.NotificationType
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.android.ext.android.getKoin

/**
 * Receives pushes while the app is in the foreground (in the background the system shows them itself,
 * in the same channel, replacing the same tag) and new tokens. A tap opens [MainActivity] with the push's `deepLink`.
 */
class TripinglyMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        (getKoin().getOrNull<PushMessaging>() as? FcmPushMessaging)?.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            message.data.forEach { (key, value) -> putExtra(key, value) }
        }
        val id = message.data[NOTIFICATION_ID]?.hashCode() ?: System.currentTimeMillis().toInt()
        val tap = PendingIntent.getActivity(this, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // The backend names the channel and a tag: a newer push with the same tag replaces the older one
        // (id 0 with a tag, as Firebase shows background pushes, so the two replace each other too).
        NotificationManagerCompat.from(this).notify(
            notification.tag,
            if (notification.tag != null) 0 else id,
            NotificationCompat.Builder(this, notification.channelId ?: message.data[CHANNEL] ?: NotificationType.TRIP_CHANGES.channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notification.title)
                .setContentText(notification.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        /** Keys of the push's data, also the extras of the intent a tap opens. */
        const val DEEP_LINK = "deepLink"
        const val NOTIFICATION_ID = "notificationId"
        private const val CHANNEL = "channel"
    }
}
