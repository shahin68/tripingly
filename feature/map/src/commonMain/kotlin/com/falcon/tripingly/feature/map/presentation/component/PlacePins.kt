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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import kotlin.math.roundToInt

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

/** A place's pin on the map, how big it is drawn and whether it shows the place's photo. */
@Immutable
data class PlacePin(val place: MapPlace, val size: Dp, val photo: Boolean = false)

/**
 * The place pins for the squares in view: places inside [area] that aren't stops of the trip ([hiddenPlaceIds]),
 * liked places first (the most liked first), then the best known, at most [AppConfig.mapMaxPins]. Pins keep their
 * size on screen, so each one costs the map; the server already puts a square's best places first. The first
 * [AppConfig.mapMaxPhotoPins] places that have a photo and are well known or liked enough show it ([showsPhoto]).
 */
fun placePins(squares: Collection<PlacesInView>, area: GeoBounds, hiddenPlaceIds: Set<String>, config: AppConfig): List<PlacePin> {
    var photos = 0
    return squares.asSequence()
        .flatMap { it.places }
        .filter { it.location in area && it.id !in hiddenPlaceIds }
        .distinctBy { it.id }
        .sortedWith(PinOrder)
        .take(config.mapMaxPins)
        .map { place ->
            val photo = photos < config.mapMaxPhotoPins && place.showsPhoto(config)
            if (photo) photos++
            PlacePin(place, if (photo) PhotoPinSize else placePinSize(place, config), photo)
        }
        .toList()
}

/** A place with a photo shows it on its pin once it's well known or liked enough (server values). */
private fun MapPlace.showsPhoto(config: AppConfig): Boolean =
    coverImageId != null && coverThumbUrl != null &&
        (prominence >= config.photoPinMinProminence || likeCount >= config.photoPinMinLikes)

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

/** A pin with the place's photo. */
val PhotoPinSize: Dp = 40.dp

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

/**
 * Draws a photo pin into a bitmap: the place's photo, cropped to a circle, in a white ring and a ring of the
 * pin's [fill] color.
 */
fun drawPlacePhotoPin(
    photo: ImageBitmap,
    size: Dp,
    fill: Color,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val px = with(density) { size.roundToPx() }
    val border = with(density) { 2.dp.toPx() }
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        drawCircle(Color.White)
        drawCircle(fill, radius = this.size.minDimension / 2 - border)
        val inset = (border * 2).roundToInt()
        val side = px - inset * 2
        val crop = minOf(photo.width, photo.height)
        clipPath(Path().apply { addOval(Rect(inset.toFloat(), inset.toFloat(), (inset + side).toFloat(), (inset + side).toFloat())) }) {
            drawImage(
                photo,
                srcOffset = IntOffset((photo.width - crop) / 2, (photo.height - crop) / 2),
                srcSize = IntSize(crop, crop),
                dstOffset = IntOffset(inset, inset),
                dstSize = IntSize(side, side),
            )
        }
    }
    return bitmap
}

/** What a pin looks like: its category icon, or the photo with [photoId] once its thumbnail has loaded. */
internal data class PlacePinLook(val category: PlaceCategory, val isTripinly: Boolean, val size: Dp, val photoId: String? = null)

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
