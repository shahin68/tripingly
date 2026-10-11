package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MarkerTipShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width / 2f, size.height)
    close()
}

/**
 * A stop's pin: its number in the stop's color, or, once the stop has a cover photo, the photo in a ring of
 * that color with the number in a small badge. The tip marks the point.
 */
@Composable
fun TripMarkerIcon(
    orderNumber: Int,
    color: Color,
    modifier: Modifier = Modifier,
    cover: ImageBitmap? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (cover == null) {
            Box(
                modifier = Modifier
                    .size(PIN_SIZE)
                    .background(color, CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = orderNumber.toString(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Box(modifier = Modifier.size(COVER_PIN_SIZE)) {
                Image(
                    bitmap = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .background(color, CircleShape)
                        .padding(COVER_RING)
                        .clip(CircleShape),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(COVER_BADGE_SIZE)
                        .background(color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = orderNumber.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(width = 12.dp, height = TIP_HEIGHT)
                .offset(y = -TIP_OVERLAP) // Slight overlap to avoid gap
                .background(color, MarkerTipShape)
        )
    }
}

private val PIN_SIZE = 32.dp
private val TIP_HEIGHT = 8.dp
private val TIP_OVERLAP = 2.dp

/** From the bottom of the pin, which sits on the stop, to its top. */
internal fun stopPinHeight(hasCover: Boolean): Dp = (if (hasCover) COVER_PIN_SIZE else PIN_SIZE) + TIP_HEIGHT

/** A pin with a cover photo: the photo's circle, its colored ring and the number badge on it. */
internal val COVER_PIN_SIZE = 44.dp
internal val COVER_RING = 3.dp
internal val COVER_BADGE_SIZE = 18.dp
