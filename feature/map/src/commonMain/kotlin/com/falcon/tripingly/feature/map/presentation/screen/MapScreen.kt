package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.component.SuggestionItem
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.JoinRequestStatus
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.generated.resources.*
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.presentation.component.GoogleMapView
import com.falcon.tripingly.feature.map.presentation.component.PlaceCard
import com.falcon.tripingly.feature.map.presentation.component.StopCard
import com.falcon.tripingly.feature.map.presentation.component.fitCamera
import com.falcon.tripingly.feature.map.presentation.component.SelectedPinSize
import com.falcon.tripingly.feature.map.presentation.component.stopPinHeight
import com.falcon.tripingly.feature.map.presentation.photos.rememberPhotoPicker
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Action
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun MapScreen(
    state: State,
    onAction: (Action) -> Unit,
) {
    // Stops show their name next to their number in the day, so the numbers follow deletes and
    // reorders. A stop just tapped on the map has no name until the server answers; it shows
    // "Stop N" until then. Kept as the same list while the stops don't change, so the map and
    // the chips skip recomposing on unrelated updates such as the user's location.
    val stopTitles = state.markers.map { stringResource(Res.string.map_stop_title_format, it.orderNumber) }
    val markers = remember(state.markers, stopTitles) {
        state.markers.mapIndexed { index, marker -> marker.copy(title = marker.title.ifBlank { stopTitles[index] }) }
            .toImmutableList()
    }

    // Fits the framed stops into the part of the map the cards leave free; again on every return to the
    // screen, but not after a rotation: the camera keeps its center and zoom then.
    val density = LocalDensity.current
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var headerBottom by remember { mutableStateOf(0f) }
    var tabsTop by remember { mutableStateOf(0f) }
    var bottomCardsTop by remember { mutableStateOf(0f) }
    var fittedFrameId by rememberSaveable { mutableStateOf<Int?>(null) }
    val isLaidOut = mapSize != IntSize.Zero
    LaunchedEffect(state.frame, isLaidOut) {
        val frame = state.frame ?: return@LaunchedEffect
        if (!isLaidOut || frame.id == fittedFrameId) return@LaunchedEffect
        fittedFrameId = frame.id
        val fit = with(density) {
            fitCamera(
                points = frame.points,
                mapWidth = mapSize.width.toDp().value,
                mapHeight = mapSize.height.toDp().value,
                top = headerBottom.toDp().value,
                bottom = if (tabsTop > 0f) (mapSize.height - tabsTop).toDp().value else 0f,
                margin = 48f,
            )
        }
        fit?.let { onAction(Action.NavigateToLocation(it.center, it.zoom)) }
    }

    val isPlaceCardShown = state.selectedPlace != null && state.isPlaceCardShown
    val isStopCardShown = state.selectedMarker != null && state.isStopCardShown
    // Each card keeps showing its last place or stop while it scales out.
    val card = remember { mutableStateOf(state) }.apply { if (isPlaceCardShown) value = state }.value
    val selectedStop = markers.firstOrNull { it.id == state.selectedMarker?.id }
    val stopCard = remember { mutableStateOf(selectedStop) }.apply { if (isStopCardShown) value = selectedStop }.value
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    var stopCardBounds by remember { mutableStateOf(Rect.Zero) }
    val isCardOpen by rememberUpdatedState(isPlaceCardShown)
    val isStopCardOpen by rememberUpdatedState(isStopCardShown)
    val pickPhotos = rememberPhotoPicker { images -> onAction(Action.OnAddStopPhotos(images)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { mapSize = it }
            // Any touch outside the open card closes it, and still reaches the map and the controls.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val isOnCard = (isCardOpen && cardBounds.contains(down.position)) ||
                        (isStopCardOpen && stopCardBounds.contains(down.position))
                    if (!isOnCard) onAction(Action.OnScreenTouch)
                }
            }
    ) {
        // Not before the camera knows where the trip is: the map opens right there instead of flying from Paris.
        if (state.isCameraReady) {
            GoogleMapView(
                modifier = Modifier.fillMaxSize(),
                cameraTarget = state.cameraTarget,
                zoomLevel = state.zoomLevel,
                markers = markers,
                placePins = state.placePins,
                placeClusters = state.placeClusters,
                selectedPlace = state.selectedPlace,
                isMyLocationEnabled = state.isPermissionGranted,
                coveredBottom = with(density) { if (bottomCardsTop > 0f) (mapSize.height - bottomCardsTop).toDp() else 0.dp },
                onCameraMove = { coords, zoom, bounds -> onAction(Action.OnCameraMove(coords, zoom, bounds)) },
                onMapTouch = { onAction(Action.OnScreenTouch) },
                onMapClick = { coords -> onAction(Action.OnMapClick(coords)) },
                onPlacePinClick = { place -> onAction(Action.OnPlacePinClick(place)) },
                onClusterClick = { cluster -> onAction(Action.OnClusterClick(cluster)) },
                onMapLongClick = { coords -> onAction(Action.OnMapLongClick(coords)) },
                onMarkerClick = { marker -> onAction(Action.OnMarkerClick(marker)) },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
        ) {
            TripHeader(
                state = state,
                onAction = onAction,
                modifier = Modifier.onGloballyPositioned { headerBottom = it.positionInRoot().y + it.size.height },
            )

            if (state.pendingJoinRequestCount > 0 && !state.isSearchOpen) {
                FilledTonalButton(
                    onClick = { onAction(Action.OnOpenJoinRequests) },
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                ) {
                    Text(pluralStringResource(Res.plurals.map_join_request_count, state.pendingJoinRequestCount, state.pendingJoinRequestCount))
                }
            }

            if (state.isSearchOpen && state.searchResults.isNotEmpty()) {
                SearchResults(
                    results = state.searchResults,
                    onAction = onAction,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                )
            }
        }

        // Retry sends every stop change that failed for a connection problem, earlier ones too.
        ShowMessage(
            message = state.message?.asString(),
            onShown = { onAction(Action.OnMessageShown) },
            isError = state.message !is UiText.Note,
            actionLabel = if (state.canRetry) stringResource(Res.string.map_retry) else null,
            onAction = if (state.canRetry) ({ onAction(Action.RetryFailedChanges) }) else null,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .animateContentSize()
                .onGloballyPositioned { bottomCardsTop = it.positionInRoot().y }
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.medium),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Our places come from OpenStreetMap.
                OsmAttribution(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(4.dp)),
                )
                Spacer(modifier = Modifier.weight(1f))
                LocationFab(state = state, onAction = onAction)
            }

            DaySelectionTabs(
                state = state,
                onDaySelected = { onAction(Action.OnDaySelected(it)) },
                modifier = Modifier.onGloballyPositioned { tabsTop = it.positionInRoot().y },
            )

            AnimatedVisibility(
                visible = state.markers.isNotEmpty(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxWidth()
            ) {
                TripItineraryCard(
                    markers = markers,
                    selectedMarkerId = state.selectedMarker?.id,
                    canClear = state.canDeleteDay,
                    onAction = onAction,
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.spacing.medium)
                        .padding(bottom = MaterialTheme.spacing.medium)
                )
            }
        }

        // The camera has centered the tapped pin, so the card opens just above the middle of the map,
        // growing out of the pin, and shrinks back into it when it closes.
        val place = card.selectedPlace
        val pinClearance = place?.let { SelectedPinSize / 2 + MaterialTheme.spacing.small } ?: 0.dp
        AnimatedVisibility(
            visible = isPlaceCardShown,
            enter = scaleIn(transformOrigin = TransformOrigin(0.5f, 1f)) + fadeIn(),
            exit = scaleOut(transformOrigin = TransformOrigin(0.5f, 1f)) + fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = MaterialTheme.spacing.large)
                .layout { measurable, constraints ->
                    val measured = measurable.measure(constraints)
                    layout(measured.width, measured.height) {
                        measured.place(0, -measured.height / 2 - pinClearance.roundToPx())
                    }
                }
                .onGloballyPositioned { cardBounds = it.boundsInParent() },
        ) {
            if (place != null) {
                PlaceCard(
                    place = place,
                    details = card.selectedPlaceDetails,
                    address = card.selectedPlaceAddress,
                    dayNumber = card.days.getOrNull(card.activeDayIndex)?.number?.takeIf { card.canEdit },
                    onAdd = { onAction(Action.OnAddPlaceToDay) },
                    onOpenImage = { index -> onAction(Action.OnOpenPlaceImage(index)) },
                    onOpenPhotos = { onAction(Action.OnOpenPlacePhotos) },
                )
            }
        }

        // The same for a stop, above its pin.
        val stopClearance = stopCard?.let { stopPinHeight(hasCover = it.coverPhotoId != null) + MaterialTheme.spacing.small } ?: 0.dp
        AnimatedVisibility(
            visible = isStopCardShown,
            enter = scaleIn(transformOrigin = TransformOrigin(0.5f, 1f)) + fadeIn(),
            exit = scaleOut(transformOrigin = TransformOrigin(0.5f, 1f)) + fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = MaterialTheme.spacing.large)
                .layout { measurable, constraints ->
                    val measured = measurable.measure(constraints)
                    layout(measured.width, measured.height) {
                        measured.place(0, -measured.height / 2 - stopClearance.roundToPx())
                    }
                }
                .onGloballyPositioned { stopCardBounds = it.boundsInParent() },
        ) {
            if (stopCard != null) {
                StopCard(
                    marker = stopCard,
                    placeImage = state.selectedStopPlaceImage,
                    canEdit = state.canEdit,
                    onOpen = { onAction(Action.OnOpenStopPhotos) },
                    onAddPhotos = pickPhotos,
                    onTakePhoto = { onAction(Action.OnOpenPhotoCamera) },
                    onToggleLike = { onAction(Action.OnToggleStopLike(stopCard.id)) },
                    onOpenComments = { onAction(Action.OnOpenStopComments) },
                    canReport = state.isViewer,
                    onReport = { onAction(Action.OnReportStop) },
                    onRemoveMyContent = { onAction(Action.OnRemoveMyContent) }.takeIf { state.canEdit && !stopCard.canDelete },
                )
            }
        }
    }
}

