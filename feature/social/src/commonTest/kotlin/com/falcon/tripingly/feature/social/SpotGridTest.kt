package com.falcon.tripingly.feature.social

import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.feature.social.presentation.search.HALF
import com.falcon.tripingly.feature.social.presentation.search.NARROW
import com.falcon.tripingly.feature.social.presentation.search.WIDE
import com.falcon.tripingly.feature.social.presentation.search.arrangeTiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpotGridTest {

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
    fun `rows repeat wide and small then two halves then small and wide and a wide slot takes the next spot with a photo`() {
        val spots = listOf(spot("a"), spot("b", hasPhoto = true), spot("c"), spot("d", hasPhoto = true), spot("e"), spot("f", hasPhoto = true), spot("g"))

        // 2+1: b wide, then a. 1.5+1.5: c, d (photo or not). 1+2: e, then f wide. 2+1 with only one spot left: g small.
        assertEquals(
            listOf("b" to WIDE, "a" to NARROW, "c" to HALF, "d" to HALF, "e" to NARROW, "f" to WIDE, "g" to NARROW),
            arrangeTiles(spots).map { it.place.id to it.span },
        )
    }

    @Test
    fun `every row fills the width of three columns`() {
        val tiles = arrangeTiles(List(30) { spot("s$it", hasPhoto = it % 3 == 0) })
        var row = 0
        for (tile in tiles) {
            row += tile.span
            if (row == 3 * NARROW) row = 0
            assertTrue(row < 3 * NARROW, "a row overflows: ${tiles.map { it.span }}")
        }
    }

    @Test
    fun `every spot shows once and without photos none are wide`() {
        val spots = List(20) { spot("s$it", hasPhoto = it % 4 == 1) }
        val tiles = arrangeTiles(spots)
        assertEquals(spots.map { it.id }.sorted(), tiles.map { it.place.id }.sorted())

        // Without photos: 1+1+1, then 1.5+1.5, then 1+1+1, and so on.
        assertEquals(listOf(NARROW, NARROW, NARROW, HALF, HALF, NARROW, NARROW, NARROW), arrangeTiles(List(8) { spot("p$it") }).map { it.span })
    }
}
