package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
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
import com.falcon.tripingly.feature.map.presentation.component.StopCard
import com.falcon.tripingly.feature.map.presentation.component.stopPinHeight
import com.falcon.tripingly.feature.map.presentation.photos.rememberPhotoPicker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The trip's map with the header on top and the day's cards at the bottom. What they cover is the map's content
 * padding: the camera centers and fits within the rest, and the tapped pin's card opens just above its middle.
 */
@Composable
fun MapScreen(
    state: MapUiState,
    cameraCommands: Flow<CameraCommand>,
    onAction: (MapAction) -> Unit,
) {
    val density = LocalDensity.current
    // Layout only: how much of the map the header and the bottom cards cover, handed to the map as its padding.
    var coveredTop by remember { mutableStateOf(0.dp) }
    var coveredBottom by remember { mutableStateOf(0.dp) }
    // Where the open cards are, so a touch on them doesn't close them.
    var placeCardBounds by remember { mutableStateOf(Rect.Zero) }
    var stopCardBounds by remember { mutableStateOf(Rect.Zero) }
    val isPlaceCardOpen by rememberUpdatedState(state.placeCard != null)
    val isStopCardOpen by rememberUpdatedState(state.stopCard != null)

    Box(
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
        // Not before the camera knows where the trip is: the map opens right there instead of flying from Paris.
        if (state.camera.isReady) {
            val layers = state.layers
            GoogleMapView(
                modifier = Modifier.fillMaxSize(),
                initialTarget = state.camera.target,
                initialZoom = state.camera.zoom,
                cameraCommands = cameraCommands,
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
                modifier = Modifier.onGloballyPositioned { coveredTop = with(density) { it.boundsInRoot().bottom.toDp() } },
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
            state = state,
            onAction = onAction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { coveredBottom = with(density) { it.height.toDp() } },
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

/** The credits and the location button, the route-start chip, the day tabs and the open day's card. */
@Composable
private fun BottomCards(state: MapUiState, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.End,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Our places come from OpenStreetMap, routes from openrouteservice too.
            val attributionBackground = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            val routeAttribution = state.routeAttribution
            if (routeAttribution != null) {
                RouteAttribution(routeAttribution, modifier = Modifier.weight(1f, fill = false).then(attributionBackground))
            } else {
                OsmAttribution(modifier = attributionBackground)
            }
            Spacer(modifier = Modifier.weight(1f))
            LocationFab(location = state.location, onAction = onAction)
        }

        state.routeStartName?.let { name ->
            RouteStartChip(name, onAction, Modifier.align(Alignment.Start).padding(horizontal = MaterialTheme.spacing.medium))
        }

        DayTabs(tabs = state.dayTabs, onAction = onAction)

        val cardPadding = Modifier.padding(horizontal = MaterialTheme.spacing.medium).padding(bottom = MaterialTheme.spacing.medium)
        // Each card slides in and out, showing what it last showed while it leaves; while shown, it only updates.
        SlidingCard(card = state.stopsCard, key = { true }) { StopsCard(card = it, onAction = onAction, modifier = cardPadding) }
        // A route from A to B takes the stops' place until it's closed.
        SlidingCard(card = state.pointRouteCard, key = { it.from to it.to }) { PointRouteCard(card = it, onAction = onAction, modifier = cardPadding) }
    }
}

@Composable
private fun <T : Any> SlidingCard(card: T?, key: (T) -> Any, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = card,
        contentKey = { it?.let(key) },
        transitionSpec = { (fadeIn() + slideInVertically { it }) togetherWith (fadeOut() + slideOutVertically { it }) },
        modifier = Modifier.fillMaxWidth(),
    ) { shown ->
        if (shown != null) content(shown)
    }
}

/**
 * A card over the camera's middle, where the tapped pin is: it grows out of the pin, just above it, and shrinks back
 * into it when it closes. A card for another pin replaces it; the same pin's card only updates.
 */
@Composable
private fun <T : Any> PinCard(
    card: T?,
    key: (T) -> Any,
    pinClearance: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = card,
            contentKey = { it?.let(key) },
            transitionSpec = {
                (scaleIn(transformOrigin = TransformOrigin(0.5f, 1f)) + fadeIn()) togetherWith
                    (scaleOut(transformOrigin = TransformOrigin(0.5f, 1f)) + fadeOut())
            },
            modifier = modifier
                .padding(horizontal = MaterialTheme.spacing.large)
                .layout { measurable, constraints ->
                    val measured = measurable.measure(constraints)
                    layout(measured.width, measured.height) {
                        measured.place(0, -measured.height / 2 - pinClearance.roundToPx())
                    }
                },
        ) { shown ->
            if (shown != null) content(shown)
        }
    }
}

@Preview
@Composable
private fun MapScreenPreview() {
    TripinglyTheme {
        MapScreen(
            state = mapScreenStatePreviewData,
            cameraCommands = emptyFlow(),
            onAction = {},
        )
    }
}
