package com.falcon.tripingly.core.location.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.NSObject

@Composable
actual fun rememberLocationPermissionController(
    onResult: (LocationPermission) -> Unit,
    onLocationSwitched: () -> Unit,
): LocationPermissionController {
    val currentOnResult = rememberUpdatedState(onResult)
    val currentOnSwitched = rememberUpdatedState(onLocationSwitched)
    return remember { IosLocationPermissionController({ currentOnResult.value(it) }, { currentOnSwitched.value() }) }
}

/**
 * iOS shows its prompt once; after a denial only Settings can change it. Location Services switched off reads as a
 * denial here too, and switching them reports an authorization change.
 */
private class IosLocationPermissionController(
    private val onResult: (LocationPermission) -> Unit,
    private val onSwitched: () -> Unit,
) : LocationPermissionController {
    private val manager = CLLocationManager()
    private var isAsking = false

    // The answer arrives here, after the user taps Allow or Don't Allow.
    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            val status = status()
            if (isAsking && status != LocationPermission.NotAsked) {
                isAsking = false
                onResult(status)
            } else {
                onSwitched()
            }
        }
    }

    init {
        // The manager holds its delegate weakly; this controller keeps it alive.
        manager.delegate = delegate
    }

    override fun status(): LocationPermission = when (manager.authorizationStatus) {
        kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> LocationPermission.Granted
        kCLAuthorizationStatusNotDetermined -> LocationPermission.NotAsked
        else -> LocationPermission.Blocked
    }

    override fun request() {
        isAsking = true
        manager.requestWhenInUseAuthorization()
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
    }

    // Apple asks not to call it on the main thread.
    override suspend fun isLocationOn(): Boolean = withContext(Dispatchers.Default) { CLLocationManager.locationServicesEnabled() }

    // Apps can't open the Location Services page itself: Settings it is.
    override fun turnOnLocation() = openSettings()
}
