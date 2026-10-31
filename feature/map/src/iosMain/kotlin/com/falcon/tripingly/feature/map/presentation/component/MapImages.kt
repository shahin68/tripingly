package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import com.falcon.tripingly.core.designsystem.theme.MapColors
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

/**
 * Bold text for numbers on pins, at [points] screen points, in the system font. (Matching a null family
 * finds no font on iOS, which drew no numbers at all.)
 */
internal fun boldFont(points: Float): Font = Font(FontMgr.default.legacyMakeTypeface("", FontStyle.BOLD), points)

/** A stop's pin: its number on near-black ivy in a white ring, centered on the stop, as `TripMarkerIcon` draws it on Android. */
internal fun drawTripPin(orderNumber: Int, density: Float, fontScale: Float): UIImage {
    val pin = PIN_SIZE.value * density
    val surface = Surface.makeRasterN32Premul(pin.toInt(), pin.toInt())
    surface.canvas.apply {
        drawCircle(pin / 2, pin / 2, pin / 2, fill(WHITE))
        drawCircle(pin / 2, pin / 2, pin / 2 - PIN_RING.value * density, fill(STOP))
        drawNumber(orderNumber, pin / 2, pin / 2, boldFont(13 * density * fontScale))
    }
    return UIImage(data = surface.makeImageSnapshot().png(), scale = density.toDouble())
}

/**
 * A stop's pin with its cover photo: the photo in a white ring with the number in a small badge on it, as
 * `TripMarkerIcon` draws it on Android.
 */
internal fun drawCoverPin(orderNumber: Int, cover: ImageBitmap, density: Float, fontScale: Float): UIImage {
    val pin = COVER_PIN_SIZE.value * density
    val ring = COVER_RING.value * density
    val badge = COVER_BADGE_SIZE.value * density
    val surface = Surface.makeRasterN32Premul(pin.toInt(), pin.toInt())
    val photo = Image.makeFromBitmap(cover.asSkiaBitmap())
    surface.canvas.apply {
        drawCircle(pin / 2, pin / 2, pin / 2, fill(WHITE))
        save()
        val inner = Rect.makeXYWH(ring, ring, pin - 2 * ring, pin - 2 * ring)
        clipRRect(RRect.makeOvalLTRB(inner.left, inner.top, inner.right, inner.bottom), antiAlias = true)
        // Center crop, as the photo is square or close to it.
        val side = minOf(photo.width, photo.height).toFloat()
        val source = Rect.makeXYWH((photo.width - side) / 2, (photo.height - side) / 2, side, side)
        drawImageRect(photo, source, inner, SamplingMode.LINEAR, Paint().apply { isAntiAlias = true }, true)
        restore()
        drawCircle(pin - badge / 2, badge / 2, badge / 2, fill(WHITE))
        drawCircle(pin - badge / 2, badge / 2, badge / 2 - PIN_RING.value / 2 * density, fill(STOP))
        drawNumber(orderNumber, pin - badge / 2, badge / 2, boldFont(10 * density * fontScale))
    }
    return UIImage(data = surface.makeImageSnapshot().png(), scale = density.toDouble())
}

private fun fill(color: Int) = Paint().apply {
    this.color = color
    isAntiAlias = true
}

/** [orderNumber] in white, centered on ([x], [y]). */
private fun org.jetbrains.skia.Canvas.drawNumber(orderNumber: Int, x: Float, y: Float, font: Font) {
    val label = orderNumber.toString()
    val metrics = font.metrics
    drawString(label, x - font.measureTextWidth(label) / 2, y - (metrics.ascent + metrics.descent) / 2, font, fill(WHITE))
}

private val STOP = MapColors.Stop.toArgb()

internal const val WHITE = 0xFFFFFFFF.toInt()
