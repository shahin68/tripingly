package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.common.coroutines.DefaultCoroutineDispatchers
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.data.photos.PhotoRepository
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.MarkerChangeFailure
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.TripErrorCodes
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceImage
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.JoinRequestStatus
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMarker
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.domain.model.CameraFrame
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.error_location_denied_manual
import com.falcon.tripingly.feature.map.generated.resources.error_location_permission_required
import com.falcon.tripingly.feature.map.generated.resources.error_location_services_disabled
import com.falcon.tripingly.feature.map.generated.resources.error_location_unavailable
import com.falcon.tripingly.feature.map.generated.resources.error_location_unexpected
import com.falcon.tripingly.feature.map.generated.resources.error_location_unknown
import com.falcon.tripingly.feature.map.generated.resources.map_change_not_saved
import com.falcon.tripingly.feature.map.generated.resources.map_error_add_day
import com.falcon.tripingly.feature.map.generated.resources.map_error_cancel_join
import com.falcon.tripingly.feature.map.generated.resources.map_error_delete_day
import com.falcon.tripingly.feature.map.generated.resources.map_error_join
import com.falcon.tripingly.feature.map.generated.resources.map_error_like
import com.falcon.tripingly.feature.map.generated.resources.map_error_load_trip
import com.falcon.tripingly.feature.map.generated.resources.map_error_remove_my_content
import com.falcon.tripingly.feature.map.generated.resources.map_error_report
import com.falcon.tripingly.feature.map.generated.resources.map_error_save_trip
import com.falcon.tripingly.feature.map.generated.resources.map_error_stop_change
import com.falcon.tripingly.feature.map.generated.resources.map_error_unsave_trip
import com.falcon.tripingly.feature.map.generated.resources.map_join_declined
import com.falcon.tripingly.feature.map.generated.resources.map_join_requested
import com.falcon.tripingly.feature.map.generated.resources.map_join_withdrawn
import com.falcon.tripingly.feature.map.generated.resources.map_my_content_removed
import com.falcon.tripingly.feature.map.generated.resources.map_reported
import com.falcon.tripingly.feature.map.generated.resources.map_trip_saved
import com.falcon.tripingly.feature.map.generated.resources.map_trip_unsaved
import com.falcon.tripingly.feature.map.presentation.component.PlacePin
import com.falcon.tripingly.feature.map.presentation.component.placeClusters
import com.falcon.tripingly.feature.map.presentation.component.placePins
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * One trip on the map. Days and markers come from the cached trip, reloaded from
 * the server on open; owners and editors add and remove stops and days. Stop
 * changes show at once and are saved in the background; one that fails is
 * undone and explained, with Retry when the connection was the problem. Day
 * changes wait for the server. Place pins load by map square once the camera rests (see [showPins]);
 * a tapped place opens a card that adds it to the open day. Everyone who sees the trip likes and comments
 * on its stops; someone else's public trip can also be added to my trips, its owner's profile opened and
 * the trip or a stop reported.
 */
class MapViewModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
    private val markerRepository: MarkerRepository,
    private val placeRepository: PlaceRepository,
    private val photoRepository: PhotoRepository,
    private val socialRepository: SocialRepository,
    private val appConfigRepository: AppConfigRepository,
    sessionRepository: SessionRepository,
    private val getCurrentLocationUseCase: GetCurrentLocationUseCase,
    private val dispatchers: CoroutineDispatchers = DefaultCoroutineDispatchers(),
) : ViewModel() {

    private val ui = MutableStateFlow(UiState())
    private val myUserId = (sessionRepository.session.value as? SessionState.SignedIn)?.account?.id

    private val trip: StateFlow<TripDetails?> =
        tripRepository.observeTrip(tripId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<State> = combine(trip, ui, ::toState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toState(null, ui.value))

    private var prefetchJob: Job? = null
    private var pinsJob: Job? = null
    /** The view the place pins are for, once the camera has rested. */
    private var pinArea: PinArea? = null
    private var placeDetailsJob: Job? = null
    /** Places whose first image is on its way for a stop card; see [loadStopPlaceImage]. */
    private val loadingStopPlaces = mutableSetOf<String>()
    private var searchJob: Job? = null
    /** The latest touch closed a place or stop, so its tap only closes it (see [onScreenTouch]). */
    private var isClosingTouch = false

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadTrip()
        watchFailedChanges()
        watchPinChanges()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnPermissionChecked -> onPermissionChecked(action.permission)
            is Action.OnPermissionResult -> onPermissionResult(action.permission)
            is Action.OnConfirmLocationPrompt -> confirmLocationPrompt()
            is Action.OnDismissLocationPrompt -> ui.update { it.copy(locationPrompt = null, isWaitingForFirstLocation = false) }
            is Action.CenterOnUserLocation -> centerOnUserLocation()
            is Action.NavigateToLocation -> navigateTo(action.coordinates, action.zoom)
            is Action.OnMapClick -> onMapClick(action.coordinates)
            is Action.OnPlacePinClick -> openPlace(action.place)
            is Action.OnClusterClick -> navigateTo(
                Coordinates(action.cluster.location.lat, action.cluster.location.lng),
                ui.value.zoomLevel + CLUSTER_ZOOM_STEP,
            )
            is Action.OnMapLongClick -> if (canEdit() && activeDay() != null) ui.update { it.copy(pinToName = action.coordinates) }
            is Action.OnConfirmPinName -> addNamedPin(action.name)
            is Action.OnDismissPinName -> ui.update { it.copy(pinToName = null) }
            is Action.OnMarkerClick -> selectMarker(action.marker)
            is Action.OnRemoveMarker -> removeMarker(action.markerId)
            is Action.ClearAllMarkers -> ui.update { it.copy(confirm = Confirm.ClearDay) }
            is Action.DeleteDay -> ui.update { it.copy(confirm = Confirm.DeleteDay) }
            is Action.OnConfirm -> confirm()
            is Action.OnDismissConfirm -> ui.update { it.copy(confirm = null) }
            is Action.OnRemoveMyContent -> if (ui.value.selectedMarkerId != null) ui.update { it.copy(confirm = Confirm.RemoveMyContent) }
            is Action.OnDeleteAnyway -> deleteAnyway()
            is Action.OnDismissDeleteAnyway -> ui.update { it.copy(othersContent = null) }
            is Action.AddDay -> addDay()
            is Action.OnMessageShown -> ui.update { it.copy(message = null) }
            is Action.RetryFailedChanges -> retryFailedChanges()
            is Action.OnCameraMove -> onCameraMove(action.coordinates, action.zoom, action.bounds)
            is Action.OnScreenTouch -> onScreenTouch()
            is Action.OnOpenStopPhotos -> ui.update { it.copy(photosMarkerId = it.selectedMarkerId) }
            is Action.OnCloseStopPhotos -> ui.update { it.copy(photosMarkerId = null) }
            is Action.OnOpenPlacePhotos -> ui.update { it.copy(photosPlace = it.selectedPlace.takeIf { _ -> it.selectedAddress == null }) }
            is Action.OnClosePlacePhotos -> ui.update { it.copy(photosPlace = null) }
            is Action.OnAddStopPhotos -> addStopPhotos(action.images)
            is Action.OnOpenPhotoCamera -> if (canEdit()) ui.update { it.copy(isPhotoCameraOpen = it.selectedMarkerId != null) }
            is Action.OnClosePhotoCamera -> ui.update { it.copy(isPhotoCameraOpen = false) }
            is Action.OnAddPlaceToDay -> addPlace()
            is Action.OnOpenSearch -> ui.update { it.copy(isSearchOpen = true, selectedMarkerId = null, isStopCardShown = false) }
            is Action.OnSearchQueryChanged -> search(action.query)
            is Action.OnSearchResultClick -> showSearchResult(action.result)
            is Action.OnCloseSearch -> closeSearch()
            is Action.OnDaySelected -> selectDay(action.dayIndex)
            is Action.OnBackClick -> sendEvent(Event.NavigateBack)
            is Action.OnToggleStopLike -> toggleStopLike(action.markerId)
            is Action.OnOpenStopComments -> ui.update { it.copy(commentsMarkerId = it.selectedMarkerId) }
            is Action.OnCloseStopComments -> ui.update { it.copy(commentsMarkerId = null) }
            is Action.OnToggleSaveTrip -> toggleSave()
            is Action.OnRequestToJoin -> updateSharing(Res.string.map_join_requested, Res.string.map_error_join) { tripRepository.requestToJoin(tripId) }
            is Action.OnCancelJoinRequest -> updateSharing(Res.string.map_join_withdrawn, Res.string.map_error_cancel_join) { tripRepository.cancelJoinRequest(tripId) }
            is Action.OnOpenJoinRequests -> ui.update { it.copy(isMembersOpen = true) }
            is Action.OnCloseJoinRequests -> ui.update { it.copy(isMembersOpen = false) }
            is Action.OnOpenOwnerProfile -> trip.value?.trip?.owner?.let { sendEvent(Event.OpenProfile(it.username)) }
            is Action.OnReportTrip -> ui.update { it.copy(reporting = ReportSubject(ReportTarget.Trip, tripId)) }
            is Action.OnReportStop -> ui.value.selectedMarkerId?.let { id ->
                ui.update { it.copy(reporting = ReportSubject(ReportTarget.Marker, id)) }
            }
            is Action.OnSendReport -> sendReport(action.reason, action.details)
            is Action.OnDismissReport -> ui.update { it.copy(reporting = null) }
            is Action.OnOpenPlaceImage -> ui.update { it.copy(placeImageIndex = action.index) }
            is Action.OnClosePlaceImage -> ui.update { it.copy(placeImageIndex = null) }
            is Action.OnReportPhoto -> ui.update {
                // A place's Commons image is reported as the place's image, every other photo as a photo.
                val target = if (action.photo.credit != null) ReportTarget.PlaceImage else ReportTarget.Photo
                it.copy(reporting = ReportSubject(target, action.photo.id))
            }
        }
    }

    private fun toState(details: TripDetails?, ui: UiState): State {
        val days = details?.days.orEmpty()
        val activeIndex = ui.activeDayIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0))
        val role = details?.trip?.role
        val markers = days.getOrNull(activeIndex)?.markers.orEmpty().mapIndexed { index, it -> it.toMapMarker(index + 1, role) }
        val selectedMarker = markers.firstOrNull { it.id == ui.selectedMarkerId }
        val selectedPlaceDetails = ui.selectedPlaceDetails?.takeIf { it.id == ui.selectedPlace?.id }
        return State(
            tripName = details?.trip?.name.orEmpty(),
            tripCreatedAt = details?.trip?.createdAt,
            days = days.map { DayTab(it.id, it.position + 1, it.date) }.toImmutableList(),
            activeDayIndex = activeIndex,
            markers = markers.toImmutableList(),
            canEdit = role?.canEdit == true,
            // Like the server: an editor only when every stop on the day is theirs.
            canDeleteDay = role == TripRole.OWNER || (role?.canEdit == true && markers.all { it.canDelete }),
            isViewer = details?.trip?.role == TripRole.VIEWER,
            ownerUsername = details?.trip?.owner?.username,
            isSavedByMe = details?.trip?.savedByMe == true,
            myJoinRequest = details?.myJoinRequest,
            pendingJoinRequestCount = details?.pendingJoinRequestCount ?: 0,
            isMembersOpen = ui.isMembersOpen,
            isLoading = days.isEmpty() && ui.isLoadingTrip,
            isSaving = ui.isSaving,
            currentLocation = ui.currentLocation,
            cameraTarget = ui.cameraTarget,
            zoomLevel = ui.zoomLevel,
            isCameraReady = ui.isCameraReady,
            // From the stops as they are now, so a return to the screen frames them without one deleted since.
            frame = ui.frame?.let { frame ->
                val stops = if (frame.dayId == null) days.flatMap { it.markers } else days.firstOrNull { it.id == frame.dayId }?.markers.orEmpty()
                stops.takeIf { it.isNotEmpty() }?.let { CameraFrame(it.map { stop -> Coordinates(stop.location.lat, stop.location.lng) }, frame.id) }
            },
            isPermissionGranted = ui.permission == LocationPermission.Granted,
            isLoadingLocation = ui.isLoadingLocation,
            selectedMarker = selectedMarker,
            selectedStopPlaceImage = selectedMarker?.takeIf { it.coverPhotoId == null }?.placeId?.let { ui.stopPlaceImages[it] },
            isStopCardShown = ui.isStopCardShown && markers.any { it.id == ui.selectedMarkerId },
            photosMarker = markers.firstOrNull { it.id == ui.photosMarkerId },
            photosPlace = ui.photosPlace,
            commentsMarker = markers.firstOrNull { it.id == ui.commentsMarkerId },
            isReportOpen = ui.reporting != null,
            isReporting = ui.isReporting,
            isUpdatingSharing = ui.isUpdatingSharing,
            isPhotoCameraOpen = ui.isPhotoCameraOpen && markers.any { it.id == ui.selectedMarkerId },
            placePins = ui.placePins.toImmutableList(),
            placeClusters = ui.placeClusters.toImmutableList(),
            selectedPlace = ui.selectedPlace,
            selectedPlaceDetails = selectedPlaceDetails,
            placeImageIndex = ui.placeImageIndex?.takeIf { index -> index in selectedPlaceDetails?.images.orEmpty().indices },
            selectedPlaceAddress = ui.selectedAddress?.address,
            isPlaceCardShown = ui.selectedPlace != null && ui.isPlaceCardShown,
            isSearchOpen = ui.isSearchOpen,
            searchQuery = ui.searchQuery,
            searchResults = ui.searchResults.toImmutableList(),
            isSearching = ui.isSearching,
            isNamingPin = ui.pinToName != null,
            message = ui.message,
            canRetry = ui.message != null && ui.failedChanges.isNotEmpty(),
            isWaitingForFirstLocation = ui.isWaitingForFirstLocation,
            confirm = ui.confirm,
            othersContentMessage = ui.othersContent?.message,
            locationPrompt = ui.locationPrompt,
        )
    }

    private fun loadTrip() {
        viewModelScope.launch {
            val result = tripRepository.refreshTrip(tripId)
            ui.update {
                it.copy(
                    isLoadingTrip = false,
                    message = (result as? AppResult.Error)?.error?.toUiText(Res.string.map_error_load_trip),
                    // Without the trip, or a trip without days, there's nothing to show first: the map opens on the default.
                    isCameraReady = it.isCameraReady || result is AppResult.Error || (result as? AppResult.Success)?.data?.days?.isEmpty() == true,
                )
            }
        }
        // Show the open day's stops when the trip opens, or the whole trip's while that day has none,
        // or the trip's destination while it has no stops at all.
        viewModelScope.launch {
            val details = trip.first { it != null && it.days.isNotEmpty() } ?: return@launch
            val stops = activeDay()?.markers.orEmpty().ifEmpty { details.days.flatMap { it.markers } }
            val destination = details.destination
            if (stops.isEmpty() && destination != null) {
                navigateTo(Coordinates(destination.location.lat, destination.location.lng), DESTINATION_ZOOM)
            } else if (stops.isEmpty()) {
                // A trip without a location: the map opens on the default.
                ui.update { it.copy(isCameraReady = true) }
            } else {
                // The open day as when its tab is tapped; the whole trip while that day has no stops.
                frame(activeDay()?.takeIf { it.markers.isNotEmpty() }?.id)
            }
        }
    }

    private fun currentDays(): List<TripDay> = trip.value?.days.orEmpty()

    private fun activeDay(): TripDay? {
        val days = currentDays()
        return days.getOrNull(ui.value.activeDayIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0)))
    }

    private fun canEdit(): Boolean = trip.value?.trip?.role?.canEdit == true

    private fun selectDay(index: Int) {
        ui.update { it.copy(activeDayIndex = index, selectedMarkerId = null, isStopCardShown = false) }
        currentDays().getOrNull(index)?.takeIf { it.markers.isNotEmpty() }?.let { frame(it.id) }
    }

    /** From the map or a chip: bring the stop to the middle, then show its card (at once when it's already there). */
    private fun selectMarker(marker: MapMarker) {
        closePlace()
        val zoom = ui.value.zoomLevel
        val isThere = isCameraAt(GeoPoint(marker.position.latitude, marker.position.longitude), zoom)
        ui.update { it.copy(selectedMarkerId = marker.id, isStopCardShown = isThere) }
        navigateTo(marker.position, zoom)
        if (marker.coverPhotoId == null) loadStopPlaceImage(marker.placeId)
    }

    /** A stop without a cover shows its place's first image; asked for once per place. */
    private fun loadStopPlaceImage(placeId: String?) {
        if (placeId == null || placeId in ui.value.stopPlaceImages || !loadingStopPlaces.add(placeId)) return
        viewModelScope.launch {
            placeRepository.place(placeId).onSuccess { details ->
                ui.update { it.copy(stopPlaceImages = it.stopPlaceImages + (placeId to details.images.firstOrNull())) }
            }
            loadingStopPlaces -= placeId
        }
    }

    /**
     * Photos picked or taken on the stop card: they upload in the background, and the sheet opens to show them
     * going up.
     */
    private fun addStopPhotos(images: List<PickedImage>) {
        ui.update { it.copy(isPhotoCameraOpen = false) }
        val markerId = ui.value.selectedMarkerId ?: return
        if (images.isEmpty() || !canEdit()) return
        photoRepository.upload(PhotoOwner.Stop(markerId), images)
        ui.update { it.copy(photosMarkerId = markerId) }
    }

    /** A stop's heart likes its place; shows at once, see [SocialRepository.setLiked]. */
    private fun toggleStopLike(markerId: String) {
        val stop = currentDays().firstNotNullOfOrNull { day -> day.markers.firstOrNull { it.id == markerId } } ?: return
        if (!stop.canLike) return
        viewModelScope.launch {
            val result = socialRepository.setLiked(LikeTarget.Place, stop.placeId, liked = !stop.likedByMe)
            if (result is AppResult.Error) ui.update { it.copy(message = result.error.toUiText(Res.string.map_error_like)) }
        }
    }

    /** Someone else's trip goes into My Trips (read-only, the owner's live trip), or out again. */
    private fun toggleSave() {
        val trip = trip.value?.trip ?: return
        val saved = !trip.savedByMe
        updateSharing(
            done = if (saved) Res.string.map_trip_saved else Res.string.map_trip_unsaved,
            failed = if (saved) Res.string.map_error_save_trip else Res.string.map_error_unsave_trip,
        ) {
            tripRepository.setSaved(trip, saved)
        }
    }

    /** Saving or joining, shown once the server agreed (the cached trip then shows the new state). */
    private fun updateSharing(done: StringResource, failed: StringResource, change: suspend () -> AppResult<Unit, DataError.Network>) {
        if (ui.value.isUpdatingSharing) return
        ui.update { it.copy(isUpdatingSharing = true) }
        viewModelScope.launch {
            val result = change()
            ui.update {
                it.copy(
                    isUpdatingSharing = false,
                    message = when (result) {
                        is AppResult.Success -> UiText.Note(UiText.Resource(done))
                        is AppResult.Error -> if (result.error.hasCode(TripErrorCodes.JOIN_REQUEST_DECLINED)) {
                            UiText.Resource(Res.string.map_join_declined)
                        } else {
                            result.error.toUiText(failed)
                        }
                    },
                )
            }
        }
    }

    private fun sendReport(reason: ReportReason, details: String) {
        val subject = ui.value.reporting ?: return
        if (ui.value.isReporting) return
        ui.update { it.copy(isReporting = true) }
        viewModelScope.launch {
            val result = socialRepository.report(subject.target, subject.id, reason, details)
            ui.update {
                when (result) {
                    is AppResult.Success -> it.copy(isReporting = false, reporting = null, message = UiText.Note(UiText.Resource(Res.string.map_reported)))
                    is AppResult.Error -> it.copy(isReporting = false, message = result.error.toUiText(Res.string.map_error_report))
                }
            }
        }
    }

    private fun closeStop() {
        ui.update { it.copy(selectedMarkerId = null, isStopCardShown = false) }
    }

    /** The screen fits the camera to the stops of the day with [dayId], or of the whole trip when null. */
    private fun frame(dayId: String?) {
        ui.update { it.copy(frame = FrameRequest(dayId, (it.frame?.id ?: 0) + 1)) }
    }

    private fun watchFailedChanges() {
        viewModelScope.launch {
            markerRepository.failures.collect { failure ->
                if (failure.tripId != tripId) return@collect
                ui.update {
                    if (failure.needsConfirm) {
                        it.copy(othersContent = OthersContent.Change(failure.error.serverMessage(), failure))
                    } else if (failure.isConnectionProblem) {
                        it.copy(
                            message = UiText.Resource(Res.string.map_change_not_saved),
                            failedChanges = it.failedChanges + failure,
                        )
                    } else {
                        // Trying again won't change the server's mind.
                        it.copy(message = failure.error.toUiText(Res.string.map_error_stop_change), failedChanges = emptyList())
                    }
                }
            }
        }
    }

    private fun retryFailedChanges() {
        val failed = ui.value.failedChanges
        ui.update { it.copy(message = null, failedChanges = emptyList()) }
        viewModelScope.launch { failed.forEach { markerRepository.retry(it) } }
    }

    /** Shows at once; see [MarkerRepository]. */
    private fun addMarker(coordinates: Coordinates) {
        val day = activeDay() ?: return
        if (!canEdit()) return
        // No name: the server names the stop after the place or address there.
        viewModelScope.launch {
            markerRepository.addMarker(tripId, day.id, NewMarker("", GeoPoint(coordinates.latitude, coordinates.longitude)))
        }
    }

    /**
     * The map loads the squares it shows by itself. Once the camera has rested, the ring of squares around
     * the view loads too (the ones not loaded yet, in one request), so a pan finds its places ready.
     */
    private fun onCameraMove(coordinates: Coordinates, zoom: Float, bounds: GeoBounds?) {
        ui.update { it.copy(cameraTarget = coordinates, zoomLevel = zoom) }
        // The camera came to rest: on the tapped place or stop, its card opens; anywhere else (the user moved
        // the map while it flew), the place or stop closes.
        ui.value.selectedPlace?.takeUnless { ui.value.isPlaceCardShown }?.let { place ->
            if (isCentered(place.location, coordinates, zoom)) ui.update { it.copy(isPlaceCardShown = true) } else closePlace()
        }
        ui.value.selectedMarkerId?.takeUnless { ui.value.isStopCardShown }?.let { id ->
            val stop = activeDay()?.markers?.firstOrNull { it.id == id }
            if (stop != null && isCentered(stop.location, coordinates, zoom)) ui.update { it.copy(isStopCardShown = true) } else closeStop()
        }
        if (bounds == null) return
        val squares = PlaceSquare.covering(bounds, floor(zoom).toInt())
        pinsJob?.cancel()
        pinsJob = viewModelScope.launch {
            // Loaded together with the squares already kept; null while offline, so the pins stay.
            placeRepository.placesIn(squares)
            pinArea = PinArea(bounds, squares)
            showPins()
        }
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            delay(PREFETCH_DELAY_MILLIS)
            placeRepository.placesIn(PlaceSquare.covering(bounds, floor(zoom).toInt(), ring = 1))
        }
    }

    /**
     * A finger went down anywhere but on the open card: an open place or stop closes. A tap that closes one
     * still opens another pin, but doesn't add a stop.
     */
    private fun onScreenTouch() {
        isClosingTouch = ui.value.selectedPlace != null || ui.value.selectedMarkerId != null
        if (ui.value.selectedPlace != null) closePlace()
        if (ui.value.selectedMarkerId != null) closeStop()
    }

    /** A tap on the map itself (pins take their own taps) adds a stop, unless it only closed a card. */
    private fun onMapClick(coordinates: Coordinates) {
        if (!isClosingTouch) addMarker(coordinates)
    }

    /**
     * Shows the pins of the squares the camera rests on, from what the repository keeps: liked places first, then
     * the best known, without the trip's stops, at most as many as the server allows.
     */
    private suspend fun showPins() {
        val area = pinArea ?: return
        val squares = placeRepository.loadedPlacesIn(area.squares).values
        val hidden = stopPlaceIds(trip.value)
        val config = appConfigRepository.config.value
        ui.update {
            it.copy(placePins = placePins(squares, area.bounds, hidden, config), placeClusters = placeClusters(squares, area.bounds))
        }
    }

    /** Pins change when a square in view reloads with new places, and when stops come or go (a stop hides its place). */
    private fun watchPinChanges() {
        viewModelScope.launch {
            placeRepository.changedSquares.collect { square -> if (pinArea?.squares?.contains(square) == true) showPins() }
        }
        viewModelScope.launch {
            trip.map(::stopPlaceIds).distinctUntilChanged().drop(1).collect { showPins() }
        }
    }

    private data class PinArea(val bounds: GeoBounds, val squares: List<PlaceSquare>)

    /** A place that is already a stop shows as the stop only. */
    private fun stopPlaceIds(details: TripDetails?): Set<String> =
        details?.days.orEmpty().flatMapTo(HashSet()) { day -> day.markers.map { it.placeId } }

    /**
     * Centers the place, then shows its card with what the pin knows (at once when the camera is already
     * there) and adds the place's details.
     */
    private fun openPlace(place: MapPlace, zoom: Float = ui.value.zoomLevel) {
        val isThere = isCameraAt(place.location, zoom)
        ui.update { it.copy(selectedPlace = place, selectedAddress = null, selectedMarkerId = null, isStopCardShown = false, isPlaceCardShown = isThere) }
        navigateTo(Coordinates(place.location.lat, place.location.lng), zoom)
        placeDetailsJob?.cancel()
        placeDetailsJob = viewModelScope.launch {
            placeRepository.place(place.id).onSuccess { details -> ui.update { it.copy(selectedPlaceDetails = details) } }
        }
    }

    /** An address from search has no place of ours yet: its card shows the address and adds it by name. */
    private fun openAddress(result: PlaceSearchResult) {
        placeDetailsJob?.cancel()
        val place = MapPlace(
            id = "",
            name = result.name,
            category = PlaceCategory.Other,
            location = result.location,
            isTripinly = false,
            likeCount = 0,
            prominence = 0,
        )
        val zoom = result.type.zoom()
        val isThere = isCameraAt(result.location, zoom)
        ui.update {
            it.copy(
                selectedPlace = place,
                selectedAddress = result,
                selectedPlaceDetails = null,
                selectedMarkerId = null,
                isStopCardShown = false,
                isPlaceCardShown = isThere,
            )
        }
        navigateTo(Coordinates(result.location.lat, result.location.lng), zoom)
    }

    private fun closePlace() {
        placeDetailsJob?.cancel()
        ui.update { it.copy(selectedPlace = null, selectedAddress = null, selectedPlaceDetails = null, isPlaceCardShown = false, placeImageIndex = null) }
    }

    /** The camera already rests on [location] at [zoom], so it won't move. */
    private fun isCameraAt(location: GeoPoint, zoom: Float): Boolean =
        abs(ui.value.zoomLevel - zoom) < 0.01f && isCentered(location, ui.value.cameraTarget, zoom)

    /** [location] is within a couple of screen points of the camera's [center]. */
    private fun isCentered(location: GeoPoint, center: Coordinates, zoom: Float): Boolean {
        val pointsPerDegree = 256 * 2.0.pow(zoom.toDouble()) / 360
        val dx = (location.lng - center.longitude) * pointsPerDegree
        val dy = (location.lat - center.latitude) * pointsPerDegree / cos(center.latitude * PI / 180)
        return sqrt(dx * dx + dy * dy) <= CENTERED_POINTS
    }

    /** A stop at the place or address, named after it; shows at once like a tap on the map. */
    private fun addPlace() {
        val place = ui.value.selectedPlace ?: return
        val address = ui.value.selectedAddress
        val day = activeDay() ?: return
        closePlace()
        if (!canEdit()) return
        val marker = if (address != null) {
            NewMarker(address.name, address.location, osm = address.osm)
        } else {
            NewMarker(place.name, place.location, placeId = place.id)
        }
        viewModelScope.launch { markerRepository.addMarker(tripId, day.id, marker) }
    }

    /**
     * Like the destination search: asks the server once typing pauses, ranked near the map's center.
     * A cleared field clears the results at once.
     */
    private fun search(query: String) {
        ui.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            ui.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            ui.update { it.copy(isSearching = true) }
            val center = ui.value.cameraTarget
            val results = (placeRepository.search(query, GeoPoint(center.latitude, center.longitude)) as? AppResult.Success)?.data
            ui.update { it.copy(searchResults = results ?: it.searchResults, isSearching = false) }
        }
    }

    /** One of our places or an address opens its card; a city or larger area only moves the map there. */
    private fun showSearchResult(result: PlaceSearchResult) {
        closeSearch()
        when {
            result.place != null -> openPlace(result.place!!, result.type.zoom())
            result.type in ADDRESS_TYPES -> openAddress(result)
            else -> navigateTo(Coordinates(result.location.lat, result.location.lng), result.type.zoom())
        }
    }

    private fun closeSearch() {
        searchJob?.cancel()
        ui.update { it.copy(isSearchOpen = false, searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    /** Close enough to see what was picked: a country whole, a café with its street. */
    private fun SearchResultType.zoom(): Float = when (this) {
        SearchResultType.Country -> 5f
        SearchResultType.State -> 7f
        SearchResultType.County -> 9f
        SearchResultType.City -> 12f
        SearchResultType.District, SearchResultType.Locality -> 14f
        SearchResultType.Place, SearchResultType.House, SearchResultType.Street, SearchResultType.Other -> 17f
    }

    /** A long press drops a pin the user names; it shows at once like a tap. */
    private fun addNamedPin(name: String) {
        val location = ui.value.pinToName ?: return
        val day = activeDay() ?: return
        ui.update { it.copy(pinToName = null) }
        if (name.isBlank() || !canEdit()) return
        viewModelScope.launch {
            markerRepository.addMarker(tripId, day.id, NewMarker(name, GeoPoint(location.latitude, location.longitude)))
        }
    }

    private fun removeMarker(markerId: String) {
        if (!canEdit()) return
        ui.update { if (it.selectedMarkerId == markerId) it.copy(selectedMarkerId = null, isStopCardShown = false) else it }
        viewModelScope.launch { markerRepository.deleteMarker(markerId) }
    }

    private fun addDay() {
        if (!canEdit()) return
        save(Res.string.map_error_add_day) {
            tripRepository.addDay(tripId).also { result ->
                if (result is AppResult.Success) {
                    ui.update { it.copy(activeDayIndex = result.data.days.lastIndex, selectedMarkerId = null, isStopCardShown = false) }
                }
            }
        }
    }

    private fun confirm() {
        val confirm = ui.value.confirm ?: return
        val day = activeDay()
        ui.update { it.copy(confirm = null) }
        if (day == null || !canEdit()) return
        when (confirm) {
            Confirm.ClearDay -> {
                ui.update { it.copy(selectedMarkerId = null, isStopCardShown = false) }
                viewModelScope.launch { markerRepository.clearDay(tripId, day.id) }
            }
            Confirm.DeleteDay -> deleteDay(day.id, confirm = false)
            Confirm.RemoveMyContent -> ui.value.selectedMarkerId?.let(::removeMyContent)
        }
    }

    private fun deleteDay(dayId: String, confirm: Boolean) = save(Res.string.map_error_delete_day) {
        tripRepository.deleteDay(tripId, dayId, confirm).also { result ->
            when {
                result is AppResult.Success -> ui.update {
                    it.copy(activeDayIndex = (it.activeDayIndex - 1).coerceAtLeast(0), selectedMarkerId = null, isStopCardShown = false)
                }
                result is AppResult.Error && result.error.hasCode(TripErrorCodes.STOP_HAS_OTHERS_CONTENT) -> ui.update {
                    it.copy(othersContent = OthersContent.Day(result.error.serverMessage(), dayId))
                }
            }
        }
    }

    /** The user agreed to delete other people's photos, comments and likes with the stop or day. */
    private fun deleteAnyway() {
        val othersContent = ui.value.othersContent ?: return
        ui.update { it.copy(othersContent = null) }
        when (othersContent) {
            is OthersContent.Change -> viewModelScope.launch { markerRepository.deleteAnyway(othersContent.failure) }
            is OthersContent.Day -> deleteDay(othersContent.dayId, confirm = true)
        }
    }

    private fun removeMyContent(markerId: String) {
        viewModelScope.launch {
            val result = markerRepository.removeMyContent(tripId, markerId)
            ui.update {
                it.copy(
                    message = when (result) {
                        is AppResult.Success -> UiText.Note(UiText.Resource(Res.string.map_my_content_removed))
                        is AppResult.Error -> result.error.toUiText(Res.string.map_error_remove_my_content)
                    },
                )
            }
        }
    }

    /** The server's localized explanation, for the warning before deleting other people's content. */
    private fun DataError.Network.serverMessage(): UiText = UiText.Server((this as? DataError.Network.Api)?.message.orEmpty())

    /** Runs one day change at a time; a refusal shows [failed] and why. */
    private fun save(failed: StringResource, change: suspend () -> AppResult<*, DataError.Network>) {
        if (ui.value.isSaving) return
        ui.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = change()
            // Other people's content on the day asks first instead (see deleteDay).
            val error = (result as? AppResult.Error)?.error?.takeUnless { it.hasCode(TripErrorCodes.STOP_HAS_OTHERS_CONTENT) }
            ui.update {
                it.copy(isSaving = false, message = error?.toUiText(failed) ?: it.message)
            }
        }
    }

    /**
     * The permission as read when the map opens or comes back to the front (e.g. from Settings). Only the
     * location button asks for it; once granted, the user's dot shows without moving the camera.
     */
    private fun onPermissionChecked(permission: LocationPermission) {
        val before = ui.value
        ui.update { it.copy(permission = permission) }
        if (permission == LocationPermission.Granted && before.permission != LocationPermission.Granted) startLocationUpdates()
    }

    /** The user answered the system prompt. */
    private fun onPermissionResult(permission: LocationPermission) {
        val before = ui.value
        ui.update { it.copy(permission = permission) }
        if (permission == LocationPermission.Granted) {
            if (before.permission != LocationPermission.Granted) startLocationUpdates()
        } else if (permission == LocationPermission.Blocked && before.isWaitingForFirstLocation) {
            // The location button asked, but the system can't show its prompt any more.
            ui.update { it.copy(locationPrompt = LocationPrompt.OpenSettings) }
        } else {
            ui.update {
                it.copy(
                    isWaitingForFirstLocation = false,
                    message = UiText.Resource(Res.string.error_location_denied_manual),
                )
            }
        }
    }

    /** The location button: moves to the user, asking for the permission first when it can. */
    private fun centerOnUserLocation() {
        when (ui.value.permission) {
            LocationPermission.Granted -> {
                val current = ui.value.currentLocation
                if (current != null) {
                    navigateTo(current, 15f)
                } else {
                    ui.update { it.copy(isWaitingForFirstLocation = true) }
                }
            }
            LocationPermission.NotAsked, null -> {
                ui.update { it.copy(isWaitingForFirstLocation = true) }
                sendEvent(Event.RequestPermission)
            }
            LocationPermission.ShouldExplain ->
                ui.update { it.copy(isWaitingForFirstLocation = true, locationPrompt = LocationPrompt.Explain) }
            LocationPermission.Blocked ->
                ui.update { it.copy(isWaitingForFirstLocation = true, locationPrompt = LocationPrompt.OpenSettings) }
        }
    }

    private fun confirmLocationPrompt() {
        val prompt = ui.value.locationPrompt ?: return
        ui.update { it.copy(locationPrompt = null) }
        when (prompt) {
            LocationPrompt.Explain -> sendEvent(Event.RequestPermission)
            LocationPrompt.OpenSettings -> sendEvent(Event.OpenAppSettings)
        }
    }

    private fun startLocationUpdates() {
        viewModelScope.launch(dispatchers.main) {
            ui.update { it.copy(isLoadingLocation = true, message = null) }
            try {
                getCurrentLocationUseCase().collect { result ->
                    when (result) {
                        is AppResult.Success -> {
                            val coords = result.data
                            ui.update { state ->
                                // Only the location button moves the camera to the user.
                                val shouldZoom = state.isWaitingForFirstLocation
                                if (shouldZoom) {
                                    sendEvent(Event.AnimateCamera(coords, 15f))
                                }
                                state.copy(
                                    currentLocation = coords,
                                    cameraTarget = if (shouldZoom) coords else state.cameraTarget,
                                    zoomLevel = if (shouldZoom) 15f else state.zoomLevel,
                                    isLoadingLocation = false,
                                    isWaitingForFirstLocation = false
                                )
                            }
                        }
                        is AppResult.Error -> {
                            val resource = when (result.error) {
                                is DataError.Location.PermissionDenied -> Res.string.error_location_permission_required
                                is DataError.Location.ServiceDisabled -> Res.string.error_location_services_disabled
                                is DataError.Location.Unavailable -> Res.string.error_location_unavailable
                                is DataError.Location.Unknown -> Res.string.error_location_unknown
                            }
                            ui.update { it.copy(isLoadingLocation = false, message = UiText.Resource(resource)) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.update {
                    it.copy(isLoadingLocation = false, message = UiText.Resource(Res.string.error_location_unexpected))
                }
            }
        }
    }

    private fun navigateTo(coordinates: Coordinates, zoom: Float) {
        ui.update { it.copy(cameraTarget = coordinates, zoomLevel = zoom, isCameraReady = true) }
        sendEvent(Event.AnimateCamera(coordinates, zoom))
    }

    private fun sendEvent(event: Event) {
        viewModelScope.launch(dispatchers.main) {
            _events.send(event)
        }
    }

    /**
     * [orderNumber] is the place in the day's list, which stays 1, 2, 3 while a delete waits for the reload.
     * Only the stop's creator and the trip's owner may delete it.
     */
    private fun TripMarker.toMapMarker(orderNumber: Int, role: TripRole?) = MapMarker(
        id = id,
        position = Coordinates(location.lat, location.lng),
        title = name,
        orderNumber = orderNumber,
        snippet = time,
        coverPhotoId = coverPhotoId,
        coverThumbUrl = coverThumbUrl,
        photoCount = photoCount,
        likeCount = likeCount,
        likedByMe = likedByMe,
        canLike = canLike,
        commentCount = commentCount,
        placeId = placeId,
        canDelete = role == TripRole.OWNER || (role?.canEdit == true && myUserId != null && createdById == myUserId),
    )

    /** Which stops to frame: a day's, or the whole trip's when [dayId] is null. A new [id] frames them again. */
    private data class FrameRequest(val dayId: String?, val id: Int)

    private data class UiState(
        val placePins: List<PlacePin> = emptyList(),
        val placeClusters: List<PlaceCluster> = emptyList(),
        val activeDayIndex: Int = 0,
        val isLoadingTrip: Boolean = true,
        val isSaving: Boolean = false,
        val currentLocation: Coordinates? = null,
        val cameraTarget: Coordinates = Coordinates.Paris,
        val zoomLevel: Float = 13f,
        val isCameraReady: Boolean = false,
        /** Null until the screen has read it. */
        val permission: LocationPermission? = null,
        val locationPrompt: LocationPrompt? = null,
        val isLoadingLocation: Boolean = false,
        val selectedMarkerId: String? = null,
        /** The camera has centered the selected stop, so its card shows. */
        val isStopCardShown: Boolean = false,
        /** The stop whose photos are open. */
        val photosMarkerId: String? = null,
        /** The place whose photos are open; it stays open when the place's card closes. */
        val photosPlace: MapPlace? = null,
        /** The in-app camera is open for the selected stop. */
        val isPhotoCameraOpen: Boolean = false,
        /** The stop whose comments are open. */
        val commentsMarkerId: String? = null,
        /** What the report dialog is about. */
        val reporting: ReportSubject? = null,
        val isReporting: Boolean = false,
        /** Saving or joining the trip is waiting for the server. */
        val isUpdatingSharing: Boolean = false,
        /** The owner opened who asked to join. */
        val isMembersOpen: Boolean = false,
        val message: UiText? = null,
        /** Stop changes undone because the connection failed; Retry sends them again. */
        val failedChanges: List<MarkerChangeFailure> = emptyList(),
        val isWaitingForFirstLocation: Boolean = false,
        val frame: FrameRequest? = null,
        val confirm: Confirm? = null,
        /** A delete waits for the user to agree that other people's content goes too. */
        val othersContent: OthersContent? = null,
        val selectedPlace: MapPlace? = null,
        val selectedPlaceDetails: PlaceDetails? = null,
        /** The image of [selectedPlaceDetails] open full screen. */
        val placeImageIndex: Int? = null,
        /** The first image of each stop's place, by place ID, for stops without a cover; null when it has none. */
        val stopPlaceImages: Map<String, PlaceImage?> = emptyMap(),
        /** The address picked from search whose card is open; [selectedPlace] is drawn from it. */
        val selectedAddress: PlaceSearchResult? = null,
        /** The camera has centered [selectedPlace], so its card shows. */
        val isPlaceCardShown: Boolean = false,
        val isSearchOpen: Boolean = false,
        val searchQuery: String = "",
        val searchResults: List<PlaceSearchResult> = emptyList(),
        val isSearching: Boolean = false,
        /** Where a long press dropped a pin that waits for its name. */
        val pinToName: Coordinates? = null,
    )

    /** A day tab: [number] counts from 1; [date] is null when the trip has no dates. */
    @Immutable
    data class DayTab(val id: String, val number: Int, val date: LocalDate?)

    /** [RemoveMyContent] is about the selected stop. */
    enum class Confirm { ClearDay, DeleteDay, RemoveMyContent }

    /** What waits for the warning about other people's content, with the server's [message]. */
    private sealed interface OthersContent {
        val message: UiText

        data class Change(override val message: UiText, val failure: MarkerChangeFailure) : OthersContent
        data class Day(override val message: UiText, val dayId: String) : OthersContent
    }

    private data class ReportSubject(val target: ReportTarget, val id: String)

    /** Shown when the location button can't simply ask: explain first (Android), or send the user to Settings. */
    enum class LocationPrompt { Explain, OpenSettings }

    @Immutable
    data class State(
        val tripName: String = "",
        /** Shown as a date under the trip name. */
        val tripCreatedAt: Instant? = null,
        val days: ImmutableList<DayTab> = persistentListOf(),
        val activeDayIndex: Int = 0,
        /** The active day's stops in order. */
        val markers: ImmutableList<MapMarker> = persistentListOf(),
        /** Owners and editors add stops and days. Stops are deleted per [MapMarker.canDelete]. */
        val canEdit: Boolean = false,
        /** The owner, or an editor whose stops are all of the active day's: clear it or delete it. */
        val canDeleteDay: Boolean = false,
        /** Someone else's public trip: it can be added to my trips and reported. */
        val isViewer: Boolean = false,
        val ownerUsername: String? = null,
        /** The trip hasn't loaded yet (nothing cached). */
        val isLoading: Boolean = false,
        /** A day change is waiting for the server. Stop changes don't wait. */
        val isSaving: Boolean = false,
        val currentLocation: Coordinates? = null,
        val cameraTarget: Coordinates = Coordinates.Paris,
        val zoomLevel: Float = 13f,
        /**
         * The map shows once the camera knows where the trip is (its stops or destination), so it opens right
         * there; only a trip without a location opens on the default ([cameraTarget] starts at Paris).
         */
        val isCameraReady: Boolean = false,
        /** The stops to fit on screen: the open day's, framed on open, on a day change and on every return. */
        val frame: CameraFrame? = null,
        val isPermissionGranted: Boolean = false,
        val isLoadingLocation: Boolean = false,
        val selectedMarker: MapMarker? = null,
        /** The first image of [selectedMarker]'s place, for its card while the stop has no cover. */
        val selectedStopPlaceImage: PlaceImage? = null,
        /** The card of [selectedMarker] opens once the camera has centered its pin. */
        val isStopCardShown: Boolean = false,
        /** The stop whose photos are open in the sheet. */
        val photosMarker: MapMarker? = null,
        /** The place whose photos are open in the sheet. */
        val photosPlace: MapPlace? = null,
        /** The in-app camera is open; its photo goes to [selectedMarker]. */
        val isPhotoCameraOpen: Boolean = false,
        /** The stop whose comments are open in the sheet. */
        val commentsMarker: MapMarker? = null,
        /** The report dialog is open, for the trip or the selected stop. */
        val isReportOpen: Boolean = false,
        /** A report is waiting for the server. */
        val isReporting: Boolean = false,
        /** Someone else's trip that I saved to My Trips. */
        val isSavedByMe: Boolean = false,
        /** My request to join someone else's trip. */
        val myJoinRequest: JoinRequestStatus? = null,
        /** The owner's: people waiting for an answer to join. */
        val pendingJoinRequestCount: Int = 0,
        /** The owner's members sheet, with the requests to join, is open. */
        val isMembersOpen: Boolean = false,
        /** Saving or joining is waiting for the server. */
        val isUpdatingSharing: Boolean = false,
        val message: UiText? = null,
        /** [message] is about stop changes that didn't save; Retry sends them again. */
        val canRetry: Boolean = false,
        val isWaitingForFirstLocation: Boolean = false,
        val confirm: Confirm? = null,
        /** Deleting would delete other people's photos, comments or likes too; the warning says so. */
        val othersContentMessage: UiText? = null,
        val locationPrompt: LocationPrompt? = null,
        /** Place pins in view, the most liked and best known first. */
        val placePins: ImmutableList<PlacePin> = persistentListOf(),
        /** Bubbles of liked places when zoomed out. */
        val placeClusters: ImmutableList<PlaceCluster> = persistentListOf(),
        /** The place whose card is open. */
        val selectedPlace: MapPlace? = null,
        /** Images, opening hours and website, once loaded. */
        val selectedPlaceDetails: PlaceDetails? = null,
        /** The image of [selectedPlaceDetails] open full screen. */
        val placeImageIndex: Int? = null,
        /** Street, city and country when the card is for an address from search. */
        val selectedPlaceAddress: String? = null,
        /** The card of [selectedPlace] opens once the camera has centered its pin. */
        val isPlaceCardShown: Boolean = false,
        /** The header shows the search field instead of the trip name. */
        val isSearchOpen: Boolean = false,
        val searchQuery: String = "",
        val searchResults: ImmutableList<PlaceSearchResult> = persistentListOf(),
        /** A search is waiting for the server. */
        val isSearching: Boolean = false,
        /** A long press dropped a pin; the name dialog is open. */
        val isNamingPin: Boolean = false,
    )

    sealed interface Event {
        data class AnimateCamera(val coordinates: Coordinates, val zoom: Float) : Event
        data object RequestPermission : Event
        data object OpenAppSettings : Event
        data object NavigateBack : Event
        data class OpenProfile(val username: String) : Event
    }

    sealed interface Action {
        data class OnPermissionChecked(val permission: LocationPermission) : Action
        data class OnPermissionResult(val permission: LocationPermission) : Action
        data object OnConfirmLocationPrompt : Action
        data object OnDismissLocationPrompt : Action
        data class NavigateToLocation(val coordinates: Coordinates, val zoom: Float = 13f) : Action
        data object CenterOnUserLocation : Action
        /** A tap on the map, not on a pin. */
        data class OnMapClick(val coordinates: Coordinates) : Action
        data class OnPlacePinClick(val place: MapPlace) : Action
        /** A tapped cluster opens up into its places. */
        data class OnClusterClick(val cluster: PlaceCluster) : Action
        data class OnMapLongClick(val coordinates: Coordinates) : Action
        data class OnConfirmPinName(val name: String) : Action
        data object OnDismissPinName : Action
        data class OnMarkerClick(val marker: MapMarker) : Action
        data class OnRemoveMarker(val markerId: String) : Action
        data object ClearAllMarkers : Action
        data object AddDay : Action
        data object DeleteDay : Action
        data object OnConfirm : Action
        data object OnDismissConfirm : Action
        /** An editor removes their photos, comments and likes from the selected stop they can't delete. */
        data object OnRemoveMyContent : Action
        /** Deletes the stop or day although other people's content goes with it. */
        data object OnDeleteAnyway : Action
        data object OnDismissDeleteAnyway : Action
        /** The banner has the message; failed changes stay for the next Retry. */
        data object OnMessageShown : Action
        data object RetryFailedChanges : Action
        data class OnCameraMove(val coordinates: Coordinates, val zoom: Float, val bounds: GeoBounds? = null) : Action
        /** A touch began anywhere outside the place card, on the map or on the screen's other controls. */
        data object OnScreenTouch : Action
        data object OnAddPlaceToDay : Action
        /** The selected stop's photos open in a sheet. */
        data object OnOpenStopPhotos : Action
        data object OnCloseStopPhotos : Action
        /** The selected place's photos, which anyone can add to (not for an address from search). */
        data object OnOpenPlacePhotos : Action
        data object OnClosePlacePhotos : Action
        /** Photos picked or taken from the stop card for the selected stop. */
        data class OnAddStopPhotos(val images: List<PickedImage>) : Action
        /** The in-app camera opens for the selected stop; its photo comes back as [OnAddStopPhotos]. */
        data object OnOpenPhotoCamera : Action
        data object OnClosePhotoCamera : Action
        data object OnOpenSearch : Action
        data class OnSearchQueryChanged(val query: String) : Action
        data class OnSearchResultClick(val result: PlaceSearchResult) : Action
        data object OnCloseSearch : Action
        data class OnDaySelected(val dayIndex: Int) : Action
        data object OnBackClick : Action
        data class OnToggleStopLike(val markerId: String) : Action
        /** The selected stop's comments open in a sheet. */
        data object OnOpenStopComments : Action
        data object OnCloseStopComments : Action
        /** Saves someone else's trip to My Trips, or removes it. */
        data object OnToggleSaveTrip : Action

        /** Asks the owner to let me edit their trip with them. */
        data object OnRequestToJoin : Action
        data object OnCancelJoinRequest : Action

        /** The owner opens the members sheet to answer requests to join. */
        data object OnOpenJoinRequests : Action
        data object OnCloseJoinRequests : Action
        data object OnOpenOwnerProfile : Action
        data object OnReportTrip : Action
        /** Reports the selected stop. */
        data object OnReportStop : Action
        data class OnSendReport(val reason: ReportReason, val details: String) : Action
        data object OnDismissReport : Action
        /** A tapped image on the place card opens full screen. */
        data class OnOpenPlaceImage(val index: Int) : Action
        data object OnClosePlaceImage : Action
        /** Reports the photo open in a viewer: a Commons image as the place's image, anything else as a photo. */
        data class OnReportPhoto(val photo: Photo) : Action
    }

    private companion object {
        /** A city fills the screen. */
        const val DESTINATION_ZOOM = 12f
        /** Same wait as for search: the camera has rested. */
        const val PREFETCH_DELAY_MILLIS = 300L
        /** How far the camera may rest from a place it flew to and still count as on it (rounding). */
        const val CENTERED_POINTS = 2.0
        /** Typing has paused; see the search rule in 07-architecture. */
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        /** Search results small enough to add as a stop. */
        val ADDRESS_TYPES = setOf(SearchResultType.Place, SearchResultType.House, SearchResultType.Street, SearchResultType.Other)
        /** A tapped cluster opens up into its places. */
        const val CLUSTER_ZOOM_STEP = 2f
    }
}
