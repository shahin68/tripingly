package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
    fun `the target holds on until another pin is clearly nearer`() {
        val graben = PinEnd.Place(MapPlace("p-graben", "Graben", PlaceCategory.Attraction, GeoPoint(48.2087, 16.3699), isTripinly = false, hotCount = 0, prominence = 70))
        val near = listOf(ShownPin(opera, Offset(200f, 400f), 22f), ShownPin(graben, Offset(215f, 400f), 22f))
        val holder = PinHolder()
        holder.start(stephansdom, Offset(100f, 400f))
        holder.move(Offset(200f, 456f), near, density)
        assertEquals(opera, holder.hold?.target?.end)
        // The handle at (209, 400): the place is 3 px nearer, not enough to take over.
        holder.move(Offset(209f, 456f), near, density)
        assertEquals(opera, holder.hold?.target?.end)
        holder.move(Offset(214f, 456f), near, density)
        assertEquals(graben, holder.hold?.target?.end)
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
    fun `the camera stays still in the middle of the free map and speeds up toward its edges`() {
        // A 400 x 800 map with a 100 px header and a 300 px plan: the free part is 400 x 400, its middle at (200, 300).
        val free = freeArea(Size(400f, 800f), PaddingValues(top = 100.dp, bottom = 300.dp), density, LayoutDirection.Ltr)
        assertEquals(Rect(0f, 100f, 400f, 500f), free)
        assertEquals(Offset.Zero, edgePanVelocity(Offset(200f, 300f), free, density))
        // Sideways the still middle reaches 45 % of the way to the sides, up and down only 35 %.
        assertEquals(Offset.Zero, edgePanVelocity(Offset(280f, 300f), free, density))
        assertEquals(Offset.Zero, edgePanVelocity(Offset(200f, 240f), free, density))
        assertTrue(edgePanVelocity(Offset(200f, 210f), free, density).y < 0f)
        val near = edgePanVelocity(Offset(330f, 300f), free, density)
        val edge = edgePanVelocity(Offset(400f, 300f), free, density)
        assertTrue(near.x > 0f && near.y == 0f)
        assertTrue(edge.x > near.x)
        assertEquals(PAN_MAX_SPEED.value, edge.x, 0.01f)
        // Over the header: as fast as at the edge.
        assertEquals(PAN_MAX_SPEED.value, -edgePanVelocity(Offset(200f, 20f), free, density).y, 0.01f)
    }
}
