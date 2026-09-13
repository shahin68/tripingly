package com.falcon.tripingly.feature.map.presentation.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted

@Composable
actual fun rememberLocationPermissionLauncher(
    onResult: (Boolean) -> Unit
): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val locationManager = remember { CLLocationManager() }

    LaunchedEffect(Unit) {
        val status = locationManager.authorizationStatus
        if (status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways) {
            currentOnResult.value(true)
        } else if (status == kCLAuthorizationStatusDenied || status == kCLAuthorizationStatusRestricted) {
            currentOnResult.value(false)
        }
    }

    return {
        val status = locationManager.authorizationStatus
        if (status == kCLAuthorizationStatusNotDetermined) {
            locationManager.requestWhenInUseAuthorization()
        }
        val currentStatus = locationManager.authorizationStatus
        val granted = currentStatus == kCLAuthorizationStatusAuthorizedWhenInUse ||
                currentStatus == kCLAuthorizationStatusAuthorizedAlways
        currentOnResult.value(granted)
    }
}
