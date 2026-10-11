package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

/** The Google map takes images, so pins drawn by Compose or Skia go to it as PNG. */
internal fun ImageBitmap.toUIImage(scale: Float): UIImage =
    UIImage(data = Image.makeFromBitmap(asSkiaBitmap()).png(), scale = scale.toDouble())

internal fun Image.png(): NSData = encodeToData(EncodedImageFormat.PNG)!!.bytes.toNSData()

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

/** Bold text for numbers on pins, at [points] screen points. */
internal fun boldFont(points: Float): Font = Font(FontMgr.default.matchFamilyStyle(null, FontStyle.BOLD), points)

/**
 * A stop's pin: a round badge in the day's color with its number, on a small tip touching its
 * point, as `TripMarkerIcon` draws it on Android.
 */
internal fun drawTripPin(orderNumber: Int, color: Int, density: Float, fontScale: Float): UIImage {
    val badge = 32 * density
    val tipWidth = 12 * density
    val tipHeight = 8 * density
    // The tip overlaps the badge by 2 points, so they meet without a gap.
    val height = badge + tipHeight - 2 * density
    val surface = Surface.makeRasterN32Premul(badge.toInt(), height.toInt())
    val fill = Paint().apply {
        this.color = color
        isAntiAlias = true
    }
    surface.canvas.apply {
        drawCircle(badge / 2, badge / 2, badge / 2, fill)
        drawPath(
            PathBuilder()
                .moveTo((badge - tipWidth) / 2, height - tipHeight)
                .lineTo((badge + tipWidth) / 2, height - tipHeight)
                .lineTo(badge / 2, height)
                .closePath()
                .detach(),
            fill,
        )
        val font = boldFont(14 * density * fontScale)
        val label = orderNumber.toString()
        val metrics = font.metrics
        drawString(
            label,
            (badge - font.measureTextWidth(label)) / 2,
            badge / 2 - (metrics.ascent + metrics.descent) / 2,
            font,
            Paint().apply {
                this.color = WHITE
                isAntiAlias = true
            },
        )
    }
    return UIImage(data = surface.makeImageSnapshot().png(), scale = density.toDouble())
}

/**
 * A stop's pin with its cover photo: the photo in a ring of the day's color, the number in a small badge on
 * it and the tip below, as `TripMarkerIcon` draws it on Android.
 */
internal fun drawCoverPin(orderNumber: Int, color: Int, cover: ImageBitmap, density: Float, fontScale: Float): UIImage {
    val pin = COVER_PIN_SIZE.value * density
    val ring = COVER_RING.value * density
    val badge = COVER_BADGE_SIZE.value * density
    val tipWidth = 12 * density
    val tipHeight = 8 * density
    val height = pin + tipHeight - 2 * density
    val surface = Surface.makeRasterN32Premul(pin.toInt(), height.toInt())
    val fill = Paint().apply {
        this.color = color
        isAntiAlias = true
    }
    val photo = Image.makeFromBitmap(cover.asSkiaBitmap())
    surface.canvas.apply {
        drawCircle(pin / 2, pin / 2, pin / 2, fill)
        drawPath(
            PathBuilder()
                .moveTo((pin - tipWidth) / 2, height - tipHeight)
                .lineTo((pin + tipWidth) / 2, height - tipHeight)
                .lineTo(pin / 2, height)
                .closePath()
                .detach(),
            fill,
        )
        save()
        val inner = Rect.makeXYWH(ring, ring, pin - 2 * ring, pin - 2 * ring)
        clipRRect(RRect.makeOvalLTRB(inner.left, inner.top, inner.right, inner.bottom), antiAlias = true)
        // Center crop, as the photo is square or close to it.
        val side = minOf(photo.width, photo.height).toFloat()
        val source = Rect.makeXYWH((photo.width - side) / 2, (photo.height - side) / 2, side, side)
        drawImageRect(photo, source, inner, SamplingMode.LINEAR, Paint().apply { isAntiAlias = true }, true)
        restore()
        drawCircle(pin - badge / 2, badge / 2, badge / 2, fill)
        val font = boldFont(10 * density * fontScale)
        val label = orderNumber.toString()
        val metrics = font.metrics
        drawString(
            label,
            pin - badge / 2 - font.measureTextWidth(label) / 2,
            badge / 2 - (metrics.ascent + metrics.descent) / 2,
            font,
            Paint().apply {
                this.color = WHITE
                isAntiAlias = true
            },
        )
    }
    return UIImage(data = surface.makeImageSnapshot().png(), scale = density.toDouble())
}

internal const val WHITE = 0xFFFFFFFF.toInt()
