package com.falcon.tripingly.feature.map.presentation.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceRotationCoordinator
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.fileDataRepresentation
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientationDidChangeNotification
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.coroutines.resume

/** AVFoundation: a photo session on the back camera, its picture turned with the screen. */
@Composable
internal actual fun CameraPreview(
    onReady: (CameraShutter) -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier,
) {
    val camera = remember { IosCamera() }
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    DisposableEffect(camera) {
        val isStarting = camera.start(onRunning = { currentOnReady(CameraShutter { camera.takePhoto() }) })
        if (!isStarting) currentOnUnavailable()
        onDispose { camera.stop() }
    }
    UIKitView(factory = { camera.view }, modifier = modifier)
}

@OptIn(ExperimentalForeignApi::class)
private class IosCamera {
    private val session = AVCaptureSession()
    private val output = AVCapturePhotoOutput()
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        // The whole picture, so the photo shows what the user saw.
        videoGravity = AVLayerVideoGravityResizeAspect
    }
    /** Laid out again when the screen turns between portrait and landscape. */
    val view: UIView = PreviewView(previewLayer, onLayout = ::turnPreview)

    /** startRunning and stopRunning block, so they run here, in order. */
    private val queue = dispatch_queue_create("com.falcon.tripingly.camera", null)
    private var rotation: AVCaptureDeviceRotationCoordinator? = null

    /** A turn from one landscape to the other doesn't change the layout, so the phone's turns are followed too. */
    private var orientationObserver: Any? = null

    /** The capture holds its delegate weakly, so the one in flight is kept here. */
    private var delegate: PhotoDelegate? = null
    private var isStopped = false

    /** False when there's no camera to open (the simulator); otherwise [onRunning] comes once photos can be taken. */
    fun start(onRunning: () -> Unit): Boolean {
        val device = listOf(AVCaptureDevicePositionBack, AVCaptureDevicePositionFront).firstNotNullOfOrNull {
            AVCaptureDevice.defaultDeviceWithDeviceType(AVCaptureDeviceTypeBuiltInWideAngleCamera, AVMediaTypeVideo, it)
        } ?: return false
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return false
        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetPhoto
        val canRun = session.canAddInput(input) && session.canAddOutput(output)
        if (canRun) {
            session.addInput(input)
            session.addOutput(output)
        }
        session.commitConfiguration()
        if (!canRun) return false

        rotation = AVCaptureDeviceRotationCoordinator(device = device, previewLayer = previewLayer)
        UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()
        orientationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIDeviceOrientationDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            // After the screen has turned with the phone.
            dispatch_async(dispatch_get_main_queue()) { turnPreview() }
        }
        turnPreview()
        dispatch_async(queue) {
            session.startRunning()
            dispatch_async(dispatch_get_main_queue()) { if (!isStopped && session.running) onRunning() }
        }
        return true
    }

    fun stop() {
        isStopped = true
        orientationObserver?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
            UIDevice.currentDevice.endGeneratingDeviceOrientationNotifications()
        }
        orientationObserver = null
        rotation = null
        dispatch_async(queue) { session.stopRunning() }
    }

    suspend fun takePhoto(): ByteArray? = suspendCancellableCoroutine { continuation ->
        val connection = output.connectionWithMediaType(AVMediaTypeVideo)
        if (connection == null || !connection.active) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        // Upright as the phone is held; the angle goes into the photo's metadata.
        rotation?.videoRotationAngleForHorizonLevelCapture?.let { angle ->
            if (connection.isVideoRotationAngleSupported(angle)) connection.videoRotationAngle = angle
        }
        val photoDelegate = PhotoDelegate { photo ->
            delegate = null
            continuation.resume(photo)
        }
        delegate = photoDelegate
        output.capturePhotoWithSettings(AVCapturePhotoSettings.photoSettings(), photoDelegate)
    }

    private fun turnPreview() {
        val angle = rotation?.videoRotationAngleForHorizonLevelPreview ?: return
        previewLayer.connection?.let { if (it.isVideoRotationAngleSupported(angle)) it.videoRotationAngle = angle }
    }
}

/** Keeps the preview layer the size of the view, without the layer's own animation on rotation. */
@OptIn(ExperimentalForeignApi::class)
private class PreviewView(
    private val previewLayer: AVCaptureVideoPreviewLayer,
    private val onLayout: () -> Unit,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        CATransaction.commit()
        onLayout()
    }
}

private class PhotoDelegate(private val onPhoto: (ByteArray?) -> Unit) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
    override fun captureOutput(output: AVCapturePhotoOutput, didFinishProcessingPhoto: AVCapturePhoto, error: NSError?) {
        onPhoto(if (error == null) didFinishProcessingPhoto.fileDataRepresentation()?.toByteArray() else null)
    }
}
