package com.falcon.tripingly.feature.map.presentation.photos

import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** CameraX: a preview and a photo capture bound to the screen's lifecycle, drawn by the Compose viewfinder. */
@Composable
internal actual fun CameraPreview(
    onReady: (CameraShutter) -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    val preview = remember { Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } } }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }

    LaunchedEffect(lifecycleOwner) {
        val provider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // CameraX couldn't start: no camera, or none it can use.
            null
        }
        // Back camera first; some tablets only have a front one.
        val selector = listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)
            .firstOrNull { runCatching { provider?.hasCamera(it) == true }.getOrDefault(false) }
        if (provider == null || selector == null) {
            currentOnUnavailable()
            return@LaunchedEffect
        }
        try {
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            val executor = ContextCompat.getMainExecutor(context)
            currentOnReady(CameraShutter { imageCapture.takePhoto(executor) })
            awaitCancellation()
        } finally {
            provider.unbind(preview, imageCapture)
        }
    }

    // The photo comes out upright however the phone is held, even when the screen doesn't turn with it.
    DisposableEffect(imageCapture) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                imageCapture.targetRotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    surfaceRequest?.let { request ->
        CameraXViewfinder(surfaceRequest = request, modifier = modifier, contentScale = ContentScale.Fit)
    }
}

/** Saved like a camera file, a JPEG with its turn in EXIF, which the upload step applies. */
private suspend fun ImageCapture.takePhoto(executor: Executor): ByteArray? =
    suspendCancellableCoroutine { continuation ->
        val output = ByteArrayOutputStream()
        takePicture(
            ImageCapture.OutputFileOptions.Builder(output).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    continuation.resume(output.toByteArray())
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resume(null)
                }
            },
        )
    }
