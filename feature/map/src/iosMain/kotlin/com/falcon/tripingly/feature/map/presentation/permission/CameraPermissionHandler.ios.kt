package com.falcon.tripingly.feature.map.presentation.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberCameraPermissionController(onResult: (CameraPermission) -> Unit): CameraPermissionController {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember { IosCameraPermissionController { currentOnResult.value(it) } }
}

/** iOS shows its prompt once; after a denial only Settings can change it. */
private class IosCameraPermissionController(
    private val onResult: (CameraPermission) -> Unit,
) : CameraPermissionController {

    override fun status(): CameraPermission = when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusAuthorized -> CameraPermission.Granted
        AVAuthorizationStatusNotDetermined -> CameraPermission.NotAsked
        else -> CameraPermission.Blocked
    }

    override fun request() {
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
            // The answer comes on a background queue.
            dispatch_async(dispatch_get_main_queue()) { onResult(status()) }
        }
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
    }
}
