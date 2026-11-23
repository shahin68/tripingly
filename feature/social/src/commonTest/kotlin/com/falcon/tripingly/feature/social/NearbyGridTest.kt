package com.falcon.tripingly.feature.social

import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.feature.social.presentation.nearby.arrangeTiles
import kotlin.test.Test
import kotlin.test.assertEquals

class NearbyGridTest {

    private fun spot(id: String, hasPhoto: Boolean = false) = NearbyPlace(
        id = id,
        name = id,
        category = PlaceCategory("museum"),
        location = GeoPoint(48.2, 16.37),
        isTripinly = true,
        hotCount = 1,
        hotByMe = false,
        coverImageId = if (hasPhoto) "photo-$id" else null,
        coverSource = null,
        coverThumbUrl = null,
        distanceMeters = 100,
    )

    @Test
    fun `rows go 2+1, 1+1+1, 1+2, a wide slot taking the next spot with a photo`() {
        val spots = listOf(spot("a"), spot("b", hasPhoto = true), spot("c"), spot("d", hasPhoto = true), spot("e"), spot("f"), spot("g", hasPhoto = true), spot("h"))

        // 2+1: b wide, then a. 1+1+1: c, d (its photo shown small), e. 1+2: f, then g wide. 2+1 without a photo: h.
        assertEquals(
            listOf("b" to 2, "a" to 1, "c" to 1, "d" to 1, "e" to 1, "f" to 1, "g" to 2, "h" to 1),
            arrangeTiles(spots).map { it.place.id to it.span },
        )
    }

    @Test
    fun `every spot shows once, and without photos all are one column`() {
        val spots = List(20) { spot("s$it", hasPhoto = it % 4 == 1) }
        val tiles = arrangeTiles(spots)
        assertEquals(spots.map { it.id }.sorted(), tiles.map { it.place.id }.sorted())

        assertEquals(List(7) { 1 }, arrangeTiles(List(7) { spot("p$it") }).map { it.span })
    }
}
