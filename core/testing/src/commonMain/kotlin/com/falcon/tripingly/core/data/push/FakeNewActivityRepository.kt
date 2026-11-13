package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** Stop news kept in memory, by trip; [arrive] sends one as the server would. */
class FakeNewActivityRepository(news: Map<String, Set<StopNews>> = emptyMap()) : NewActivityRepository {
    /** Unread news, by trip. */
    val news = news.toMutableMap()
    /** News sent as seen, in order. */
    val seen = mutableListOf<StopNews>()
    private val live = MutableSharedFlow<Pair<String, StopNews>>(extraBufferCapacity = 16)
    private val changed = MutableSharedFlow<String>(extraBufferCapacity = 16)

    override suspend fun stopsWithNewActivity(tripId: String): AppResult<Set<StopNews>, DataError.Network> =
        AppResult.Success(news[tripId].orEmpty())

    override fun newActivity(tripId: String): Flow<StopNews> = live.filter { it.first == tripId }.map { it.second }

    override fun marksChanged(tripId: String): Flow<Unit> = changed.filter { it == tripId }.map { }

    override suspend fun markSeen(news: StopNews) {
        seen += news
        this.news.keys.forEach { this.news[it] = this.news.getValue(it) - news }
    }

    /** The stop's news was read somewhere else (another screen or device), as the server tells it. */
    fun readElsewhere(tripId: String, markerId: String) {
        news[tripId] = news[tripId].orEmpty().filterNot { it.markerId == markerId }.toSet()
        changed.tryEmit(tripId)
    }

    fun arrive(tripId: String, markerId: String, kind: NewsKind = NewsKind.Other) {
        val stopNews = StopNews(markerId, kind)
        news[tripId] = news[tripId].orEmpty() + stopNews
        live.tryEmit(tripId to stopNews)
    }
}
