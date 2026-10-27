package com.falcon.tripingly.feature.map.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.ErrorScreen
import com.falcon.tripingly.core.designsystem.component.GlassIconButton
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.SkeletonBox
import com.falcon.tripingly.core.designsystem.component.safeSides
import com.falcon.tripingly.core.designsystem.component.safeTopAndSides
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_back
import com.falcon.tripingly.feature.map.generated.resources.map_retry
import com.falcon.tripingly.feature.map.presentation.component.CameraCommand
import com.falcon.tripingly.feature.map.presentation.component.GoogleMapView
import com.falcon.tripingly.feature.map.presentation.component.SelectedPinSize
import com.falcon.tripingly.feature.map.presentation.component.fittedCamera
import com.falcon.tripingly.feature.map.presentation.component.stopPinRadius
import com.falcon.tripingly.feature.map.presentation.map.preview.MapScreenPreviewStates
import com.falcon.tripingly.feature.map.presentation.map.ui.BottomCards
import com.falcon.tripingly.feature.map.presentation.map.ui.MapHeader
import com.falcon.tripingly.feature.map.presentation.map.ui.PinCard
import com.falcon.tripingly.feature.map.presentation.map.ui.PlaceCard
import com.falcon.tripingly.feature.map.presentation.map.ui.PlanSheetPeek
import com.falcon.tripingly.feature.map.presentation.map.ui.PlanSheetValue
import com.falcon.tripingly.feature.map.presentation.map.ui.SearchResults
import com.falcon.tripingly.feature.map.presentation.map.ui.StopCard
import com.falcon.tripingly.feature.map.presentation.map.ui.pulledUp
import com.falcon.tripingly.feature.map.presentation.map.ui.rememberPlanSheetState
import com.falcon.tripingly.feature.map.presentation.photos.rememberPhotoPicker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource

/**
 * The trip's map with the header on top and the day's cards at the bottom. What they cover is the map's content
 * padding: the camera centers and fits within the rest, and the tapped pin's card opens just above its middle.
 * In landscape the header and the cards take the right half and the map's free part is the left half (the other
 * way round from Google Maps), so the camera fits the stops there.
 */
