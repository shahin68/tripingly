package com.falcon.tripingly.feature.map.presentation.permission

import android.Manifest
import android.app.Activity
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
actual fun rememberCameraPermissionController(onResult: (CameraPermission) -> Unit): CameraPermissionController {
    val activity = LocalContext.current.findActivity()
    val currentOnResult = rememberUpdatedState(onResult)
    val controller = remember(activity) { AndroidCameraPermissionController(activity) }
    controller.launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Still denied without a reason to explain: Android didn't show its prompt, or the user said no for good.
        val status = controller.status()
        currentOnResult.value(if (status == CameraPermission.NotAsked) CameraPermission.Blocked else status)
    }
    return controller
}

private class AndroidCameraPermissionController(private val activity: Activity) : CameraPermissionController {
    lateinit var launcher: ActivityResultLauncher<String>

    override fun status(): CameraPermission = when {
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
            CameraPermission.Granted
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) ->
            CameraPermission.ShouldExplain
        else -> CameraPermission.NotAsked
    }

    override fun request() {
        launcher.launch(Manifest.permission.CAMERA)
    }

    override fun openSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
        )
    }
}
