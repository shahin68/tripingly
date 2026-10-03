package com.falcon.tripingly.feature.map.domain.usecase

import com.falcon.tripingly.core.database.dao.MarkerDao

class DeleteMarkerUseCase(
    private val markerDao: MarkerDao
) {
    suspend operator fun invoke(markerId: String) {
        markerDao.deleteMarkerById(markerId)
    }
}
