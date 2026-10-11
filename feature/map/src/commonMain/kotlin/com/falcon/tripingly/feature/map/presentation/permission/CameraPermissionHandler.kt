package com.falcon.tripingly.feature.map.presentation.permission

import androidx.compose.runtime.Composable

/** Where the camera permission stands; the same steps as [LocationPermission]. */
enum class CameraPermission {
    Granted,

    /** Not allowed and nothing to explain: try the system prompt. */
    NotAsked,

    /** Android, after a denial: explain why first, then the system prompt can be shown again. */
    ShouldExplain,

    /** The system won't show its prompt (iOS after a denial; Android when a request came back denied for good). */
    Blocked,
}

/** Reads and requests the camera permission, and opens the app's settings. */
interface CameraPermissionController {
    fun status(): CameraPermission

    /** Shows the system prompt; the answer goes to the `onResult` it was created with. */
    fun request()

    fun openSettings()
}

@Composable
expect fun rememberCameraPermissionController(onResult: (CameraPermission) -> Unit): CameraPermissionController
