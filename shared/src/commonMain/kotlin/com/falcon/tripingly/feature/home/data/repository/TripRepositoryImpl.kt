package com.falcon.tripingly.feature.home.data.repository

import com.falcon.tripingly.feature.home.data.local.dao.TripDao
import com.falcon.tripingly.feature.home.data.local.entity.TripEntity
import com.falcon.tripingly.feature.home.domain.model.Trip
import com.falcon.tripingly.feature.home.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class TripRepositoryImpl(
    private val tripDao: TripDao
) : TripRepository {

    override fun getAllTrips(): Flow<List<Trip>> {
        return tripDao.getAllTrips().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTripById(id: String): Trip? {
        return tripDao.getTripById(id)?.toDomain()
    }

    override suspend fun saveTrip(trip: Trip) {
        tripDao.insertTrip(trip.toEntity())
    }

    override suspend fun updateTripName(id: String, name: String) {
        tripDao.updateTripName(id, name)
    }

    override suspend fun updateTripDates(id: String, startDate: LocalDate, endDate: LocalDate) {
        tripDao.updateTripDates(
            tripId = id,
            startDate = startDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
            endDate = endDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        )
    }

    override suspend fun deleteTrip(id: String) {
        tripDao.deleteTripById(id)
    }

    private fun TripEntity.toDomain(): Trip = Trip(
        id = id,
        name = name,
        startDate = Instant.fromEpochMilliseconds(startDate).toLocalDateTime(TimeZone.UTC).date,
        endDate = Instant.fromEpochMilliseconds(endDate).toLocalDateTime(TimeZone.UTC).date,
        imageUrl = imageUrl
    )

    private fun Trip.toEntity(): TripEntity = TripEntity(
        id = id,
        name = name,
        startDate = startDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        endDate = endDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        imageUrl = imageUrl
    )
}