@Composable
private fun TripHeader(
    state: State,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.medium),
        // A see-through surface doesn't get a content color of its own, so it's set here.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (state.isSearchOpen) {
            SearchField(state = state, onAction = onAction)
            return@Card
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onAction(Action.OnBackClick) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.map_back))
            }
            Column(Modifier.weight(1f).padding(vertical = if (state.tripCreatedAt == null) 16.dp else 8.dp)) {
                Text(
                    text = state.tripName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                state.tripCreatedAt?.let { createdAt ->
                    Text(
                        text = stringResource(Res.string.map_trip_created, DateUtils.formatFormal(createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.isSaving || state.isLoading || state.isUpdatingSharing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            IconButton(onClick = { onAction(Action.OnOpenSearch) }) {
                Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.map_search))
            }
            if (state.isViewer) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.map_trip_menu))
                    }
                    AppDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        actions = listOfNotNull(
                            DropdownAction(stringResource(if (state.isSavedByMe) Res.string.map_unsave_trip else Res.string.map_save_trip)) {
                                onAction(Action.OnToggleSaveTrip)
                            },
                            // Once the owner declined, there's nothing more to ask.
                            when (state.myJoinRequest) {
                                null -> DropdownAction(stringResource(Res.string.map_join_trip)) { onAction(Action.OnRequestToJoin) }
                                JoinRequestStatus.PENDING -> DropdownAction(stringResource(Res.string.map_cancel_join)) {
                                    onAction(Action.OnCancelJoinRequest)
                                }
                                JoinRequestStatus.DECLINED -> null
                            },
                            state.ownerUsername?.let { owner ->
                                DropdownAction(stringResource(Res.string.map_owner_profile, owner)) { onAction(Action.OnOpenOwnerProfile) }
                            },
                            DropdownAction(stringResource(Res.string.map_report_trip)) { onAction(Action.OnReportTrip) },
                        ),
                    )
                }
            }
            if (state.canEdit) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.map_day_menu))
                    }
                    AppDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        actions = buildList {
                            add(DropdownAction(stringResource(Res.string.map_add_day)) { onAction(Action.AddDay) })
                            if (state.days.size > 1 && state.canDeleteDay) {
                                add(
                                    DropdownAction(stringResource(Res.string.map_delete_day), isDestructive = true) {
                                        onAction(Action.DeleteDay)
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Replaces the trip name while searching; back closes the search. */
@Composable
private fun SearchField(
    state: State,
    onAction: (Action) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onAction(Action.OnCloseSearch) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.map_back))
        }
        TextField(
            value = state.searchQuery,
            onValueChange = { onAction(Action.OnSearchQueryChanged(it)) },
            placeholder = { Text(stringResource(Res.string.map_search_placeholder)) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
        when {
            state.isSearching -> CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = 14.dp).size(20.dp),
                strokeWidth = 2.dp,
            )
            state.searchQuery.isNotEmpty() -> IconButton(onClick = { onAction(Action.OnSearchQueryChanged("")) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_search_clear))
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: ImmutableList<PlaceSearchResult>,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            results.forEach { result ->
                SuggestionItem(
                    title = result.name,
                    subtitle = result.address,
                    onClick = { onAction(Action.OnSearchResultClick(result)) },
                )
            }
            OsmAttribution(modifier = Modifier.padding(MaterialTheme.spacing.medium))
        }
    }
}

