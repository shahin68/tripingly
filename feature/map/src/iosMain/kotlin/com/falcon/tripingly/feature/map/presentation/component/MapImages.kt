package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.falcon.tripingly.core.designsystem.theme.MapColors
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Point
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import kotlin.math.ceil

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

/**
 * A stop's pin: its [onStop] number on the [stop] colour in the pins' thin ring, a circle whatever the number, with the
 * "new" dot when [isNew], as `TripMarkerIcon` draws it on Android; in its [halo] while the day's route shows.
 */
internal fun drawTripPin(orderNumber: Int, isNew: Boolean, stop: Color, onStop: Color, halo: Color?, density: Float, fontScale: Float): UIImage {
    val pin = PIN_SIZE.value * density
    val font = boldFont(13 * density * fontScale)
    val width = pillWidth(orderNumber, font, pin, padding = 0f)
    return drawStopPin(width, pin, hasCover = false, isNew, badgeWidth = 0f, halo, density, fontScale) {
        drawNumberPill(orderNumber, Rect.makeXYWH(0f, 0f, width, pin), font, stop.toArgb(), onStop.toArgb(), density)
    }
}

/**
 * A stop's pin with its cover photo: the photo in an [onStop] ring with the number in a small badge, its right
 * half-circle on the ring at 315° and a wider badge reaching further left, and the "new" dot when [isNew], as
 * `TripMarkerIcon` draws it on Android; in its [halo] while the day's route shows.
 */
internal fun drawCoverPin(orderNumber: Int, cover: ImageBitmap, isNew: Boolean, stop: Color, onStop: Color, halo: Color?, density: Float, fontScale: Float): UIImage {
    val pin = COVER_PIN_SIZE.value * density
    val ring = PIN_STROKE.value * density
    val badge = COVER_BADGE_SIZE.value * density
    val corner = ringCorner(hasCover = true).value * density
    val font = boldFont(10 * density * fontScale)
    val badgeWidth = pillWidth(orderNumber, font, badge, BADGE_NUMBER_PADDING.value * density)
    val photo = Image.makeFromBitmap(cover.asSkiaBitmap())
    return drawStopPin(pin, pin, hasCover = true, isNew, badgeWidth, halo, density, fontScale) {
        drawCircle(pin / 2, pin / 2, pin / 2, fill(PIN_STROKE_COLOR))
        save()
        val inner = Rect.makeXYWH(ring, ring, pin - 2 * ring, pin - 2 * ring)
        clipRRect(RRect.makeOvalLTRB(inner.left, inner.top, inner.right, inner.bottom), antiAlias = true)
        // Center crop, as the photo is square or close to it.
        val side = minOf(photo.width, photo.height).toFloat()
        val source = Rect.makeXYWH((photo.width - side) / 2, (photo.height - side) / 2, side, side)
        drawImageRect(photo, source, inner, SamplingMode.LINEAR, Paint().apply { isAntiAlias = true }, true)
        restore()
        val badgeRight = pin / 2 - corner + badge / 2
        drawNumberPill(orderNumber, Rect.makeXYWH(badgeRight - badgeWidth, pin / 2 - corner - badge / 2, badgeWidth, badge), font, stop.toArgb(), onStop.toArgb(), density)
    }
}

/**
 * A stop's marker image: the pin's body [width] by [height], drawn by [drawBody] from its own top left, in [halo] when
 * there is one (see [STOP_HALO_WIDTH]), with the "new" dot when [isNew]. The image reaches as far on each side of the
 * stop as on the opposite one (see [stopMarkerHalfSize]), so the stop is at its center and the 0.5 anchor holds.
 */
