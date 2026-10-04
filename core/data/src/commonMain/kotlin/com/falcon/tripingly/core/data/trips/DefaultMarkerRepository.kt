package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.trip.MarkerUpdate
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.TripMarker
import com.falcon.tripingly.core.network.model.CopyMarkerDto
import com.falcon.tripingly.core.network.model.CreateMarkerDto
import com.falcon.tripingly.core.network.model.MarkerDto
import com.falcon.tripingly.core.network.model.MarkerOrderDto
import com.falcon.tripingly.core.network.model.UpdateMarkerDto
import com.falcon.tripingly.core.network.newIdempotencyKey
import com.falcon.tripingly.core.network.trips.MarkersApi

/**
 * Applies the server's answer to the cache at once, then reloads the trip so
 * positions and counts the server recalculated are right.
 */
internal class DefaultMarkerRepository(
    private val api: MarkersApi,
    private val trips: TripRepository,
    private val local: TripLocalDataSource,
) : MarkerRepository {

    override suspend fun addMarker(dayId: String, marker: NewMarker): AppResult<TripMarker, DataError.Network> =
        api.create(
            dayId,
            CreateMarkerDto(name = marker.name.trim(), location = marker.location.toDto(), time = marker.time),
            newIdempotencyKey(),
        ).saved()

    override suspend fun updateMarker(markerId: String, update: MarkerUpdate): AppResult<TripMarker, DataError.Network> =
        api.update(
            markerId,
            UpdateMarkerDto(name = update.name?.trim(), location = update.location?.toDto(), dayId = update.dayId),
        ).saved()

    override suspend fun deleteMarker(markerId: String): AppResult<Unit, DataError.Network> {
        val tripId = local.tripIdOfMarker(markerId)
        val result = api.delete(markerId)
        if (result is AppResult.Success || (result is AppResult.Error && result.error.hasCode(TripErrorCodes.NOT_FOUND))) {
            local.deleteMarker(markerId)
            tripId?.let { trips.refreshTrip(it) }
        }
        return result
    }

    override suspend fun reorderMarkers(
        tripId: String,
        dayId: String,
        markerIds: List<String>,
    ): AppResult<Unit, DataError.Network> {
        val before = local.trip(tripId)?.days?.firstOrNull { it.id == dayId }?.markers?.map { it.id }
        local.reorderMarkers(markerIds)
        return when (val result = api.reorder(dayId, MarkerOrderDto(markerIds = markerIds))) {
            is AppResult.Success -> {
                local.reorderMarkers(result.data.markerIds)
                AppResult.Success(Unit)
            }
            is AppResult.Error -> {
                before?.let { local.reorderMarkers(it) }
                result
            }
        }
    }

    override suspend fun copyMarker(markerId: String, targetDayId: String): AppResult<TripMarker, DataError.Network> =
        api.copy(markerId, CopyMarkerDto(dayId = targetDayId), newIdempotencyKey()).saved()

    private suspend fun AppResult<MarkerDto, DataError.Network>.saved(): AppResult<TripMarker, DataError.Network> =
        when (this) {
            is AppResult.Success -> {
                val marker = data.toMarker()
                // Only cached trips keep markers; a copy into a trip not opened yet loads with that trip.
                if (local.trip(marker.tripId) != null) {
                    local.saveMarker(marker)
                    trips.refreshTrip(marker.tripId)
                }
                AppResult.Success(marker)
            }
            is AppResult.Error -> this
        }
}
