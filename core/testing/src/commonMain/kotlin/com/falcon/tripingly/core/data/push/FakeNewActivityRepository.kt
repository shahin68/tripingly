package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** Marks kept in memory ([commentMarked]: news about the stop's comments); [arrive] sends one as the server would. */
class FakeNewActivityRepository(
    marked: Map<String, Set<String>> = emptyMap(),
    commentMarked: Map<String, Set<String>> = emptyMap(),
) : NewActivityRepository {
    /** Stops with other news, by trip. */
    val marked = marked.toMutableMap()
    /** Stops with news about their comments, by trip. */
    val commentMarked = commentMarked.toMutableMap()
    /** Stops sent as seen, in order, with whether it was their comments. */
    val seen = mutableListOf<StopNews>()
    private val live = MutableSharedFlow<Pair<String, StopNews>>(extraBufferCapacity = 16)
    private val changed = MutableSharedFlow<String>(extraBufferCapacity = 16)

    override suspend fun stopsWithNewActivity(tripId: String): AppResult<NewActivity, DataError.Network> {
        val comments = commentMarked[tripId].orEmpty()
        return AppResult.Success(NewActivity(marked[tripId].orEmpty() + comments, comments))
    }

    override fun newActivity(tripId: String): Flow<StopNews> = live.filter { it.first == tripId }.map { it.second }

    override fun marksChanged(tripId: String): Flow<Unit> = changed.filter { it == tripId }.map { }

    override suspend fun markSeen(markerId: String, comments: Boolean) {
        seen += StopNews(markerId, comments)
        val from = if (comments) commentMarked else marked
        from.keys.forEach { from[it] = from.getValue(it) - markerId }
    }

    /** The stop's news was read somewhere else (another screen or device), as the server tells it. */
    fun readElsewhere(tripId: String, markerId: String) {
        marked[tripId] = marked[tripId].orEmpty() - markerId
        commentMarked[tripId] = commentMarked[tripId].orEmpty() - markerId
        changed.tryEmit(tripId)
    }

    fun arrive(tripId: String, markerId: String, aboutComments: Boolean = false) {
        val to = if (aboutComments) commentMarked else marked
        to[tripId] = to[tripId].orEmpty() + markerId
        live.tryEmit(tripId to StopNews(markerId, aboutComments))
    }
}
