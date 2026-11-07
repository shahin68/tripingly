package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.push.NewActivityRepository
import com.falcon.tripingly.core.data.push.StopNews
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The "new" marks on stops with notifications I haven't seen: loaded when the trip opens and added live. News about a
 * stop's comments clears when its comments open, the rest when the stop opens; the mark goes once both are seen.
 * [pushedStopId] is the stop a push opened the map on: its mark shows even though opening the push already read that
 * notification.
 */
internal class MapNewActivity(
    private val context: MapContext,
    private val repository: NewActivityRepository,
    private val pushedStopId: String?,
) {
    fun start() {
        pushedStopId?.let { mark(StopNews(it, aboutComments = false)) }
        context.scope.launch {
            repository.stopsWithNewActivity(context.tripId).onSuccess { news ->
                (news.stopIds - news.commentStopIds).forEach { mark(StopNews(it, aboutComments = false)) }
                news.commentStopIds.forEach { mark(StopNews(it, aboutComments = true)) }
            }
        }
        context.scope.launch { repository.newActivity(context.tripId).collect(::mark) }
        context.scope.launch {
            context.model.map { (it.selection as? Selection.Stop)?.markerId }.distinctUntilChanged().filterNotNull().collect { seen(it, comments = false) }
        }
        context.scope.launch {
            context.model.map { (it.sheet as? Sheet.StopComments)?.markerId }.distinctUntilChanged().filterNotNull().collect { seen(it, comments = true) }
        }
    }

    /** Marks the stop, unless that news shows already: its comments, or for the rest the stop, are open. */
    private fun mark(news: StopNews) {
        val model = context.model.value
        val open = if (news.aboutComments) (model.sheet as? Sheet.StopComments)?.markerId else (model.selection as? Selection.Stop)?.markerId
        if (open == news.markerId) {
            context.scope.launch { repository.markSeen(news.markerId, news.aboutComments) }
            return
        }
        context.update {
            if (news.aboutComments) it.copy(newCommentStopIds = it.newCommentStopIds + news.markerId) else it.copy(newStopIds = it.newStopIds + news.markerId)
        }
    }

    private fun seen(markerId: String, comments: Boolean) {
        val model = context.model.value
        if (markerId !in (if (comments) model.newCommentStopIds else model.newStopIds)) return
        context.update { if (comments) it.copy(newCommentStopIds = it.newCommentStopIds - markerId) else it.copy(newStopIds = it.newStopIds - markerId) }
        context.scope.launch { repository.markSeen(markerId, comments) }
    }
}
