package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
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
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlacesInView
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
    /** Found by the map searched by name: red, while everything else is see-through ([SearchDimAlpha]). */
    val isSearchResult: Boolean = false,
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

/** The places the map was searched for by name: red, like Google Maps' results (Shahin, 2026-11-19). */
val SearchResultPinColor = Color(0xFFE53935)

/**
 * The look of [this] pin as a dot: only its color matters, so every dot of a color shares one bitmap. The same while a
 * route shows: its see-through is the marker's own opacity ([dotAlpha]), so a route turning on or off makes no new
 * markers (Shahin's recording, 2026-11-21: swapping ~150 dots' bitmaps stopped the map for a frame).
 */
internal fun PlacePin.dotLook() = PlacePinLook(PlaceCategory.Other, place.isTripinly, PlaceDotSize, isAlongTheWay = isAlongTheWay, isDot = true)

/** How much of [this] pin's dot shows: see-through while a route shows ([PlacePin.isDot]). */
internal val PlacePin.dotAlpha: Float get() = if (isDot) RouteDotAlpha else 1f

/**
 * How much of a place's dot shows while a route shows ([PlacePin.isDot]): see-through, so the line, its ends, the stops
 * and the places along it lead; every other pin stays as it is (Shahin, 2026-11-16, D1/D2).
 */
const val RouteDotAlpha = 0.3f

/**
 * How much shows of everything that isn't a search result while the map is searched by name (stops, the line, other
 * pins): as much as a place's dot while a route shows (Shahin, 2026-11-19).
 */
const val SearchDimAlpha = RouteDotAlpha

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
    val border = with(density) { PIN_STROKE.toPx() }
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        drawCircle(MapColors.PinStroke)
        drawCircle(fill, radius = this.size.minDimension / 2 - border)
        val iconSize = this.size * 0.6f
        translate((this.size.width - iconSize.width) / 2, (this.size.height - iconSize.height) / 2) {
            with(icon) { draw(iconSize, colorFilter = ColorFilter.tint(iconColor)) }
        }
    }
    return bitmap
}

/**
 * Draws a brand store's pin into a bitmap: a rounded square, the brand's [logo] on white inside one thin ring of the pin's
 * [fill] colour, with a very small margin (Shahin, 2026-11-20: square pins behave like the others, only square; the logo
 * fits inside with a very small spacing). The tile stays white in dark mode too: logos are made for white.
 */
fun drawPlaceLogoPin(
    logo: ImageBitmap,
    size: Dp,
    fill: Color,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val px = with(density) { size.roundToPx() }
    val border = with(density) { PIN_STROKE.toPx() }
    val margin = with(density) { LOGO_MARGIN.toPx() }
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        val corner = this.size.minDimension * LOGO_PIN_CORNER
        drawRoundRect(fill, cornerRadius = CornerRadius(corner))
        val tile = Rect(border, border, this.size.width - border, this.size.height - border)
        val tileCorner = CornerRadius((corner - border).coerceAtLeast(0f))
        drawRoundRect(Color.White, topLeft = tile.topLeft, size = tile.size, cornerRadius = tileCorner)
        val inset = (border + margin).roundToInt()
        val side = px - inset * 2
        clipPath(Path().apply { addRoundRect(RoundRect(tile, tileCorner)) }) {
            drawImage(
                logo,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(logo.width, logo.height),
                dstOffset = IntOffset(inset, inset),
                dstSize = IntSize(side, side),
            )
        }
    }
    return bitmap
}

/** How round a brand pin's corners are, of its side: a rounded square like Google Maps' brand pins. */
private const val LOGO_PIN_CORNER = 0.28f

/** The white between a brand pin's ring and its logo: very small, so the logo fills the pin. */
private val LOGO_MARGIN = 1.5.dp

/**
 * Draws a dot pin into a bitmap: a small circle of the pin's [fill] color in the pins' thin ring, the whole dot [alpha]
 * strong (the ring is drawn round the fill, not under it, so a see-through dot is evenly see-through).
 */
fun drawPlaceDot(size: Dp, fill: Color, density: Density, layoutDirection: LayoutDirection): ImageBitmap {
    val px = with(density) { size.roundToPx() }
    val border = with(density) { PIN_STROKE.toPx() }
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        val radius = this.size.minDimension / 2
        drawCircle(MapColors.PinStroke, radius = radius - border / 2, style = Stroke(border))
        drawCircle(fill, radius = radius - border)
    }
    return bitmap
}

/**
 * Draws a photo pin into a bitmap: the place's photo, cropped to a circle, in one thin ring of the pin's [fill] color, so
 * a hot spot or a place along the way still tells itself apart (Shahin, 2026-11-15: a single stroke, not a white and a
 * coloured one; "make these thin strokes colored").
 */
fun drawPlacePhotoPin(
    photo: ImageBitmap,
    size: Dp,
    fill: Color,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val px = with(density) { size.roundToPx() }
    val border = with(density) { PIN_STROKE.toPx() }
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        drawCircle(fill)
        val inset = border.roundToInt().coerceAtLeast(1)
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
 * What a pin looks like: its category icon, the photo with [photoId] once its thumbnail has loaded, or its brand's logo
 * ([logoUrl]) on a rounded square; a dot ([isDot]) while zoomed out.
 */
internal data class PlacePinLook(
    val category: PlaceCategory,
    val isTripinly: Boolean,
    val size: Dp,
    val photoId: String? = null,
    val isAlongTheWay: Boolean = false,
    val isDot: Boolean = false,
    /** In [SearchResultPinColor]. */
    val isSearchResult: Boolean = false,
    /** A brand store's logo, by its URL once loaded: drawn as a rounded square ([drawPlaceLogoPin]) instead of icon or photo. */
    val logoUrl: String? = null,
)

/**
 * Draws a cluster of liked places: a bubble with their count in bold, in the pins' thin ring, at least
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
    val border = with(density) { PIN_STROKE.toPx() }
    val px = ceil(radius * 2).toInt()
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        drawCircle(MapColors.PinStroke, radius)
        drawCircle(fill, radius - border)
        drawText(text, topLeft = Offset((size.width - text.size.width) / 2, (size.height - text.size.height) / 2))
    }
    return bitmap
}

private val CLUSTER_RADIUS = 18.dp
private val CLUSTER_PADDING = 8.dp
private val CLUSTER_TEXT = 14.sp
