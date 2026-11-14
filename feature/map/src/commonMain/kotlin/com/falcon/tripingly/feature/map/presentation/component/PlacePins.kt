package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser
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
import com.falcon.tripingly.core.model.trip.GeoPoint
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A place's pin on the map, how big it is drawn, whether it shows the place's photo and whether the place is
 * along the shown route (drawn in [AlongTheWayPinColor]).
 */
@Immutable
data class PlacePin(
    val place: MapPlace,
    val size: Dp,
    val photo: Boolean = false,
    val isAlongTheWay: Boolean = false,
    /** Drawn as its dot at any zoom, as every other pin is while a route shows. */
    val isDot: Boolean = false,
    /**
     * A route's end or a place tapped while routing: a pin at any zoom, never its dot, until the route closes
     * (Shahin, 2026-11-13).
     */
    val isKept: Boolean = false,
)

/** Drawn as its dot: zoomed out ([isZoomedOut]) unless it's kept, or a dot anyway while a route shows. */
internal fun PlacePin.isDotAt(isZoomedOut: Boolean) = isDot || (isZoomedOut && !isKept)

/**
 * The place pins for the squares in view: places inside [area] that aren't stops of the open day ([hiddenPlaceIds]),
 * liked places first (the most liked first), then the best known, at most [AppConfig.mapMaxPins]. Pins keep their
 * size on screen, so each one costs the map; the server already puts a square's best places first. The first
 * [AppConfig.mapMaxPhotoPins] places that have a photo and are well known or liked enough show it ([showsPhoto]).
 *
 * Panning doesn't reshuffle them: a pin already [shown] that is still in [area] stays, with its photo or icon, even past
 * the cap (a pin on screen never vanishes), and the places new to the view take the free places, best first (Shahin,
 * 2026-11-10; pins in view vanished and came back). Pins only leave once they're out of [area].
 *
 * With [keepUnlisted] (the same zoom or closer than when they showed) a shown pin in [area] stays even when the squares
 * don't list it any more: the server picks each square's places per zoom, so a zoom step or a reload can leave out a
 * small place the map already shows. Zooming out drops the ones the farther squares don't list (Shahin, 2026-11-10).
 */
fun placePins(
    squares: Collection<PlacesInView>,
    area: GeoBounds,
    hiddenPlaceIds: Set<String>,
    config: AppConfig,
    shown: List<PlacePin> = emptyList(),
    keepUnlisted: Boolean = false,
): List<PlacePin> {
    val inView = squares.asSequence()
        .flatMap { it.places }
        .filter { it.location in area && it.id !in hiddenPlaceIds }
        .distinctBy { it.id }
        .sortedWith(PinOrder)
        .toList()
    val shownPhoto = shown.associate { it.place.id to it.photo }
    val listedIds = inView.mapTo(HashSet()) { it.id }
    val unlisted = if (!keepUnlisted) emptyList() else shown.asSequence()
        .map { it.place }
        .filter { it.id !in listedIds && it.location in area && it.id !in hiddenPlaceIds }
        .toList()
    val kept = inView.filter { it.id in shownPhoto } + unlisted
    val keptIds = kept.mapTo(HashSet()) { it.id }
    var photos = kept.count { shownPhoto.getValue(it.id) && it.showsPhoto(config) }
    val pins = kept.map { place -> pin(place, shownPhoto.getValue(place.id) && place.showsPhoto(config), config) } +
        inView.asSequence()
            .filter { it.id !in keptIds }
            .take((config.mapMaxPins - kept.size).coerceAtLeast(0))
            .map { place ->
                val photo = photos < config.mapMaxPhotoPins && place.showsPhoto(config)
                if (photo) photos++
                pin(place, photo, config)
            }
    return pins.sortedWith(compareBy(PinOrder) { it.place })
}

private fun pin(place: MapPlace, photo: Boolean, config: AppConfig) =
    PlacePin(place, if (photo) PhotoPinSize else placePinSize(place, config), photo)

/** A place with a photo shows it on its pin once it's well known or liked enough (server values). */
private fun MapPlace.showsPhoto(config: AppConfig): Boolean =
    coverImageId != null && coverThumbUrl != null &&
        (prominence >= config.photoPinMinProminence || hotCount >= config.photoPinMinHotCount)

/** The clusters of liked places inside [area]. */
fun placeClusters(squares: Collection<PlacesInView>, area: GeoBounds): List<PlaceCluster> =
    squares.flatMap { it.clusters }.filter { it.location in area }

private val PinOrder = compareByDescending<MapPlace> { it.isTripinly }
    .thenByDescending { it.hotCount }
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
    return if (place.isTripinly) maxOf(byProminence, hotSpotSize(place.hotCount)) else byProminence
}

/** Liked places in three sizes, so the most liked stand out. */
fun hotSpotSize(hotCount: Int): Dp = when {
    hotCount >= 20 -> HotSpotSizes[2]
    hotCount >= 5 -> HotSpotSizes[1]
    else -> HotSpotSizes[0]
}

