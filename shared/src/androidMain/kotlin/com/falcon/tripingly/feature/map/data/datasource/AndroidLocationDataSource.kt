package com.falcon.tripingly.feature.map.data.datasource

import android.annotation.SuppressLint
import android.content.Context
import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.core.domain.result.asError
import com.falcon.tripingly.core.domain.result.asSuccess
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

class AndroidLocationDataSource(
    private val context: Context
) : LocationDataSource {

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownOrCurrentLocation(): AppResult<Coordinates, DataError.Location> {
        return try {
            val lastLocation = getLastKnownLocation()
            if (lastLocation != null) {
                return Coordinates(lastLocation.latitude, lastLocation.longitude).asSuccess()
            }

            val freshLocation = requestFreshLocation()
            if (freshLocation != null) {
                Coordinates(freshLocation.latitude, freshLocation.longitude).asSuccess()
            } else {
                AppResult.Error(DataError.Location.Unavailable)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            DataError.Location.PermissionDenied.asError()
        } catch (e: Exception) {
            AppResult.Error(DataError.Location.Unknown(e.message))
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): android.location.Location? =
        suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    continuation.resume(location)
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
                .addOnCanceledListener {
                    continuation.cancel()
                }
        }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): android.location.Location? =
        suspendCancellableCoroutine { continuation ->
            val cts = CancellationTokenSource()
            continuation.invokeOnCancellation { cts.cancel() }

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cts.token
            ).addOnSuccessListener { location ->
                continuation.resume(location)
            }.addOnFailureListener {
                continuation.resume(null)
            }.addOnCanceledListener {
                continuation.cancel()
            }
        }
}
