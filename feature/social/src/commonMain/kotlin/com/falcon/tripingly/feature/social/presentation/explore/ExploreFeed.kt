package com.falcon.tripingly.feature.social.presentation.explore

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.social.ExploreFilter
import com.falcon.tripingly.core.model.social.ExplorePeriod
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_load_explore
import com.falcon.tripingly.feature.social.generated.resources.social_explore_nearby_blocked
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_location_unavailable
import com.falcon.tripingly.feature.social.presentation.explore.ExploreViewModel.Event
import com.falcon.tripingly.feature.social.presentation.explore.ExploreViewModel.State
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Explore's list: public trips for the period, a page at a time. Its Nearby filter asks for the location only
 * when it's switched on, and keeps it in memory only while it's on.
 */
internal class ExploreFeed(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<State>,
    private val socialRepository: SocialRepository,
    private val getCurrentLocation: GetCurrentLocationUseCase,
    private val sendEvent: (Event) -> Unit,
    private val showMessage: (UiText) -> Unit,
) {
    private var cursor: String? = null
    private var point: GeoPoint? = null

    /** The location was asked for because the Nearby filter was switched on. */
    private var isAskingForLocation = false
    private var loading: Job? = null
    private var loadingMore: Job? = null

    private fun update(change: (State) -> State) = state.update(change)

    /** The first page for the current filters; replaces a load still on its way, and stops loading more of the old list. */
    fun load() {
        loading?.cancel()
        loadingMore?.cancel()
        loading = scope.launch {
            update { it.copy(isLoading = true) }
            when (val result = socialRepository.explore(filter(), cursor = null)) {
                is AppResult.Success -> {
                    cursor = result.data.nextCursor
                    update { it.copy(trips = result.data.items.toImmutableList(), isLoading = false, hasLoaded = true, loadError = null) }
                }
                is AppResult.Error -> {
                    // Nothing to show yet: the error screen; trips already shown stay, with a banner.
                    val message = result.error.toUiText(Res.string.social_error_load_explore)
                    val isEmpty = state.value.trips.isEmpty()
                    update { it.copy(isLoading = false, hasLoaded = true, loadError = message.takeIf { isEmpty }) }
                    if (!isEmpty) showMessage(message)
                }
            }
        }
    }

    /** The next page; one request at a time, and none while the first page is loading. */
    fun loadMore() {
        if (loadingMore?.isActive == true || loading?.isActive == true) return
        val cursor = cursor ?: return
        loadingMore = scope.launch {
            val result = socialRepository.explore(filter(), cursor)
            if (result is AppResult.Success) {
                this@ExploreFeed.cursor = result.data.nextCursor
                update { explore ->
                    val known = explore.trips.map { it.trip.id }.toSet()
                    explore.copy(trips = (explore.trips + result.data.items.filterNot { it.trip.id in known }).toImmutableList())
                }
            }
        }
    }

    private fun filter() = ExploreFilter(period = state.value.period, near = point.takeIf { state.value.isNearbyFilter })

    fun selectPeriod(period: ExplorePeriod) {
        if (period == state.value.period) return
        update { it.copy(period = period) }
        load()
    }

    /** Off at once; on only once the location is known, asked for right then if it isn't allowed yet. */
    fun toggleNearby() {
        if (state.value.isNearbyFilter) {
            point = null
            update { it.copy(isNearbyFilter = false) }
            load()
        } else {
            sendEvent(Event.CheckLocation)
        }
    }

    fun onLocationChecked(permission: LocationPermission) {
        when (permission) {
            LocationPermission.Granted -> locate()
            LocationPermission.NotAsked, LocationPermission.ShouldExplain -> {
                isAskingForLocation = true
                sendEvent(Event.RequestPermission)
            }
            LocationPermission.Blocked -> showMessage(UiText.Resource(Res.string.social_explore_nearby_blocked))
        }
    }

    /** The answer to the prompt the Near me filter asked for; a no leaves the filter off. */
    fun onPermissionAnswer(permission: LocationPermission) {
        if (!isAskingForLocation) return
        isAskingForLocation = false
        if (permission == LocationPermission.Granted) locate()
    }

    private fun locate() {
        update { it.copy(isNearbyFilter = true, isLoading = true) }
        scope.launch {
            when (val location = getCurrentLocation().first()) {
                is AppResult.Success -> {
                    point = GeoPoint(location.data.latitude, location.data.longitude)
                    load()
                }
                is AppResult.Error -> {
                    update { it.copy(isNearbyFilter = false, isLoading = false) }
                    showMessage(UiText.Resource(Res.string.social_nearby_location_unavailable))
                }
            }
        }
    }
}
