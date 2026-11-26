package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A stop's pin centered on the stop: its number on ivy in the pins' one thin ring, a circle whatever the number, or,
 * once the stop has a cover photo, the photo in that ring with the number in a small badge, its right half-circle on
 * the ring at 315°, stretched outwards to the left into a pill when the number is wider (Shahin, 2026-11-26). [isNew]:
 * a red dot on the ring at 45°, in a gap cut out of the pin so the map shows through, says something happened here
 * I haven't seen. [halo]: the day's route shows, so the pin stands in a soft halo of the route's colour (see
 * [STOP_HALO_WIDTH]). The marker reaches as far on each side of the stop as on the opposite one, so the stop stays at
 * its center (see [stopMarkerHalfSize]).
 */
@Composable
fun TripMarkerIcon(
    orderNumber: Int,
    modifier: Modifier = Modifier,
    cover: ImageBitmap? = null,
    isNew: Boolean = false,
    halo: Color? = null,
) {
    val hasCover = cover != null
    Layout(
        content = {
            val haloModifier = if (halo != null) Modifier.background(halo.copy(alpha = STOP_HALO_ALPHA), CircleShape).padding(STOP_HALO_WIDTH) else Modifier
            Box(haloModifier) { PinBody(orderNumber, cover) }
            if (hasCover) NumberPill(orderNumber, height = COVER_BADGE_SIZE, fontSize = 10, padding = BADGE_NUMBER_PADDING)
        },
        modifier = if (isNew) modifier.newDot(hasCover) else modifier,
    ) { measurables, _ ->
        val body = measurables[0].measure(Constraints())
        val badge = measurables.getOrNull(1)?.measure(Constraints())
        val half = stopMarkerHalfSize(hasCover, isNew, Size(body.width.toFloat(), body.height.toFloat()), badge?.width?.toFloat() ?: 0f)
        // The same room on opposite sides, whole pixels, so the stop stays exactly at the marker's center.
        val roomX = ceil(half.width - body.width / 2f).toInt()
        val roomY = ceil(half.height - body.height / 2f).toInt()
        val width = body.width + 2 * roomX
        val height = body.height + 2 * roomY
        layout(width, height) {
            body.place(roomX, roomY)
            if (badge != null) {
                val corner = ringCorner(hasCover).toPx()
                // The badge's right half-circle centered on the ring at 315°, a wider badge reaching further left.
                badge.place((width / 2f - corner + COVER_BADGE_SIZE.toPx() / 2 - badge.width).roundToInt(), (height / 2f - corner - COVER_BADGE_SIZE.toPx() / 2).roundToInt())
            }
        }
    }
}

/**
 * The "new" dot on the pin's ring at 45°: a gap round it is cut out of everything drawn under it (pin, badge, halo),
 * so the map shows through, then the red dot is drawn in the middle (Shahin, 2026-11-26).
 */
private fun Modifier.newDot(hasCover: Boolean): Modifier = drawWithContent {
    val corner = ringCorner(hasCover).toPx()
    val dot = center + Offset(corner, -corner)
    drawContext.canvas.saveLayer(size.toRect(), Paint())
    drawContent()
    drawCircle(Color.Black, radius = NEW_DOT_SIZE.toPx() / 2 + NEW_DOT_GAP.toPx(), center = dot, blendMode = BlendMode.Clear)
    drawContext.canvas.restore()
    drawCircle(MapColors.NewActivity, radius = NEW_DOT_SIZE.toPx() / 2, center = dot)
}

/**
 * The soft halo round each stop while the day's route shows: this wide, of the route's colour at [STOP_HALO_ALPHA], in
 * the pin's own shape, so the stops stand out among the places along the line (Shahin, 2026-11-16, S3).
 */
internal val STOP_HALO_WIDTH = 9.dp
internal const val STOP_HALO_ALPHA = 0.32f

@Composable
private fun PinBody(orderNumber: Int, cover: ImageBitmap?) {
    if (cover == null) {
        // No room added round the number: the pin stays a circle (a pill only past 4 digits, which no day has).
        NumberPill(orderNumber, height = PIN_SIZE, fontSize = 13, padding = 0.dp)
    } else {
        Image(
            bitmap = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(COVER_PIN_SIZE)
                .background(MapColors.PinStroke, CircleShape)
                .padding(PIN_STROKE)
                .clip(CircleShape),
        )
    }
}

/**
 * [orderNumber] in the middle of a circle [height] round, stretched into a pill when the number is wider, edged in [ring]
 * (white on the map, the sheet's colour where it sits on one).
 */
