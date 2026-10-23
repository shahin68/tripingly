package com.falcon.tripingly.feature.map.presentation.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.*
import com.falcon.tripingly.feature.map.presentation.permission.CameraPermission
import com.falcon.tripingly.feature.map.presentation.permission.rememberCameraPermissionController
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import androidx.compose.foundation.layout.Row
import com.falcon.tripingly.feature.map.generated.resources.map_camera_settings_message
import com.falcon.tripingly.feature.map.generated.resources.map_camera_open_settings

/** Takes one photo with the running camera. */
fun interface CameraShutter {
    /** The photo as the camera saves it (JPEG or HEIC, its turn in the metadata); null when it failed. */
    suspend fun takePhoto(): ByteArray?
}

/**
 * The live camera picture, the back camera when there is one, fitted whole into [modifier]'s bounds so the
 * photo shows what the user saw. [onReady] gets the shutter once the camera runs; [onUnavailable] is called
 * when the device has no camera to open (the iOS simulator).
 */
@Composable
internal expect fun CameraPreview(
    onReady: (CameraShutter) -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
)

/**
 * The in-app camera, full screen over the map: the live picture and a shutter. A photo goes to [onPhoto] as
 * soon as it's taken, so it uploads right away. Asks for the camera permission first.
 */
@Composable
internal fun PhotoCameraDialog(onPhoto: (PickedImage) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraPermissionGate { LiveCamera(onPhoto) }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.safeDrawingPadding().padding(MaterialTheme.spacing.small),
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_close), tint = Color.White)
            }
        }
    }
}

/** Shows [content] once the camera is allowed; asks on first open, then explains or points to Settings. */
@Composable
private fun CameraPermissionGate(content: @Composable () -> Unit) {
    var permission by remember { mutableStateOf<CameraPermission?>(null) }
    // Survives rotation, so the prompt isn't shown twice.
    var hasAsked by rememberSaveable { mutableStateOf(false) }
    val controller = rememberCameraPermissionController { permission = it }
    // Read on open and on every return, e.g. from Settings or the system prompt.
    LifecycleResumeEffect(controller) {
        val status = controller.status()
        when {
            status != CameraPermission.NotAsked -> permission = status
            !hasAsked -> {
                hasAsked = true
                controller.request()
            }
            // Asked before and still "not asked": Android won't show its prompt again.
            else -> permission = CameraPermission.Blocked
        }
        onPauseOrDispose { }
    }
    when (permission) {
        CameraPermission.Granted -> content()
        CameraPermission.ShouldExplain -> CameraMessage(
            text = stringResource(Res.string.map_camera_permission_message),
            action = stringResource(Res.string.map_camera_allow),
            onAction = controller::request,
        )
        CameraPermission.Blocked -> CameraMessage(
            text = stringResource(Res.string.map_camera_settings_message),
            action = stringResource(Res.string.map_camera_open_settings),
            onAction = controller::openSettings,
        )
        // The system prompt is up.
        CameraPermission.NotAsked, null -> Unit
    }
}

@Composable
private fun LiveCamera(onPhoto: (PickedImage) -> Unit) {
    var shutter by remember { mutableStateOf<CameraShutter?>(null) }
    var isUnavailable by remember { mutableStateOf(false) }
    var isTaking by remember { mutableStateOf(false) }
    var hasFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        CameraPreview(
            onReady = { shutter = it },
            onUnavailable = { isUnavailable = true },
            modifier = Modifier.fillMaxSize(),
        )
        if (isUnavailable) {
            CameraMessage(text = stringResource(Res.string.map_camera_unavailable))
            return@BoxWithConstraints
        }
        if (hasFailed) {
            Text(
                text = stringResource(Res.string.map_camera_failed),
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = MaterialTheme.spacing.large, start = 56.dp, end = 56.dp)
                    .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                    .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
            )
        }
        // Where a camera app has it: under the picture in portrait, beside it in landscape.
        ShutterButton(
            enabled = shutter != null && !isTaking,
            isTaking = isTaking,
            onClick = {
                val camera = shutter ?: return@ShutterButton
                isTaking = true
                hasFailed = false
                scope.launch {
                    val photo = camera.takePhoto()
                    isTaking = false
                    if (photo != null) onPhoto(PickedImage { photo }) else hasFailed = true
                }
            },
            modifier = Modifier
                .align(if (maxWidth > maxHeight) Alignment.CenterEnd else Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(MaterialTheme.spacing.large),
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, isTaking: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.map_camera_take_photo)
    Box(
        modifier = modifier
            .size(76.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .border(4.dp, Color.White, CircleShape)
            .padding(10.dp)
            .background(if (enabled || isTaking) Color.White else Color.White.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isTaking) CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
    }
}

@Composable
private fun CameraMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(MaterialTheme.spacing.large), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Text(text = text, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            if (action != null) Button(onClick = onAction) { Text(action) }
        }
    }
}

/** The shutter ready, waiting for the camera, and taking a photo. */
@Preview
@Composable
private fun ShutterButtonPreview() {
    TripinglyTheme {
        Row(Modifier.background(Color.Black).padding(MaterialTheme.spacing.medium), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            ShutterButton(enabled = true, isTaking = false, onClick = {})
            ShutterButton(enabled = false, isTaking = false, onClick = {})
            ShutterButton(enabled = false, isTaking = true, onClick = {})
        }
    }
}

/** What shows instead of the camera when it isn't allowed. */
@Preview(heightDp = 360)
@Composable
private fun CameraMessagePreview() {
    TripinglyTheme {
        Box(Modifier.background(Color.Black)) {
            CameraMessage(text = stringResource(Res.string.map_camera_settings_message), action = stringResource(Res.string.map_camera_open_settings))
        }
    }
}
