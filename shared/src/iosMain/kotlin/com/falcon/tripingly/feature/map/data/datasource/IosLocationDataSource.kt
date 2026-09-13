package com.falcon.tripingly.feature.map.data.datasource

import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.core.domain.result.asSuccess
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import kotlin.coroutines.cancellation.CancellationException

class IosLocationDataSource : LocationDataSource {
    private val locationManager = CLLocationManager()

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun getLastKnownOrCurrentLocation(): AppResult<Coordinates, DataError.Location> {
        return try {
            val status = locationManager.authorizationStatus
            if (status == kCLAuthorizationStatusDenied || status == kCLAuthorizationStatusRestricted) {
                return AppResult.Error(DataError.Location.PermissionDenied)
            }

            val location = locationManager.location
            if (location != null) {
                val coords = location.coordinate.useContents {
                    Coordinates(latitude = latitude, longitude = longitude)
                }
                coords.asSuccess()
            } else {
                AppResult.Error(DataError.Location.Unavailable)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Error(DataError.Location.Unknown(e.message))
        }
    }
}
