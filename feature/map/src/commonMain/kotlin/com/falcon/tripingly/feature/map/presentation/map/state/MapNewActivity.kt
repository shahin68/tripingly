package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.push.NewActivityRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The "new" marks on stops with notifications I haven't seen: loaded when the trip opens, added live, cleared when the
 * stop is opened. [pushedStopId] is the stop a push opened the map on: its mark shows even though opening the push
 * already read that notification.
 */
internal class MapNewActivity(
    private val context: MapContext,
    private val repository: NewActivityRepository,
    private val pushedStopId: String?,
) {
    fun start() {
        mark(setOfNotNull(pushedStopId))
        context.scope.launch { repository.stopsWithNewActivity(context.tripId).onSuccess(::mark) }
        context.scope.launch { repository.newActivity(context.tripId).collect { mark(setOf(it)) } }
        context.scope.launch {
            context.model.map { (it.selection as? Selection.Stop)?.markerId }.distinctUntilChanged().filterNotNull().collect(::seen)
        }
    }

    /** Marks [markerIds]; the open stop is seen already. */
    private fun mark(markerIds: Set<String>) {
        val open = (context.model.value.selection as? Selection.Stop)?.markerId
        if (open != null && open in markerIds) context.scope.launch { repository.markSeen(open) }
        context.update { it.copy(newStopIds = it.newStopIds + (markerIds - setOfNotNull(open))) }
    }

    private fun seen(markerId: String) {
        if (markerId !in context.model.value.newStopIds) return
        context.update { it.copy(newStopIds = it.newStopIds - markerId) }
        context.scope.launch { repository.markSeen(markerId) }
    }
}
