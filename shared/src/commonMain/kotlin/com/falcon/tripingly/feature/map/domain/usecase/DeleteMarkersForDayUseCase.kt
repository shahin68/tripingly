package com.falcon.tripingly.feature.map.domain.usecase

import com.falcon.tripingly.feature.map.data.local.dao.MarkerDao

class DeleteMarkersForDayUseCase(
    private val markerDao: MarkerDao
) {
    suspend operator fun invoke(tripId: String, dayIndex: Int) {
        markerDao.deleteMarkersForDay(tripId, dayIndex)
    }
}
