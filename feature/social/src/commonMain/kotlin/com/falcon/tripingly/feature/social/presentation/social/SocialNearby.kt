package com.falcon.tripingly.feature.social.presentation.social

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.social.LikeState
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_add_place_no_trips
import com.falcon.tripingly.feature.social.generated.resources.social_error_like
import com.falcon.tripingly.feature.social.generated.resources.social_error_load_days
import com.falcon.tripingly.feature.social.generated.resources.social_error_load_nearby
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_location_unavailable
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.AddPlaceState
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.Event
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.NearbyState
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.NearbyStatus
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Nearby tab: popular places around me, liked and added to a day of one of my trips. It asks for the location
 * only once the tab is open; where it looked stays in memory only, for the next pages.
 */
internal class SocialNearby(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<State>,
    private val placeRepository: PlaceRepository,
    private val socialRepository: SocialRepository,
    private val tripRepository: TripRepository,
    private val markerRepository: MarkerRepository,
    private val getCurrentLocation: GetCurrentLocationUseCase,
    private val sendEvent: (Event) -> Unit,
    private val showMessage: (UiText) -> Unit,
) {
    private var cursor: String? = null
    private var point: GeoPoint? = null
    private var loadingMore: Job? = null
    private val likeJobs = mutableMapOf<String, Job>()

    /** As the server last confirmed it: the heart before the first tap of a burst. */
    private val confirmedLikes = mutableMapOf<String, LikeState>()

    private fun update(change: (NearbyState) -> NearbyState) = state.update { it.copy(nearby = change(it.nearby)) }

    /** A first check that finds nothing allowed yet shows the ask; an answer of no keeps it. */
    fun onPermission(permission: LocationPermission, isAnswer: Boolean) {
        val status = when (permission) {
            LocationPermission.Granted -> null
            LocationPermission.Blocked -> NearbyStatus.Blocked
            LocationPermission.NotAsked, LocationPermission.ShouldExplain -> NearbyStatus.NeedsPermission
        }
        val current = state.value.nearby.status
        if (status != null) {
            if (isAnswer || current != NearbyStatus.Ready) update { it.copy(status = status) }
            return
        }
        if (current == NearbyStatus.Ready || current == NearbyStatus.Locating) return
        locate()
    }

    private fun locate() {
        update { it.copy(status = NearbyStatus.Locating) }
        scope.launch {
            when (val location = getCurrentLocation().first()) {
                is AppResult.Success -> {
                    val point = GeoPoint(location.data.latitude, location.data.longitude)
                    this@SocialNearby.point = point
                    load(point)
                }
                is AppResult.Error -> {
                    update { it.copy(status = NearbyStatus.Unavailable) }
                    showMessage(UiText.Resource(Res.string.social_nearby_location_unavailable))
                }
            }
        }
    }

    fun refresh() {
        point?.let(::load)
    }

    private fun load(point: GeoPoint) {
        loadingMore?.cancel()
        scope.launch {
            update { it.copy(isLoading = true) }
            when (val result = placeRepository.nearby(point, cursor = null)) {
                is AppResult.Success -> {
                    cursor = result.data.nextCursor
                    update { it.copy(places = result.data.items.toImmutableList(), isLoading = false, status = NearbyStatus.Ready) }
                }
                is AppResult.Error -> {
                    update { it.copy(isLoading = false, status = NearbyStatus.Ready) }
                    showMessage(result.error.toUiText(Res.string.social_error_load_nearby))
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
                this@SocialNearby.cursor = result.data.nextCursor
                update { nearby ->
                    val known = nearby.places.map { it.id }.toSet()
                    nearby.copy(places = (nearby.places + result.data.items.filterNot { it.id in known }).toImmutableList())
                }
            }
        }
    }

    /** Like a trip card's heart: shown at once and kept; a newer tap wins, and only a refusal puts it back. */
    fun toggleLike(placeId: String) {
        val shown = state.value.nearby.places.firstOrNull { it.id == placeId } ?: return
        if (likeJobs[placeId]?.isActive != true) confirmedLikes[placeId] = LikeState(shown.likedByMe, shown.likeCount)
        likeJobs[placeId]?.cancel()
        val liked = !shown.likedByMe
        updatePlace(placeId) { it.copy(likedByMe = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0)) }
        likeJobs[placeId] = scope.launch {
            val result = socialRepository.setLiked(LikeTarget.Place, placeId, liked)
            if (result is AppResult.Error) {
                val before = confirmedLikes.getValue(placeId)
                updatePlace(placeId) { it.copy(likedByMe = before.liked, likeCount = before.likeCount) }
                showMessage(result.error.toUiText(Res.string.social_error_like))
            }
        }
    }

    private fun updatePlace(placeId: String, change: (NearbyPlace) -> NearbyPlace) =
        update { nearby -> nearby.copy(places = nearby.places.map { if (it.id == placeId) change(it) else it }.toImmutableList()) }

    /** The trips I can add stops to, from the device; the picked trip's days come from the server. */
    fun openAddPlace(place: NearbyPlace) {
        scope.launch {
            val trips = tripRepository.observeMyTrips().first().filter { it.role.canEdit }
            if (trips.isEmpty()) {
                showMessage(UiText.Resource(Res.string.social_add_place_no_trips))
                return@launch
            }
            state.update { it.copy(addPlace = AddPlaceState(place, trips.toImmutableList())) }
        }
    }

    fun pickTrip(trip: Trip) {
        state.update { it.copy(addPlace = it.addPlace?.copy(trip = trip, days = persistentListOf(), isLoadingDays = true)) }
        scope.launch {
            val result = tripRepository.refreshTrip(trip.id)
            state.update { state ->
                val addPlace = state.addPlace?.takeIf { it.trip?.id == trip.id } ?: return@update state
                when (result) {
                    is AppResult.Success -> state.copy(addPlace = addPlace.copy(days = result.data.days.toImmutableList(), isLoadingDays = false))
                    is AppResult.Error -> state.copy(
                        addPlace = addPlace.copy(isLoadingDays = false),
                        message = result.error.toUiText(Res.string.social_error_load_days),
                    )
                }
            }
        }
    }

    /** Added at once, like a stop added on the map, and the trip opens to show it. */
    fun addPlace(day: TripDay) {
        val addPlace = state.value.addPlace ?: return
        val trip = addPlace.trip ?: return
        val place = addPlace.place
        state.update { it.copy(addPlace = null) }
        scope.launch {
            markerRepository.addMarker(trip.id, day.id, NewMarker(name = place.name, location = place.location, placeId = place.id))
            sendEvent(Event.OpenTrip(trip.id))
        }
    }
}
