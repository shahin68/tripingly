package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.domain.model.CameraCommand
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_join_request_count
import com.falcon.tripingly.feature.map.generated.resources.map_retry
import com.falcon.tripingly.feature.map.presentation.component.GoogleMapView
import com.falcon.tripingly.feature.map.presentation.component.PlaceCard
import com.falcon.tripingly.feature.map.presentation.component.RouteAttribution
import com.falcon.tripingly.feature.map.presentation.component.SelectedPinSize
import com.falcon.tripingly.feature.map.presentation.component.fittedCamera
import com.falcon.tripingly.feature.map.presentation.component.StopCard
import com.falcon.tripingly.feature.map.presentation.component.stopPinHeight
import com.falcon.tripingly.feature.map.presentation.photos.rememberPhotoPicker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The trip's map with the header on top and the day's cards at the bottom. What they cover is the map's content
 * padding: the camera centers and fits within the rest, and the tapped pin's card opens just above its middle.
 */
@Composable
internal fun MapScreen(
    state: MapUiState,
    cameraCommands: Flow<CameraCommand>,
    onAction: (MapAction) -> Unit,
    planSheet: AnchoredDraggableState<PlanSheetValue> = rememberPlanSheetState(),
) {
    val density = LocalDensity.current
    // Layout only: what the header, the day tabs and the credits cover of the map, handed to it as its padding. Each is
    // measured when its size changes; the map waits for all three, so it opens already fitted to what's left.
    var headerHeight by remember { mutableStateOf<Dp?>(null) }
    // The tabs' height with whether there were days to show, so the map doesn't open on the height without tabs.
    var tabsHeight by remember { mutableStateOf<Pair<Boolean, Dp>?>(null) }
    var creditsHeight by remember { mutableStateOf<Dp?>(null) }
    // Where the open cards are, so a touch on them doesn't close them.
    var placeCardBounds by remember { mutableStateOf(Rect.Zero) }
    var stopCardBounds by remember { mutableStateOf(Rect.Zero) }
    val isPlaceCardOpen by rememberUpdatedState(state.placeCard != null)
    val isStopCardOpen by rememberUpdatedState(state.stopCard != null)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Any touch outside the open card closes it, and still reaches the map and the controls.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val isOnCard = (isPlaceCardOpen && placeCardBounds.contains(down.position)) ||
                        (isStopCardOpen && stopCardBounds.contains(down.position))
                    if (!isOnCard) onAction(MapAction.OnScreenTouch)
                }
            }
    ) {
        val plan: Any? = state.pointRouteCard ?: state.stopsCard
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val coveredTop = statusBar + (headerHeight ?: 0.dp)
        // The pulled-up sheet doesn't count: only the part that always shows.
        val hasDays = state.dayTabs.days.isNotEmpty()
        val coveredBottom = (tabsHeight?.second ?: 0.dp) + (if (plan != null) PlanSheetPeek else 0.dp) + (creditsHeight ?: 0.dp)
        // Not before the camera knows where the trip is: the map opens there, fitted to the open day, instead of
        // starting somewhere and then fitting.
        // A fit frames the bounds in the map that shows right now: above the sheet as far as it's pulled up.
        val fit by rememberUpdatedState { bounds: GeoBounds ->
            val pulledUp = if (plan != null) with(density) { planSheet.pulledUp().toDp() } else 0.dp
            fittedCamera(bounds, maxWidth, maxHeight - coveredTop - coveredBottom - pulledUp, raise = pulledUp / 2)
        }
        val moves = remember(cameraCommands) {
            cameraCommands.map { command ->
                when (command) {
                    is CameraCommand.MoveTo -> command
                    is CameraCommand.Fit -> fit(command.bounds).let { (target, zoom) -> CameraCommand.MoveTo(target, zoom) }
                }
            }
        }
        if (state.camera.isReady && headerHeight != null && tabsHeight?.first == hasDays && creditsHeight != null) {
            val camera = state.camera
            val (target, zoom) = camera.openingBounds?.let(fit) ?: (camera.target to camera.zoom)
            val layers = state.layers
            GoogleMapView(
                modifier = Modifier.fillMaxSize(),
                initialTarget = target,
                initialZoom = zoom,
                cameraCommands = moves,
                contentPadding = PaddingValues(top = coveredTop, bottom = coveredBottom),
                markers = layers.stops,
                placePins = layers.placePins,
                pinDotBelowZoom = layers.pinDotBelowZoom,
                placeClusters = layers.placeClusters,
                selectedPlace = layers.selectedPlace,
                line = layers.line,
                isMyLocationEnabled = layers.isMyLocationEnabled,
                onCameraMove = { coords, zoom, bounds -> onAction(MapAction.OnCameraMove(coords, zoom, bounds)) },
                onMapTouch = { onAction(MapAction.OnScreenTouch) },
                onMapClick = { coords -> onAction(MapAction.OnMapClick(coords)) },
                onPlacePinClick = { place -> onAction(MapAction.OnPlacePinClick(place)) },
                onClusterClick = { cluster -> onAction(MapAction.OnClusterClick(cluster)) },
                onMapLongClick = { coords -> onAction(MapAction.OnMapLongClick(coords)) },
                onMarkerClick = { marker -> onAction(MapAction.OnMarkerClick(marker)) },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
        ) {
            MapHeader(
                header = state.header,
                search = state.search,
                onAction = onAction,
                modifier = Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
            )
            if (state.header.pendingJoinRequestCount > 0 && state.search == null) {
                FilledTonalButton(
                    onClick = { onAction(MapAction.OnOpenJoinRequests) },
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                ) {
                    val count = state.header.pendingJoinRequestCount
                    Text(pluralStringResource(Res.plurals.map_join_request_count, count, count))
                }
            }
            state.search?.results?.takeIf { it.isNotEmpty() }?.let { results ->
                SearchResults(results = results, onAction = onAction, modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium))
            }
        }

        // Retry sends every stop change that failed for a connection problem, earlier ones too.
        val message = state.message
        ShowMessage(
            message = message?.text?.asString(),
            onShown = { onAction(MapAction.OnMessageShown) },
            isError = message?.text !is UiText.Note,
            actionLabel = if (message?.canRetry == true) stringResource(Res.string.map_retry) else null,
            onAction = if (message?.canRetry == true) ({ onAction(MapAction.RetryFailedChanges) }) else null,
        )

        BottomCards(
            routeAttribution = state.routeAttribution,
            location = state.location,
            routeStartName = state.routeStartName,
            dayTabs = state.dayTabs,
            plan = plan,
            planSheet = planSheet,
            onAction = onAction,
            onTabsHeight = { tabsHeight = hasDays to with(density) { it.toDp() } },
            onCreditsHeight = { creditsHeight = with(density) { it.toDp() } },
            modifier = Modifier.align(Alignment.BottomCenter).padding(top = coveredTop),
        )

        // The part of the map nothing covers: its middle is where the camera puts a tapped pin.
        Box(modifier = Modifier.fillMaxSize().padding(top = coveredTop, bottom = coveredBottom)) {
            PinCard(
                card = state.placeCard,
                key = { it.place.id to it.place.location },
                pinClearance = SelectedPinSize / 2 + MaterialTheme.spacing.small,
                modifier = Modifier.onGloballyPositioned { placeCardBounds = it.boundsInRoot() },
            ) { card ->
                PlaceCard(
                    place = card.place,
                    details = card.details,
                    address = card.address,
                    dayNumber = card.addToDayNumber,
                    onAdd = { onAction(MapAction.OnAddPlaceToDay) },
                    onOpenImage = { index -> onAction(MapAction.OnOpenPlaceImage(index)) },
                    onOpenPhotos = { onAction(MapAction.OnOpenPlacePhotos) },
                    onRouteFrom = { onAction(MapAction.OnRouteFromPlace) },
                    onRouteTo = { onAction(MapAction.OnRouteToPlace) }.takeIf { card.canRouteTo },
                )
            }
            val pickPhotos = rememberPhotoPicker { images -> onAction(MapAction.OnAddStopPhotos(images)) }
            PinCard(
                card = state.stopCard,
                key = { it.stop.id },
                pinClearance = stopPinHeight(hasCover = state.stopCard?.stop?.coverPhotoId != null) + MaterialTheme.spacing.small,
                modifier = Modifier.onGloballyPositioned { stopCardBounds = it.boundsInRoot() },
            ) { card ->
                StopCard(
                    marker = card.stop,
                    placeImage = card.placeImage,
                    canEdit = card.canEdit,
                    onOpen = { onAction(MapAction.OnOpenStopPhotos) },
                    onAddPhotos = pickPhotos,
                    onTakePhoto = { onAction(MapAction.OnOpenPhotoCamera) },
                    onToggleLike = { onAction(MapAction.OnToggleStopLike(card.stop.id)) },
                    onOpenComments = { onAction(MapAction.OnOpenStopComments) },
                    canReport = card.canReport,
                    onReport = { onAction(MapAction.OnReportStop) },
                    onRemoveMyContent = { onAction(MapAction.OnRemoveMyContent) }.takeIf { card.canRemoveMyContent },
                )
            }
        }
    }
}

