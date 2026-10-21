package com.falcon.tripingly.feature.map.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.data.photos.PhotoRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.routing.RouteRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.TripErrorCodes
import com.falcon.tripingly.core.data.trips.TripMapViewRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.model.route.ROUTES_ENTITLEMENT
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMapView
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.domain.model.CameraCommand
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_cancel_join
import com.falcon.tripingly.feature.map.generated.resources.map_error_join
import com.falcon.tripingly.feature.map.generated.resources.map_error_like
import com.falcon.tripingly.feature.map.generated.resources.map_error_load_trip
import com.falcon.tripingly.feature.map.generated.resources.map_error_report
import com.falcon.tripingly.feature.map.generated.resources.map_error_save_trip
import com.falcon.tripingly.feature.map.generated.resources.map_error_unsave_trip
import com.falcon.tripingly.feature.map.generated.resources.map_join_declined
import com.falcon.tripingly.feature.map.generated.resources.map_join_requested
import com.falcon.tripingly.feature.map.generated.resources.map_join_withdrawn
import com.falcon.tripingly.feature.map.generated.resources.map_reported
import com.falcon.tripingly.feature.map.generated.resources.map_trip_saved
import com.falcon.tripingly.feature.map.generated.resources.map_trip_unsaved
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * One trip on the map. Days and stops come from the cached trip, reloaded from the server on open. The work is
 * split by what it's about: the tapped stop or place ([MapSelection]), stop and day changes ([MapEdits]),
 * place pins ([MapPins]), search ([MapSearch]), routes and the best order ([MapRoutes]), the user's location
 * ([MapLocation]) and the camera ([MapCamera]). Here: the trip and its saved view, the day tabs, the sheets,
 * likes, saving or joining someone else's trip and reports.
 */
