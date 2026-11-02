package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * The stops with notifications I haven't seen (a comment, a like on a photo, a collaborator's change): their pins
 * carry a "new" mark until the stop is opened.
 */
interface NewActivityRepository {
    /** The marked stops of [tripId], as the server has them. */
    suspend fun stopsWithNewActivity(tripId: String): AppResult<Set<String>, DataError.Network>

    /** Stops of [tripId] getting a mark while the app runs. */
    fun newActivity(tripId: String): Flow<String>

    /** The stop was opened: its notifications count as read. */
    suspend fun markSeen(markerId: String)
}