/**
 * The credits and the location button, the route-start chip and the open day's [plan] (the stops, or a route from A to
 * B) in its sheet, over the day tabs at the bottom. [onTabsHeight] and [onCreditsHeight] report what always covers the
 * map; the location button and a pulled-up sheet only float over it.
 */
@Composable
private fun BottomCards(
    routeAttribution: String?,
    location: LocationState,
    routeStartName: String?,
    dayTabs: DayTabsState,
    plan: Any?,
    planSheet: AnchoredDraggableState<PlanSheetValue>,
    onAction: (MapAction) -> Unit,
    onTabsHeight: (Int) -> Unit,
    onCreditsHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PlanSheet(
            plan = plan,
            state = planSheet,
            above = {
                routeStartName?.let { name ->
                    RouteStartChip(name, onAction, Modifier.padding(start = MaterialTheme.spacing.medium))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.spacing.medium),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Our places come from OpenStreetMap, routes from openrouteservice too. The credits and the gap
                    // under them are what this row covers of the map.
                    val credits = Modifier
                        .onSizeChanged { onCreditsHeight(it.height) }
                        .padding(bottom = MaterialTheme.spacing.small)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    if (routeAttribution != null) {
                        RouteAttribution(routeAttribution, modifier = Modifier.weight(1f, fill = false).then(credits))
                    } else {
                        OsmAttribution(modifier = credits)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                        modifier = Modifier.padding(bottom = MaterialTheme.spacing.small),
                    ) {
                        FitDayFab(onAction = onAction)
                        LocationFab(location = location, onAction = onAction)
                    }
                }
            },
            modifier = Modifier.weight(1f, fill = false),
        ) { shown ->
            // A route from A to B takes the stops' place until it's closed.
            when (shown) {
                is PointRouteCardState -> PointRouteCard(card = shown, onAction = onAction)
                is StopsCardState -> StopsCard(card = shown, onAction = onAction)
            }
        }
        // The day tabs stay at the bottom; everything else opens above them.
        DayTabs(tabs = dayTabs, onAction = onAction, modifier = Modifier.onSizeChanged { onTabsHeight(it.height) })
    }
}

@Preview
@Composable
private fun MapScreenPreview(@PreviewParameter(MapScreenPreviewStates::class) state: MapUiState) {
    TripinglyTheme {
        MapScreen(
            state = state,
            cameraCommands = emptyFlow(),
            onAction = {},
        )
    }
}
