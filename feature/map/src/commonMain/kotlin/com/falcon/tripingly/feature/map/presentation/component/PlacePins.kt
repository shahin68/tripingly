package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A place on the map, drawn into a bitmap that the map reuses for every place that looks the same:
 * a place liked on Tripinly gets a heart pin that grows with its likes, an OpenStreetMap place a
 * small dot.
 */
@Composable
fun rememberPlacePinBitmap(isTripinly: Boolean, likeCount: Int): ImageBitmap {
    val fill = if (isTripinly) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
    val heartColor = MaterialTheme.colorScheme.onTertiary
    val heart = rememberVectorPainter(Icons.Default.Favorite)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val size = if (isTripinly) hotSpotSize(likeCount) else 14.dp
    return remember(fill, heartColor, heart, density, size) {
        val px = with(density) { size.roundToPx() }
        val border = with(density) { 2.dp.toPx() }
        val bitmap = ImageBitmap(px, px)
        CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
            drawCircle(Color.White)
            drawCircle(fill, radius = this.size.minDimension / 2 - border)
            if (isTripinly) {
                val heartSize = this.size / 2f
                translate(heartSize.width / 2, heartSize.height / 2) {
                    with(heart) { draw(heartSize, colorFilter = ColorFilter.tint(heartColor)) }
                }
            }
        }
        bitmap
    }
}

/** Hot spots in three sizes, so the most liked places stand out. */
fun hotSpotSize(likeCount: Int): Dp = when {
    likeCount >= 20 -> 36.dp
    likeCount >= 5 -> 30.dp
    else -> 24.dp
}

/** Several Tripinly places while zoomed out, with how many. */
@Composable
fun PlaceClusterPin(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = MaterialTheme.colorScheme.onTertiary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
