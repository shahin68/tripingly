package com.falcon.tripingly.feature.map.presentation.component

import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.ImageCredit
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceImage
import com.falcon.tripingly.core.model.place.PlaceImageSource
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import kotlinx.collections.immutable.persistentListOf

internal val previewPlace = MapPlace(
    id = "p1",
    name = "Eiffel Tower",
    category = PlaceCategory.Landmark,
    location = GeoPoint(48.8584, 2.2945),
    isTripinly = true,
    likeCount = 24,
    prominence = 98,
)

internal val previewCafe = MapPlace(
    id = "p2",
    name = "Café de Flore",
    category = PlaceCategory.Cafe,
    location = GeoPoint(48.8541, 2.3326),
    isTripinly = false,
    likeCount = 0,
    prominence = 70,
)

internal val previewPlaceImages = listOf(
    PlaceImage(
        id = "i1",
        source = PlaceImageSource.Commons,
        thumbUrl = null,
        displayUrl = null,
        width = 1600,
        height = 1200,
        credit = ImageCredit(
            author = "Benh Lieu Song",
            title = null,
            license = "CC BY-SA 3.0",
            licenseUrl = null,
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Tour_Eiffel.jpg",
        ),
    ),
    PlaceImage(id = "i2", source = PlaceImageSource.Trip, thumbUrl = null, displayUrl = null, width = 1200, height = 1600, credit = null),
)

internal val previewPlaceDetails = PlaceDetails(
    id = previewPlace.id,
    name = previewPlace.name,
    category = previewPlace.category,
    likeCount = previewPlace.likeCount,
    openingHours = "Mo-Su 09:00-23:45",
    website = "https://www.toureiffel.paris",
    images = previewPlaceImages,
)

internal val previewAlongTheWay = persistentListOf(
    AlongTheWayPlace(previewCafe, distanceFromRouteMeters = 120, etaFromStartSeconds = 900),
    AlongTheWayPlace(
        place = previewCafe.copy(id = "p3", name = "Jardin du Luxembourg", category = PlaceCategory.Park),
        distanceFromRouteMeters = 1_400,
        etaFromStartSeconds = 4_800,
    ),
)

internal val previewStop = MapMarker(
    id = "s1",
    position = Coordinates(48.8584, 2.2945),
    title = UiText.Server("Eiffel Tower"),
    orderNumber = 1,
    photoCount = 3,
    likeCount = 5,
    likedByMe = true,
    commentCount = 2,
    placeId = previewPlace.id,
    canDelete = true,
)
