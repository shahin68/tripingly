package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme

/**
 * A stop's pin, a circle centered on the stop: its number on near-black ivy in a white ring, or, once the stop
 * has a cover photo, the photo in the white ring with the number in a small badge.
 */
@Composable
fun TripMarkerIcon(
    orderNumber: Int,
    modifier: Modifier = Modifier,
    cover: ImageBitmap? = null,
) {
    if (cover == null) {
        Box(
            modifier = modifier
                .size(PIN_SIZE)
                .background(MapColors.OnStop, CircleShape)
                .padding(PIN_RING)
                .background(MapColors.Stop, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PinNumber(orderNumber, fontSize = 13)
        }
    } else {
        Box(modifier = modifier.size(COVER_PIN_SIZE)) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .background(MapColors.OnStop, CircleShape)
                    .padding(COVER_RING)
                    .clip(CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(COVER_BADGE_SIZE)
                    .background(MapColors.Stop, CircleShape)
                    .border(PIN_RING / 2, MapColors.OnStop, CircleShape),
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
        color = MapColors.OnStop,
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

/** A stop's pin with one and two digits, and with a cover. */
@Preview
@Composable
private fun TripMarkerIconPreview() {
    TripinglyTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.background(MapColors.Plan.copy(alpha = 0.2f)).padding(8.dp)) {
            TripMarkerIcon(orderNumber = 1)
            TripMarkerIcon(orderNumber = 12)
            TripMarkerIcon(orderNumber = 3, cover = ImageBitmap(64, 64))
        }
    }
}

@Preview
@Composable
private fun TripMarkerIconDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.background(MapColors.Stop.copy(alpha = 0.6f)).padding(8.dp)) {
            TripMarkerIcon(orderNumber = 4)
            TripMarkerIcon(orderNumber = 5, cover = ImageBitmap(64, 64))
        }
    }
}
