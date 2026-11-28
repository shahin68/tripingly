package com.falcon.tripingly.feature.map.presentation.spot

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.falcon.tripingly.core.designsystem.component.ErrorScreen
import com.falcon.tripingly.core.designsystem.component.LocalScreenArrived
import com.falcon.tripingly.core.designsystem.component.LocalScreenOnTop
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.SkeletonBox
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.presentation.component.CameraCommand
import com.falcon.tripingly.feature.map.presentation.component.GoogleMapView
import com.falcon.tripingly.feature.map.presentation.component.SelectedPinSize
import com.falcon.tripingly.feature.map.presentation.map.DayTabsState
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.rememberCameraMoves
import com.falcon.tripingly.feature.map.presentation.map.rememberMapPanelLayout
import com.falcon.tripingly.feature.map.presentation.map.ui.BottomCards
import com.falcon.tripingly.feature.map.presentation.map.ui.MapButtons
import com.falcon.tripingly.feature.map.presentation.map.ui.MapCredits
import com.falcon.tripingly.feature.map.presentation.map.ui.PinCard
import com.falcon.tripingly.feature.map.presentation.map.ui.PlaceViewCard
import com.falcon.tripingly.feature.map.presentation.map.ui.PlanSheetPeek
import com.falcon.tripingly.feature.map.presentation.map.ui.PlanSheetValue
import com.falcon.tripingly.feature.map.presentation.map.ui.SmallCreditsStyle
import com.falcon.tripingly.feature.map.presentation.map.ui.TripCardScrim
import com.falcon.tripingly.feature.map.presentation.map.ui.animatePillGrowth
import com.falcon.tripingly.feature.map.presentation.map.ui.pulledUp
import com.falcon.tripingly.feature.map.presentation.map.ui.rememberPlanSheetState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The spot's map, laid out like the trip map ([rememberMapPanelLayout]): the header on top, the pin with its card in
 * the middle of what's free, and the bottom card with Go here's route or Around in its sheet over the spot's bar. In
 * landscape the header and the bottom card take the left half and the map's free part is the right half, with the
 * credits and the map buttons on it. [onAction]: the spot map's own buttons; [onMapAction]: the parts it shares with the
 * trip map.
 */
