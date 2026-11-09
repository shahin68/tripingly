package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.network.model.MarkReadDto
import com.falcon.tripingly.core.network.push.PushApi
import com.falcon.tripingly.core.network.realtime.RealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge

internal class DefaultNewActivityRepository(
    private val api: PushApi,
    private val realtime: RealtimeClient,
) : NewActivityRepository {

    override suspend fun stopsWithNewActivity(tripId: String): AppResult<NewActivity, DataError.Network> =
        api.newActivity(tripId).map { NewActivity(it.markerIds.toSet(), it.commentMarkerIds.toSet()) }

    override fun newActivity(tripId: String): Flow<StopNews> =
        realtime.events.filterIsInstance<RealtimeEvent.NotificationCreated>()
            .mapNotNull { news -> news.markerId?.takeIf { news.tripId == tripId }?.let { StopNews(it, news.aboutComments) } }

    // Also on every (re)connection: news sent while the app was in the background or offline never came live.
    override fun marksChanged(tripId: String): Flow<Unit> = merge(
        realtime.events.filterIsInstance<RealtimeEvent.NotificationsChanged>()
            .mapNotNull { changed -> Unit.takeIf { tripId in changed.tripIds || changed.change == RealtimeEvent.NotificationsChanged.Change.Cleared } },
        realtime.connections,
    )

    // Best effort: offline, the mark comes back the next time the trip opens.
    override suspend fun markSeen(markerId: String, comments: Boolean) {
        api.markRead(MarkReadDto(markerId = markerId, kind = if (comments) MarkReadDto.Kind.COMMENTS else MarkReadDto.Kind.OTHER))
    }
}
