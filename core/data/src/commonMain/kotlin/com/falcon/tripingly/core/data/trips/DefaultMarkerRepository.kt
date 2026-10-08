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
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies marker changes to the cache at once and sends them to the server one
 * at a time per trip, from [scope] so leaving the map doesn't cancel them.
 *
 * A dropped connection is retried after 1 s and again after 2 s; if it still
 * fails, the trip counts as offline and its remaining queued changes fail at
 * once instead of each waiting. Every failed change is undone and reported.
 * The trip isn't reloaded afterwards: the device already shows the result, and
 * the place the server matched to a new or moved stop is taken from its answer.
 * Positions here only keep the order (a delete leaves a gap); the next reload
 * of the trip brings the server's.
 */
internal class DefaultMarkerRepository(
    private val api: MarkersApi,
    private val trips: TripRepository,
    private val local: TripLocalDataSource,
    private val pending: PendingTripWrites,
    private val scope: CoroutineScope,
) : MarkerRepository {

    private val queuesLock = Mutex()
    private val queues = mutableMapOf<String, Channel<MarkerChange>>()
    private val _failures = MutableSharedFlow<MarkerChangeFailure>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val failures: Flow<MarkerChangeFailure> = _failures.asSharedFlow()

    override suspend fun addMarker(tripId: String, dayId: String, marker: NewMarker): TripMarker {
        val day = local.trip(tripId)?.days?.firstOrNull { it.id == dayId }
        val created = TripMarker(
            id = Uuid.random().toString(),
            tripId = tripId,
            dayId = dayId,
            // Known once the server has matched the place; the reload fills it in.
            placeId = "",
            name = marker.name.trim(),
            location = marker.location,
            time = marker.time,
            position = day?.markers?.nextPosition() ?: 0,
        )
        submit(MarkerChange.Add(created))
        return created
    }

    override suspend fun updateMarker(markerId: String, update: MarkerUpdate) {
        val before = cachedMarker(markerId) ?: return
        val targetDay = update.dayId ?: before.dayId
        val moved = targetDay != before.dayId
        val after = before.copy(
            name = update.name?.trim() ?: before.name,
            location = update.location ?: before.location,
            dayId = targetDay,
            position = if (moved) local.trip(before.tripId)?.days?.firstOrNull { it.id == targetDay }?.markers?.nextPosition() ?: 0 else before.position,
        )
        submit(MarkerChange.Update(before, after, update))
    }

    override suspend fun deleteMarker(markerId: String) {
        val marker = cachedMarker(markerId) ?: return
        submit(MarkerChange.Delete(marker))
    }

    override suspend fun clearDay(tripId: String, dayId: String) {
        val markers = local.trip(tripId)?.days?.firstOrNull { it.id == dayId }?.markers.orEmpty()
        if (markers.isEmpty()) return
        submit(MarkerChange.ClearDay(tripId, dayId, markers))
    }

    override suspend fun reorderMarkers(tripId: String, dayId: String, markerIds: List<String>) {
        val before = local.trip(tripId)?.days?.firstOrNull { it.id == dayId }?.markers?.map { it.id } ?: return
        submit(MarkerChange.Reorder(tripId, dayId, before, markerIds))
    }

    override suspend fun retry(failure: MarkerChangeFailure) = submit(failure.change)

    override suspend fun copyMarker(markerId: String, targetDayId: String): AppResult<TripMarker, DataError.Network> =
        when (val result = api.copy(markerId, CopyMarkerDto(dayId = targetDayId), newIdempotencyKey())) {
            is AppResult.Success -> {
                val marker = result.data.toMarker()
                // Only cached trips keep markers; a copy into a trip not opened yet loads with that trip.
                if (local.trip(marker.tripId) != null) trips.refreshTrip(marker.tripId)
                AppResult.Success(marker)
            }
            is AppResult.Error -> result
        }

    private fun List<TripMarker>.nextPosition(): Int = (maxOfOrNull { it.position } ?: -1) + 1

    private suspend fun cachedMarker(markerId: String): TripMarker? {
        val tripId = local.tripIdOfMarker(markerId) ?: return null
        return local.trip(tripId)?.days?.flatMap { it.markers }?.firstOrNull { it.id == markerId }
    }

    private suspend fun submit(change: MarkerChange) {
        pending.begin(change.tripId) { local.apply(change) }
        queueOf(change.tripId).send(change)
    }

    private suspend fun queueOf(tripId: String): Channel<MarkerChange> = queuesLock.withLock {
        queues.getOrPut(tripId) {
            Channel<MarkerChange>(Channel.UNLIMITED).also { queue ->
                scope.launch {
                    val state = QueueState()
                    for (change in queue) process(change, state)
                }
            }
        }
    }

    /** What one trip's queue remembers until it is empty again. */
    private class QueueState {
        /** Set when the connection failed even after retrying. */
        var offline: DataError.Network? = null

        /** Markers whose add never reached the server; later changes to them are dropped. */
        val failedAdds = mutableSetOf<String>()
    }

    private suspend fun process(change: MarkerChange, state: QueueState) {
        try {
            // Undoing the failed add already removed this marker.
            if (change !is MarkerChange.Reorder && change.markerId in state.failedAdds) return
            val error = state.offline ?: sendWithRetries(change, state)
            if (error != null) {
                if (error.isConnectionProblem()) state.offline = error
                if (change is MarkerChange.Add) state.failedAdds += change.markerId
                local.undo(change)
                _failures.emit(MarkerChangeFailure(change.tripId, change.markerName, error, change))
            }
        } finally {
            if (pending.end(change.tripId)) {
                state.offline = null
                state.failedAdds.clear()
            }
        }
    }

    private suspend fun sendWithRetries(change: MarkerChange, state: QueueState): DataError.Network? {
        var attempt = 0
        while (true) {
            val error = send(change, state) ?: return null
            if (!error.isConnectionProblem() || attempt == RETRY_DELAYS.size) return error
            delay(RETRY_DELAYS[attempt++])
        }
    }

    /** Sends one change; null when the server has it. */
    private suspend fun send(change: MarkerChange, state: QueueState): DataError.Network? {
        val result: AppResult<*, DataError.Network> = when (change) {
            is MarkerChange.Add -> api.create(
                change.marker.dayId,
                CreateMarkerDto(
                    id = change.marker.id,
                    name = change.marker.name,
                    location = change.marker.location.toDto(),
                    time = change.marker.time,
                ),
                // A retry after a lost answer gets the first answer back instead of a duplicate.
                change.marker.id,
            )
            is MarkerChange.Update -> api.update(
                change.markerId,
                UpdateMarkerDto(
                    name = change.update.name?.trim(),
                    location = change.update.location?.toDto(),
                    dayId = change.update.dayId,
                ),
            )
            is MarkerChange.Delete -> api.delete(change.markerId)
            is MarkerChange.ClearDay -> api.clearDay(change.dayId)
            is MarkerChange.Reorder -> api.reorder(
                change.dayId,
                MarkerOrderDto(markerIds = change.after.filterNot { it in state.failedAdds }),
            )
        }
        if (result is AppResult.Success) {
            // The place the server matched; the rest is already on the device.
            (result.data as? MarkerDto)?.let { local.savePlaceOfMarker(it.id, it.placeId) }
            return null
        }
        val error = (result as AppResult.Error).error
        return when {
            // The marker is there already: an earlier attempt got through.
            change is MarkerChange.Add && error.hasCode(TripErrorCodes.ID_CONFLICT) -> null
            // Gone already, which is what was asked.
            (change is MarkerChange.Delete || change is MarkerChange.ClearDay) && error.hasCode(TripErrorCodes.NOT_FOUND) -> null
            else -> error
        }
    }

    private companion object {
        /** Waits before each quiet retry of a dropped connection. */
        val RETRY_DELAYS = listOf(1.seconds, 2.seconds)
    }
}
