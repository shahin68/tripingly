package com.falcon.tripingly.feature.home.domain.usecase

import com.falcon.tripingly.feature.home.domain.repository.TripRepository
import kotlinx.datetime.LocalDate

class UpdateTripDatesUseCase(
    private val repository: TripRepository
) {
    suspend operator fun invoke(id: String, startDate: LocalDate, endDate: LocalDate) {
        repository.updateTripDates(id, startDate, endDate)
    }
}
