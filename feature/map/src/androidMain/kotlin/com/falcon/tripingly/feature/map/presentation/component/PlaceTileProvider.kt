package com.falcon.tripingly.feature.map.presentation.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/**
 * Paints the place pins into map tiles. The map asks for tiles on its own background threads, keeps
 * them and fades them in, so pins never cost the main thread a frame, however many there are. A tile
 * shows the places of the squares under it at the tile's zoom, and the pins reaching into it from the
 * next squares, so pins on a tile edge are drawn whole.
 */
internal class PlaceTileProvider(
    /** One bitmap per pin look, drawn once. */
    private val pins: Map<PlacePinLook, Bitmap>,
    private val clusterColor: Int,
    private val onClusterColor: Int,
    /** Pixels per screen point. */
    private val density: Float,
    private val fontScale: Float,
) : TileProvider {

    /** Swapped by the map when the trip's stops change, together with clearing the drawn tiles. */
    @Volatile
    var source: MapPlaceSource? = null

    private val tileSize = (PlaceTile.POINTS * density).roundToInt()
    private val margin = pins.values.maxOf { it.width } / density / 2.0 + PlaceTile.CLUSTER_RADIUS_POINTS
    private val clusterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = clusterColor }
    private val clusterBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    private val clusterTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onClusterColor
        textSize = PlaceTile.CLUSTER_TEXT_POINTS * density * fontScale
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    override fun getTile(x: Int, y: Int, zoom: Int): Tile? {
        val source = source ?: return null
        val tile = PlaceTile(x, y, zoom)
        // Null: not loaded now; the map asks again a little later.
        val squares = runBlocking { withTimeoutOrNull(TILE_WAIT) { source.placesIn(PlaceSquare.covering(tile.area(margin), zoom)) } }
            ?: return null
        val places = squares.flatMap { it.places }
        val clusters = squares.flatMap { it.clusters }
        if (places.isEmpty() && clusters.isEmpty()) return TileProvider.NO_TILE

        val bitmap = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        places.inDrawingOrder().forEach { place ->
            val pin = pins.getValue(PlacePinLook(place.category, place.isTripinly, placePinSize(place, isSelected = false)))
            val cx = tile.x(place.location.lng).toFloat() * density
            val cy = tile.y(place.location.lat).toFloat() * density
            canvas.drawBitmap(pin, cx - pin.width / 2f, cy - pin.height / 2f, null)
        }
        clusters.forEach { cluster ->
            val cx = tile.x(cluster.location.lng).toFloat() * density
            val cy = tile.y(cluster.location.lat).toFloat() * density
            val label = cluster.count.toString()
            val radius = maxOf(
                PlaceTile.CLUSTER_RADIUS_POINTS * density,
                clusterTextPaint.measureText(label) / 2 + PlaceTile.CLUSTER_PADDING_POINTS * density,
            )
            canvas.drawCircle(cx, cy, radius, clusterBorderPaint)
            canvas.drawCircle(cx, cy, radius - PlaceTile.CLUSTER_BORDER_POINTS * density, clusterPaint)
            canvas.drawText(label, cx, cy - (clusterTextPaint.ascent() + clusterTextPaint.descent()) / 2, clusterTextPaint)
        }
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        return Tile(tileSize, tileSize, png)
    }

    private companion object {
        val TILE_WAIT = 10.seconds
    }
}
