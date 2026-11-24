package com.falcon.tripingly.feature.social.presentation.nearby

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_load_nearby
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_location_unavailable
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.State
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Status
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Nearby's places: our users' hot spots around me first, then OpenStreetMap sights where we have few (the server's
 * order), marked hot here. It asks for the location only once the tab is open; where it looked stays in memory only,
 * for the next pages and the search ([point]).
 */
internal class NearbyPlaces(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<State>,
    private val placeRepository: PlaceRepository,
    private val socialRepository: SocialRepository,
    private val getCurrentLocation: GetCurrentLocationUseCase,
    private val showMessage: (UiText) -> Unit,
) {
    private var cursor: String? = null

    /** Where I was when Nearby looked; in memory only. */
    var point: GeoPoint? = null
        private set
    private var loadingMore: Job? = null

    init {
        // A flame changed on another screen (a stop on the trip map), or a count the server sends live, shows here too.
        scope.launch {
            socialRepository.hotChanges.collect { change ->
                updatePlace(change.placeId) { it.copy(hotByMe = change.hotByMe ?: it.hotByMe, hotCount = change.hotCount) }
            }
        }
    }

    private fun update(change: (State) -> State) = state.update(change)

    /** A first check that finds nothing allowed yet shows the ask; an answer of no keeps it. */
    fun onPermission(permission: LocationPermission, isAnswer: Boolean) {
        val status = when (permission) {
            LocationPermission.Granted -> null
            LocationPermission.Blocked -> Status.Blocked
            LocationPermission.NotAsked, LocationPermission.ShouldExplain -> Status.NeedsPermission
        }
        val current = state.value.status
        if (status != null) {
            if (isAnswer || current != Status.Ready) update { it.copy(status = status) }
            return
        }
        if (current == Status.Ready || current == Status.Locating) return
        locate()
    }

    private fun locate() {
        update { it.copy(status = Status.Locating) }
        scope.launch {
            when (val location = getCurrentLocation().first()) {
                is AppResult.Success -> {
                    val point = GeoPoint(location.data.latitude, location.data.longitude)
                    this@NearbyPlaces.point = point
                    load(point)
                }
                is AppResult.Error -> {
                    update { it.copy(status = Status.Unavailable) }
                    showMessage(UiText.Resource(Res.string.social_nearby_location_unavailable))
                }
            }
        }
    }

    fun refresh() {
        point?.let(::load)
    }

    /** The places around [point]. */
    private fun load(point: GeoPoint) {
        loadingMore?.cancel()
        // A retry from the error screen leaves it as it is until the answer; failing again also says why in the banner
        // (Shahin, 2026-11-22).
        val isRetry = state.value.loadError != null
        scope.launch {
            update { it.copy(isLoading = true) }
            when (val result = placeRepository.nearby(point, cursor = null)) {
                is AppResult.Success -> {
                    cursor = result.data.nextCursor
                    update { it.copy(places = result.data.items.toImmutableList(), isLoading = false, status = Status.Ready, loadError = null) }
                }
                is AppResult.Error -> {
                    // Nothing to show yet: the error screen; places already shown stay, with a banner.
                    val message = result.error.toUiText(Res.string.social_error_load_nearby)
                    val isEmpty = state.value.places.isEmpty()
                    update { it.copy(isLoading = false, status = Status.Ready, loadError = message.takeIf { isEmpty }) }
                    if (!isEmpty || isRetry) showMessage(message)
                }
            }
        }
    }

    /** The next page; one request at a time. */
    fun loadMore() {
        if (loadingMore?.isActive == true) return
        val cursor = cursor ?: return
        val point = point ?: return
        loadingMore = scope.launch {
            val result = placeRepository.nearby(point, cursor)
            if (result is AppResult.Success) {
                this@NearbyPlaces.cursor = result.data.nextCursor
                update { nearby ->
                    val known = nearby.places.map { it.id }.toSet()
                    nearby.copy(places = (nearby.places + result.data.items.filterNot { it.id in known }).toImmutableList())
                }
            }
        }
    }

    private fun updatePlace(placeId: String, change: (NearbyPlace) -> NearbyPlace) = update { nearby ->
        val changed: (List<NearbyPlace>) -> List<NearbyPlace> = { list -> list.map { if (it.id == placeId) change(it) else it } }
        nearby.copy(places = changed(nearby.places).toImmutableList(), search = nearby.search.copy(spots = changed(nearby.search.spots).toImmutableList()))
    }
}
