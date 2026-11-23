package com.falcon.tripingly.feature.map.presentation.spot

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.billing.ProRepository
import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.data.photos.PhotoRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.routing.RouteRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.social.HotState
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_hot
import com.falcon.tripingly.feature.map.generated.resources.map_error_load_spot
import com.falcon.tripingly.feature.map.presentation.component.CameraCommand
import com.falcon.tripingly.feature.map.presentation.component.PlacePin
import com.falcon.tripingly.feature.map.presentation.component.placePinSize
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.MapEvent
import com.falcon.tripingly.feature.map.presentation.map.MapSheet
import com.falcon.tripingly.feature.map.presentation.map.MapStateMapper
import com.falcon.tripingly.feature.map.presentation.map.MapUiState
import com.falcon.tripingly.feature.map.presentation.map.PinAdd
import com.falcon.tripingly.feature.map.presentation.map.PinRoutesState
import com.falcon.tripingly.feature.map.presentation.map.pinRoutes
import com.falcon.tripingly.feature.map.presentation.map.metersTo
import com.falcon.tripingly.feature.map.presentation.map.state.MapAddToTrip
import com.falcon.tripingly.feature.map.presentation.map.state.MapAround
import com.falcon.tripingly.feature.map.presentation.map.state.MapCamera
import com.falcon.tripingly.feature.map.presentation.map.state.MapCardPhotos
import com.falcon.tripingly.feature.map.presentation.map.state.MapContext
import com.falcon.tripingly.feature.map.presentation.map.state.MapLocation
import com.falcon.tripingly.feature.map.presentation.map.state.MapModel
import com.falcon.tripingly.feature.map.presentation.map.state.MapRouting
import com.falcon.tripingly.feature.map.presentation.map.state.MapSelection
import com.falcon.tripingly.feature.map.presentation.map.state.MapSharing
import com.falcon.tripingly.feature.map.presentation.map.state.Selection
import com.falcon.tripingly.feature.map.presentation.map.state.Sheet
import com.falcon.tripingly.feature.map.presentation.map.state.isGoingTo
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

/**
 * One spot on its own map (Shahin, 2026-11-23): "the map is the card". Its pin with its card (the photos and the credit),
 * its name, owner and numbers in the header, and its buttons in the bottom bar: its photos, Go here, Around (both
 * Tripinly Pro), its details and the green + onto one of my trips. No other pins. The trip map's parts do the work
 * (camera, selection, location, routes, Around, add to trip, the card's photos, reports), on a map without a trip; one
 * card shows at a time: a place found around the spot opens a view-only card in its place.
 */
