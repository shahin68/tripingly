package com.falcon.tripingly.core.location.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.BroadcastReceiver
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

@Composable
actual fun rememberLocationPermissionController(
    onResult: (LocationPermission) -> Unit,
    onLocationSwitched: () -> Unit,
): LocationPermissionController {
    val activity = LocalContext.current.findActivity()
    val currentOnResult = rememberUpdatedState(onResult)
    val currentOnSwitched = rememberUpdatedState(onLocationSwitched)
    val controller = remember(activity) { AndroidLocationPermissionController(activity) }
    controller.launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Still denied without a reason to explain: Android didn't show its prompt, or the user said no for good.
        val status = controller.status()
        currentOnResult.value(if (status == LocationPermission.NotAsked) LocationPermission.Blocked else status)
    }
    controller.turnOnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        currentOnSwitched.value()
    }
    // Location switched in the quick settings doesn't pause the screen: Android says so with a broadcast.
    DisposableEffect(activity) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = currentOnSwitched.value()
        }
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { activity.unregisterReceiver(receiver) }
    }
    return controller
}

private class AndroidLocationPermissionController(private val activity: Activity) : LocationPermissionController {
    lateinit var launcher: ActivityResultLauncher<Array<String>>
    lateinit var turnOnLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun status(): LocationPermission = when {
        PERMISSIONS.any { ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED } ->
            LocationPermission.Granted
        PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) } ->
            LocationPermission.ShouldExplain
        else -> LocationPermission.NotAsked
    }

    override fun request() {
        launcher.launch(PERMISSIONS)
    }

    override fun openSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
        )
    }

    override suspend fun isLocationOn(): Boolean =
        activity.getSystemService(LocationManager::class.java)?.let(LocationManagerCompat::isLocationEnabled) ?: false

    override fun turnOnLocation() {
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L).build())
            .build()
        LocationServices.getSettingsClient(activity).checkLocationSettings(request).addOnFailureListener { error ->
            if (error is ResolvableApiException) {
                turnOnLauncher.launch(IntentSenderRequest.Builder(error.resolution).build())
            } else {
                activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
    }

    private companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}

/** The Activity hosting this context; runtime permissions are asked through it. */
tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Asking for permissions needs an Activity")
}