@Composable
internal fun MapScreen(
    state: MapUiState,
    cameraCommands: Flow<CameraCommand>,
    onAction: (MapAction) -> Unit,
    planSheet: AnchoredDraggableState<PlanSheetValue> = rememberPlanSheetState(),
) {
    state.loadError?.let { error -> return MapLoadError(error.asString(), onAction) }
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
                    if (!isOnCard) onAction(MapAction.MapView.ScreenTouch)
                }
            }
    ) {
        val plan = state.plan
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val coveredTop = statusBar + (headerHeight ?: 0.dp)
        // The pulled-up sheet doesn't count: only the part that always shows.
        val hasDays = state.dayTabs.days.isNotEmpty()
        val coveredBottom = (tabsHeight?.second ?: 0.dp) + (if (plan != null) PlanSheetPeek else 0.dp) + (creditsHeight ?: 0.dp)
        val isWide = maxWidth > maxHeight
        val side = WindowInsets.safeSides.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr)
        val panelWidth = (maxWidth - side * 2) / 2
        // What the header and the cards cover: the top and the bottom, or in landscape the right half.
        val covered = if (isWide) {
            PaddingValues(start = side, top = statusBar, end = side + panelWidth)
        } else {
            PaddingValues(top = coveredTop, bottom = coveredBottom)
        }
        // The header and the cards: full width, or the right half in landscape; never under the camera cutout.
        val panel = Modifier.windowInsetsPadding(WindowInsets.safeSides).then(if (isWide) Modifier.width(panelWidth) else Modifier.fillMaxWidth())
        // Not before the camera knows where the trip is: the map opens there, fitted to the open day, instead of
        // starting somewhere and then fitting.
        // A fit frames the bounds in the map that shows right now: above the sheet as far as it's pulled up.
        val fit by rememberUpdatedState { bounds: GeoBounds ->
            val pulledUp = if (plan != null && !isWide) with(density) { planSheet.pulledUp().toDp() } else 0.dp
            val width = maxWidth - covered.calculateLeftPadding(LayoutDirection.Ltr) - covered.calculateRightPadding(LayoutDirection.Ltr)
            val height = maxHeight - covered.calculateTopPadding() - covered.calculateBottomPadding() - pulledUp
            fittedCamera(bounds, width, height, raise = pulledUp / 2)
        }
        val moves = remember(cameraCommands) {
            cameraCommands.map { command ->
                when (command) {
                    is CameraCommand.MoveTo -> command
                    is CameraCommand.Fit -> fit(command.bounds).let { (target, zoom) -> CameraCommand.MoveTo(target, zoom) }
                }
            }
        }
        val isMapShown = state.camera.isReady && headerHeight != null && tabsHeight?.first == hasDays && creditsHeight != null
        // The map's place while the trip loads: no flash of an empty default map before it opens on the trip.
        if (!isMapShown) SkeletonBox(Modifier.fillMaxSize(), shape = RectangleShape)
        if (isMapShown) {
            // Only where the map starts: worked out once, when it first shows.
            val (target, zoom) = remember { state.camera.let { it.openingBounds?.let(fit) ?: (it.target to it.zoom) } }
            GoogleMapView(
                modifier = Modifier.fillMaxSize(),
                initialTarget = target,
                initialZoom = zoom,
                cameraCommands = moves,
                contentPadding = covered,
                layers = state.layers,
                onCameraMove = { coords, zoom, bounds -> onAction(MapAction.MapView.CameraRest(coords, zoom, bounds)) },
                onMapTouch = { onAction(MapAction.MapView.ScreenTouch) },
                onMapClick = { coords -> onAction(MapAction.MapView.Click(coords)) },
                onPlacePinClick = { place -> onAction(MapAction.MapView.PlacePinClick(place)) },
                onClusterClick = { cluster -> onAction(MapAction.MapView.ClusterClick(cluster)) },
                onMapLongClick = { coords -> onAction(MapAction.MapView.LongClick(coords)) },
                onMarkerClick = { marker -> onAction(MapAction.MapView.MarkerClick(marker)) },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .then(panel)
        ) {
            MapHeader(
                header = state.header,
                search = state.search,
                showDayActions = plan == null,
                onAction = onAction,
                modifier = Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
            )
            state.search?.results?.takeIf { it.isNotEmpty() }?.let { results ->
                SearchResults(results = results, onAction = onAction, modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium))
            }
        }

        // Retry sends every stop change that failed for a connection problem, earlier ones too.
        val message = state.message
        ShowMessage(
            message = message?.text?.asString(),
            onShown = { onAction(MapAction.Message.Shown) },
            isError = message?.text !is UiText.Note,
            actionLabel = if (message?.canRetry == true) stringResource(Res.string.map_retry) else null,
            onAction = if (message?.canRetry == true) ({ onAction(MapAction.Message.RetryFailedChanges) }) else null,
        )

        BottomCards(
            routeAttribution = state.routeAttribution,
            location = state.location,
            routeStartName = state.routeStartName,
            dayTabs = state.dayTabs,
            plan = plan,
            editorMenu = state.header.editorMenu,
            planSheet = planSheet,
            onAction = onAction,
            onTabsHeight = { tabsHeight = hasDays to with(density) { it.toDp() } },
            onCreditsHeight = { creditsHeight = with(density) { it.toDp() } },
            modifier = Modifier.align(Alignment.BottomEnd).then(panel).padding(top = coveredTop),
        )

        // The part of the map nothing covers: its middle is where the camera puts a tapped pin.
        Box(modifier = Modifier.fillMaxSize().padding(covered)) {
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
                    onAdd = { onAction(MapAction.PlaceCard.AddToDay) },
                    onOpenImage = { index -> onAction(MapAction.PlaceCard.OpenImage(index)) },
                    onOpenPhotos = { onAction(MapAction.PlaceCard.OpenPhotos) },
                    onRouteFrom = { onAction(MapAction.PlaceCard.RouteFrom) },
                    onRouteTo = { onAction(MapAction.PlaceCard.RouteTo) }.takeIf { card.canRouteTo },
                )
            }
            val pickPhotos = rememberPhotoPicker { images -> onAction(MapAction.StopCard.AddPhotos(images)) }
            PinCard(
                card = state.stopCard,
                key = { it.stop.id },
                pinClearance = stopPinRadius(hasCover = state.stopCard?.stop?.coverPhotoId != null) + MaterialTheme.spacing.small,
                modifier = Modifier.onGloballyPositioned { stopCardBounds = it.boundsInRoot() },
            ) { card ->
                StopCard(
                    marker = card.stop,
                    coverCredit = card.coverCredit,
                    canEdit = card.canEdit,
                    onOpen = { onAction(MapAction.StopCard.OpenPhotos) },
                    onAddPhotos = pickPhotos,
                    onTakePhoto = { onAction(MapAction.StopCard.OpenCamera) },
                    onToggleHot = { onAction(MapAction.StopCard.ToggleHot(card.stop.id)) },
                    onOpenComments = { onAction(MapAction.StopCard.OpenComments) },
                    canReport = card.canReport,
                    onReport = { onAction(MapAction.StopCard.Report) },
                    onRemoveMyContent = { onAction(MapAction.StopCard.RemoveMyContent) }.takeIf { card.canRemoveMyContent },
                )
            }
        }
    }
}

/** The trip couldn't load: the error with Retry, and the back button where the header has it. */
@Composable
private fun MapLoadError(message: String, onAction: (MapAction) -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ErrorScreen(onRetry = { onAction(MapAction.Header.Retry) }, message = message, modifier = Modifier.safeDrawingPadding())
        GlassIconButton(
            onClick = { onAction(MapAction.Header.Back) },
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeTopAndSides).padding(MaterialTheme.spacing.medium),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.map_back))
        }
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

@Preview
@Composable
private fun MapScreenDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        MapScreen(state = MapScreenPreviewStates().values.first(), cameraCommands = emptyFlow(), onAction = {})
    }
}

/** The trip couldn't load and there's none on the device. */
@Preview
@Composable
private fun MapScreenLoadErrorPreview() {
    TripinglyTheme(darkTheme = true) {
        MapScreen(state = MapUiState(loadError = UiText.Server("No internet connection.")), cameraCommands = emptyFlow(), onAction = {})
    }
}
