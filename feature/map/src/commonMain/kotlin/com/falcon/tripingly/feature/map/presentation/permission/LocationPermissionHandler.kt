package com.falcon.tripingly.feature.map.presentation.permission

import androidx.compose.runtime.Composable

/** Where the location permission stands, as far as asking the user goes. */
enum class LocationPermission {
    Granted,

    /** Never asked: the system prompt can be shown. */
    NotAsked,

    /** Android, after a denial: explain why first, then the system prompt can be shown again. */
    ShouldExplain,

    /** The system won't show its prompt any more; only the app's settings can change it. */
    Blocked,
}

/** Reads and requests the location permission, and opens the app's settings. */
interface LocationPermissionController {
    fun status(): LocationPermission

    /** Shows the system prompt; the answer goes to the `onResult` it was created with. */
    fun request()

    fun openSettings()
}

@Composable
expect fun rememberLocationPermissionController(
    onResult: (LocationPermission) -> Unit
): LocationPermissionController
