package com.falcon.tripingly.feature.map.presentation.component

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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlacesInView
import kotlin.math.ceil

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

/** A place's pin on the map and how big it is drawn. */
@Immutable
data class PlacePin(val place: MapPlace, val size: Dp)

/**
 * The place pins for the squares in view: places inside [area] that aren't stops of the trip ([hiddenPlaceIds]),
 * liked places first (the most liked first), then the best known, at most [AppConfig.mapMaxPins]. Pins keep their
 * size on screen, so each one costs the map; the server already puts a square's best places first.
 */
fun placePins(squares: Collection<PlacesInView>, area: GeoBounds, hiddenPlaceIds: Set<String>, config: AppConfig): List<PlacePin> =
    squares.asSequence()
        .flatMap { it.places }
        .filter { it.location in area && it.id !in hiddenPlaceIds }
        .distinctBy { it.id }
        .sortedWith(PinOrder)
        .take(config.mapMaxPins)
        .map { PlacePin(it, placePinSize(it, config)) }
        .toList()

/** The clusters of liked places inside [area]. */
fun placeClusters(squares: Collection<PlacesInView>, area: GeoBounds): List<PlaceCluster> =
    squares.flatMap { it.clusters }.filter { it.location in area }

private val PinOrder = compareByDescending<MapPlace> { it.isTripinly }
    .thenByDescending { it.likeCount }
    .thenByDescending { it.prominence }
    .thenBy { it.id }

/**
 * How big a place's pin is: the better known, the bigger ([AppConfig.pinLargeMinProminence] and
 * [AppConfig.pinMediumMinProminence]). A liked place grows with its likes too and takes the bigger size.
 */
fun placePinSize(place: MapPlace, config: AppConfig): Dp {
    val byProminence = when {
        place.prominence >= config.pinLargeMinProminence -> PinSizes[2]
        place.prominence >= config.pinMediumMinProminence -> PinSizes[1]
        else -> PinSizes[0]
    }
    return if (place.isTripinly) maxOf(byProminence, hotSpotSize(place.likeCount)) else byProminence
}

/** Liked places in three sizes, so the most liked stand out. */
fun hotSpotSize(likeCount: Int): Dp = when {
    likeCount >= 20 -> HotSpotSizes[2]
    likeCount >= 5 -> HotSpotSizes[1]
    else -> HotSpotSizes[0]
}

/** Place pins by prominence: below medium, medium, large. */
val PinSizes: List<Dp> = listOf(18.dp, 30.dp, 36.dp)
val HotSpotSizes: List<Dp> = listOf(24.dp, 30.dp, 36.dp)

/** The tapped place, the biggest pin. */
val SelectedPinSize: Dp = 44.dp

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

/** What a pin looks like. */
internal data class PlacePinLook(val category: PlaceCategory, val isTripinly: Boolean, val size: Dp)

/**
 * Draws a cluster of liked places: a bubble with their count in bold, in a white ring, at least
 * [CLUSTER_RADIUS] round and wider for long counts.
 */
fun drawClusterPin(
    count: Int,
    fill: Color,
    textColor: Color,
    textMeasurer: TextMeasurer,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val text = textMeasurer.measure(
        count.toString(),
        TextStyle(color = textColor, fontSize = CLUSTER_TEXT, fontWeight = FontWeight.Bold),
        density = density,
        layoutDirection = layoutDirection,
    )
    val radius = with(density) { maxOf(CLUSTER_RADIUS.toPx(), text.size.width / 2f + CLUSTER_PADDING.toPx()) }
    val border = with(density) { CLUSTER_BORDER.toPx() }
    val px = ceil(radius * 2).toInt()
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        drawCircle(Color.White, radius)
        drawCircle(fill, radius - border)
        drawText(text, topLeft = Offset((size.width - text.size.width) / 2, (size.height - text.size.height) / 2))
    }
    return bitmap
}

private val CLUSTER_RADIUS = 18.dp
private val CLUSTER_PADDING = 8.dp
private val CLUSTER_BORDER = 2.dp
private val CLUSTER_TEXT = 14.sp
