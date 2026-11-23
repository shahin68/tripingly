package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.model.place.Brand
import com.falcon.tripingly.core.model.place.CategoryCount
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.model.CategoryCountDto
import com.falcon.tripingly.core.network.model.PlaceBrandDto
import com.falcon.tripingly.core.network.model.PlaceItemDto
import kotlin.math.roundToInt

internal fun PlaceItemDto.toMapPlace(api: ApiConfig) = MapPlace(
    id = id,
    name = name,
    category = PlaceCategory(category),
    location = GeoPoint(location.lat, location.lng),
    isTripinly = isTripinly,
    hotCount = hotCount.toInt(),
    prominence = prominence.toInt(),
    coverImageId = coverImageId,
    coverThumbUrl = coverThumbUrl,
    brand = brand?.toBrand(api),
)

/** A brand, its logo path made a full URL on the API. */
internal fun PlaceBrandDto.toBrand(api: ApiConfig) = Brand(id, name, logoUrl?.let(api::urlOf))

/** Kinds of place as a query parameter (`cafe,fast_food`); null for none, which means every kind. */
internal fun Set<PlaceCategory>.query(): String? = takeIf { it.isNotEmpty() }?.joinToString(",") { it.key }

/** A filter's counts, most first as the server sends them. */
internal fun List<CategoryCountDto>.toCategoryCounts() = map { CategoryCount(PlaceCategory(it.category), it.count.roundToInt()) }
