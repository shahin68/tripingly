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

    override suspend fun stopsWithNewActivity(tripId: String): AppResult<Set<StopNews>, DataError.Network> =
        api.newActivity(tripId).map { news ->
            listOf(
                news.commentMarkerIds to NewsKind.Comments,
                news.photoMarkerIds to NewsKind.Photos,
                news.hotMarkerIds to NewsKind.Hot,
                news.otherMarkerIds to NewsKind.Other,
            ).flatMap { (ids, kind) -> ids.map { StopNews(it, kind) } }.toSet()
        }

    override fun newActivity(tripId: String): Flow<StopNews> =
        realtime.events.filterIsInstance<RealtimeEvent.NotificationCreated>()
            .mapNotNull { news -> news.markerId?.takeIf { news.tripId == tripId }?.let { StopNews(it, news.newsKind.toNewsKind()) } }

    // Also on every (re)connection: news sent while the app was in the background or offline never came live.
    override fun marksChanged(tripId: String): Flow<Unit> = merge(
        realtime.events.filterIsInstance<RealtimeEvent.NotificationsChanged>()
            .mapNotNull { changed -> Unit.takeIf { tripId in changed.tripIds || changed.change == RealtimeEvent.NotificationsChanged.Change.Cleared } },
        realtime.connections,
    )

    // Best effort: offline, the mark comes back the next time the trip opens.
    override suspend fun markSeen(news: StopNews) {
        val kind = when (news.kind) {
            NewsKind.Comments -> MarkReadDto.Kind.COMMENTS
            NewsKind.Photos -> MarkReadDto.Kind.PHOTOS
            NewsKind.Hot -> MarkReadDto.Kind.HOT
            NewsKind.Other -> MarkReadDto.Kind.OTHER
        }
        api.markRead(MarkReadDto(markerId = news.markerId, kind = kind))
    }

    /** The server's `newsKind`; anything this app doesn't know yet is seen when the stop opens. */
    private fun String.toNewsKind() = NewsKind.entries.firstOrNull { it.name.equals(this, ignoreCase = true) } ?: NewsKind.Other
}
