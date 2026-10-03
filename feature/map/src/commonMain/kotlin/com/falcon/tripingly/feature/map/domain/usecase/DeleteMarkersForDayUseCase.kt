package com.falcon.tripingly.feature.map.domain.usecase

import com.falcon.tripingly.core.database.dao.MarkerDao

class DeleteMarkersForDayUseCase(
    private val markerDao: MarkerDao
) {
    suspend operator fun invoke(tripId: String, dayIndex: Int) {
        markerDao.deleteMarkersForDay(tripId, dayIndex)
    }
}
