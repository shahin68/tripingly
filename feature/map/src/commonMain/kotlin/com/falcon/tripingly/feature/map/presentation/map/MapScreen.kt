package com.falcon.tripingly.feature.map.presentation.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.LocalScreenArrived
import com.falcon.tripingly.core.designsystem.component.LocalScreenOnTop
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.SkeletonBox
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_retry
import com.falcon.tripingly.feature.map.presentation.component.CameraCommand
import com.falcon.tripingly.feature.map.presentation.component.GoogleMapView
import com.falcon.tripingly.feature.map.presentation.map.preview.MapScreenPreviewStates
import com.falcon.tripingly.feature.map.presentation.map.ui.BottomCards
import com.falcon.tripingly.feature.map.presentation.map.ui.JoinRequestsPill
import com.falcon.tripingly.feature.map.presentation.map.ui.PlaceFilterPill
import com.falcon.tripingly.feature.map.presentation.map.ui.ViewerJoinPill
import com.falcon.tripingly.feature.map.presentation.map.ui.MapButtons
import com.falcon.tripingly.feature.map.presentation.map.ui.MapCredits
import com.falcon.tripingly.feature.map.presentation.map.ui.SmallCreditsStyle
import com.falcon.tripingly.feature.map.presentation.map.ui.MapHeader
import com.falcon.tripingly.feature.map.presentation.map.ui.MapPinCards
import com.falcon.tripingly.feature.map.presentation.map.ui.PlanSheetPeek
import com.falcon.tripingly.feature.map.presentation.map.ui.PlanSheetValue
import com.falcon.tripingly.feature.map.presentation.map.ui.RouteBar
import com.falcon.tripingly.feature.map.presentation.map.ui.SearchResults
import com.falcon.tripingly.feature.map.presentation.map.ui.TripCard
import com.falcon.tripingly.feature.map.presentation.map.ui.TripCardScrim
import com.falcon.tripingly.feature.map.presentation.map.ui.TripTitleContent
import com.falcon.tripingly.feature.map.presentation.map.ui.animatePillGrowth
import com.falcon.tripingly.feature.map.presentation.map.ui.pulledUp
import com.falcon.tripingly.feature.map.presentation.map.ui.rememberPlanSheetState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.stringResource

/**
 * The trip's map with the header on top and the day's cards at the bottom. What they cover is the map's content
 * padding: the camera centers and fits within the rest, and the tapped pin's card opens just above its middle.
 * In landscape the header and the cards take the left half and the map's free part is the right half, like Google
 * Maps (Shahin, 2026-11-14), so the camera fits the stops there; the map buttons float at its bottom end, the credits
 * at its top end.
 */
