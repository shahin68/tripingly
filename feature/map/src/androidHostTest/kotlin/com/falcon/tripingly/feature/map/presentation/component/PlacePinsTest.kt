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

    private fun place(id: String, hotCount: Int = 0, prominence: Int = 0, photo: Boolean = true) = MapPlace(
        id = id,
        name = id,
        category = PlaceCategory.Attraction,
        location = GeoPoint(48.2, 16.37),
        isTripinly = hotCount > 0,
        hotCount = hotCount,
        prominence = prominence,
        coverImageId = if (photo) "image-$id" else null,
        coverThumbUrl = if (photo) "https://storage.test/$id.webp" else null,
    )

    private fun photoPins(vararg places: MapPlace, config: AppConfig = AppConfig()) =
        placePins(listOf(PlacesInView(places.toList(), emptyList())), area, emptySet(), config)
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
        val pins = placePins(listOf(PlacesInView(places.toList(), emptyList())), area, emptySet(), config)
        assertEquals(PhotoPinSize, pins.single { it.place.id == "c" }.size)
        assertEquals(PinSizes[2], pins.single { it.place.id == "a" }.size)
    }
}
