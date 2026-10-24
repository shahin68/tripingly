package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.network.model.PlaceItemDto

internal fun PlaceItemDto.toMapPlace() = MapPlace(
    id = id,
    name = name,
    category = category.toCategory(),
    location = GeoPoint(location.lat, location.lng),
    isTripinly = isTripinly,
    hotCount = hotCount.toInt(),
    prominence = prominence.toInt(),
    coverImageId = coverImageId,
    coverThumbUrl = coverThumbUrl,
)

internal fun PlaceItemDto.Category.toCategory() = when (this) {
    PlaceItemDto.Category.CAFE -> PlaceCategory.Cafe
    PlaceItemDto.Category.RESTAURANT -> PlaceCategory.Restaurant
    PlaceItemDto.Category.BAR -> PlaceCategory.Bar
    PlaceItemDto.Category.ATTRACTION -> PlaceCategory.Attraction
    PlaceItemDto.Category.MUSEUM -> PlaceCategory.Museum
    PlaceItemDto.Category.HISTORIC -> PlaceCategory.Historic
    PlaceItemDto.Category.PARK -> PlaceCategory.Park
    PlaceItemDto.Category.NATURE -> PlaceCategory.Nature
    PlaceItemDto.Category.LANDMARK -> PlaceCategory.Landmark
    PlaceItemDto.Category.OTHER -> PlaceCategory.Other
}
