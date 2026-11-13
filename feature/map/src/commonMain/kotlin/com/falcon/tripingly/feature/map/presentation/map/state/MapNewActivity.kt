package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.push.NewActivityRepository
import com.falcon.tripingly.core.data.push.NewsKind
import com.falcon.tripingly.core.data.push.StopNews
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The "new" marks on stops with notifications I haven't seen: loaded when the trip opens and added live. Each kind of
 * news clears where it is seen (Shahin, 2026-11-13): comments when its comments open, photos when its photos open (both
 * buttons carry a dot until then), the rest when the stop opens; flames mark the pin and day tab but no button, and are seen
 * once the flame shows in the expanded card or the details ([seenHot]). News read anywhere else (the notification center, another device) takes the marks back to what
 * the server has. [pushedStopId] is the stop a push opened the map on: its mark shows even though opening the push
 * already read that notification, until the stop is opened; a push about its comments ([pushedAboutComments]) opens
 * them, so it shows none.
 */
internal class MapNewActivity(
    private val context: MapContext,
    private val repository: NewActivityRepository,
    private val pushedStopId: String?,
    private val pushedAboutComments: Boolean = false,
) {
    /** Still shown, though read: the stop a push opened the map on, until it is seen here. */
    private var pushed = pushedStopId?.let { StopNews(it, if (pushedAboutComments) NewsKind.Comments else NewsKind.Other) }

    fun start() {
        pushed?.let(::mark)
        context.scope.launch { load() }
        context.scope.launch { repository.newActivity(context.tripId).collect(::mark) }
        context.scope.launch { repository.marksChanged(context.tripId).collect { load() } }
        context.scope.launch {
            context.model.map { (it.selection as? Selection.Stop)?.markerId }.distinctUntilChanged().filterNotNull().collect { seen(StopNews(it, NewsKind.Other)) }
        }
        context.scope.launch {
            context.model.map { (it.sheet as? Sheet.StopComments)?.markerId }.distinctUntilChanged().filterNotNull().collect { seen(StopNews(it, NewsKind.Comments)) }
        }
        context.scope.launch {
            context.model.map { (it.sheet as? Sheet.StopPhotos)?.markerId }.distinctUntilChanged().filterNotNull().collect { seen(StopNews(it, NewsKind.Photos)) }
        }
    }

    /** The open stop's flame showed (expanded card or details): its flames count as seen. */
    fun seenHot(markerId: String) = seen(StopNews(markerId, NewsKind.Hot))

    /** The marks as the server has them (the pushed stop's too, until it is seen). */
    private suspend fun load() {
        repository.stopsWithNewActivity(context.tripId).onSuccess { news ->
            val shown = (listOfNotNull(pushed) + news).filterNot(::seenAlready).toSet()
            context.update { it.copy(news = shown) }
        }
    }

    /** Marks the stop, unless that news shows already. */
    private fun mark(news: StopNews) {
        if (seenAlready(news)) return
        context.update { it.copy(news = it.news + news) }
    }

    /**
     * That news shows already (its comments or photos, or for the rest the stop, are open): read, not marked. Flames
     * are kept for the open stop's flame to pop.
     */
    private fun seenAlready(news: StopNews): Boolean {
        val model = context.model.value
        val open = when (news.kind) {
            NewsKind.Comments -> (model.sheet as? Sheet.StopComments)?.markerId
            NewsKind.Photos -> (model.sheet as? Sheet.StopPhotos)?.markerId
            NewsKind.Other -> (model.selection as? Selection.Stop)?.markerId
            NewsKind.Hot -> null
        }
        if (open != news.markerId) return false
        if (pushed == news) pushed = null
        context.scope.launch { repository.markSeen(news) }
        return true
    }

    private fun seen(news: StopNews) {
        if (pushed == news) pushed = null
        if (news !in context.model.value.news) return
        context.update { it.copy(news = it.news - news) }
        context.scope.launch { repository.markSeen(news) }
    }
}
