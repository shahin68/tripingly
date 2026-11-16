package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.falcon.tripingly.core.model.trip.GeoPoint

/**
 * One end of an A→B route on the map: its pin ([pinSize] round: a stop's own, or the place's grown pin) stands in the
 * same soft halo of the way of travelling's [color] as the stops of a day's route (S3); no flag, no name (Shahin,
 * 2026-11-16: name pills tried and dropped for the halo).
 */
@Immutable
data class RouteEndMark(val position: GeoPoint, val pinSize: Dp, val color: Color)

/** Draws [RouteEndMark]'s halo: a disc [STOP_HALO_WIDTH] wider than the pin all round, centred on the end, under its pin. */
fun drawRouteEndHalo(pinSize: Dp, color: Color, density: Density, layoutDirection: LayoutDirection): ImageBitmap {
    val px = with(density) { (pinSize + STOP_HALO_WIDTH * 2).roundToPx() }
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        drawCircle(color.copy(alpha = STOP_HALO_ALPHA))
    }
    return bitmap
}
