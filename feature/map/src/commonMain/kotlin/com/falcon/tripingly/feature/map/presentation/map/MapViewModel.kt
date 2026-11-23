package com.falcon.tripingly.feature.map.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.billing.ProRepository
import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.data.photos.PhotoRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.push.NewActivityRepository
import com.falcon.tripingly.core.data.routing.RouteRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.TripMapViewRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_hot
import com.falcon.tripingly.feature.map.presentation.component.CameraCommand
import com.falcon.tripingly.feature.map.presentation.component.MapLayersState
import com.falcon.tripingly.feature.map.presentation.map.state.Dialog
import com.falcon.tripingly.feature.map.presentation.map.state.MapAddToTrip
import com.falcon.tripingly.feature.map.presentation.map.state.MapAround
import com.falcon.tripingly.feature.map.presentation.map.state.MapCamera
import com.falcon.tripingly.feature.map.presentation.map.state.MapCardPhotos
import com.falcon.tripingly.feature.map.presentation.map.state.MapContext
import com.falcon.tripingly.feature.map.presentation.map.state.MapEdits
import com.falcon.tripingly.feature.map.presentation.map.state.MapJoinRequests
import com.falcon.tripingly.feature.map.presentation.map.state.MapLocation
import com.falcon.tripingly.feature.map.presentation.map.state.MapModel
import com.falcon.tripingly.feature.map.presentation.map.state.MapNewActivity
import com.falcon.tripingly.feature.map.presentation.map.state.MapOpening
import com.falcon.tripingly.feature.map.presentation.map.state.MapPins
import com.falcon.tripingly.feature.map.presentation.map.state.MapRouting
import com.falcon.tripingly.feature.map.presentation.map.state.MapSearch
import com.falcon.tripingly.feature.map.presentation.map.state.MapSelection
import com.falcon.tripingly.feature.map.presentation.map.state.MapSharing
import com.falcon.tripingly.feature.map.presentation.map.state.Sheet
import com.falcon.tripingly.feature.map.presentation.map.state.activeDayIndex
import com.falcon.tripingly.feature.map.presentation.map.state.isPicking
import com.falcon.tripingly.feature.map.presentation.map.state.pickedAlone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

/**
 * One trip on the map. Days and stops come from the cached trip, reloaded from the server on open. The work is
 * split by what it's about: the tapped stop or place ([MapSelection]), stop and day changes ([MapEdits]),
 * place pins ([MapPins]), search ([MapSearch]), routes and the best order ([MapRouting]), the user's location
 * ([MapLocation]), the camera ([MapCamera]), opening the trip as it was left ([MapOpening]), "new" marks on stops
 * ([MapNewActivity]), and saving, joining or reporting someone else's trip ([MapSharing]). Here: the day tabs, the sheets and likes.
 */
