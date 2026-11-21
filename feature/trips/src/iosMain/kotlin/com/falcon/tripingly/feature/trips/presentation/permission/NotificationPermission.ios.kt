package com.falcon.tripingly.feature.trips.presentation.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionProvidesAppNotificationSettings
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

/** iOS shows its prompt once; after that it answers by itself (the user changes it in Settings). */
@Composable
internal actual fun rememberNotificationPermissionRequest(): () -> Unit = remember {
    {
        // Push works either way; a "no" only hides notifications. The phone's notification page for the app
        // links to Tripinly's own switches in Settings (iOS has no channels).
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionProvidesAppNotificationSettings,
        ) { _, _ -> }
    }
}
