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
 * stop's comments clears when its comments open, the rest when the stop opens; the mark goes once both are seen. News
 * read anywhere else (the notification center, another device) takes the marks back to what the server has.
 * [pushedStopId] is the stop a push opened the map on: its mark shows even though opening the push already read that
 * notification, until the stop is opened; a push about its comments ([pushedAboutComments]) opens them, so it shows
 * none.
 */
internal class MapNewActivity(
    private val context: MapContext,
    private val repository: NewActivityRepository,
    private val pushedStopId: String?,
    private val pushedAboutComments: Boolean = false,
) {
    /** Still shown, though read: the stop a push opened the map on, until it is seen here. */
    private var pushed = pushedStopId?.let { StopNews(it, pushedAboutComments) }

    fun start() {
        pushed?.let(::mark)
        context.scope.launch { load() }
        context.scope.launch { repository.newActivity(context.tripId).collect(::mark) }
        context.scope.launch { repository.marksChanged(context.tripId).collect { load() } }
        context.scope.launch {
            context.model.map { (it.selection as? Selection.Stop)?.markerId }.distinctUntilChanged().filterNotNull().collect { seen(it, comments = false) }
        }
        context.scope.launch {
            context.model.map { (it.sheet as? Sheet.StopComments)?.markerId }.distinctUntilChanged().filterNotNull().collect { seen(it, comments = true) }
        }
    }

    /** The marks as the server has them (the pushed stop's too, until it is seen). */
    private suspend fun load() {
        repository.stopsWithNewActivity(context.tripId).onSuccess { news ->
            val all = listOfNotNull(pushed) + (news.stopIds - news.commentStopIds).map { StopNews(it, aboutComments = false) } +
                news.commentStopIds.map { StopNews(it, aboutComments = true) }
            val shown = all.filterNot(::seenAlready)
            context.update { model ->
                model.copy(
                    newStopIds = shown.filterNot { it.aboutComments }.map { it.markerId }.toSet(),
                    newCommentStopIds = shown.filter { it.aboutComments }.map { it.markerId }.toSet(),
                )
            }
        }
    }

    /** Marks the stop, unless that news shows already. */
    private fun mark(news: StopNews) {
        if (seenAlready(news)) return
        context.update {
            if (news.aboutComments) it.copy(newCommentStopIds = it.newCommentStopIds + news.markerId) else it.copy(newStopIds = it.newStopIds + news.markerId)
        }
    }

    /** That news shows already (its comments, or for the rest the stop, are open): read, not marked. */
    private fun seenAlready(news: StopNews): Boolean {
        val model = context.model.value
        val open = if (news.aboutComments) (model.sheet as? Sheet.StopComments)?.markerId else (model.selection as? Selection.Stop)?.markerId
        if (open != news.markerId) return false
        if (pushed == news) pushed = null
        context.scope.launch { repository.markSeen(news.markerId, news.aboutComments) }
        return true
    }

    private fun seen(markerId: String, comments: Boolean) {
        if (pushed == StopNews(markerId, comments)) pushed = null
        val model = context.model.value
        if (markerId !in (if (comments) model.newCommentStopIds else model.newStopIds)) return
        context.update { if (comments) it.copy(newCommentStopIds = it.newCommentStopIds - markerId) else it.copy(newStopIds = it.newStopIds - markerId) }
        context.scope.launch { repository.markSeen(markerId, comments) }
    }
}
