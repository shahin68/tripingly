package com.falcon.tripingly.core.location

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.asSuccess
import com.falcon.tripingly.core.location.model.Coordinates
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSError
import platform.darwin.NSObject

class IosLocationDataSource : LocationDataSource {
    @OptIn(ExperimentalForeignApi::class)
    override fun getLocationUpdatesStream(): Flow<AppResult<Coordinates, DataError.Location>> = callbackFlow {
        // One manager per collector, so one stopping doesn't stop the others. It's made on the main thread (flowOn below):
        // Core Location calls the delegate on the run loop of the thread that made the manager, and only main has one.
        val locationManager = CLLocationManager()
        val status = locationManager.authorizationStatus
        if (status == kCLAuthorizationStatusDenied || status == kCLAuthorizationStatusRestricted) {
            trySend(AppResult.Error(DataError.Location.PermissionDenied))
            close()
            return@callbackFlow
        }

        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val lastLocation = didUpdateLocations.lastOrNull() as? CLLocation
                if (lastLocation != null) {
                    val coords = lastLocation.coordinate.useContents {
                        Coordinates(latitude = latitude, longitude = longitude)
                    }
                    trySend(coords.asSuccess())
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                trySend(AppResult.Error(DataError.Location.Unavailable))
            }
        }

        locationManager.delegate = delegate
        locationManager.startUpdatingLocation()

        // The manager holds its delegate weakly; using it here keeps it alive until the flow ends.
        awaitClose {
            locationManager.stopUpdatingLocation()
            if (locationManager.delegate === delegate) locationManager.delegate = null
        }
    }.flowOn(Dispatchers.Main)
}
