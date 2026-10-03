package com.falcon.tripingly.feature.trips.domain.usecase

import com.falcon.tripingly.core.model.Trip
import com.falcon.tripingly.core.data.repository.TripRepository
import kotlinx.datetime.LocalDate
import kotlin.random.Random

class CreateTripUseCase(
    private val repository: TripRepository
) {
    suspend operator fun invoke(name: String, startDate: LocalDate, endDate: LocalDate) {
        val trip = Trip(
            id = generateId(),
            name = name,
            startDate = startDate,
            endDate = endDate
        )
        repository.saveTrip(trip)
    }

    private fun generateId(): String = "trip_${Random.nextInt(1000000)}"
}
