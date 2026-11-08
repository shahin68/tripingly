package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * The stops with notifications I haven't seen (a comment, a like on a photo, a collaborator's change): their pins
 * carry a "new" mark until that news is seen. News about a stop's comments ([StopNews.aboutComments]: a new comment
 * or a like on one) is seen in its comments, the rest when the stop opens.
 */
interface NewActivityRepository {
    /** The marked stops of [tripId], as the server has them. */
    suspend fun stopsWithNewActivity(tripId: String): AppResult<NewActivity, DataError.Network>

    /** News about stops of [tripId] while the app runs. */
    fun newActivity(tripId: String): Flow<StopNews>

    /** Some of [tripId]'s marks may have gone: news read or removed somewhere else (another screen or device). */
    fun marksChanged(tripId: String): Flow<Unit>

    /** The stop's comments were seen ([comments]), or the stop was opened: that news counts as read. */
    suspend fun markSeen(markerId: String, comments: Boolean)
}

/** [stopIds]: stops with any unread news; [commentStopIds]: those of them with news about their comments. */
data class NewActivity(val stopIds: Set<String>, val commentStopIds: Set<String>)

data class StopNews(val markerId: String, val aboutComments: Boolean)
