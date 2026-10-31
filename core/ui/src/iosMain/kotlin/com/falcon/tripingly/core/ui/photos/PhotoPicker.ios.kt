package com.falcon.tripingly.core.ui.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import com.falcon.tripingly.core.model.photo.PickedImage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume

@Composable
actual fun rememberPhotoPicker(single: Boolean, onPicked: (List<PickedImage>) -> Unit): () -> Unit {
    val controller = LocalUIViewController.current
    val currentOnPicked by rememberUpdatedState(onPicked)
    // The picker holds its delegate weakly, so it's kept here.
    val delegate = remember { PickerDelegate { currentOnPicked(it) } }
    return remember(controller, delegate, single) {
        {
            val configuration = PHPickerConfiguration().apply {
                // 0 is no limit.
                selectionLimit = if (single) 1 else 0
                filter = PHPickerFilter.imagesFilter
            }
            val picker = PHPickerViewController(configuration = configuration)
            picker.delegate = delegate
            controller.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PickerDelegate(private val onPicked: (List<PickedImage>) -> Unit) :
    NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val images = didFinishPicking.filterIsInstance<PHPickerResult>().map { result ->
            PickedImage { result.itemProvider.imageData() }
        }
        if (images.isNotEmpty()) onPicked(images)
    }
}

/** The image as stored (HEIC, JPEG …); read when its upload starts. */
private suspend fun NSItemProvider.imageData(): ByteArray? = suspendCancellableCoroutine { continuation ->
    loadDataRepresentationForTypeIdentifier("public.image") { data, _ -> continuation.resume(data?.toByteArray()) }
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), bytes, length) } }
}
