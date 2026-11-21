package com.falcon.tripingly.push

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import com.falcon.tripingly.R
import com.falcon.tripingly.core.data.push.SystemNotificationChannels
import com.falcon.tripingly.core.data.push.channelId
import com.falcon.tripingly.core.model.account.NotificationType

/**
 * One channel per switch in Settings, under one "Activity" group, so the phone's notification page lists
 * the same kinds as the app. The backend picks the channel of each push.
 */
class AndroidNotificationChannels(private val context: Context) : SystemNotificationChannels {

    private val manager = NotificationManagerCompat.from(context)

    override fun blocked(): Set<NotificationType> {
        if (!manager.areNotificationsEnabled()) return NotificationType.entries.toSet()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptySet()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && manager.getNotificationChannelGroup(GROUP_ID)?.isBlocked == true) {
            return NotificationType.entries.toSet()
        }
        return NotificationType.entries.filterTo(mutableSetOf()) {
            manager.getNotificationChannel(it.channelId)?.importance == NotificationManager.IMPORTANCE_NONE
        }
    }

    override fun open(type: NotificationType) {
        // With the app's notifications off the channel page can't be changed, so the app's page opens instead.
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.areNotificationsEnabled()) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, type.channelId)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }
        context.startActivity(intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        private const val GROUP_ID = "activity"

        /** The channel of the first Tripinly builds, replaced by the ones below. */
        private const val OLD_CHANNEL_ID = "updates"

        /** Creates (or renames, when the language changed) the group and its channels; the person's choices stay. */
        fun create(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannelGroup(NotificationChannelGroup(GROUP_ID, context.getString(R.string.notification_group_activity)))
            manager.createNotificationChannels(
                NotificationType.entries.map { type ->
                    NotificationChannel(type.channelId, context.getString(type.channelName()), NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { group = GROUP_ID }
                },
            )
            manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        }

        @StringRes
        private fun NotificationType.channelName(): Int = when (this) {
            NotificationType.COMMENTS -> R.string.notification_channel_comments
            NotificationType.ADDED_TO_TRIP -> R.string.notification_channel_added
            NotificationType.TRIP_CHANGES -> R.string.notification_channel_changes
            NotificationType.LIKES -> R.string.notification_channel_likes
            NotificationType.JOIN_REQUESTS -> R.string.notification_channel_join
            NotificationType.NEW_FOLLOWERS -> R.string.notification_channel_followers
        }
    }
}
