package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.trips.TripRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * "@anna and 2 others want to join": the owner's pill under the header. The newest requests load whenever the trip's
 * count of waiting requests changes (only the owner's is above 0). Tapping the pill dismisses the people it showed and
 * opens the members sheet, where the owner answers them; someone new asking shows it again.
 */
internal class MapJoinRequests(private val context: MapContext, private val repository: TripRepository) {
    fun start() {
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

    fun dismiss() = context.update { it.copy(dismissedJoinRequesterIds = it.dismissedJoinRequesterIds + it.joinRequesters.map { user -> user.id }) }
}