/** Place pins by prominence: below medium, medium, large. */
val PinSizes: List<Dp> = listOf(18.dp, 24.dp, 36.dp)
val HotSpotSizes: List<Dp> = listOf(24.dp, 30.dp, 36.dp)

/** A pin with the place's photo. */
val PhotoPinSize: Dp = 40.dp

/** The tapped place, the biggest pin. */
val SelectedPinSize: Dp = 44.dp

/** A pin while zoomed out below [AppConfig.pinDotBelowZoom]: a small dot, so route lines and stops stay readable. */
val PlaceDotSize: Dp = 10.dp

/** Places along the shown route: violet, apart from the map's places and from the hot spots. */
val AlongTheWayPinColor = Color(0xFF8A5AAB)

/** The look of [this] pin as a dot: only its color matters, so every dot of a color shares one bitmap. */
internal fun PlacePin.dotLook() = PlacePinLook(PlaceCategory.Other, place.isTripinly, PlaceDotSize, isAlongTheWay = isAlongTheWay, isDot = true)

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

/** Draws a dot pin into a bitmap: a small circle of the pin's [fill] color in a white ring. */
fun drawPlaceDot(size: Dp, fill: Color, density: Density, layoutDirection: LayoutDirection): ImageBitmap {
    val px = with(density) { size.roundToPx() }
    val border = with(density) { 1.5.dp.toPx() }
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        drawCircle(Color.White)
        drawCircle(fill, radius = this.size.minDimension / 2 - border)
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

/**
 * What a pin looks like: its category icon, or the photo with [photoId] once its thumbnail has loaded; a dot
 * ([isDot]) while zoomed out.
 */
internal data class PlacePinLook(
    val category: PlaceCategory,
    val isTripinly: Boolean,
    val size: Dp,
    val photoId: String? = null,
    val isAlongTheWay: Boolean = false,
    val isDot: Boolean = false,
)

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

/**
 * One end of a route on the map ([isStart]: where it starts): the end's pin, a place's grown, with a thick ring round it
 * in the way of travelling's [color] (Shahin, 2026-11-13: for now, to be redesigned after a few rounds of refinements).
 * [pinSize] is the pin's size, so the ring hugs it.
 */
@Immutable
data class RouteEndMark(val position: GeoPoint, val isStart: Boolean, val pinSize: Dp, val color: Color)

/** A route end's look and where on it the end's point is: [anchorY] as a share of its height (x is the middle). */
class RouteEndIcon(val bitmap: ImageBitmap, val anchorY: Float)

/**
 * Draws a route end's ring, drawn under the end's pin: a [color] disc [RouteEndRingWidth] wider than the pin all round,
 * in a thin white edge so it shows on any map. The pin covers its middle. The destination ([isStart] false) also has
 * the route bar's flag standing on the ring's top, in [color] with a white edge (Shahin, 2026-11-14: "we can't tell
 * which pin is start and which is destination").
 */
fun drawRouteEndRing(pinSize: Dp, color: Color, isStart: Boolean, density: Density, layoutDirection: LayoutDirection): RouteEndIcon {
    val ring = with(density) { (pinSize + RouteEndRingWidth * 2).roundToPx() }
    val flagRoom = if (isStart) 0 else with(density) { RouteEndFlagHeight.roundToPx() }
    val edge = with(density) { RouteEndRingEdge.toPx() }
    val bitmap = ImageBitmap(ring, ring + flagRoom)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(ring.toFloat(), (ring + flagRoom).toFloat())) {
        val center = Offset(ring / 2f, flagRoom + ring / 2f)
        drawCircle(Color.White, radius = ring / 2f, center = center)
        drawCircle(color, radius = ring / 2f - edge, center = center)
        if (!isStart) {
            // The Material flag (24 units = 24 dp): its pole's foot (x 6, y 21) stands on the ring's top.
            val unit = 1.dp.toPx()
            translate(left = center.x - 6 * unit, top = flagRoom - 21 * unit) {
                scale(unit, unit, pivot = Offset.Zero) {
                    drawPath(RouteEndFlag, Color.White, style = Stroke(width = 2.5f, join = StrokeJoin.Round))
                    drawPath(RouteEndFlag, color)
                }
            }
        }
    }
    return RouteEndIcon(bitmap, anchorY = (flagRoom + ring / 2f) / (ring + flagRoom))
}

/** Material's filled flag (Icons.Filled.Flag), the route bar's destination icon, in its 24-unit box. */
private val RouteEndFlag = PathParser().parsePathString("M14.4,6L14,4H5v17h2v-7h5.6l0.4,2h7V6z").toPath()

/** How much a destination's flag rises above its ring (the flag's 17 units and a little for its white edge). */
private val RouteEndFlagHeight = 19.dp

/** How far a route end's ring reaches out from its pin. */
private val RouteEndRingWidth = 5.dp
private val RouteEndRingEdge = 1.dp

private val CLUSTER_RADIUS = 18.dp
private val CLUSTER_PADDING = 8.dp
private val CLUSTER_BORDER = 2.dp
private val CLUSTER_TEXT = 14.sp