class MapViewModel(
    tripId: String,
    private val tripRepository: TripRepository,
    markerRepository: MarkerRepository,
    placeRepository: PlaceRepository,
    routeRepository: RouteRepository,
    private val photoRepository: PhotoRepository,
    private val socialRepository: SocialRepository,
    private val appConfigRepository: AppConfigRepository,
    /** How this trip's map was left: restored when it opens, saved as it changes. */
    private val mapViews: TripMapViewRepository,
    sessionRepository: SessionRepository,
    getCurrentLocationUseCase: GetCurrentLocationUseCase,
) : ViewModel() {

    private val session = sessionRepository.session
    private val myUserId = (session.value as? SessionState.SignedIn)?.account?.id

    private val trip: StateFlow<TripDetails?> =
        tripRepository.observeTrip(tripId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val context = MapContext(tripId, viewModelScope, trip)
    private val model get() = context.model.value

    private val _events = Channel<MapEvent>(capacity = Channel.BUFFERED)
    val events: Flow<MapEvent> = _events.receiveAsFlow()

    private val camera = MapCamera(context)
    /** Where the map's camera goes; the map view runs each command once. */
    val cameraCommands: Flow<CameraCommand> = camera.commands

    private val selection = MapSelection(context, camera, placeRepository)
    private val edits = MapEdits(context, selection, tripRepository, markerRepository)
    private val pins = MapPins(context, placeRepository, appConfigRepository)
    private val search = MapSearch(context, camera, selection, placeRepository)
    private val routes = MapRoutes(context, camera, selection, routeRepository, markerRepository, ::hasRoutes) { sendEvent(MapEvent.OpenPaywall) }
    private val location = MapLocation(context, camera, getCurrentLocationUseCase, ::sendEvent)

    val uiState: StateFlow<MapUiState> = combine(trip, context.model, ::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUiState(null, model))

    init {
        loadTrip()
        rememberView()
    }

    fun onAction(action: MapAction) {
        when (action) {
            is MapAction.OnPermissionChecked -> location.onPermissionChecked(action.permission)
            is MapAction.OnPermissionResult -> location.onPermissionResult(action.permission)
            is MapAction.OnConfirmLocationPrompt -> location.confirmPrompt()
            is MapAction.OnDismissLocationPrompt -> location.dismissPrompt()
            is MapAction.CenterOnUserLocation -> location.centerOnUser()
            is MapAction.OnMapClick -> edits.onMapClick(action.coordinates)
            is MapAction.OnPlacePinClick -> selection.openPlace(action.place)
            is MapAction.OnClusterClick -> camera.moveTo(
                Coordinates(action.cluster.location.lat, action.cluster.location.lng),
                camera.zoom + CLUSTER_ZOOM_STEP,
            )
            is MapAction.OnMapLongClick -> edits.onMapLongClick(action.coordinates)
            is MapAction.OnConfirmPinName -> edits.addNamedPin(action.name)
            is MapAction.OnDismissPinName -> closeDialog()
            is MapAction.OnMarkerClick -> selection.selectMarker(action.marker)
            is MapAction.OnRemoveMarker -> edits.removeMarker(action.markerId)
            is MapAction.ClearAllMarkers -> openDialog(Dialog.ClearDay)
            is MapAction.DeleteDay -> openDialog(Dialog.DeleteDay)
            is MapAction.OnConfirm -> edits.confirm()
            is MapAction.OnDismissConfirm -> closeDialog()
            is MapAction.OnRemoveMyContent -> if (selection.selectedMarkerId != null) openDialog(Dialog.RemoveMyContent)
            is MapAction.OnDeleteAnyway -> edits.deleteAnyway()
            is MapAction.OnDismissDeleteAnyway -> closeDialog()
            is MapAction.AddDay -> edits.addDay()
            is MapAction.OnMessageShown -> context.showMessage(null)
            is MapAction.RetryFailedChanges -> edits.retryFailedChanges()
            is MapAction.OnCameraMove -> onCameraMove(action)
            is MapAction.OnScreenTouch -> selection.onScreenTouch()
            is MapAction.OnOpenStopPhotos -> selection.selectedMarkerId?.let { openSheet(Sheet.StopPhotos(it)) }
            is MapAction.OnCloseStopPhotos -> closeSheet()
            // Not for an address from search: it isn't one of our places yet.
            is MapAction.OnOpenPlacePhotos -> selection.selectedPlace?.takeIf { it.address == null }?.let { openSheet(Sheet.PlacePhotos(it.place)) }
            is MapAction.OnClosePlacePhotos -> closeSheet()
            is MapAction.OnAddStopPhotos -> addStopPhotos(action.images)
            is MapAction.OnOpenPhotoCamera -> if (context.canEdit() && selection.selectedMarkerId != null) openSheet(Sheet.PhotoCamera)
            is MapAction.OnClosePhotoCamera -> closeSheet()
            is MapAction.OnAddPlaceToDay -> edits.addPlace()
            is MapAction.OnOpenSearch -> search.open()
            is MapAction.OnSearchQueryChanged -> search.onQueryChanged(action.query)
            is MapAction.OnSearchResultClick -> search.onResultClick(action.result)
            is MapAction.OnCloseSearch -> search.close()
            is MapAction.OnDaySelected -> selectDay(action.dayIndex)
            is MapAction.OnBackClick -> sendEvent(MapEvent.NavigateBack)
            is MapAction.OnToggleStopLike -> toggleStopLike(action.markerId)
            is MapAction.OnOpenStopComments -> selection.selectedMarkerId?.let { openSheet(Sheet.StopComments(it)) }
            is MapAction.OnCloseStopComments -> closeSheet()
            is MapAction.OnToggleSaveTrip -> toggleSave()
            is MapAction.OnRequestToJoin -> updateSharing(Res.string.map_join_requested, Res.string.map_error_join) { tripRepository.requestToJoin(context.tripId) }
            is MapAction.OnCancelJoinRequest -> updateSharing(Res.string.map_join_withdrawn, Res.string.map_error_cancel_join) { tripRepository.cancelJoinRequest(context.tripId) }
            is MapAction.OnOpenJoinRequests -> openSheet(Sheet.Members)
            is MapAction.OnCloseJoinRequests -> closeSheet()
            is MapAction.OnOpenOwnerProfile -> trip.value?.trip?.owner?.let { sendEvent(MapEvent.OpenProfile(it.username)) }
            is MapAction.OnReportTrip -> openDialog(Dialog.Report(ReportTarget.Trip, context.tripId))
            is MapAction.OnReportStop -> selection.selectedMarkerId?.let { openDialog(Dialog.Report(ReportTarget.Marker, it)) }
            is MapAction.OnSendReport -> sendReport(action.reason, action.details)
            is MapAction.OnDismissReport -> closeDialog()
            is MapAction.OnOpenPlaceImage -> selection.openPlaceImage(action.index)
            is MapAction.OnClosePlaceImage -> selection.openPlaceImage(null)
            is MapAction.OnReportPhoto -> reportPhoto(action.photo)
            is MapAction.OnToggleDayRoute -> routes.toggleDayRoute()
            is MapAction.OnTravelModeSelected -> routes.selectTravelMode(action.mode)
            is MapAction.OnRouteFromPlace -> routes.startPointRoute()
            is MapAction.OnRouteToPlace -> routes.showPointRoute()
            is MapAction.OnCancelRouteStart -> routes.cancelRouteStart()
            is MapAction.OnClosePointRoute -> routes.closePointRoute()
            is MapAction.OnAlongTheWayClick -> selection.openPlace(action.place)
            is MapAction.OnAddAlongTheWay -> routes.addAlongTheWay(action.place)
            is MapAction.OnFindBestOrder -> routes.findBestOrder()
            is MapAction.OnApplyBestOrder -> routes.applyBestOrder()
            is MapAction.OnDismissBestOrder -> closeDialog()
        }
    }

    private fun toUiState(details: TripDetails?, model: MapModel): MapUiState =
        MapStateMapper(details, model, appConfigRepository.config.value, myUserId).map()

    private fun loadTrip() {
        viewModelScope.launch {
            val result = tripRepository.refreshTrip(context.tripId)
            context.update { it.copy(isLoadingTrip = false) }
            context.showMessage((result as? AppResult.Error)?.error?.toUiText(Res.string.map_error_load_trip))
            // Without the trip, or a trip without days, there's nothing to show first: the map opens on the default.
            if (result is AppResult.Error || (result as? AppResult.Success)?.data?.days?.isEmpty() == true) camera.showAsItIs()
        }
        // Show the trip as it was left; the first time, the open day's stops, or the whole trip's while that day
        // has none, or the trip's destination while it has no stops at all.
        viewModelScope.launch {
            val left = mapViews.get(context.tripId)
            val details = trip.first { it != null && it.days.isNotEmpty() } ?: return@launch
            if (left != null) return@launch restoreView(left, details)
            val stops = context.activeDay()?.markers.orEmpty().ifEmpty { details.days.flatMap { it.markers } }
            val destination = details.destination
            when {
                stops.isNotEmpty() -> camera.frame(stops.map { Coordinates(it.location.lat, it.location.lng) })
                destination != null -> camera.moveTo(Coordinates(destination.location.lat, destination.location.lng), DESTINATION_ZOOM)
                // A trip without a location: the map opens on the default.
                else -> camera.showAsItIs()
            }
        }
    }

    /** The open day (the first one if it's gone), the camera where it was and the Route button, as they were left. */
    private fun restoreView(left: TripMapView, details: TripDetails) {
        context.update {
            it.copy(
                activeDayIndex = details.days.indexOfFirst { day -> day.id == left.dayId }.coerceAtLeast(0),
                camera = CameraModel(isReady = true, target = Coordinates(left.latitude, left.longitude), zoom = left.zoom),
                // Routes are Pro: one left on stays off once Pro is gone.
                route = it.route.copy(isDayRouteShown = left.isRouteShown && hasRoutes(), travelMode = left.travelMode),
            )
        }
    }

    /** Saves the view once it rests, not on every camera frame, so it's there after the app is closed too. */
    private fun rememberView() {
        viewModelScope.launch {
            combine(trip, context.model, ::viewOf).distinctUntilChanged().collectLatest { view ->
                view ?: return@collectLatest
                delay(SAVE_VIEW_DELAY_MILLIS)
                mapViews.save(context.tripId, view)
            }
        }
    }

    /** Null until the map shows the trip. */
    private fun viewOf(details: TripDetails?, model: MapModel): TripMapView? {
        if (!model.camera.isReady) return null
        val day = details?.days?.getOrNull(model.activeDayIndex) ?: return null
        return TripMapView(
            dayId = day.id,
            latitude = model.camera.target.latitude,
            longitude = model.camera.target.longitude,
            zoom = model.camera.zoom,
            isRouteShown = model.route.isDayRouteShown,
            travelMode = model.route.travelMode,
        )
    }

    private fun onCameraMove(action: MapAction.OnCameraMove) {
        camera.onRest(action.coordinates, action.zoom)
        selection.onCameraRest(action.coordinates, action.zoom)
        action.bounds?.let { pins.onCameraRest(it, action.zoom) }
    }

    private fun selectDay(index: Int) {
        selection.closeStop()
        context.update { it.copy(activeDayIndex = index, dialog = it.dialog.takeUnless { dialog -> dialog is Dialog.BestOrder }) }
        context.days().getOrNull(index)?.let { day -> camera.frame(day.markers.map { Coordinates(it.location.lat, it.location.lng) }) }
    }

    private fun openSheet(sheet: Sheet) = context.update { it.copy(sheet = sheet) }

    private fun closeSheet() = context.update { it.copy(sheet = null) }

    private fun openDialog(dialog: Dialog) = context.update { it.copy(dialog = dialog) }

    private fun closeDialog() = context.update { it.copy(dialog = null) }

    /** Photos picked or taken on the stop card: they upload in the background, and the sheet opens to show them going up. */
    private fun addStopPhotos(images: List<PickedImage>) {
        if (model.sheet == Sheet.PhotoCamera) closeSheet()
        val markerId = selection.selectedMarkerId ?: return
        if (images.isEmpty() || !context.canEdit()) return
        photoRepository.upload(PhotoOwner.Stop(markerId), images)
        openSheet(Sheet.StopPhotos(markerId))
    }

    /** A stop's heart likes its place; shows at once, see [SocialRepository.setLiked]. */
    private fun toggleStopLike(markerId: String) {
        val stop = context.days().firstNotNullOfOrNull { day -> day.markers.firstOrNull { it.id == markerId } } ?: return
        if (!stop.canLike) return
        viewModelScope.launch {
            val result = socialRepository.setLiked(LikeTarget.Place, stop.placeId, liked = !stop.likedByMe)
            if (result is AppResult.Error) context.showMessage(result.error.toUiText(Res.string.map_error_like))
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
        if (model.isUpdatingSharing) return
        context.update { it.copy(isUpdatingSharing = true) }
        viewModelScope.launch {
            val result = change()
            context.update {
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

    /** A place's Commons image is reported as the place's image, every other photo as a photo. */
    private fun reportPhoto(photo: Photo) {
        val target = if (photo.credit != null) ReportTarget.PlaceImage else ReportTarget.Photo
        openDialog(Dialog.Report(target, photo.id))
    }

    private fun sendReport(reason: ReportReason, details: String) {
        val report = model.dialog as? Dialog.Report ?: return
        if (report.isSending) return
        openDialog(report.copy(isSending = true))
        viewModelScope.launch {
            val result = socialRepository.report(report.target, report.id, reason, details)
            context.update {
                when (result) {
                    is AppResult.Success -> it.copy(dialog = null, message = UiText.Note(UiText.Resource(Res.string.map_reported)))
                    is AppResult.Error -> it.copy(dialog = report, message = result.error.toUiText(Res.string.map_error_report))
                }
            }
        }
    }

    /** Routes along streets are Tripinly Pro; the server decides, `GET /me` tells the app. */
    private fun hasRoutes(): Boolean =
        (session.value as? SessionState.SignedIn)?.account?.entitlements?.contains(ROUTES_ENTITLEMENT) == true

    private fun sendEvent(event: MapEvent) {
        _events.trySend(event)
    }

    private companion object {
        /** A city fills the screen. */
        const val DESTINATION_ZOOM = 12f
        /** How long the view rests before it's saved. */
        const val SAVE_VIEW_DELAY_MILLIS = 500L
        /** A tapped cluster opens up into its places. */
        const val CLUSTER_ZOOM_STEP = 2f
    }
}
