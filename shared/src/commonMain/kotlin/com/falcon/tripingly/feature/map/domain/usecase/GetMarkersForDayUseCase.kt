package com.falcon.tripingly.feature.map.domain.usecase

import com.falcon.tripingly.feature.map.data.local.dao.MarkerDao
import com.falcon.tripingly.feature.map.data.local.entity.MarkerEntity
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetMarkersForDayUseCase(
    private val markerDao: MarkerDao
) {
    operator fun invoke(tripId: String, dayIndex: Int): Flow<List<MapMarker>> {
        return markerDao.getMarkersForDay(tripId, dayIndex).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private fun MarkerEntity.toDomain() = MapMarker(
        id = id,
        position = Coordinates(latitude, longitude),
        title = title,
        orderNumber = orderNumber,
        snippet = snippet,
        color = color
    )
}
