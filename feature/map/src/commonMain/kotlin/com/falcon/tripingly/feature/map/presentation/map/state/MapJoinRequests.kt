package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.trips.TripMapViewRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * "@anna and 2 others want to join": the owner's pill under the header. The newest requests load whenever the trip's
 * count of waiting requests changes (only the owner's is above 0). Tapping the pill dismisses the people it showed and
 * opens the members sheet, where the owner answers them; someone new asking shows it again. Once the pill has shown
 * its people in full it shrinks to their faces, and stays that small for them from then on (Shahin, 2026-11-16); a long
 * press shows them in full again for a while (Shahin, 2026-11-17).
 */
internal class MapJoinRequests(
    private val context: MapContext,
    private val repository: TripRepository,
    private val mapViews: TripMapViewRepository,
) {
    fun start() {
        context.scope.launch {
            val seen = mapViews.seenJoinRequesters(context.tripId)
            context.update { it.copy(seenJoinRequesterIds = seen) }
        }
        context.scope.launch {
            context.trip.map { it?.pendingJoinRequestCount ?: 0 }.distinctUntilChanged().collectLatest { count ->
                if (count == 0) {
                    context.update { it.copy(joinRequesters = emptyList()) }
                } else {
                    repository.joinRequests(context.tripId).onSuccess { page ->
                        context.update { it.copy(joinRequesters = page.items.map { request -> request.user }) }
                    }
                }
            }
        }
    }

    /** The pill showed its people in full: from now on it is a small circle for them. */
    fun markSeen() {
        val model = context.model.value
        val seen = model.seenJoinRequesterIds.orEmpty() + model.joinRequesters.map { it.id }
        context.update { it.copy(seenJoinRequesterIds = seen, isJoinRequestsShownAgain = false) }
        context.scope.launch { mapViews.saveSeenJoinRequesters(context.tripId, seen) }
    }

    /** A long press on the small pill: it shows its people in full again, then shrinks as before (Shahin, 2026-11-17). */
    fun showAgain() = context.update { it.copy(isJoinRequestsShownAgain = true) }

    fun dismiss() = context.update { it.copy(dismissedJoinRequesterIds = it.dismissedJoinRequesterIds + it.joinRequesters.map { user -> user.id }) }
}
