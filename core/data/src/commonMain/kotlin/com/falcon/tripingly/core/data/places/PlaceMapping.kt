package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.model.place.CategoryCount
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.network.model.CategoryCountDto
import com.falcon.tripingly.core.network.model.CreateMarkerDto
import com.falcon.tripingly.core.network.model.PlaceItemDto
import kotlin.math.roundToInt

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
    PlaceItemDto.Category.CINEMA -> PlaceCategory.Cinema
    PlaceItemDto.Category.NIGHTLIFE -> PlaceCategory.Nightlife
    PlaceItemDto.Category.HEALTH -> PlaceCategory.Health
    PlaceItemDto.Category.PHARMACY -> PlaceCategory.Pharmacy
    PlaceItemDto.Category.GROCERIES -> PlaceCategory.Groceries
    PlaceItemDto.Category.MARKET -> PlaceCategory.Market
    PlaceItemDto.Category.SPORTS -> PlaceCategory.Sports
    PlaceItemDto.Category.SPA -> PlaceCategory.Spa
    PlaceItemDto.Category.ZOO -> PlaceCategory.Zoo
    PlaceItemDto.Category.KIDS -> PlaceCategory.Kids
    PlaceItemDto.Category.LIBRARY -> PlaceCategory.Library
    PlaceItemDto.Category.CAMPING -> PlaceCategory.Camping
    PlaceItemDto.Category.WINERY -> PlaceCategory.Winery
    PlaceItemDto.Category.PARKING -> PlaceCategory.Parking
    PlaceItemDto.Category.FUEL -> PlaceCategory.Fuel
    PlaceItemDto.Category.MONEY -> PlaceCategory.Money
    PlaceItemDto.Category.TOILETS -> PlaceCategory.Toilets
    PlaceItemDto.Category.RENTAL -> PlaceCategory.Rental
    PlaceItemDto.Category.PHOTO_SPOT -> PlaceCategory.PhotoSpot
    PlaceItemDto.Category.ROMANTIC -> PlaceCategory.Romantic
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
    PlaceCategory.Cinema -> CreateMarkerDto.Category.CINEMA
    PlaceCategory.Nightlife -> CreateMarkerDto.Category.NIGHTLIFE
    PlaceCategory.Health -> CreateMarkerDto.Category.HEALTH
    PlaceCategory.Pharmacy -> CreateMarkerDto.Category.PHARMACY
    PlaceCategory.Groceries -> CreateMarkerDto.Category.GROCERIES
    PlaceCategory.Market -> CreateMarkerDto.Category.MARKET
    PlaceCategory.Sports -> CreateMarkerDto.Category.SPORTS
    PlaceCategory.Spa -> CreateMarkerDto.Category.SPA
    PlaceCategory.Zoo -> CreateMarkerDto.Category.ZOO
    PlaceCategory.Kids -> CreateMarkerDto.Category.KIDS
    PlaceCategory.Library -> CreateMarkerDto.Category.LIBRARY
    PlaceCategory.Camping -> CreateMarkerDto.Category.CAMPING
    PlaceCategory.Winery -> CreateMarkerDto.Category.WINERY
    PlaceCategory.Parking -> CreateMarkerDto.Category.PARKING
    PlaceCategory.Fuel -> CreateMarkerDto.Category.FUEL
    PlaceCategory.Money -> CreateMarkerDto.Category.MONEY
    PlaceCategory.Toilets -> CreateMarkerDto.Category.TOILETS
    PlaceCategory.Rental -> CreateMarkerDto.Category.RENTAL
    PlaceCategory.PhotoSpot -> CreateMarkerDto.Category.PHOTO_SPOT
    PlaceCategory.Romantic -> CreateMarkerDto.Category.ROMANTIC
}

/** Kinds of place as a query parameter (`cafe,fast_food`); null for none, which means every kind. */
internal fun Set<PlaceCategory>.query(): String? = takeIf { it.isNotEmpty() }?.joinToString(",") { it.toCreateDto().value }

/** A filter's counts, most first as the server sends them. */
internal fun List<CategoryCountDto>.toCategoryCounts() = map { CategoryCount(PlaceItemDto.Category.valueOf(it.category.name).toCategory(), it.count.roundToInt()) }
