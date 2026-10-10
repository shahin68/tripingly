package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory

/** What a kind of place looks like, on its pin and in its card. */
val PlaceCategory.icon: ImageVector
    get() = when (this) {
        PlaceCategory.Cafe -> Icons.Default.LocalCafe
        PlaceCategory.Restaurant -> Icons.Default.Restaurant
        PlaceCategory.Bar -> Icons.Default.LocalBar
        PlaceCategory.Attraction -> Icons.Default.Attractions
        PlaceCategory.Museum -> Icons.Default.Museum
        PlaceCategory.Historic -> Icons.Default.Castle
        PlaceCategory.Park -> Icons.Default.Park
        PlaceCategory.Nature -> Icons.Default.Landscape
        PlaceCategory.Landmark -> Icons.Default.AccountBalance
        PlaceCategory.Other -> Icons.Default.Place
    }

/** How big a place pin is: hot spots grow with their likes, and the tapped place is the biggest. */
fun placePinSize(place: MapPlace, isSelected: Boolean): Dp = when {
    isSelected -> 44.dp
    place.isTripinly -> hotSpotSize(place.likeCount)
    else -> 22.dp
}

/** Hot spots in three sizes, so the most liked places stand out. */
fun hotSpotSize(likeCount: Int): Dp = when {
    likeCount >= 20 -> 36.dp
    likeCount >= 5 -> 30.dp
    else -> 24.dp
}

/**
 * Draws a place pin into a bitmap: a round badge with the place's category icon. The map reuses one
 * bitmap for every pin that looks the same, so a pin costs nothing to draw once its look exists.
 */
fun drawPlacePin(
    icon: Painter,
    size: Dp,
    fill: Color,
    iconColor: Color,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val px = with(density) { size.roundToPx() }
    val border = with(density) { 2.dp.toPx() }
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        drawCircle(Color.White)
        drawCircle(fill, radius = this.size.minDimension / 2 - border)
        val iconSize = this.size * 0.6f
        translate((this.size.width - iconSize.width) / 2, (this.size.height - iconSize.height) / 2) {
            with(icon) { draw(iconSize, colorFilter = ColorFilter.tint(iconColor)) }
        }
    }
    return bitmap
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