@Composable
private fun DaySelectionTabs(
    state: State,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.days.isEmpty()) return
    SecondaryScrollableTabRow(
        selectedTabIndex = state.activeDayIndex,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 16.dp,
        divider = {},
    ) {
        state.days.forEachIndexed { index, day ->
            Tab(
                selected = state.activeDayIndex == index,
                onClick = { onDaySelected(index) },
                text = {
                    Text(
                        if (day.date != null) {
                            stringResource(Res.string.map_day_label, day.number, DateUtils.formatAbbreviated(day.date))
                        } else {
                            stringResource(Res.string.map_day_label_no_date, day.number)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun LocationFab(
    state: State,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = { onAction(Action.CenterOnUserLocation) },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    ) {
        if (state.isLoadingLocation) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = "📍",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun TripItineraryCard(
    markers: ImmutableList<MapMarker>,
    selectedMarkerId: String?,
    canClear: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            ItineraryTitle(stopCount = markers.size, canClear = canClear, onAction = onAction)

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(markers, key = { it.id }) { marker ->
                    ItineraryMarkerChip(
                        marker = marker,
                        isSelected = marker.id == selectedMarkerId,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItineraryMarkerChip(
    marker: MapMarker,
    isSelected: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    InputChip(
        selected = isSelected,
        onClick = { onAction(Action.OnMarkerClick(marker)) },
        label = {
            Text(
                text = marker.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = marker.orderNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        trailingIcon = if (marker.canDelete) {
            {
                IconButton(
                    onClick = { onAction(Action.OnRemoveMarker(marker.id)) },
                    modifier = Modifier.size(16.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.map_remove_stop),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        } else {
            null
        },
        colors = InputChipDefaults.inputChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = modifier,
    )
}

@Composable
private fun ItineraryTitle(
    stopCount: Int,
    canClear: Boolean,
    onAction: (Action) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.map_itinerary_title, stopCount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (canClear) {
            TextButton(onClick = { onAction(Action.ClearAllMarkers) }) {
                Text(stringResource(Res.string.map_itinerary_clear_all), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Preview
@Composable
private fun MapScreenPreview() {
    TripinglyTheme {
        MapScreen(
            state = mapScreenStatePreviewData,
            onAction = {}
        )
    }
}

