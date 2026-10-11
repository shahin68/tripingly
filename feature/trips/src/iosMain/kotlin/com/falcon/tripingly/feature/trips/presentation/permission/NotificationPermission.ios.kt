package com.falcon.tripingly.feature.trips.presentation.permission

import androidx.compose.runtime.Composable

/** iOS push comes with APNs (it needs an Apple Developer account); until then there's nothing to ask. */
@Composable
internal actual fun rememberNotificationPermissionRequest(): () -> Unit = {}