@Composable
internal fun MapScreen(
    state: MapUiState,
    cameraCommands: Flow<CameraCommand>,
    onAction: (MapAction) -> Unit,
    planSheet: AnchoredDraggableState<PlanSheetValue> = rememberPlanSheetState(),
    /** The Route button's on/off, ahead of [state] (see MapViewModel.isDayRouteOn). */
    isDayRouteOn: Boolean = (state.plan as? StopsCardState)?.isRouteOn == true,
) {
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    // Layout only: what the header, the day tabs and the credits cover of the map, handed to it as its padding. Each is
    // measured when its size changes; the map waits for all three, so it opens already fitted to what's left.
    var headerHeight by remember { mutableStateOf<Dp?>(null) }
    // The header's own height, without the route bar over it: where the join pill sits.
    var titleHeight by remember { mutableIntStateOf(0) }
    // The tabs' height with whether there were days to show, so the map doesn't open on the height without tabs.
    var tabsHeight by remember { mutableStateOf<Pair<Boolean, Dp>?>(null) }
    var creditsHeight by remember { mutableStateOf<Dp?>(null) }
    // In portrait, the credits and map buttons riding on the card: the search results end above them.
    var buttonsHeight by remember { mutableStateOf(0.dp) }
    // Which way the map is turned, every frame it turns: read only by the compass while it draws.
    var bearing by remember { mutableFloatStateOf(0f) }
    // Where the open cards are, so a touch on them doesn't close them.
    var placeCardBounds by remember { mutableStateOf(Rect.Zero) }
    var stopCardBounds by remember { mutableStateOf(Rect.Zero) }
    var newSpotCardBounds by remember { mutableStateOf(Rect.Zero) }
    val isPlaceCardOpen by rememberUpdatedState(state.placeCard != null)
    val isStopCardOpen by rememberUpdatedState(state.stopCard != null)
    val isNewSpotCardOpen by rememberUpdatedState(state.newSpotCard != null)
    // The trip card that grows out of the title pill, and where that pill is on the screen.
    // Where the pill is inside the header's padding, which is where the card starts too.
    var isTripCardOpen by rememberSaveable { mutableStateOf(false) }
    var titleBounds by remember { mutableStateOf(Rect.Zero) }
    val tripCardState = remember { MutableTransitionState(isTripCardOpen) }
    tripCardState.targetState = isTripCardOpen
    val tripCard = rememberTransition(tripCardState, label = "trip card")
    val tripCardProgress by tripCard.animatePillGrowth()
    val isTripCardShown = tripCardState.currentState || tripCardState.targetState
    // Routing between two points, the route bar grows out of the title pill the same way, and back into it on close.
    val routeBarState = remember { MutableTransitionState(state.routeBar != null) }
    routeBarState.targetState = state.routeBar != null
    val routeBarTransition = rememberTransition(routeBarState, label = "route bar")
    val routeBarProgress by routeBarTransition.animatePillGrowth()
    // While it shrinks back, it keeps showing the ends it had.
    var shownRouteBar by remember { mutableStateOf(state.routeBar) }
    state.routeBar?.let { shownRouteBar = it }
    val isRouteBarShown = routeBarState.currentState || routeBarState.targetState
    // Back closes the trip card, else the search (it has no back button: back does what its ✕ does). Not while
    // another screen covers the map: back closes that screen.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = (isTripCardOpen || state.search != null) && LocalScreenOnTop.current,
        onBackCompleted = { if (isTripCardOpen) isTripCardOpen = false else onAction(MapAction.Search.Close) },
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Any touch outside the open card closes it, and still reaches the map and the controls.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val isOnCard = (isPlaceCardOpen && placeCardBounds.contains(down.position)) ||
                        (isStopCardOpen && stopCardBounds.contains(down.position)) ||
                        (isNewSpotCardOpen && newSpotCardBounds.contains(down.position))
                    if (!isOnCard) onAction(MapAction.MapView.ScreenTouch)
                }
            }
    ) {
        val plan = state.plan
        // The pulled-up sheet doesn't count: only the part that always shows.
        val hasDays = state.dayTabs.days.isNotEmpty()
        val coveredBottom = (tabsHeight?.second ?: 0.dp) + (if (plan != null) PlanSheetPeek else 0.dp) + (creditsHeight ?: 0.dp)
        val layout = rememberMapPanelLayout(headerHeight ?: 0.dp, coveredBottom) { onAction(MapAction.MapView.LayoutChange) }
        val isWide = layout.isWide
        val coveredTop = layout.coveredTop
        val covered = layout.covered
        val pinLowering = layout.pinLowering
        val panel = layout.panel
        // Not before the camera knows where the trip is: the map opens there, fitted to the open day, instead of
        // starting somewhere and then fitting.
        // A fit frames the bounds in the map that shows right now: above the sheet as far as it's pulled up.
        val fit by rememberUpdatedState { bounds: GeoBounds ->
            layout.fit(bounds, pulledUp = if (plan != null && !isWide) with(density) { planSheet.pulledUp().toDp() } else 0.dp)
        }
        val moves = rememberCameraMoves(cameraCommands) { fit }
        // And not while the screen still slides in: building the native map then skips frames (Shahin, 2026-11-08).
        val isMapShown = state.camera.isReady && headerHeight != null && tabsHeight?.first == hasDays && creditsHeight != null &&
            LocalScreenArrived.current
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
                contentPadding = layout.cameraPadding,
                // In landscape the camera's padding keeps a tapped pin low down, which would leave a held pin only a
                // strip to rest in; it pulls the camera near the free half's edges instead (Shahin, 2026-11-14).
                freePadding = if (isWide) covered else layout.cameraPadding,
                layers = state.layers,
                onCameraMove = { coords, zoom, bounds -> onAction(MapAction.MapView.CameraRest(coords, zoom, bounds)) },
                onCameraMoving = { bounds, zoom -> onAction(MapAction.MapView.CameraMoving(bounds, zoom)) },
                onBearingChange = { bearing = it },
                onMapTouch = { onAction(MapAction.MapView.ScreenTouch) },
                onUserMove = { onAction(MapAction.MapView.UserMove) },
                onMapClick = { coords -> onAction(MapAction.MapView.Click(coords)) },
                onPlacePinClick = { place -> onAction(MapAction.MapView.PlacePinClick(place)) },
                onClusterClick = { cluster -> onAction(MapAction.MapView.ClusterClick(cluster)) },
                onMapLongClick = { coords -> onAction(MapAction.MapView.LongClick(coords)) },
                onMarkerClick = { marker -> onAction(MapAction.MapView.MarkerClick(marker)) },
                onPinDrop = { from, to -> onAction(MapAction.MapView.PinDrop(from, to)) },
                onAroundRadius = { meters, isFinal -> onAction(MapAction.Around.SetRadius(meters, isFinal)) },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .then(panel)
        ) {
            // The route bar over the header it pushes away: the taller of the two is what the header covers.
            Box(Modifier.zIndex(1f).onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) {
                MapHeader(
                    modifier = Modifier.onSizeChanged { titleHeight = it.height },
                    header = state.header,
                    // While routing, the search is the route bar's "to".
                    search = state.search.takeUnless { isRouteBarShown },
                    onAction = onAction,
                    isTitleHidden = isTripCardShown || isRouteBarShown,
                    onTitleClick = {
                        haptics.tick()
                        isTripCardOpen = true
                    },
                    onTitlePlaced = { titleBounds = it },
                    cardProgress = { maxOf(tripCardProgress, routeBarProgress) },
                )
                val routeBar = shownRouteBar
                if (isRouteBarShown && routeBar != null) {
                    RouteBar(
                        bar = routeBar,
                        search = state.search,
                        transition = routeBarTransition,
                        progress = { routeBarProgress },
                        pill = titleBounds,
                        pillContent = { TripTitleContent(header = state.header) },
                        onAction = onAction,
                        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
                    )
                }
            }
            // The join pill under the header (the search results go over it, further down, so neither moves the
            // other). The pill stays under the header's own part, under the route bar coming over it
            // (not pushed down by it), and scales and fades away under the route bar or the search (Shahin, 2026-11-16);
            // while it goes it keeps the people it had.
            Box(Modifier.fillMaxWidth()) {
                var shownJoinRequests by remember { mutableStateOf(state.joinRequests) }
                state.joinRequests?.let { shownJoinRequests = it }
                // The filter circle mirrors the join pill at the end, at the same height, and opens up to the join pill
                // with a small gap (Shahin, 2026-11-17); it scales away while a route, Around or the route bar shows,
                // keeping its last look while it goes.
                // The join pill's space glides open and shut with it, the gap too, so the filter beside it glides along.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, titleHeight - with(density) { (headerHeight ?: 0.dp).roundToPx() }) }
                        .padding(horizontal = MaterialTheme.spacing.medium),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.joinRequests != null && !isRouteBarShown,
                        enter = expandHorizontally(expandFrom = Alignment.Start, clip = false) + scaleIn() + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start, clip = false) + scaleOut() + fadeOut(),
                    ) {
                        shownJoinRequests?.let { requests ->
                            JoinRequestsPill(
                                requests = requests,
                                onClick = { onAction(MapAction.Header.OpenJoinRequests) },
                                onLongClick = { onAction(MapAction.Header.ShowJoinRequests) },
                                onSeen = { onAction(MapAction.Header.JoinRequestsSeen) },
                                modifier = Modifier.padding(end = MaterialTheme.spacing.small),
                            )
                        }
                    }
                    // The row's weight is the most it may grow; closed it is only its circle, at the end. It scales away and
                    // back where it stands, never gliding in from the side (Shahin, 2026-11-19).
                    val filter = state.placeFilter.takeUnless { isRouteBarShown }
                    var shownFilter by remember { mutableStateOf(filter) }
                    filter?.let { shownFilter = it }
                    // Someone else's trip: the heart and Ask to join at the end, where members have the filter, as high as
                    // the header (Shahin, 2026-11-22), scaling in and away where it stands as the filter does, keeping
                    // its last look while it goes (accepted or declined); away while searching or routing.
                    val joinButton = state.header.viewerMenu?.joinButton
                    var shownViewerMenu by remember { mutableStateOf(state.header.viewerMenu) }
                    if (joinButton != null) shownViewerMenu = state.header.viewerMenu
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
                        androidx.compose.animation.AnimatedVisibility(visible = filter != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                            shownFilter?.let { PlaceFilterPill(filter = it, onAction = onAction) }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = joinButton != null && !isRouteBarShown && state.search == null,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut(),
                        ) {
                            shownViewerMenu?.let { menu ->
                                menu.joinButton?.let { ViewerJoinPill(joinButton = it, isLikedByMe = menu.isLikedByMe, onAction = onAction) }
                            }
                        }
                    }
                }
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
            isOsmCreditShown = state.isOsmCreditShown,
            location = state.location,
            arePlacesHidden = state.arePlacesHidden,
            canHidePlaces = state.canHidePlaces,
            bearing = { bearing },
            dayTabs = state.dayTabs,
            plan = plan,
            isDayRouteOn = isDayRouteOn,
            editorMenu = state.header.editorMenu,
            planSheet = planSheet,
            isWide = isWide,
            onAction = onAction,
            onTabsHeight = { tabsHeight = hasDays to with(density) { it.toDp() } },
            onCreditsHeight = { creditsHeight = with(density) { it.toDp() } },
            onButtonsHeight = { buttonsHeight = with(density) { it.toDp() } },
            // Only the header limits the sheet: opened, it goes over the join pill and anything else under the header.
            modifier = Modifier.align(Alignment.BottomStart).then(panel).padding(top = coveredTop),
        )

        // In landscape the map's credits and buttons sit on the map half, so the plan sheet has the left half's whole
        // height (Shahin, 2026-11-09, option 1); in portrait they ride on the sheet. The buttons float at the map's
        // bottom end, their bottom in line with the day tabs' card (the free part's bottom), the credits a little
        // smaller at its top end (Shahin, 2026-11-14); both 16 dp in from the screen's end (or its cutout).
        if (isWide) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(covered)
                    .padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.small, end = MaterialTheme.spacing.medium),
            ) {
                MapCredits(
                    routeAttribution = state.routeAttribution,
                    isOsmCreditShown = state.isOsmCreditShown,
                    style = SmallCreditsStyle,
                    atEnd = true,
                    onOpenHeight = { creditsHeight = with(density) { it.toDp() } },
                    modifier = Modifier.align(Alignment.TopEnd),
                )
                MapButtons(state.location, state.arePlacesHidden, state.canHidePlaces, { bearing }, onAction, Modifier.align(Alignment.BottomEnd))
            }
        }

        MapPinCards(
            placeCard = state.placeCard,
            stopCard = state.stopCard,
            newSpotCard = state.newSpotCard,
            pinBelowMiddle = pinLowering / 2,
            onAction = onAction,
            onPlaceCardBounds = { placeCardBounds = it },
            onStopCardBounds = { stopCardBounds = it },
            onNewSpotCardBounds = { newSpotCardBounds = it },
            modifier = Modifier.fillMaxSize().padding(covered),
        )

        // The search results over every other part of the map screen (only the trip card goes over them), under the
        // header. The plan sheet folds down while they show, and they reach down to just above it: in portrait above
        // the map buttons riding on it too, in landscape (the buttons on the map half) to the sheet itself (Shahin,
        // 2026-11-20).
        val results = state.search?.results?.takeIf { it.isNotEmpty() }
        val hasResults = results != null
        LaunchedEffect(hasResults) { if (hasResults) planSheet.animateTo(PlanSheetValue.Collapsed) }
        // Opens growing down out of its top edge and closes folding back up, like the trip card but only vertically
        // (Shahin, 2026-11-21): the glass itself grows ([SearchResults]' reveal), nothing fades or scales. While it
        // folds up the results are gone from the state, so it keeps showing the last ones.
        val lastResults = remember { mutableStateOf<ImmutableList<SearchResultItem>>(persistentListOf()) }
        if (results != null) lastResults.value = results
        AnimatedVisibility(
            visible = results != null,
            modifier = Modifier.align(Alignment.TopStart).padding(top = coveredTop).then(panel),
            // No motion of their own: they only keep the card for as long as its growth runs.
            enter = slideInVertically(SearchResultsHold) { 0 },
            exit = slideOutVertically(SearchResultsHold) { 0 },
        ) {
            val reveal = transition.animateFloat(transitionSpec = { tween(SEARCH_RESULTS_MILLIS, easing = SearchResultsEasing) }, label = "search results") { state ->
                if (state == EnterExitState.Visible) 1f else 0f
            }
            val belowResults = (tabsHeight?.second ?: 0.dp) + (if (plan != null) PlanSheetPeek else 0.dp) +
                (if (isWide) 0.dp else buttonsHeight) + MaterialTheme.spacing.small
            SearchResults(
                results = lastResults.value,
                isOsmCreditShown = state.isOsmCreditShown,
                onAction = onAction,
                maxHeight = (maxHeight - coveredTop - belowResults).coerceAtLeast(SearchResultsMinHeight),
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                reveal = { reveal.value },
            )
        }

        // Over everything: the dimmed map and the trip card grown out of the title, in the header's place.
        if (isTripCardShown) {
            TripCardScrim(progress = { tripCardProgress }, onClose = { isTripCardOpen = false })
            TripCard(
                header = state.header,
                transition = tripCard,
                progress = { tripCardProgress },
                pill = titleBounds,
                onClose = { isTripCardOpen = false },
                onOwnerClick = {
                    isTripCardOpen = false
                    onAction(MapAction.Header.OpenOwnerProfile)
                },
                onMembersClick = {
                    isTripCardOpen = false
                    onAction(MapAction.Header.OpenJoinRequests)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .then(panel)
                    .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
            )
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

/** The search results card growing open and folding shut: the trip card's decelerate, fast at first, easing into place. */
private const val SEARCH_RESULTS_MILLIS = 300
private val SearchResultsEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val SearchResultsHold = tween<IntOffset>(SEARCH_RESULTS_MILLIS)

/** The least the search results get, however little room the screen leaves them. */
private val SearchResultsMinHeight = 120.dp
