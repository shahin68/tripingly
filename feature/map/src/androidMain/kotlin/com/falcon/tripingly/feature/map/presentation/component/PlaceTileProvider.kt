package com.falcon.tripingly.feature.map.presentation.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.unit.Dp
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan
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

    private val tileSize = (TILE_POINTS * density).roundToInt()
    private val margin = pins.values.maxOf { it.width } / density / 2 + CLUSTER_RADIUS_POINTS
    private val clusterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = clusterColor }
    private val clusterBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    private val clusterTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onClusterColor
        textSize = CLUSTER_TEXT_POINTS * density * fontScale
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    override fun getTile(x: Int, y: Int, zoom: Int): Tile? {
        val source = source ?: return null
        val worldPoints = TILE_POINTS * (1 shl zoom).toDouble()
        val left = x * TILE_POINTS.toDouble()
        val top = y * TILE_POINTS.toDouble()
        val near = GeoBounds(
            south = latitudeAt(top + TILE_POINTS + margin, worldPoints),
            west = longitudeAt(left - margin, worldPoints),
            north = latitudeAt(top - margin, worldPoints),
            east = longitudeAt(left + TILE_POINTS + margin, worldPoints),
        )
        // Null: not loaded now; the map asks again a little later.
        val squares = runBlocking { withTimeoutOrNull(TILE_WAIT) { source.placesIn(PlaceSquare.covering(near, zoom)) } }
            ?: return null
        val places = squares.flatMap { it.places }
        val clusters = squares.flatMap { it.clusters }
        if (places.isEmpty() && clusters.isEmpty()) return TileProvider.NO_TILE

        val bitmap = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        fun pixelX(lng: Double) = ((lng + 180) / 360 * worldPoints - left).toFloat() * density
        fun pixelY(lat: Double) = (yAt(lat, worldPoints) - top).toFloat() * density
        // Hot spots over plain places, the most liked on top.
        places.sortedWith(compareBy<MapPlace>({ it.isTripinly }, { it.likeCount })).forEach { place ->
            val pin = pins.getValue(PlacePinLook(place.category, place.isTripinly, placePinSize(place, isSelected = false)))
            canvas.drawBitmap(pin, pixelX(place.location.lng) - pin.width / 2f, pixelY(place.location.lat) - pin.height / 2f, null)
        }
        clusters.forEach { cluster ->
            val cx = pixelX(cluster.location.lng)
            val cy = pixelY(cluster.location.lat)
            val label = cluster.count.toString()
            val radius = maxOf(CLUSTER_RADIUS_POINTS * density, clusterTextPaint.measureText(label) / 2 + CLUSTER_PADDING_POINTS * density)
            canvas.drawCircle(cx, cy, radius, clusterBorderPaint)
            canvas.drawCircle(cx, cy, radius - CLUSTER_BORDER_POINTS * density, clusterPaint)
            canvas.drawText(label, cx, cy - (clusterTextPaint.ascent() + clusterTextPaint.descent()) / 2, clusterTextPaint)
        }
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        return Tile(tileSize, tileSize, png)
    }

    // Web Mercator, in points of the whole map at a zoom.
    private fun yAt(lat: Double, worldPoints: Double): Double {
        val phi = lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / 180
        return (1 - ln(tan(PI / 4 + phi / 2)) / PI) / 2 * worldPoints
    }

    private fun latitudeAt(y: Double, worldPoints: Double): Double =
        atan(sinh(PI * (1 - 2 * y.coerceIn(0.0, worldPoints) / worldPoints))) * 180 / PI

    private fun longitudeAt(x: Double, worldPoints: Double): Double = (x / worldPoints * 360 - 180).coerceIn(-180.0, 180.0)

    private companion object {
        const val TILE_POINTS = 256
        const val MAX_LATITUDE = 85.05112878
        // A cluster: at least 36 points round, a 2-point white ring, its count in bold.
        const val CLUSTER_RADIUS_POINTS = 18f
        const val CLUSTER_PADDING_POINTS = 8f
        const val CLUSTER_BORDER_POINTS = 2f
        const val CLUSTER_TEXT_POINTS = 14f
        val TILE_WAIT = 10.seconds
    }
}

/** What a pin looks like. */
internal data class PlacePinLook(val category: PlaceCategory, val isTripinly: Boolean, val size: Dp)