@Composable
internal fun NumberPill(orderNumber: Int, height: Dp, fontSize: Int, padding: Dp, modifier: Modifier = Modifier, ring: Color = MapColors.PinStroke) {
    Box(
        modifier = modifier
            .height(height)
            .widthIn(min = height)
            .background(MaterialTheme.extendedColors.stop, CircleShape)
            .border(PIN_STROKE, ring, CircleShape)
            .padding(horizontal = padding),
        contentAlignment = Alignment.Center,
    ) {
        PinNumber(orderNumber, fontSize)
    }
}

@Composable
private fun PinNumber(orderNumber: Int, fontSize: Int) {
    Text(
        text = orderNumber.toString(),
        color = MaterialTheme.extendedColors.onStop,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        style = PinNumberStyle,
    )
}

/** The number sits in the middle of its circle: one line exactly as tall as its glyphs, without the theme's line height. */
private val PinNumberStyle = TextStyle(
    lineHeight = 1.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

internal val PIN_SIZE = 32.dp

/** The one ring round every pin on the map, in [MapColors.PinStroke] (Shahin, 2026-11-15: 1 dp, a single ring). */
internal val PIN_STROKE = 1.dp

/** Room on either side of a photo pin's number before its badge stretches into a pill. */
internal val BADGE_NUMBER_PADDING = 4.dp

/** From the stop, at the pin's center, to the pin's top edge. */
internal fun stopPinRadius(hasCover: Boolean): Dp = (if (hasCover) COVER_PIN_SIZE else PIN_SIZE) / 2

/** A pin with a cover photo: the photo's circle, its white ring and the number badge on it. */
internal val COVER_PIN_SIZE = 44.dp
internal val COVER_BADGE_SIZE = 18.dp

/** The "new" dot and the gap cut round it (Shahin, 2026-11-26: smaller than the approved 10 dp, a thicker edge, cut). */
internal val NEW_DOT_SIZE = 8.dp
internal val NEW_DOT_GAP = 2.dp

/**
 * From the pin's center to the middle of its ring at 45°, along each axis: the number badge is centered there at 315°
 * and the "new" dot at 45° (Shahin, 2026-11-25, the approved pin layout).
 */
internal fun ringCorner(hasCover: Boolean): Dp = (stopPinRadius(hasCover) - PIN_STROKE / 2) * CORNER_EDGE_POINT

/**
 * Half the stop marker's width and height, in px: as far as the [body] (with its halo), the number badge [badgeWidth]
 * wide (0 without one) or the "new" dot reach from the stop. Both platforms draw the marker this big around the stop.
 */
internal fun Density.stopMarkerHalfSize(hasCover: Boolean, isNew: Boolean, body: Size, badgeWidth: Float): Size {
    val corner = ringCorner(hasCover).toPx()
    val badge = COVER_BADGE_SIZE.toPx() / 2
    val dot = if (isNew) corner + NEW_DOT_SIZE.toPx() / 2 + NEW_DOT_GAP.toPx() else 0f
    val hasBadge = badgeWidth > 0f
    return Size(
        maxOf(body.width / 2, if (hasBadge) badgeWidth + corner - badge else 0f, dot),
        maxOf(body.height / 2, if (hasBadge) corner + badge else 0f, dot),
    )
}

/** cos 45°: where a circle's edge meets the diagonal to its box's corner. */
private const val CORNER_EDGE_POINT = 0.7071f

/** A stop's pin with one and two digits, with a cover, and with the "new" dot. */
@Preview
@Composable
private fun TripMarkerIconPreview() {
    TripinglyTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.background(Color(0xFFECEDEE)).padding(8.dp)) {
            TripMarkerIcon(orderNumber = 1)
            TripMarkerIcon(orderNumber = 12)
            TripMarkerIcon(orderNumber = 108)
            TripMarkerIcon(orderNumber = 11, cover = ImageBitmap(64, 64))
            TripMarkerIcon(orderNumber = 3, cover = ImageBitmap(64, 64))
            TripMarkerIcon(orderNumber = 2, isNew = true)
            TripMarkerIcon(orderNumber = 6, cover = ImageBitmap(64, 64), isNew = true)
            TripMarkerIcon(orderNumber = 4, halo = Color(0xFF1F9E6B))
            TripMarkerIcon(orderNumber = 5, cover = ImageBitmap(64, 64), isNew = true, halo = Color(0xFF1F9E6B))
        }
    }
}

@Preview
@Composable
private fun TripMarkerIconDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.background(Color(0xFF1A1C1E)).padding(8.dp)) {
            TripMarkerIcon(orderNumber = 4)
            TripMarkerIcon(orderNumber = 5, cover = ImageBitmap(64, 64))
            TripMarkerIcon(orderNumber = 7, isNew = true)
            TripMarkerIcon(orderNumber = 8, cover = ImageBitmap(64, 64), isNew = true)
        }
    }
}
