package com.falcon.tripingly.feature.map.presentation.component

import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlacesInView
import com.falcon.tripingly.core.model.trip.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class PlacePinsTest {

    private val area = GeoBounds(south = 48.0, west = 16.0, north = 49.0, east = 17.0)

    private fun place(
        id: String,
        hotCount: Int = 0,
        prominence: Int = 0,
        photo: Boolean = true,
        at: GeoPoint = GeoPoint(48.2, 16.37),
    ) = MapPlace(
        id = id,
        name = id,
        category = PlaceCategory.Attraction,
        location = at,
        isTripinly = hotCount > 0,
        hotCount = hotCount,
        prominence = prominence,
        coverImageId = if (photo) "image-$id" else null,
        coverThumbUrl = if (photo) "https://storage.test/$id.webp" else null,
    )

    private fun photoPins(vararg places: MapPlace, config: AppConfig = AppConfig()) =
        placePins(listOf(PlacesInView(places.toList(), emptyList())), area, zoom = 0, emptySet(), config)
            .filter { it.photo }
            .map { it.place.id }

    @Test
    fun `well known or liked places with a photo show it`() {
        val pins = photoPins(
            place("cathedral", prominence = 90),
            place("hot spot", hotCount = 3),
            place("quiet", hotCount = 2, prominence = 89),
            place("no photo", prominence = 100, photo = false),
        )

        assertEquals(listOf("hot spot", "cathedral"), pins)
    }

    @Test
    fun `photo pins are capped in pin order and drawn at the photo size`() {
        val config = AppConfig(mapMaxPhotoPins = 2)
        val places = arrayOf(place("a", prominence = 95), place("b", hotCount = 5), place("c", hotCount = 9))

        assertEquals(listOf("c", "b"), photoPins(*places, config = config))
        val pins = placePins(listOf(PlacesInView(places.toList(), emptyList())), area, zoom = 0, emptySet(), config)
        assertEquals(PhotoPinSize, pins.single { it.place.id == "c" }.size)
        assertEquals(PinSizes[2], pins.single { it.place.id == "a" }.size)
    }

    @Test
    fun `every map cell shows its own best places, so places elsewhere never push them out`() {
        // At zoom 10 a cell is about a third of a degree, a tenth of the area: two pins each out of 20.
        val config = AppConfig(mapMaxPins = 20, mapMaxPhotoPins = 10)
        val oldTown = GeoPoint(48.2, 16.1)
        val edge = GeoPoint(48.8, 16.9)
        val crowd = (1..5).map { place("old $it", prominence = 50 + it, photo = false, at = oldTown) }
        val stop = place("stop", prominence = 99, at = oldTown)
        fun pins(vararg more: MapPlace) =
            placePins(listOf(PlacesInView(crowd + stop + more, emptyList())), area, zoom = 10, setOf(stop.id), config)
                .map { it.place.id }.toSet()

        val before = pins(place("edge", prominence = 10, at = edge))
        val after = pins(
            place("edge", prominence = 10, at = edge),
            place("famous edge", prominence = 95, at = edge),
            place("just beyond", prominence = 90, at = GeoPoint(49.05, 16.9)),
        )

        assertEquals(setOf("old 5", "old 4", "edge"), before)
        assertEquals(setOf("old 5", "old 4", "famous edge", "edge", "just beyond"), after)
    }
}
