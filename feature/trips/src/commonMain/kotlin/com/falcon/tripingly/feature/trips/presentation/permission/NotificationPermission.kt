package com.falcon.tripingly.feature.trips.presentation.permission

import androidx.compose.runtime.Composable

/**
 * Asks to show notifications, if the system needs that and it isn't allowed yet. The system decides
 * whether its prompt appears (Android shows it at most twice); nothing is shown when it doesn't.
 */
@Composable
internal expect fun rememberNotificationPermissionRequest(): () -> Unit
