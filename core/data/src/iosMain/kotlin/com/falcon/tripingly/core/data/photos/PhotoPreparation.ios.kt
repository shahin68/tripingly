package com.falcon.tripingly.core.data.photos

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun prepareJpeg(image: ByteArray, maxEdge: Int, quality: Int): ByteArray? {
    val data = image.usePinned { NSData.create(bytes = it.addressOf(0), length = image.size.toULong()) }
    // HEIC from the camera reads like any other image.
    val picked = UIImage.imageWithData(data) ?: return null
    val (width, height) = picked.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val scale = fitScale(width.toInt(), height.toInt(), maxEdge).toDouble()
    val format = UIGraphicsImageRendererFormat.preferredFormat().apply { this.scale = 1.0 }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width * scale, height * scale), format = format)
    // Drawing applies the image's orientation, and the new JPEG has no metadata.
    val jpeg = renderer.JPEGDataWithCompressionQuality(quality / 100.0) {
        picked.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
    }
    val size = jpeg.length.toInt()
    if (size == 0) return null
    return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), jpeg.bytes, jpeg.length) } }
}
