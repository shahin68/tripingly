package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.falcon.tripingly.core.model.place.PlaceSquare
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import platform.Foundation.NSData
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/**
 * Paints the place pins into map tiles, as `PlaceTileProvider` does on Android: the map asks for
 * tiles on its own background threads, keeps them and fades them in, so pins never cost the main
 * thread a frame. A tile shows the places of the squares under it at the tile's zoom, and the pins
 * reaching into it from the next squares, so pins on a tile edge are drawn whole.
 */
internal class PlaceTilePainter(
    /** One image per pin look, drawn once. */
    pins: Map<PlacePinLook, ImageBitmap>,
    clusterColor: Int,
    onClusterColor: Int,
    /** Pixels per screen point. */
    private val density: Float,
    fontScale: Float,
) : GoogleMapTiles {

    /** Swapped by the map when the trip's stops change, together with clearing the drawn tiles. */
    @Volatile
    var source: MapPlaceSource? = null

    private val pins = pins.mapValues { Image.makeFromBitmap(it.value.asSkiaBitmap()) }
    override val size = (PlaceTile.POINTS * density).roundToInt()
    private val margin = pins.values.maxOf { it.width } / density / 2.0 + PlaceTile.CLUSTER_RADIUS_POINTS
    private val clusterPaint = Paint().apply {
        color = clusterColor
        isAntiAlias = true
    }
    private val clusterBorderPaint = Paint().apply {
        color = WHITE
        isAntiAlias = true
    }
    private val clusterTextPaint = Paint().apply {
        color = onClusterColor
        isAntiAlias = true
    }
    private val clusterFont = boldFont(PlaceTile.CLUSTER_TEXT_POINTS * density * fontScale)

    override fun tile(x: Int, y: Int, zoom: Int): NSData? {
        val source = source ?: return null
        val tile = PlaceTile(x, y, zoom)
        // Null: not loaded now; the map asks again a little later.
        val squares = runBlocking { withTimeoutOrNull(TILE_WAIT) { source.placesIn(PlaceSquare.covering(tile.area(margin), zoom)) } }
            ?: return null
        val places = squares.flatMap { it.places }
        val clusters = squares.flatMap { it.clusters }
        if (places.isEmpty() && clusters.isEmpty()) return NSData()

        val surface = Surface.makeRasterN32Premul(size, size)
        val canvas = surface.canvas
        places.inDrawingOrder().forEach { place ->
            val pin = pins.getValue(PlacePinLook(place.category, place.isTripinly, placePinSize(place, isSelected = false)))
            val cx = tile.x(place.location.lng).toFloat() * density
            val cy = tile.y(place.location.lat).toFloat() * density
            canvas.drawImage(pin, cx - pin.width / 2f, cy - pin.height / 2f)
        }
        val metrics = clusterFont.metrics
        clusters.forEach { cluster ->
            val cx = tile.x(cluster.location.lng).toFloat() * density
            val cy = tile.y(cluster.location.lat).toFloat() * density
            val label = cluster.count.toString()
            val textWidth = clusterFont.measureTextWidth(label)
            val radius = maxOf(PlaceTile.CLUSTER_RADIUS_POINTS * density, textWidth / 2 + PlaceTile.CLUSTER_PADDING_POINTS * density)
            canvas.drawCircle(cx, cy, radius, clusterBorderPaint)
            canvas.drawCircle(cx, cy, radius - PlaceTile.CLUSTER_BORDER_POINTS * density, clusterPaint)
            canvas.drawString(label, cx - textWidth / 2, cy - (metrics.ascent + metrics.descent) / 2, clusterFont, clusterTextPaint)
        }
        return surface.makeImageSnapshot().png()
    }

    private companion object {
        val TILE_WAIT = 10.seconds
    }
}
