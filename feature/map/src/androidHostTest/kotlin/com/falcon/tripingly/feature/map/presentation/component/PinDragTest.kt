package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

class PinDragTest {

    private val density = Density(1f)
    private val stephansdom = PinEnd.Stop(MapMarker("s1", Coordinates(48.2085, 16.3731), UiText.Server("Stephansdom"), 1, placeId = "p-dom"))
    private val opera = PinEnd.Stop(MapMarker("s2", Coordinates(48.2031, 16.3691), UiText.Server("Oper"), 2))
    private val dom = PinEnd.Place(MapPlace("p-dom", "Stephansdom", PlaceCategory.Attraction, GeoPoint(48.2085, 16.3731), isTripinly = false, hotCount = 0, prominence = 90))

    // At 1 px per dp: Stephansdom at (100, 400), the opera at (300, 400).
    private val pins = listOf(ShownPin(stephansdom, Offset(100f, 400f), 22f), ShownPin(opera, Offset(300f, 400f), 22f))

    @Test
    fun `a finger on a stop and a place holds the stop, drawn on top`() {
        val layers = MapLayersState(stops = persistentListOf(stephansdom.stop), selectedPlace = dom.place)
        val shown = layers.shownPins(zoom = 15f, density = density) { Offset(100f, 400f) }
        assertEquals(stephansdom, shown.pinAt(Offset(104f, 398f)))
        assertNull(shown.pinAt(Offset(160f, 400f)))
    }

    @Test
    fun `the handle snaps onto a pin near it once the finger has moved`() {
        val holder = PinHolder()
        holder.start(stephansdom, Offset(100f, 400f))
        // Held still on the pin: nothing to snap to, even with the opera under the handle.
        holder.retarget(pins, density)
        assertNull(holder.hold?.target)

        // The handle floats 56 px above the finger: a finger at (300, 456) has it on the opera.
        holder.move(Offset(300f, 456f), pins, density)
        assertEquals(opera, holder.hold?.target?.end)
        holder.move(Offset(200f, 600f), pins, density)
        assertNull(holder.hold?.target)
    }

    @Test
    fun `lifting on a pin gives both ends, elsewhere nothing`() {
        val holder = PinHolder()
        holder.start(stephansdom, Offset(100f, 400f))
        holder.move(Offset(300f, 456f), pins, density)
        assertEquals(stephansdom to opera, holder.end())
        assertNull(holder.hold)

        holder.start(stephansdom, Offset(100f, 400f))
        holder.move(Offset(200f, 700f), pins, density)
        assertNull(holder.end())
        assertNull(holder.end())
    }

    @Test
    fun `neither the held pin nor its stop's own place is a target`() {
        val holder = PinHolder()
        holder.start(stephansdom, Offset(100f, 300f))
        holder.move(Offset(100f, 456f), listOf(ShownPin(stephansdom, Offset(100f, 400f), 22f), ShownPin(dom, Offset(100f, 400f), 22f)), density)
        assertNull(holder.hold?.target)
    }

    @Test
    fun `the camera stays still in the middle and speeds up toward the edges`() {
        val size = Size(400f, 800f)
        assertEquals(Offset.Zero, edgePanVelocity(Offset(200f, 400f), size, density))
        assertEquals(Offset.Zero, edgePanVelocity(Offset(260f, 500f), size, density))
        val near = edgePanVelocity(Offset(330f, 400f), size, density)
        val edge = edgePanVelocity(Offset(400f, 400f), size, density)
        assertTrue(near.x > 0f && near.y == 0f)
        assertTrue(edge.x > near.x)
        assertEquals(PAN_MAX_SPEED.value, edge.x, 0.01f)
        // Toward the top: the camera moves up.
        assertTrue(edgePanVelocity(Offset(200f, 10f), size, density).y < 0f)
    }
}
