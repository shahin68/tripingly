package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.network.model.CreateMarkerDto
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
    PlaceItemDto.Category.BAKERY -> PlaceCategory.Bakery
    PlaceItemDto.Category.FAST_FOOD -> PlaceCategory.FastFood
    PlaceItemDto.Category.VIEWPOINT -> PlaceCategory.Viewpoint
    PlaceItemDto.Category.BEACH -> PlaceCategory.Beach
    PlaceItemDto.Category.ENTERTAINMENT -> PlaceCategory.Entertainment
    PlaceItemDto.Category.SHOPPING -> PlaceCategory.Shopping
    PlaceItemDto.Category.STAY -> PlaceCategory.Stay
    PlaceItemDto.Category.TRANSPORT -> PlaceCategory.Transport
}

/** A new spot's category as the server names it. */
internal fun PlaceCategory.toCreateDto() = when (this) {
    PlaceCategory.Cafe -> CreateMarkerDto.Category.CAFE
    PlaceCategory.Restaurant -> CreateMarkerDto.Category.RESTAURANT
    PlaceCategory.Bar -> CreateMarkerDto.Category.BAR
    PlaceCategory.Attraction -> CreateMarkerDto.Category.ATTRACTION
    PlaceCategory.Museum -> CreateMarkerDto.Category.MUSEUM
    PlaceCategory.Historic -> CreateMarkerDto.Category.HISTORIC
    PlaceCategory.Park -> CreateMarkerDto.Category.PARK
    PlaceCategory.Nature -> CreateMarkerDto.Category.NATURE
    PlaceCategory.Landmark -> CreateMarkerDto.Category.LANDMARK
    PlaceCategory.Other -> CreateMarkerDto.Category.OTHER
    PlaceCategory.Bakery -> CreateMarkerDto.Category.BAKERY
    PlaceCategory.FastFood -> CreateMarkerDto.Category.FAST_FOOD
    PlaceCategory.Viewpoint -> CreateMarkerDto.Category.VIEWPOINT
    PlaceCategory.Beach -> CreateMarkerDto.Category.BEACH
    PlaceCategory.Entertainment -> CreateMarkerDto.Category.ENTERTAINMENT
    PlaceCategory.Shopping -> CreateMarkerDto.Category.SHOPPING
    PlaceCategory.Stay -> CreateMarkerDto.Category.STAY
    PlaceCategory.Transport -> CreateMarkerDto.Category.TRANSPORT
}
