package com.falcon.tripingly.feature.trips.domain.usecase

import com.falcon.tripingly.core.data.repository.TripRepository
import kotlinx.datetime.LocalDate

class UpdateTripDatesUseCase(
    private val repository: TripRepository
) {
    suspend operator fun invoke(id: String, startDate: LocalDate, endDate: LocalDate) {
        repository.updateTripDates(id, startDate, endDate)
    }
}
