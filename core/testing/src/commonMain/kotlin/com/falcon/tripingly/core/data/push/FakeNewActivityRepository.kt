package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** Marks kept in memory; [arrive] sends one as the server would. */
class FakeNewActivityRepository(marked: Map<String, Set<String>> = emptyMap()) : NewActivityRepository {
    /** Marked stops by trip. */
    val marked = marked.toMutableMap()
    /** Stops sent as seen, in order. */
    val seen = mutableListOf<String>()
    private val live = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)

    override suspend fun stopsWithNewActivity(tripId: String): AppResult<Set<String>, DataError.Network> =
        AppResult.Success(marked[tripId].orEmpty())

    override fun newActivity(tripId: String): Flow<String> = live.filter { it.first == tripId }.map { it.second }

    override suspend fun markSeen(markerId: String) {
        seen += markerId
        marked.keys.forEach { marked[it] = marked.getValue(it) - markerId }
    }

    fun arrive(tripId: String, markerId: String) {
        marked[tripId] = marked[tripId].orEmpty() + markerId
        live.tryEmit(tripId to markerId)
    }
}
