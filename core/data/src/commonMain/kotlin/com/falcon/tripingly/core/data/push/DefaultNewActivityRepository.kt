package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.network.model.MarkReadDto
import com.falcon.tripingly.core.network.push.PushApi
import com.falcon.tripingly.core.network.realtime.RealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultNewActivityRepository(
    private val api: PushApi,
    private val realtime: RealtimeClient,
) : NewActivityRepository {

    override suspend fun stopsWithNewActivity(tripId: String): AppResult<Set<String>, DataError.Network> =
        api.newActivity(tripId).map { it.markerIds.toSet() }

    override fun newActivity(tripId: String): Flow<String> =
        realtime.events.filterIsInstance<RealtimeEvent.NewActivity>().filter { it.tripId == tripId }.map { it.markerId }

    // Best effort: offline, the mark comes back the next time the trip opens.
    override suspend fun markSeen(markerId: String) {
        api.markRead(MarkReadDto(markerId = markerId))
    }
}
