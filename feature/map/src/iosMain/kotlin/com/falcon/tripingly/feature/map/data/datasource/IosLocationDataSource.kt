package com.falcon.tripingly.feature.map.data.datasource

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.asSuccess
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class IosLocationDataSource : LocationDataSource {
    private val locationManager = CLLocationManager()

    @OptIn(ExperimentalForeignApi::class)
    override fun getLocationUpdatesStream(): Flow<AppResult<Coordinates, DataError.Location>> = callbackFlow {
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

        awaitClose {
            locationManager.stopUpdatingLocation()
            locationManager.delegate = null
        }
    }
}
