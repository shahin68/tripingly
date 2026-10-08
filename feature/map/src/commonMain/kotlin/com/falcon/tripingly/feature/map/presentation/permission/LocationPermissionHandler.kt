package com.falcon.tripingly.feature.map.presentation.permission

import androidx.compose.runtime.Composable

/** Where the location permission stands, as far as asking the user goes. */
enum class LocationPermission {
    Granted,

    /**
     * Not allowed and nothing to explain: try the system prompt. On Android this is also how a
     * permanent denial looks, so a request that comes straight back reports [Blocked].
     */
    NotAsked,

    /** Android, after a denial: explain why first, then the system prompt can be shown again. */
    ShouldExplain,

    /** The system won't show its prompt (iOS after a denial; Android when a request came back denied for good). */
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
