package com.falcon.tripingly.feature.map.presentation.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@Composable
actual fun rememberLocationPermissionController(
    onResult: (LocationPermission) -> Unit
): LocationPermissionController {
    val activity = LocalContext.current.findActivity()
    val currentOnResult = rememberUpdatedState(onResult)
    val controller = remember(activity) { AndroidLocationPermissionController(activity) }
    controller.launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Still denied without a reason to explain: Android didn't show its prompt, or the user said no for good.
        val status = controller.status()
        currentOnResult.value(if (status == LocationPermission.NotAsked) LocationPermission.Blocked else status)
    }
    return controller
}

private class AndroidLocationPermissionController(private val activity: Activity) : LocationPermissionController {
    lateinit var launcher: ActivityResultLauncher<Array<String>>

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

    private companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("The map needs an Activity to ask for location")
}
