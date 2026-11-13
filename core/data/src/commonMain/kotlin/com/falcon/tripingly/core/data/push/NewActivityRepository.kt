package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * The stops with notifications I haven't seen (a comment, a photo, flames, a collaborator's change): their pins carry a
 * "new" mark (not for flames) until that news is seen where it belongs ([NewsKind]).
 */
interface NewActivityRepository {
    /** The stops of [tripId] with news, as the server has them. */
    suspend fun stopsWithNewActivity(tripId: String): AppResult<Set<StopNews>, DataError.Network>

    /** News about stops of [tripId] while the app runs. */
    fun newActivity(tripId: String): Flow<StopNews>

    /**
     * [tripId]'s marks may have changed: news read or removed somewhere else (another screen or device), or the live
     * connection is back, so news sent while it was down was missed.
     */
    fun marksChanged(tripId: String): Flow<Unit>

    /** The stop's news of that kind was seen: it counts as read. */
    suspend fun markSeen(news: StopNews)
}

/**
 * Where a stop's news is seen (Shahin, 2026-11-13): [Comments] in its comments and [Photos] in its photos (their
 * buttons carry a dot until then), [Hot] (flames: no dot, the flame pops once) and [Other] when the stop opens.
 */
enum class NewsKind { Comments, Photos, Hot, Other }

data class StopNews(val markerId: String, val kind: NewsKind)
