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

    @Test
    fun `a pin still in view stays, with its look, and new places take the free places best first`() {
        val config = AppConfig(mapMaxPins = 2, mapMaxPhotoPins = 1)
        val low = place("low", prominence = 10, photo = false)
        val famous = place("famous", prominence = 95)
        val known = place("known", prominence = 92)
        val squares = listOf(PlacesInView(listOf(low, famous, known), emptyList()))
        // Shown before the pan: "low", and "known" as an icon although it has a photo.
        val shown = listOf(PlacePin(low, placePinSize(low, config)), PlacePin(known, placePinSize(known, config)))

        val pins = placePins(squares, area, emptySet(), config, shown)

        assertEquals(listOf("known", "low"), pins.map { it.place.id })
        assertEquals(listOf(false, false), pins.map { it.photo })
        // Without them the best two show, the first with its photo.
        val fresh = placePins(squares, area, emptySet(), config)
        assertEquals(listOf("famous" to true, "known" to false), fresh.map { it.place.id to it.photo })
    }

    @Test
    fun `pins still in view all stay, even past the cap, and no new ones come then`() {
        val config = AppConfig(mapMaxPins = 2, mapMaxPhotoPins = 0)
        val shownPlaces = listOf(place("a", prominence = 10), place("b", prominence = 20), place("c", prominence = 30))
        val squares = listOf(PlacesInView(shownPlaces + place("famous", prominence = 99), emptyList()))
        val shown = shownPlaces.map { PlacePin(it, placePinSize(it, config)) }

        val pins = placePins(squares, area, emptySet(), config, shown)

        assertEquals(listOf("c", "b", "a"), pins.map { it.place.id })
    }

    @Test
    fun `a shown pin the squares no longer list stays only when asked to`() {
        val config = AppConfig(mapMaxPins = 10, mapMaxPhotoPins = 0)
        val small = place("small", prominence = 5)
        val outside = place("outside", prominence = 5).copy(location = GeoPoint(50.0, 16.5))
        val squares = listOf(PlacesInView(listOf(place("big", prominence = 90)), emptyList()))
        val shown = listOf(small, outside).map { PlacePin(it, placePinSize(it, config)) }

        assertEquals(listOf("big", "small"), placePins(squares, area, emptySet(), config, shown, keepUnlisted = true).map { it.place.id })
        assertEquals(listOf("big"), placePins(squares, area, setOf("small"), config, shown, keepUnlisted = true).map { it.place.id })
        assertEquals(listOf("big"), placePins(squares, area, emptySet(), config, shown).map { it.place.id })
    }
}
