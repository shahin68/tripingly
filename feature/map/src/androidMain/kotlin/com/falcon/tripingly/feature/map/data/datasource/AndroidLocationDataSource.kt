package com.falcon.tripingly.feature.map.data.datasource

import android.annotation.SuppressLint
import android.content.Context
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.asError
import com.falcon.tripingly.core.common.result.asSuccess
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidLocationDataSource(
    private val context: Context
) : LocationDataSource {

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    override fun getLocationUpdatesStream(): Flow<AppResult<Coordinates, DataError.Location>> = callbackFlow {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(Coordinates(location.latitude, location.longitude).asSuccess())
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                context.mainLooper
            ).addOnFailureListener { e ->
                trySend(AppResult.Error(DataError.Location.Unknown(e.message)))
            }
        } catch (e: SecurityException) {
            trySend(DataError.Location.PermissionDenied.asError())
        } catch (e: Exception) {
            trySend(AppResult.Error(DataError.Location.Unknown(e.message)))
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }
}
