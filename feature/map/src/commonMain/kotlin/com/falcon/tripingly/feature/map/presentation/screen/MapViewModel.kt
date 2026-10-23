package com.falcon.tripingly.feature.map.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.data.photos.PhotoRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.routing.RouteRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.TripMapViewRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.model.route.ROUTES_ENTITLEMENT
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.domain.model.CameraCommand
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_like
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One trip on the map. Days and stops come from the cached trip, reloaded from the server on open. The work is
 * split by what it's about: the tapped stop or place ([MapSelection]), stop and day changes ([MapEdits]),
 * place pins ([MapPins]), search ([MapSearch]), routes and the best order ([MapRoutes]), the user's location
 * ([MapLocation]), the camera ([MapCamera]), opening the trip as it was left ([MapOpening]), and saving, joining
 * or reporting someone else's trip ([MapSharing]). Here: the day tabs, the sheets and likes.
 */
class MapViewModel(
    tripId: String,
    tripRepository: TripRepository,
    markerRepository: MarkerRepository,
    placeRepository: PlaceRepository,
    routeRepository: RouteRepository,
    private val photoRepository: PhotoRepository,
    private val socialRepository: SocialRepository,
    private val appConfigRepository: AppConfigRepository,
    /** How this trip's map was left: restored when it opens, saved as it changes. */
    mapViews: TripMapViewRepository,
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
    private val edits = MapEdits(context, selection, tripRepository, markerRepository) { sendEvent(MapEvent.ShowWholePlan) }
    private val pins = MapPins(context, placeRepository, appConfigRepository)
    private val search = MapSearch(context, camera, selection, placeRepository)
    private val routes = MapRoutes(
        context, camera, selection, routeRepository, markerRepository, ::hasRoutes,
        showRoutesArePro = { sendEvent(MapEvent.OpenPaywall) },
        showWholePlan = { sendEvent(MapEvent.ShowWholePlan) },
    )
    private val location = MapLocation(context, camera, getCurrentLocationUseCase, ::sendEvent)
    private val sharing = MapSharing(context, tripRepository, socialRepository)
    private val opening = MapOpening(context, camera, tripRepository, mapViews, ::hasRoutes)

    val uiState: StateFlow<MapUiState> = combine(trip, context.model, ::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUiState(null, model))

    init {
        opening.start()
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
            is MapAction.OnFitDay -> camera.showOpenDay()
            is MapAction.OnBackClick -> sendEvent(MapEvent.NavigateBack)
            is MapAction.OnToggleStopLike -> toggleStopLike(action.markerId)
            is MapAction.OnOpenStopComments -> selection.selectedMarkerId?.let { openSheet(Sheet.StopComments(it)) }
            is MapAction.OnCloseStopComments -> closeSheet()
            is MapAction.OnToggleSaveTrip -> sharing.toggleSave()
            is MapAction.OnRequestToJoin -> sharing.requestToJoin()
            is MapAction.OnCancelJoinRequest -> sharing.cancelJoinRequest()
            is MapAction.OnOpenJoinRequests -> openSheet(Sheet.Members)
            is MapAction.OnCloseJoinRequests -> closeSheet()
            is MapAction.OnOpenOwnerProfile -> trip.value?.trip?.owner?.let { sendEvent(MapEvent.OpenProfile(it.username)) }
            is MapAction.OnReportTrip -> sharing.report(ReportTarget.Trip, context.tripId)
            is MapAction.OnReportStop -> selection.selectedMarkerId?.let { sharing.report(ReportTarget.Marker, it) }
            is MapAction.OnSendReport -> sharing.sendReport(action.reason, action.details)
            is MapAction.OnDismissReport -> closeDialog()
            is MapAction.OnOpenPlaceImage -> selection.openPlaceImage(action.index)
            is MapAction.OnClosePlaceImage -> selection.openPlaceImage(null)
            is MapAction.OnReportPhoto -> sharing.reportPhoto(action.photo)
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

    private fun onCameraMove(action: MapAction.OnCameraMove) {
        camera.onRest(action.coordinates, action.zoom)
        selection.onCameraRest(action.coordinates, action.zoom)
        action.bounds?.let { pins.onCameraRest(it, action.zoom) }
    }

    private fun selectDay(index: Int) {
        selection.closeStop()
        // The camera stays where it is (Shahin, 2026-10-22); the fit-day button frames the day.
        context.update { it.copy(activeDayIndex = index, dialog = it.dialog.takeUnless { dialog -> dialog is Dialog.BestOrder }) }
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

    /** Routes along streets are Tripinly Pro; the server decides, `GET /me` tells the app. */
    private fun hasRoutes(): Boolean =
        (session.value as? SessionState.SignedIn)?.account?.entitlements?.contains(ROUTES_ENTITLEMENT) == true

    private fun sendEvent(event: MapEvent) {
        _events.trySend(event)
    }

    private companion object {
        /** A tapped cluster opens up into its places. */
        const val CLUSTER_ZOOM_STEP = 2f
    }
}