class MapViewModel(
    tripId: String,
    /** The stop a push opened the trip on; see [MapOpening]. */
    stopId: String?,
    /** A push about a request to join opened the trip: the Members sheet shows. */
    showMembers: Boolean,
    /** A push about the stop's comments opened the trip: they show. */
    showComments: Boolean,
    tripRepository: TripRepository,
    markerRepository: MarkerRepository,
    placeRepository: PlaceRepository,
    routeRepository: RouteRepository,
    private val socialRepository: SocialRepository,
    private val appConfigRepository: AppConfigRepository,
    /** How this trip's map was left: restored when it opens, saved as it changes. */
    mapViews: TripMapViewRepository,
    sessionRepository: SessionRepository,
    /** Whether street routes are unlocked: Pro, as the whole app has it. */
    private val proRepository: ProRepository,
    getCurrentLocationUseCase: GetCurrentLocationUseCase,
    newActivityRepository: NewActivityRepository,
    photoRepository: PhotoRepository,
    /** Where heavier work runs off the main thread: the place pins, worked out while the camera moves. */
    computation: CoroutineDispatcher,
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
    private val edits = MapEdits(context, selection, tripRepository, markerRepository, myUserId) { sendEvent(MapEvent.StopAdded) }
    private val pins = MapPins(context, placeRepository, appConfigRepository, computation)
    private val location = MapLocation(context, camera, getCurrentLocationUseCase, ::sendEvent)
    private val routes = MapRouting(
        context, camera, selection, routeRepository, markerRepository, ::hasRoutes,
        askForLocation = location::askForRoute,
        showRoutesArePro = { sendEvent(MapEvent.OpenPaywall) },
        showWholePlan = { sendEvent(MapEvent.ShowWholePlan) },
        transitAreas = appConfigRepository.config.map { it.transitAreas }.stateIn(viewModelScope, SharingStarted.Eagerly, appConfigRepository.config.value.transitAreas),
    )
    private val around = MapAround(context, camera, placeRepository, ::hasRoutes, showAroundIsPro = { sendEvent(MapEvent.OpenPaywall) }, showWholePlan = { sendEvent(MapEvent.ShowWholePlan) })
    private val search = MapSearch(context, camera, selection, pins, placeRepository, routeTo = routes::routeToResult)
    private val sharing = MapSharing(context, tripRepository, socialRepository) { sendEvent(MapEvent.NavigateBack) }
    private val opening = MapOpening(context, camera, tripRepository, mapViews, ::hasRoutes, stopId, showMembers, showComments)
    private val joinRequests = MapJoinRequests(context, tripRepository, mapViews)
    private val addToTrip = MapAddToTrip(context, selection, tripRepository, markerRepository) { sendEvent(MapEvent.AddedToMyTrip) }
    private val cardPhotos = MapCardPhotos(context, photoRepository)
    private val newActivity = MapNewActivity(context, newActivityRepository, pushedStopId = stopId, pushedAboutComments = showComments)

    /** The map's layers and what they were built from, kept until one of those changes. */
    private var layers: Pair<List<Any?>, MapLayersState>? = null

    /**
     * Built off the main thread (pins, route, cards: a few hundred items on every change, up to every frame while Around's
     * ring is dragged), only the newest state when several changes come at once (Shahin 2026-11-17: the map lagged on
     * the main thread after a while).
     */
    val uiState: StateFlow<MapUiState> = combine(trip, context.model, ::toUiState)
        .conflate()
        .flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUiState(null, model))

    /**
     * The Route button's on/off, straight from the model: a tap shows at once however big the trip is, while [uiState]
     * is still being built off the main thread (Shahin, 2026-11-21).
     */
    val isDayRouteOn: StateFlow<Boolean> = context.model
        .map { it.route.isDayRouteShown }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), model.route.isDayRouteShown)

    init {
        opening.start()
        newActivity.start()
        joinRequests.start()
        cardPhotos.start()
    }

    fun onAction(action: MapAction) {
        // A visitor's map is about the trip: what finds, filters or routes places doesn't start there, even from a button
        // still on screen for a frame (Shahin, 2026-11-22). Closing them still works.
        if (action.startsPlaceTools() && context.isVisitor()) return
        when (action) {
            is MapAction.MapView -> onMapView(action)
            is MapAction.Header -> onHeader(action)
            is MapAction.Search -> onSearch(action)
            is MapAction.Location -> onLocation(action)
            is MapAction.Days -> onDays(action)
            is MapAction.Plan -> onPlan(action)
            is MapAction.StopCard -> onStopCard(action)
            is MapAction.PlaceCard -> onPlaceCard(action)
            is MapAction.NewSpot.PickCategory -> edits.pickSpotCategory(action.category)
            is MapAction.NewSpot.SetPhoto -> edits.setSpotPhoto(action.photo)
            is MapAction.NewSpot.Create -> edits.createSpot()
            is MapAction.Sheet -> onSheet(action)
            is MapAction.Dialog -> onDialog(action)
            is MapAction.Message -> onMessage(action)
            is MapAction.PlaceFilter -> onPlaceFilter(action)
            is MapAction.Around -> onAround(action)
            is MapAction.PinRoute.GoHere -> routes.goHere()
            is MapAction.PinRoute.Route -> routes.onRouteButton()
            is MapAction.PinRoute.Close -> routes.closeRouteBar()
            is MapAction.PinRoute.Swap -> routes.swapEnds()
            is MapAction.PinRoute.EditEnd -> routes.editEnd(action.isFrom)
            is MapAction.PinRoute.SubmitEnd -> routes.submitEnd()
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
            is MapAction.MapView.PinDrop -> routes.routeBetweenPins(action.from, action.to)
            is MapAction.MapView.CameraRest -> onCameraRest(action)
            is MapAction.MapView.CameraMoving -> pins.onCameraMoving(action.bounds, action.zoom)
            is MapAction.MapView.ScreenTouch -> selection.onScreenTouch()
            is MapAction.MapView.UserMove -> {
                camera.onUserMove()
                search.onUserMove()
            }
            is MapAction.MapView.NorthUp -> camera.northUp()
            is MapAction.MapView.LayoutChange -> camera.onLayoutChange()
        }
    }

    private fun onHeader(action: MapAction.Header) {
        when (action) {
            is MapAction.Header.Back -> sendEvent(MapEvent.NavigateBack)
            is MapAction.Header.AddDay -> edits.addDay()
            is MapAction.Header.DeleteDay -> openDialog(Dialog.DeleteDay)
            is MapAction.Header.ToggleSave -> sharing.toggleSave()
            is MapAction.Header.ToggleLike -> sharing.toggleLike()
            is MapAction.Header.RequestToJoin -> sharing.requestToJoin()
            is MapAction.Header.CancelJoinRequest -> sharing.cancelJoinRequest()
            is MapAction.Header.OpenMembers -> openSheet(Sheet.Members)
            is MapAction.Header.JoinRequestsSeen -> joinRequests.markSeen()
            is MapAction.Header.ShowJoinRequests -> joinRequests.showAgain()
            is MapAction.Header.OpenJoinRequests -> {
                joinRequests.dismiss()
                openSheet(Sheet.Members)
            }
            is MapAction.Header.OpenOwnerProfile -> trip.value?.trip?.owner?.let { sendEvent(MapEvent.OpenProfile(it.username)) }
            is MapAction.Header.ReportTrip -> sharing.report(ReportTarget.Trip, context.tripId)
            is MapAction.Header.ShareTrip -> trip.value?.trip?.let { sendEvent(MapEvent.ShareTrip(it.name, it.startDate, it.endDate)) }
            is MapAction.Header.LeaveTrip -> sharing.askToLeave()
        }
    }

    private fun onSearch(action: MapAction.Search) {
        when (action) {
            is MapAction.Search.Open -> search.open()
            is MapAction.Search.QueryChanged -> search.onQueryChanged(action.query)
            is MapAction.Search.ResultClick -> {
                // A place that's already a stop of the open day opens that stop, not a second pin on top of it.
                val stop = action.result.place?.let { place -> uiState.value.layers.stops.firstOrNull { it.placeId == place.id } }
                if (stop != null && !context.model.value.route.isPicking) {
                    search.close()
                    selection.selectMarker(stop)
                } else {
                    search.onResultClick(action.result)
                }
            }
            is MapAction.Search.Submit -> search.submit()
            is MapAction.Search.Close -> search.close()
        }
    }

    private fun onLocation(action: MapAction.Location) {
        when (action) {
            is MapAction.Location.PermissionChecked -> location.onPermissionChecked(action.permission)
            is MapAction.Location.LocationSwitched -> location.onLocationSwitched(action.isOn)
            MapAction.Location.TurnOn -> location.turnOn()
            is MapAction.Location.PermissionResult -> location.onPermissionResult(action.permission)
            is MapAction.Location.ConfirmPrompt -> location.confirmPrompt()
            is MapAction.Location.DismissPrompt -> location.dismissPrompt()
            is MapAction.Location.CenterOnUser -> location.centerOnUser()
        }
    }

    private fun onDays(action: MapAction.Days) {
        when (action) {
            is MapAction.Days.Select -> selectDay(action.dayIndex)
            is MapAction.Days.FitDay -> if (!around.frameRing() && !search.frameNamed() && !routes.frameShownRoute()) camera.showOpenDay(also = routes.dayRouteLine())
            is MapAction.Days.TogglePlaces -> pins.setHidden(!model.arePlacesHidden)
        }
    }

    private fun onPlaceFilter(action: MapAction.PlaceFilter) {
        when (action) {
            is MapAction.PlaceFilter.Toggle -> context.update { it.copy(placeFilter = it.placeFilter.copy(isOpen = !it.placeFilter.isOpen)) }
            is MapAction.PlaceFilter.ToggleCategory -> pins.setFilter(
                action.category?.let { model.placeFilter.categories.pickedAlone(it) }.orEmpty(),
            )
        }
    }

    private fun onAround(action: MapAction.Around) {
        when (action) {
            is MapAction.Around.Open -> around.open()
            is MapAction.Around.Close -> around.close()
            is MapAction.Around.ToggleCategory -> around.toggleCategory(action.category)
            is MapAction.Around.SetRadius -> around.setRadius(action.meters, action.isFinal)
            is MapAction.Around.PlaceClick -> selection.openPlace(action.place)
            // Adding works as from along the way: to the open day, the whole plan shows.
            is MapAction.Around.Add -> routes.addAlongTheWay(action.place)
        }
    }

    private fun onPlan(action: MapAction.Plan) {
        when (action) {
            is MapAction.Plan.RemoveStop -> edits.removeMarker(action.markerId)
            is MapAction.Plan.ClearAll -> openDialog(Dialog.ClearDay)
            is MapAction.Plan.ToggleRoute -> routes.toggleDayRoute()
            is MapAction.Plan.RetryRoute -> routes.retryRoute()
            is MapAction.Plan.SelectTravelMode -> routes.selectTravelMode(action.mode)
            is MapAction.Plan.FindBestOrder -> routes.findBestOrder()
            is MapAction.Plan.AlongTheWayClick -> selection.openPlace(action.place)
            is MapAction.Plan.AddAlongTheWay -> routes.addAlongTheWay(action.place)
            is MapAction.Plan.ToggleAlongCategory -> routes.toggleAlongCategory(action.category)
            is MapAction.Plan.ClosePointRoute -> routes.closePointRoute()
            is MapAction.Plan.OpenLeaveAt -> routes.openLeaveAt()
            is MapAction.Plan.OpenTransitSteps -> if (model.route.plan?.route?.legs.orEmpty().any { it.steps.isNotEmpty() }) openSheet(Sheet.TransitSteps)
        }
    }

    private fun onStopCard(action: MapAction.StopCard) {
        when (action) {
            is MapAction.StopCard.ToggleHot -> toggleStopHot(action.markerId)
            is MapAction.StopCard.HotSeen -> newActivity.seenHot(action.markerId)
            is MapAction.StopCard.OpenPhotos -> selection.selectedMarkerId?.let { openSheet(Sheet.StopPhotos(it, fromDetails = isDetailsOpen())) }
            is MapAction.StopCard.OpenComments -> selection.selectedMarkerId?.let { openSheet(Sheet.StopComments(it, fromDetails = isDetailsOpen())) }
            is MapAction.StopCard.OpenDetails -> if (selection.selectedMarkerId != null) openSheet(Sheet.Details)
            is MapAction.StopCard.Report -> selection.selectedMarkerId?.let { sharing.report(ReportTarget.Marker, it) }
            is MapAction.StopCard.Add -> addToTrip.open(fromDetails = isDetailsOpen())
        }
    }

    private fun onPlaceCard(action: MapAction.PlaceCard) {
        when (action) {
            is MapAction.PlaceCard.Add -> if (context.canEdit()) edits.addPlace() else addToTrip.open(fromDetails = isDetailsOpen())
            // Not for an address from search: it isn't one of our places yet.
            is MapAction.PlaceCard.OpenPhotos -> selection.selectedPlace?.takeIf { it.address == null }?.let { openSheet(Sheet.PlacePhotos(it.place, fromDetails = isDetailsOpen())) }
            is MapAction.PlaceCard.OpenImage -> selection.openPlaceImage(action.index)
            is MapAction.PlaceCard.OpenDetails -> if (selection.selectedPlace != null) openSheet(Sheet.Details)
        }
    }

    private fun onSheet(action: MapAction.Sheet) {
        when (action) {
            is MapAction.Sheet.ClosePlaceImage -> selection.openPlaceImage(null)
            is MapAction.Sheet.ReportPhoto -> sharing.reportPhoto(action.photo)
            is MapAction.Sheet.OpenProfile -> sendEvent(MapEvent.OpenProfile(action.username))
            is MapAction.Sheet.PickTrip -> addToTrip.pickTrip(action.tripId)
            is MapAction.Sheet.PickDay -> addToTrip.pickDay(action.dayId)
            is MapAction.Sheet.CloseAll -> context.update { it.copy(sheet = null) }
            // The days go back to the trips; the rest to the details they were opened from.
            is MapAction.Sheet.Back -> if ((model.sheet as? Sheet.AddToTrip)?.trip != null) addToTrip.backToTrips() else backToDetails()
        }
    }

    private fun onDialog(action: MapAction.Dialog) {
        when (action) {
            is MapAction.Dialog.Confirm -> if (model.dialog == Dialog.LeaveTrip) sharing.leave() else edits.confirm()
            is MapAction.Dialog.DeleteAnyway -> edits.deleteAnyway()
            is MapAction.Dialog.SendReport -> sharing.sendReport(action.reason)
            is MapAction.Dialog.ApplyBestOrder -> routes.applyBestOrder()
            is MapAction.Dialog.PickTransitTime -> routes.pickTransitTime(LocalTime(action.hour, action.minute), action.isArriveBy)
            is MapAction.Dialog.DismissConfirm,
            is MapAction.Dialog.DismissDeleteAnyway,
            is MapAction.Dialog.DismissReport,
            is MapAction.Dialog.DismissBestOrder,
            is MapAction.Dialog.DismissLeaveAt -> closeDialog()
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
        val inputs = listOf(details, model.activeDayIndex, model.route, model.pins, model.selection, model.location.permission, model.news, model.arePlacesHidden, model.around, model.placeFilter.categories, model.placeFilter.name, config)
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

    /** One sheet at a time: one opened from the details goes back to them, while their stop or place is still open. */
    private fun backToDetails() = context.update { model ->
        model.copy(sheet = if (model.sheet?.fromDetails == true && model.selection != null) Sheet.Details else null)
    }

    private fun isDetailsOpen() = model.sheet == Sheet.Details

    private fun openDialog(dialog: Dialog) = context.update { it.copy(dialog = dialog) }

    private fun closeDialog() = context.update { it.copy(dialog = null) }

    /** A stop's flame marks its place hot; shows at once, see [SocialRepository.setHot]. */
    private fun toggleStopHot(markerId: String) {
        val stop = context.days().firstNotNullOfOrNull { day -> day.markers.firstOrNull { it.id == markerId } } ?: return
        if (!stop.canMarkHot) return
        viewModelScope.launch {
            val result = socialRepository.setHot(stop.placeId, hot = !stop.hotByMe)
            if (result is AppResult.Error) context.showMessage(result.error.toUiText(Res.string.map_error_hot))
        }
    }

    /** Routes along streets are Tripinly Pro; the server decides, [ProRepository.isPro] tells the app. */
    private fun hasRoutes(): Boolean = proRepository.isPro.value

    private fun sendEvent(event: MapEvent) {
        _events.trySend(event)
    }

    private companion object {
        /** A tapped cluster opens up into its places. */
        const val CLUSTER_ZOOM_STEP = 2f
    }
}

/** Finding, filtering or routing places: off on a visitor's map ([MapContext.isVisitor]). */
private fun MapAction.startsPlaceTools(): Boolean = when (this) {
    MapAction.Search.Open, MapAction.Days.TogglePlaces, MapAction.Around.Open, MapAction.PinRoute.GoHere, MapAction.PinRoute.Route -> true
    is MapAction.PlaceFilter, is MapAction.Plan.AlongTheWayClick, is MapAction.Plan.AddAlongTheWay, is MapAction.Plan.ToggleAlongCategory -> true
    else -> false
}
