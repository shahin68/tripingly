package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.falcon.tripingly.core.designsystem.component.NewIndicator
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors

/**
 * A stop's pin, a circle centered on the stop: its number on near-black ivy in a white ring, or, once the stop
 * has a cover photo, the photo in the white ring with the number in a small badge. [isNew]: a dot at the top left
 * says something happened here I haven't seen.
 */
@Composable
fun TripMarkerIcon(
    orderNumber: Int,
    modifier: Modifier = Modifier,
    cover: ImageBitmap? = null,
    isNew: Boolean = false,
) {
    Box(modifier) {
        PinBody(orderNumber, cover)
        if (isNew) {
            val inset = newDotInset(hasCover = cover != null)
            NewIndicator(Modifier.offset(inset, inset), size = NEW_DOT_SIZE - PIN_RING * 2, strokeColor = MaterialTheme.extendedColors.onStop, strokeWidth = PIN_RING)
        }
    }
}

@Composable
private fun PinBody(orderNumber: Int, cover: ImageBitmap?) {
    if (cover == null) {
        Box(
            modifier = Modifier
                .size(PIN_SIZE)
                .background(MaterialTheme.extendedColors.onStop, CircleShape)
                .padding(PIN_RING)
                .background(MaterialTheme.extendedColors.stop, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PinNumber(orderNumber, fontSize = 13)
        }
    } else {
        Box(modifier = Modifier.size(COVER_PIN_SIZE)) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.extendedColors.onStop, CircleShape)
                    .padding(COVER_RING)
                    .clip(CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(COVER_BADGE_SIZE)
                    .background(MaterialTheme.extendedColors.stop, CircleShape)
                    .border(PIN_RING, MaterialTheme.extendedColors.onStop, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PinNumber(orderNumber, fontSize = 10)
            }
        }
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
internal val PIN_RING = 2.dp

/** From the stop, at the pin's center, to the pin's top edge. */
internal fun stopPinRadius(hasCover: Boolean): Dp = (if (hasCover) COVER_PIN_SIZE else PIN_SIZE) / 2

/** A pin with a cover photo: the photo's circle, its white ring and the number badge on it. */
internal val COVER_PIN_SIZE = 44.dp
internal val COVER_RING = 3.dp
internal val COVER_BADGE_SIZE = 18.dp

/** The "new" dot with its white ring, in the pin's top left corner on the pin's edge, clear of the number. */
internal val NEW_DOT_SIZE = 12.dp // an 8 dp red dot in its 2 dp ring (Shahin, 2026-11-08)

/**
 * How far the "new" dot's box sits in from the pin's top left corner: a photo pin's dot is centred on its circle's
 * edge at 45° (Shahin, 2026-11-08). The small numbered pin's edge point is closer to the corner than the dot's
 * radius, so its dot stays in the corner, where it is clear of the number.
 */
internal fun newDotInset(hasCover: Boolean): Dp =
    if (hasCover) COVER_PIN_SIZE / 2 * (1 - CORNER_EDGE_POINT) - NEW_DOT_SIZE / 2 else 0.dp

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
            TripMarkerIcon(orderNumber = 3, cover = ImageBitmap(64, 64))
            TripMarkerIcon(orderNumber = 2, isNew = true)
            TripMarkerIcon(orderNumber = 6, cover = ImageBitmap(64, 64), isNew = true)
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
