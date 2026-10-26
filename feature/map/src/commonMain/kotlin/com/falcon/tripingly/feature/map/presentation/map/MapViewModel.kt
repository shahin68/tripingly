package com.falcon.tripingly.feature.map.presentation.map

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
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_hot
import com.falcon.tripingly.feature.map.presentation.component.CameraCommand
import com.falcon.tripingly.feature.map.presentation.component.MapLayersState
import com.falcon.tripingly.feature.map.presentation.map.state.Dialog
import com.falcon.tripingly.feature.map.presentation.map.state.MapCamera
import com.falcon.tripingly.feature.map.presentation.map.state.MapContext
import com.falcon.tripingly.feature.map.presentation.map.state.MapEdits
import com.falcon.tripingly.feature.map.presentation.map.state.MapLocation
import com.falcon.tripingly.feature.map.presentation.map.state.MapModel
import com.falcon.tripingly.feature.map.presentation.map.state.MapOpening
import com.falcon.tripingly.feature.map.presentation.map.state.MapPins
import com.falcon.tripingly.feature.map.presentation.map.state.MapRouting
import com.falcon.tripingly.feature.map.presentation.map.state.MapSearch
import com.falcon.tripingly.feature.map.presentation.map.state.MapSelection
import com.falcon.tripingly.feature.map.presentation.map.state.MapSharing
import com.falcon.tripingly.feature.map.presentation.map.state.Sheet
import com.falcon.tripingly.feature.map.presentation.map.state.activeDayIndex
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
 * place pins ([MapPins]), search ([MapSearch]), routes and the best order ([MapRouting]), the user's location
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
    private val routes = MapRouting(
        context, camera, selection, routeRepository, markerRepository, ::hasRoutes,
        showRoutesArePro = { sendEvent(MapEvent.OpenPaywall) },
        showWholePlan = { sendEvent(MapEvent.ShowWholePlan) },
    )
    private val location = MapLocation(context, camera, getCurrentLocationUseCase, ::sendEvent)
    private val sharing = MapSharing(context, tripRepository, socialRepository)
    private val opening = MapOpening(context, camera, tripRepository, mapViews, ::hasRoutes)

    /** The map's layers and what they were built from, kept until one of those changes. */
    private var layers: Pair<List<Any?>, MapLayersState>? = null

    val uiState: StateFlow<MapUiState> = combine(trip, context.model, ::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUiState(null, model))

    init {
        opening.start()
    }

    fun onAction(action: MapAction) {
        when (action) {
            is MapAction.MapView -> onMapView(action)
            is MapAction.Header -> onHeader(action)
            is MapAction.Search -> onSearch(action)
            is MapAction.Location -> onLocation(action)
            is MapAction.Days -> onDays(action)
            is MapAction.Plan -> onPlan(action)
            is MapAction.StopCard -> onStopCard(action)
            is MapAction.PlaceCard -> onPlaceCard(action)
            is MapAction.Sheet -> onSheet(action)
            is MapAction.Dialog -> onDialog(action)
            is MapAction.Message -> onMessage(action)
        }
    }

    private fun onMapView(action: MapAction.MapView) {
        when (action) {
            is MapAction.MapView.Click -> edits.onMapClick(action.coordinates)
            is MapAction.MapView.LongClick -> edits.onMapLongClick(action.coordinates)
            is MapAction.MapView.PlacePinClick -> selection.openPlace(action.place)
            is MapAction.MapView.ClusterClick -> camera.moveTo(
                Coordinates(action.cluster.location.lat, action.cluster.location.lng),
                camera.zoom + CLUSTER_ZOOM_STEP,
            )
            is MapAction.MapView.MarkerClick -> selection.selectMarker(action.marker)
            is MapAction.MapView.CameraRest -> onCameraRest(action)
            is MapAction.MapView.ScreenTouch -> selection.onScreenTouch()
        }
    }

    private fun onHeader(action: MapAction.Header) {
        when (action) {
            is MapAction.Header.Back -> sendEvent(MapEvent.NavigateBack)
            is MapAction.Header.Retry -> opening.retry()
            is MapAction.Header.AddDay -> edits.addDay()
            is MapAction.Header.DeleteDay -> openDialog(Dialog.DeleteDay)
            is MapAction.Header.ToggleSave -> sharing.toggleSave()
            is MapAction.Header.RequestToJoin -> sharing.requestToJoin()
            is MapAction.Header.CancelJoinRequest -> sharing.cancelJoinRequest()
            is MapAction.Header.OpenMembers -> openSheet(Sheet.Members)
            is MapAction.Header.OpenOwnerProfile -> trip.value?.trip?.owner?.let { sendEvent(MapEvent.OpenProfile(it.username)) }
            is MapAction.Header.ReportTrip -> sharing.report(ReportTarget.Trip, context.tripId)
        }
    }

    private fun onSearch(action: MapAction.Search) {
        when (action) {
            is MapAction.Search.Open -> search.open()
            is MapAction.Search.QueryChanged -> search.onQueryChanged(action.query)
            is MapAction.Search.ResultClick -> search.onResultClick(action.result)
            is MapAction.Search.Close -> search.close()
        }
    }

    private fun onLocation(action: MapAction.Location) {
        when (action) {
            is MapAction.Location.PermissionChecked -> location.onPermissionChecked(action.permission)
            is MapAction.Location.PermissionResult -> location.onPermissionResult(action.permission)
            is MapAction.Location.ConfirmPrompt -> location.confirmPrompt()
            is MapAction.Location.DismissPrompt -> location.dismissPrompt()
            is MapAction.Location.CenterOnUser -> location.centerOnUser()
        }
    }

    private fun onDays(action: MapAction.Days) {
        when (action) {
            is MapAction.Days.Select -> selectDay(action.dayIndex)
            is MapAction.Days.FitDay -> camera.showOpenDay()
        }
    }

    private fun onPlan(action: MapAction.Plan) {
        when (action) {
            is MapAction.Plan.RemoveStop -> edits.removeMarker(action.markerId)
            is MapAction.Plan.ClearAll -> openDialog(Dialog.ClearDay)
            is MapAction.Plan.ToggleRoute -> routes.toggleDayRoute()
            is MapAction.Plan.SelectTravelMode -> routes.selectTravelMode(action.mode)
            is MapAction.Plan.FindBestOrder -> routes.findBestOrder()
            is MapAction.Plan.AlongTheWayClick -> selection.openPlace(action.place)
            is MapAction.Plan.AddAlongTheWay -> routes.addAlongTheWay(action.place)
            is MapAction.Plan.ClosePointRoute -> routes.closePointRoute()
            is MapAction.Plan.CancelRouteStart -> routes.cancelRouteStart()
        }
    }

    private fun onStopCard(action: MapAction.StopCard) {
        when (action) {
            is MapAction.StopCard.ToggleHot -> toggleStopHot(action.markerId)
            is MapAction.StopCard.OpenPhotos -> selection.selectedMarkerId?.let { openSheet(Sheet.StopPhotos(it)) }
            is MapAction.StopCard.OpenComments -> selection.selectedMarkerId?.let { openSheet(Sheet.StopComments(it)) }
            is MapAction.StopCard.AddPhotos -> addStopPhotos(action.images)
            is MapAction.StopCard.OpenCamera -> if (context.canEdit() && selection.selectedMarkerId != null) openSheet(Sheet.PhotoCamera)
            is MapAction.StopCard.RemoveMyContent -> if (selection.selectedMarkerId != null) openDialog(Dialog.RemoveMyContent)
            is MapAction.StopCard.Report -> selection.selectedMarkerId?.let { sharing.report(ReportTarget.Marker, it) }
        }
    }

    private fun onPlaceCard(action: MapAction.PlaceCard) {
        when (action) {
            is MapAction.PlaceCard.AddToDay -> edits.addPlace()
            // Not for an address from search: it isn't one of our places yet.
            is MapAction.PlaceCard.OpenPhotos -> selection.selectedPlace?.takeIf { it.address == null }?.let { openSheet(Sheet.PlacePhotos(it.place)) }
            is MapAction.PlaceCard.OpenImage -> selection.openPlaceImage(action.index)
            is MapAction.PlaceCard.RouteFrom -> routes.startPointRoute()
            is MapAction.PlaceCard.RouteTo -> routes.showPointRoute()
        }
    }

    private fun onSheet(action: MapAction.Sheet) {
        when (action) {
            is MapAction.Sheet.ClosePlaceImage -> selection.openPlaceImage(null)
            is MapAction.Sheet.ReportPhoto -> sharing.reportPhoto(action.photo)
            is MapAction.Sheet.CloseStopPhotos,
            is MapAction.Sheet.ClosePlacePhotos,
            is MapAction.Sheet.CloseStopComments,
            is MapAction.Sheet.CloseMembers,
            is MapAction.Sheet.CloseCamera -> closeSheet()
        }
    }

    private fun onDialog(action: MapAction.Dialog) {
        when (action) {
            is MapAction.Dialog.ConfirmPinName -> edits.addNamedPin(action.name)
            is MapAction.Dialog.Confirm -> edits.confirm()
            is MapAction.Dialog.DeleteAnyway -> edits.deleteAnyway()
            is MapAction.Dialog.SendReport -> sharing.sendReport(action.reason, action.details)
            is MapAction.Dialog.ApplyBestOrder -> routes.applyBestOrder()
            is MapAction.Dialog.DismissPinName,
            is MapAction.Dialog.DismissConfirm,
            is MapAction.Dialog.DismissDeleteAnyway,
            is MapAction.Dialog.DismissReport,
            is MapAction.Dialog.DismissBestOrder -> closeDialog()
        }
    }

    private fun onMessage(action: MapAction.Message) {
        when (action) {
            is MapAction.Message.Shown -> context.showMessage(null)
            is MapAction.Message.RetryFailedChanges -> edits.retryFailedChanges()
        }
    }

    private fun toUiState(details: TripDetails?, model: MapModel): MapUiState {
        val config = appConfigRepository.config.value
        val mapper = MapStateMapper(details, model, config, myUserId)
        // Most changes (a search keystroke, a message, a sheet) don't touch the map: its ~150 pins aren't built again.
        val inputs = listOf(details, model.activeDayIndex, model.route, model.pins, model.selection, model.location.permission, config)
        val layers = layers?.takeIf { it.first == inputs }?.second ?: mapper.layers().also { layers = inputs to it }
        return mapper.map(layers)
    }

    private fun onCameraRest(action: MapAction.MapView.CameraRest) {
        camera.onRest(action.coordinates, action.zoom)
        selection.onCameraRest()
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

    /** A stop's flame marks its place hot; shows at once, see [SocialRepository.setHot]. */
    private fun toggleStopHot(markerId: String) {
        val stop = context.days().firstNotNullOfOrNull { day -> day.markers.firstOrNull { it.id == markerId } } ?: return
        if (!stop.canMarkHot) return
        viewModelScope.launch {
            val result = socialRepository.setHot(stop.placeId, hot = !stop.hotByMe)
            if (result is AppResult.Error) context.showMessage(result.error.toUiText(Res.string.map_error_hot))
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
