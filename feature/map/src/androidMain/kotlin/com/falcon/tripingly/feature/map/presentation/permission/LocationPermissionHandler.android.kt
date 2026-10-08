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
        currentOnResult.value(controller.status())
    }
    return controller
}

private class AndroidLocationPermissionController(private val activity: Activity) : LocationPermissionController {
    lateinit var launcher: ActivityResultLauncher<Array<String>>

    // Android can't tell "never asked" from "denied for good" (both don't want an explanation),
    // so remember that we asked.
    private val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun status(): LocationPermission = when {
        PERMISSIONS.any { ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED } ->
            LocationPermission.Granted
        PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) } ->
            LocationPermission.ShouldExplain
        prefs.getBoolean(KEY_ASKED, false) -> LocationPermission.Blocked
        else -> LocationPermission.NotAsked
    }

    override fun request() {
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        launcher.launch(PERMISSIONS)
    }

    override fun openSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
        )
    }

    private companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        const val PREFS_NAME = "location_permission"
        const val KEY_ASKED = "asked"
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("The map needs an Activity to ask for location")
}