class SpotMapViewModel(
    private val placeId: String,
    private val placeRepository: PlaceRepository,
    private val socialRepository: SocialRepository,
    tripRepository: TripRepository,
    markerRepository: MarkerRepository,
    routeRepository: RouteRepository,
    private val appConfigRepository: AppConfigRepository,
    sessionRepository: SessionRepository,
    /** Whether Go here and Around are unlocked: Pro, as the whole app has it. */
    private val proRepository: ProRepository,
    getCurrentLocationUseCase: GetCurrentLocationUseCase,
    photoRepository: PhotoRepository,
    /** Where the screen's state is built, off the main thread (Around's ring changes it every frame while dragged). */
    computation: CoroutineDispatcher,
) : ViewModel() {

    private val myUserId = (sessionRepository.session.value as? SessionState.SignedIn)?.account?.id

    /** A map without a trip: no stops, no days, nothing to edit. */
    private val context = MapContext(tripId = "", scope = viewModelScope, trip = MutableStateFlow<TripDetails?>(null))
    private val model get() = context.model.value

    private val spot = MutableStateFlow(SpotModel())

    private val _events = Channel<MapEvent>(capacity = Channel.BUFFERED)
    val events: Flow<MapEvent> = _events.receiveAsFlow()

    private val camera = MapCamera(context)
    /** Where the map's camera goes; the map view runs each command once. */
    val cameraCommands: Flow<CameraCommand> = camera.commands

    private val selection = MapSelection(context, camera, placeRepository)
    private val location = MapLocation(context, camera, getCurrentLocationUseCase, ::sendEvent)
    private val routes = MapRouting(
        context, camera, selection, routeRepository, markerRepository, ::hasRoutes,
        askForLocation = location::askForRoute,
        showRoutesArePro = { sendEvent(MapEvent.OpenPaywall) },
        showWholePlan = { sendEvent(MapEvent.ShowWholePlan) },
        transitAreas = appConfigRepository.config.map { it.transitAreas }.stateIn(viewModelScope, SharingStarted.Eagerly, appConfigRepository.config.value.transitAreas),
    )
    private val around = MapAround(context, camera, placeRepository, ::hasRoutes, showAroundIsPro = { sendEvent(MapEvent.OpenPaywall) }, showWholePlan = { sendEvent(MapEvent.ShowWholePlan) })
    private val sharing = MapSharing(context, tripRepository, socialRepository) { sendEvent(MapEvent.NavigateBack) }
    private val addToTrip = MapAddToTrip(context, selection, tripRepository, markerRepository) { sendEvent(MapEvent.AddedToMyTrip) }
    private val cardPhotos = MapCardPhotos(context, photoRepository)

    private var loadJob: Job? = null
    private var hotJob: Job? = null
    /** What the server last confirmed of the flame, before the taps on their way. */
    private var confirmedHot: HotState? = null

    val uiState: StateFlow<SpotMapUiState> = combine(context.model, spot, ::toUiState)
        .conflate()
        .flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUiState(model, spot.value))

    init {
        load()
        cardPhotos.start()
        // A flame from another screen, or a count the server sends live, shows here too; a tap still on its way wins.
        viewModelScope.launch {
            socialRepository.hotChanges.collect { change ->
                if (change.placeId != placeId || hotJob?.isActive == true) return@collect
                val hot = HotState(change.hotByMe ?: spot.value.hot?.hot ?: false, change.hotCount)
                confirmedHot = hot
                spot.update { it.copy(hot = hot) }
            }
        }
    }

    /** The spot map's own buttons. */
    fun onAction(action: SpotMapAction) {
        when (action) {
            SpotMapAction.Back -> sendEvent(MapEvent.NavigateBack)
            SpotMapAction.Retry -> load()
            SpotMapAction.ToggleHot -> toggleHot()
            SpotMapAction.OpenOwner -> spot.value.details?.addedBy?.let { sendEvent(MapEvent.OpenProfile(it.username)) }
            SpotMapAction.OpenPhotos -> spotPin()?.let { pin -> context.update { it.copy(sheet = Sheet.PlacePhotos(pin)) } }
            SpotMapAction.GoHere -> {
                // A second tap closes the route; the spot's card comes back, as when the route's card closes.
                val wasOn = spotPin()?.let { model.route.isGoingTo(it.location) } == true
                forSpot { routes.goHere() }
                if (wasOn) openSpot()
            }
            SpotMapAction.Around -> forSpot { around.open() }
            SpotMapAction.Add -> forSpot { addToTrip.open(fromDetails = false) }
            // Its details are about the spot: its card comes back under them.
            SpotMapAction.OpenDetails -> if (spotPin() != null) {
                openSpot()
                context.update { it.copy(sheet = Sheet.Details) }
            }
        }
    }

    /**
     * What the spot map shares with the trip map: the map view, the location, Around, a route's card, the place cards'
     * photos and details, the sheets and the dialogs. The rest (stops, days, search, the place filter) isn't on this map.
     */
    fun onMapAction(action: MapAction) {
        when (action) {
            is MapAction.MapView -> onMapView(action)
            is MapAction.Location -> onLocation(action)
            is MapAction.Days.FitDay -> fit()
            is MapAction.Around -> onAround(action)
            is MapAction.PinRoute.GoHere -> routes.goHere()
            is MapAction.PinRoute.Close -> routes.closeRouteBar()
            is MapAction.Plan -> onPlan(action)
            is MapAction.PlaceCard.OpenImage -> selection.openPlaceImage(action.index)
            is MapAction.PlaceCard.OpenPhotos -> selection.selectedPlace?.let { place -> context.update { it.copy(sheet = Sheet.PlacePhotos(place.place, fromDetails = isDetailsOpen())) } }
            is MapAction.PlaceCard.OpenDetails -> if (selection.selectedPlace != null) context.update { it.copy(sheet = Sheet.Details) }
            is MapAction.PlaceCard.Add -> addToTrip.open(fromDetails = isDetailsOpen())
            is MapAction.Sheet -> onSheet(action)
            is MapAction.Dialog -> onDialog(action)
            is MapAction.Message.Shown -> context.showMessage(null)
            else -> Unit
        }
    }

    private fun onMapView(action: MapAction.MapView) {
        when (action) {
            // The spot's own pin brings its card back; a place around it opens its view-only card.
            is MapAction.MapView.PlacePinClick -> if (action.place.id == placeId) openSpot() else selection.openPlace(action.place)
            is MapAction.MapView.CameraRest -> {
                camera.onRest(action.coordinates, action.zoom)
                selection.onCameraRest()
            }
            is MapAction.MapView.ScreenTouch -> selection.onScreenTouch()
            is MapAction.MapView.UserMove -> camera.onUserMove()
            is MapAction.MapView.NorthUp -> camera.northUp()
            is MapAction.MapView.LayoutChange -> camera.onLayoutChange()
            else -> Unit
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

    private fun onAround(action: MapAction.Around) {
        when (action) {
            is MapAction.Around.Open -> around.open()
            is MapAction.Around.Close -> {
                around.close()
                // Its card comes back once the ring has gone.
                openSpot()
            }
            is MapAction.Around.ToggleCategory -> around.toggleCategory(action.category)
            is MapAction.Around.SetRadius -> around.setRadius(action.meters, action.isFinal)
            is MapAction.Around.PlaceClick -> selection.openPlace(action.place)
            is MapAction.Around.Add -> Unit
        }
    }

    private fun onPlan(action: MapAction.Plan) {
        when (action) {
            is MapAction.Plan.RetryRoute -> routes.retryRoute()
            is MapAction.Plan.SelectTravelMode -> routes.selectTravelMode(action.mode)
            is MapAction.Plan.AlongTheWayClick -> selection.openPlace(action.place)
            is MapAction.Plan.ToggleAlongCategory -> routes.toggleAlongCategory(action.category)
            is MapAction.Plan.ClosePointRoute -> {
                routes.closePointRoute()
                openSpot()
            }
            is MapAction.Plan.OpenLeaveAt -> routes.openLeaveAt()
            is MapAction.Plan.OpenTransitSteps -> if (model.route.plan?.route?.legs.orEmpty().any { it.steps.isNotEmpty() }) context.update { it.copy(sheet = Sheet.TransitSteps) }
            else -> Unit
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
            is MapAction.Sheet.Back -> if ((model.sheet as? Sheet.AddToTrip)?.trip != null) {
                addToTrip.backToTrips()
            } else {
                context.update { it.copy(sheet = if (it.sheet?.fromDetails == true && it.selection != null) Sheet.Details else null) }
            }
        }
    }

    private fun onDialog(action: MapAction.Dialog) {
        when (action) {
            is MapAction.Dialog.SendReport -> sharing.sendReport(action.reason)
            is MapAction.Dialog.PickTransitTime -> routes.pickTransitTime(LocalTime(action.hour, action.minute), action.isArriveBy)
            is MapAction.Dialog.DismissReport, is MapAction.Dialog.DismissLeaveAt -> context.update { it.copy(dialog = null) }
            else -> Unit
        }
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        spot.update { it.copy(isLoading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            val result = placeRepository.place(placeId)
            val details = (result as? AppResult.Success)?.data
            if (details?.pin == null) {
                val error = (result as? AppResult.Error)?.error?.toUiText(Res.string.map_error_load_spot) ?: UiText.Resource(Res.string.map_error_load_spot)
                spot.update { it.copy(isLoading = false, loadError = error) }
                return@launch
            }
            confirmedHot = HotState(details.hotByMe, details.hotCount)
            spot.update { it.copy(details = details, isLoading = false, hot = confirmedHot) }
            openSpot(zoom = SPOT_ZOOM)
        }
    }

    /** The spot's pin and card: the camera brings it to the middle (at the zoom it has), its card opens there. */
    private fun openSpot(zoom: Float = camera.zoom) {
        val pin = spotPin() ?: return
        val isThere = model.camera.isReady && camera.isAt(pin.location, zoom)
        context.update { it.copy(selection = spotSelection(isCardShown = isThere)) }
        camera.moveTo(Coordinates(pin.location.lat, pin.location.lng), zoom)
    }

    /**
     * Runs a button of the bottom bar, which is always about the spot, also while its card is closed or a place around it
     * is open: the spot is selected for it (without moving the camera) and what was open before comes back when the
     * button left it so (Go here without my location yet, Around closing, the trips to add it to).
     */
    private fun forSpot(run: () -> Unit) {
        if (spotPin() == null) return
        val before = model.selection
        val quiet = (before as? Selection.Place)?.takeIf { it.place.id == placeId } ?: spotSelection(isCardShown = false).also { selected ->
            context.update { it.copy(selection = selected) }
        }
        run()
        if (quiet !== before) context.update { if (it.selection === quiet) it.copy(selection = before) else it }
    }

    /** The fit button: the ring while Around shows, else Go here's route, else the spot with its card. */
    private fun fit() {
        if (!around.frameRing() && !routes.frameShownRoute()) openSpot(zoom = SPOT_ZOOM)
    }

    /** The flame: shows at once and stays; a newer tap cancels the one on its way, and only a refusal puts back the last confirmed one. */
    private fun toggleHot() {
        val shown = spot.value.hot ?: return
        val hot = !shown.hot
        spot.update { it.copy(hot = HotState(hot, (shown.hotCount + if (hot) 1 else -1).coerceAtLeast(0))) }
        hotJob?.cancel()
        hotJob = viewModelScope.launch {
            when (val result = socialRepository.setHot(placeId, hot)) {
                is AppResult.Success -> confirmedHot = result.data
                is AppResult.Error -> {
                    spot.update { it.copy(hot = confirmedHot) }
                    context.showMessage(result.error.toUiText(Res.string.map_error_hot))
                }
            }
        }
    }

    private fun spotPin(): MapPlace? = spot.value.details?.pin

    private fun spotSelection(isCardShown: Boolean): Selection.Place =
        Selection.Place(spotPin()!!, details = spot.value.details, isCardShown = isCardShown)

    private fun isDetailsOpen() = model.sheet == Sheet.Details

    /** Go here and Around are Tripinly Pro; the server decides, [ProRepository.isPro] tells the app. */
    private fun hasRoutes(): Boolean = proRepository.isPro.value

    private fun sendEvent(event: MapEvent) {
        _events.trySend(event)
    }

    private fun toUiState(model: MapModel, spot: SpotModel): SpotMapUiState {
        val config = appConfigRepository.config.value
        val map = MapStateMapper(details = null, model = model, config = config, myUserId = myUserId).map()
        val details = spot.details
        val pin = details?.pin
        val hot = spot.hot
        return SpotMapUiState(
            map = if (pin == null) map else map.forSpot(pin, details, hot, placePinSize(pin, config)),
            spot = if (details == null || pin == null) null else SpotState(
                name = details.name,
                category = details.category,
                owner = details.addedBy,
                isOwnerMe = details.addedBy?.id == myUserId,
                isFromOsm = details.isFromOsm,
                address = details.address,
                distanceMeters = model.location.current?.let { GeoPoint(it.latitude, it.longitude).metersTo(pin.location) },
                isHot = hot?.hot ?: details.hotByMe,
                hotCount = hot?.hotCount ?: details.hotCount,
                tripCount = details.tripCount,
                routes = model.route.pinRoutes(pin.location, hasLocation = model.location.current != null).copy(isAroundOn = model.around?.center == pin.location),
            ),
            isLoading = spot.isLoading,
            loadError = spot.loadError,
        )
    }

    /**
     * The trip map's state as the spot map shows it: the spot's pin always on the map (a plain pin while another card
     * is open or Around shows), its card and details with its flame as shown and the + onto one of my trips; a place
     * around it as a view-only card.
     */
    private fun MapUiState.forSpot(pin: MapPlace, details: PlaceDetails, hot: HotState?, pinSize: Dp): MapUiState {
        val shownDetails = hot?.let { details.copy(hotCount = it.hotCount, hotByMe = it.hot) } ?: details
        val isSpotDrawn = layers.selectedPlace?.id == pin.id || layers.placePins.any { it.place.id == pin.id }
        return copy(
            layers = if (isSpotDrawn) layers else layers.copy(placePins = (layers.placePins + PlacePin(pin, pinSize, photo = pin.coverImageId != null, isKept = true)).toImmutableList()),
            placeCard = placeCard?.let { card -> if (card.place.id == pin.id) card.copy(details = shownDetails, add = PinAdd.ToMyTrip) else card.copy(add = null) },
            sheet = (sheet as? MapSheet.PlaceDetails)?.takeIf { it.card.place.id == pin.id }?.let { MapSheet.PlaceDetails(it.card.copy(details = shownDetails, add = PinAdd.ToMyTrip)) } ?: sheet,
        )
    }

    private companion object {
        /** A street around the spot. */
        const val SPOT_ZOOM = 16f
    }
}

/** What the spot map keeps besides the map's [MapModel]. */
internal data class SpotModel(
    val details: PlaceDetails? = null,
    val isLoading: Boolean = true,
    /** Why the spot couldn't load: the error screen with a retry. */
    val loadError: UiText? = null,
    /** The flame as shown: a tap shows at once; null until the spot loads. */
    val hot: HotState? = null,
)

/** The spot map: the trip map's [map] parts, and the spot itself for the header, its card grown out of it and the bar. */
@Immutable
data class SpotMapUiState(
    val map: MapUiState = MapUiState(),
    /** Null while it loads. */
    val spot: SpotState? = null,
    val isLoading: Boolean = true,
    val loadError: UiText? = null,
)

/** The spot as the header's pill and the card grown out of it show it. */
@Immutable
data class SpotState(
    val name: String,
    val category: PlaceCategory,
    /** Who made this custom spot; null for one from OpenStreetMap or when the server keeps it hidden. */
    val owner: UserSummary?,
    val isOwnerMe: Boolean,
    val isFromOsm: Boolean,
    val address: String?,
    /** From where I am, once that is known. */
    val distanceMeters: Int?,
    val isHot: Boolean,
    val hotCount: Int,
    /** Public trips with a stop here. */
    val tripCount: Int,
    /** The bar's Go here and Around, on while they show for the spot. */
    val routes: PinRoutesState = PinRoutesState(),
)

sealed interface SpotMapAction {
    data object Back : SpotMapAction
    data object Retry : SpotMapAction
    data object ToggleHot : SpotMapAction
    /** The owner's face in the header's pill or the card: their profile. */
    data object OpenOwner : SpotMapAction
    data object OpenPhotos : SpotMapAction
    data object GoHere : SpotMapAction
    data object Around : SpotMapAction
    data object OpenDetails : SpotMapAction
    data object Add : SpotMapAction
}
