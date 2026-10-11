package com.falcon.tripingly.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.falcon.tripingly.MainActivity
import com.falcon.tripingly.R
import com.falcon.tripingly.core.data.push.PushMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.android.ext.android.getKoin

/**
 * Receives pushes while the app is in the foreground (in the background the system shows them itself,
 * in the same channel) and new tokens. A tap opens [MainActivity] with the push's `deepLink`.
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
        NotificationManagerCompat.from(this).notify(
            id,
            NotificationCompat.Builder(this, CHANNEL_ID)
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
        /** The one channel; the manifest makes it the default for pushes shown by the system. */
        const val CHANNEL_ID = "updates"

        /** Keys of the push's data, also the extras of the intent a tap opens. */
        const val DEEP_LINK = "deepLink"
        const val NOTIFICATION_ID = "notificationId"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_updates), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}