private fun drawStopPin(
    width: Float,
    height: Float,
    hasCover: Boolean,
    isNew: Boolean,
    badgeWidth: Float,
    halo: Color?,
    density: Float,
    fontScale: Float,
    drawBody: Canvas.() -> Unit,
): UIImage {
    val pad = if (halo != null) STOP_HALO_WIDTH.value * density else 0f
    val bodyWidth = width + 2 * pad
    val bodyHeight = height + 2 * pad
    val half = Density(density, fontScale).stopMarkerHalfSize(hasCover, isNew, Size(bodyWidth, bodyHeight), badgeWidth)
    // Whole pixels on each side of the stop, so it sits exactly at the image's center.
    val centerX = ceil(half.width)
    val centerY = ceil(half.height)
    val surface = Surface.makeRasterN32Premul(2 * centerX.toInt(), 2 * centerY.toInt())
    surface.canvas.apply {
        val left = centerX - bodyWidth / 2
        val top = centerY - bodyHeight / 2
        if (halo != null) {
            drawRRect(RRect.makeLTRB(left, top, left + bodyWidth, top + bodyHeight, bodyHeight / 2), fill(halo.copy(alpha = STOP_HALO_ALPHA).toArgb()))
        }
        save()
        translate(left + pad, top + pad)
        drawBody()
        restore()
        if (isNew) drawNewDot(centerX, centerY, ringCorner(hasCover).value * density, density)
    }
    return UIImage(data = surface.makeImageSnapshot().png(), scale = density.toDouble())
}

/**
 * The "new" dot on the pin's ring at 45° from its center ([centerX], [centerY]), [corner] along each axis: a gap round
 * it cut out of what's drawn (pin, badge, halo), so the map shows through, then the red dot in the middle.
 */
private fun Canvas.drawNewDot(centerX: Float, centerY: Float, corner: Float, density: Float) {
    val radius = NEW_DOT_SIZE.value * density / 2
    val x = centerX + corner
    val y = centerY - corner
    drawCircle(x, y, radius + NEW_DOT_GAP.value * density, Paint().apply { blendMode = BlendMode.CLEAR; isAntiAlias = true })
    drawCircle(x, y, radius, fill(NEW_ACTIVITY))
}

/** A circle [height] round, or wider as a pill when [orderNumber] and [padding] on either side need more. */
private fun pillWidth(orderNumber: Int, font: Font, height: Float, padding: Float): Float =
    maxOf(height, font.measureTextWidth(orderNumber.toString()) + 2 * padding)

/** [orderNumber] in [on] in the middle of a [color] pill filling [bounds], in the pins' thin ring. */
private fun Canvas.drawNumberPill(orderNumber: Int, bounds: Rect, font: Font, color: Int, on: Int, density: Float) {
    val ring = PIN_STROKE.value * density
    val radius = bounds.height / 2
    drawRRect(RRect.makeLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom, radius), fill(PIN_STROKE_COLOR))
    drawRRect(RRect.makeLTRB(bounds.left + ring, bounds.top + ring, bounds.right - ring, bounds.bottom - ring, radius - ring), fill(color))
    drawNumber(orderNumber, (bounds.left + bounds.right) / 2, (bounds.top + bounds.bottom) / 2, font, on)
}

private fun fill(color: Int) = Paint().apply {
    this.color = color
    isAntiAlias = true
}

/**
 * [orderNumber] in [color], its ink centered on ([x], [y]): the union of the digits' own glyph bounds, each where it
 * sits in the line, not the font's line box (whose descender room set them low) or the text's measured box (which keeps
 * the advance, so a "1" with its wide side bearing set "14" off to one side, Shahin 2026-11-17), as Android's trimmed
 * line centers them.
 */
private fun Canvas.drawNumber(orderNumber: Int, x: Float, y: Float, font: Font, color: Int) {
    val label = orderNumber.toString()
    val glyphs = font.getStringGlyphs(label)
    val positions = font.getPositions(glyphs, Point(0f, 0f))
    val bounds = font.getBounds(glyphs)
    val left = bounds.indices.minOf { positions[it].x + bounds[it].left }
    val right = bounds.indices.maxOf { positions[it].x + bounds[it].right }
    val top = bounds.minOf { it.top }
    val bottom = bounds.maxOf { it.bottom }
    drawString(label, x - (left + right) / 2, y - (top + bottom) / 2, font, fill(color))
}

private val NEW_ACTIVITY = MapColors.NewActivity.toArgb()
private val PIN_STROKE_COLOR = MapColors.PinStroke.toArgb()
