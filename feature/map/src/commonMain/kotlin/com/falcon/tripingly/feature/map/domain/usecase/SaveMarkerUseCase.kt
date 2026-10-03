package com.falcon.tripingly.feature.map.domain.usecase

import com.falcon.tripingly.core.database.dao.MarkerDao
import com.falcon.tripingly.core.database.entity.MarkerEntity
import com.falcon.tripingly.feature.map.domain.model.MapMarker

class SaveMarkerUseCase(
    private val markerDao: MarkerDao
) {
    suspend operator fun invoke(tripId: String, dayIndex: Int, marker: MapMarker) {
        markerDao.insertMarker(marker.toEntity(tripId, dayIndex))
    }

    private fun MapMarker.toEntity(tripId: String, dayIndex: Int) = MarkerEntity(
        id = id,
        tripId = tripId,
        dayIndex = dayIndex,
        latitude = position.latitude,
        longitude = position.longitude,
        title = title,
        snippet = snippet,
        orderNumber = orderNumber,
        color = color
    )
}