@Composable
internal fun SpotMapScreen(
    state: SpotMapUiState,
    cameraCommands: Flow<CameraCommand>,
    onAction: (SpotMapAction) -> Unit,
    onMapAction: (MapAction) -> Unit,
    planSheet: AnchoredDraggableState<PlanSheetValue> = rememberPlanSheetState(),
) {
    val map = state.map
    val spot = state.spot
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    // Layout only: what the header, the bar and the credits cover of the map, handed to it as its padding. The map
    // waits for all three, so it opens already fitted to what's left.
    var headerHeight by remember { mutableStateOf<Dp?>(null) }
    // The bar's height with whether the spot was there to show it, so the map doesn't open on the height without it.
    var barHeight by remember { mutableStateOf<Pair<Boolean, Dp>?>(null) }
    var creditsHeight by remember { mutableStateOf<Dp?>(null) }
    var bearing by remember { mutableFloatStateOf(0f) }
    // Where the open card is, so a touch on it doesn't close it.
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    val isCardOpen by rememberUpdatedState(map.placeCard != null)
    // The spot's card that grows out of its pill, as the trip card does out of the trip's.
    var isSpotCardOpen by rememberSaveable { mutableStateOf(false) }
    var titleBounds by remember { mutableStateOf(Rect.Zero) }
    // Where the back button is: tapping it leaves the map, so it closes no card (no close animation on a leaving screen).
    var backBounds by remember { mutableStateOf(Rect.Zero) }
    val spotCardState = remember { MutableTransitionState(isSpotCardOpen) }
    spotCardState.targetState = isSpotCardOpen && spot != null
    val spotCard = rememberTransition(spotCardState, label = "spot card")
    val spotCardProgress by spotCard.animatePillGrowth()
    val isSpotCardShown = spotCardState.currentState || spotCardState.targetState
    // Back closes the card first; not while another screen covers the map: back closes that screen.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isSpotCardOpen && LocalScreenOnTop.current,
        onBackCompleted = { isSpotCardOpen = false },
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Any touch outside the open card closes it, and still reaches the map and the controls.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val isOnCard = isCardOpen && cardBounds.contains(down.position)
                    if (!isOnCard && !backBounds.contains(down.position)) onMapAction(MapAction.MapView.ScreenTouch)
                }
            },
    ) {
        val plan = map.plan
        // The pulled-up sheet doesn't count: only the part that always shows.
        val coveredBottom = (barHeight?.second ?: 0.dp) + (if (plan != null) PlanSheetPeek else 0.dp) + (creditsHeight ?: 0.dp)
        val layout = rememberMapPanelLayout(headerHeight ?: 0.dp, coveredBottom) { onMapAction(MapAction.MapView.LayoutChange) }
        val fit by rememberUpdatedState { bounds: GeoBounds ->
            layout.fit(bounds, pulledUp = if (plan != null && !layout.isWide) with(density) { planSheet.pulledUp().toDp() } else 0.dp)
        }
        val moves = rememberCameraMoves(cameraCommands) { fit }
        // Not before the spot is known: the map opens on it. And not while the screen still slides in: building the
        // native map then skips frames.
        val isMapShown = map.camera.isReady && headerHeight != null && barHeight?.first == (spot != null) && creditsHeight != null &&
            LocalScreenArrived.current
        if (!isMapShown) SkeletonBox(Modifier.fillMaxSize(), shape = RectangleShape)
        if (isMapShown) {
            // Only where the map starts: worked out once, when it first shows.
            val (target, zoom) = remember { map.camera.let { it.openingBounds?.let(fit) ?: (it.target to it.zoom) } }
            GoogleMapView(
                modifier = Modifier.fillMaxSize(),
                initialTarget = target,
                initialZoom = zoom,
                cameraCommands = moves,
                contentPadding = layout.cameraPadding,
                freePadding = if (layout.isWide) layout.covered else layout.cameraPadding,
                layers = map.layers,
                onCameraMove = { coords, zoom, bounds -> onMapAction(MapAction.MapView.CameraRest(coords, zoom, bounds)) },
                onCameraMoving = { _, _ -> },
                onBearingChange = { bearing = it },
                onMapTouch = { onMapAction(MapAction.MapView.ScreenTouch) },
                onUserMove = { onMapAction(MapAction.MapView.UserMove) },
                onMapClick = {},
                onPlacePinClick = { place -> onMapAction(MapAction.MapView.PlacePinClick(place)) },
                onClusterClick = {},
                onMapLongClick = {},
                onMarkerClick = {},
                onPinDrop = { _, _ -> },
                onAroundRadius = { meters, isFinal -> onMapAction(MapAction.Around.SetRadius(meters, isFinal)) },
            )
        }
        // The spot couldn't load: the error in the map's place, under the header and its back button.
        state.loadError?.takeIf { spot == null }?.let { error ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                ErrorScreen(onRetry = { onAction(SpotMapAction.Retry) }, message = error.asString())
            }
        }

        SpotHeader(
            spot = spot,
            onAction = onAction,
            isTitleHidden = isSpotCardShown,
            onTitleClick = {
                haptics.tick()
                isSpotCardOpen = true
            },
            onTitlePlaced = { titleBounds = it },
            onBackPlaced = { backBounds = it },
            cardProgress = { spotCardProgress },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .then(layout.panel)
                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
        )

        val message = map.message
        ShowMessage(message = message?.text?.asString(), onShown = { onMapAction(MapAction.Message.Shown) }, isError = message?.text !is UiText.Note)

        BottomCards(
            routeAttribution = map.routeAttribution,
            isOsmCreditShown = map.isOsmCreditShown,
            location = map.location,
            arePlacesHidden = false,
            canHidePlaces = false,
            bearing = { bearing },
            dayTabs = DayTabsState(),
            plan = plan,
            isDayRouteOn = false,
            editorMenu = null,
            planSheet = planSheet,
            isWide = layout.isWide,
            onAction = onMapAction,
            onTabsHeight = { barHeight = (spot != null) to with(density) { it.toDp() } },
            onCreditsHeight = { creditsHeight = with(density) { it.toDp() } },
            // Only the header limits the sheet.
            modifier = Modifier.align(Alignment.BottomStart).then(layout.panel).padding(top = layout.coveredTop),
            bottomBar = if (spot != null) {
                { SpotBar(spot, onAction) }
            } else {
                null
            },
        )

        // In landscape the credits and the map buttons sit on the map half, as on the trip map.
        if (layout.isWide) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(layout.covered)
                    .padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.small, end = MaterialTheme.spacing.medium),
            ) {
                MapCredits(
                    routeAttribution = map.routeAttribution,
                    isOsmCreditShown = map.isOsmCreditShown,
                    style = SmallCreditsStyle,
                    atEnd = true,
                    onOpenHeight = { creditsHeight = with(density) { it.toDp() } },
                    modifier = Modifier.align(Alignment.TopEnd),
                )
                MapButtons(map.location, arePlacesHidden = false, canHidePlaces = false, bearing = { bearing }, onAction = onMapAction, modifier = Modifier.align(Alignment.BottomEnd))
            }
        }

        // One card at a time over the pin in the middle: the spot's, or a place's found around it (view only).
        Box(Modifier.fillMaxSize().padding(layout.covered)) {
            PinCard(
                card = map.placeCard,
                key = { it.place.id to it.place.location },
                pinClearance = SelectedPinSize / 2 + MaterialTheme.spacing.small,
                pinBelowMiddle = layout.pinLowering / 2,
                // Where it really is: it's lifted above the pin, out of its own layout.
                modifier = Modifier.onGloballyPositioned { cardBounds = Rect(it.positionInRoot(), it.size.toSize()) },
            ) { card ->
                PlaceViewCard(
                    card = card,
                    onOpenImage = { index -> onMapAction(MapAction.PlaceCard.OpenImage(index)) },
                    onOpenPhotos = { onMapAction(MapAction.PlaceCard.OpenPhotos) },
                    // The spot's flame is in the header.
                    showsHot = card.place.id != spot?.id,
                )
            }
        }

        // Over everything: the dimmed map and the spot's card grown out of its pill, in the header's place.
        if (isSpotCardShown && spot != null) {
            TripCardScrim(progress = { spotCardProgress }, onClose = { isSpotCardOpen = false })
            SpotCard(
                spot = spot,
                transition = spotCard,
                progress = { spotCardProgress },
                pill = titleBounds,
                onClose = { isSpotCardOpen = false },
                onOwnerClick = {
                    isSpotCardOpen = false
                    onAction(SpotMapAction.OpenOwner)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .then(layout.panel)
                    .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
            )
        }
    }
}

private val PreviewSpot = SpotMapUiState(
    spot = SpotState(
        id = "p-1",
        name = "Café Central",
        category = PlaceCategory("cafe"),
        owner = null,
        isOwnerMe = false,
        isFromOsm = true,
        address = "Herrengasse 14, Wien",
        distanceMeters = 650,
        isHot = true,
        hotCount = 42,
        tripCount = 7,
        openingHours = "Mo-Sa 08:00-21:00",
        website = "cafecentral.wien",
    ),
    isLoading = false,
)

@Preview
@Composable
private fun SpotMapScreenPreview() {
    TripinglyTheme {
        SpotMapScreen(state = PreviewSpot, cameraCommands = emptyFlow(), onAction = {}, onMapAction = {})
    }
}

@Preview
@Composable
private fun SpotMapScreenDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        SpotMapScreen(state = PreviewSpot, cameraCommands = emptyFlow(), onAction = {}, onMapAction = {})
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun SpotMapScreenLandscapePreview() {
    TripinglyTheme {
        SpotMapScreen(state = PreviewSpot, cameraCommands = emptyFlow(), onAction = {}, onMapAction = {})
    }
}
