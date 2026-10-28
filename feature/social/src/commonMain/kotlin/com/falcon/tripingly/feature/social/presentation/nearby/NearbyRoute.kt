package com.falcon.tripingly.feature.social.presentation.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.AppBottomSheet
import com.falcon.tripingly.core.designsystem.component.ErrorScreen
import com.falcon.tripingly.core.designsystem.component.LoadStateContent
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.component.ReportDialog
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.screenContentPadding
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.location.permission.rememberLocationPermissionController
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.ui.ObserveAsEvents
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_location_note
import com.falcon.tripingly.feature.social.generated.resources.social_nearby
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_allow
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_ask
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_blocked
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_empty
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_open_settings
import com.falcon.tripingly.feature.social.presentation.component.EmptyText
import com.falcon.tripingly.feature.social.presentation.component.LoadMoreAtEnd
import com.falcon.tripingly.feature.social.presentation.component.NearbyPreviewStates
import com.falcon.tripingly.feature.social.presentation.component.placeholderNearbyPlaces
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Action
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Event
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.State
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Status
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The Nearby tab. [barsPadding] is the tab shell's floating top and bottom bars, which the list scrolls behind. */
@Composable
fun NearbyRoute(
    onOpenTrip: (tripId: String) -> Unit,
    placeImageContent: @Composable (placeId: String, imageId: String, onReport: (Photo) -> Unit, onDismiss: () -> Unit) -> Unit,
    barsPadding: PaddingValues = PaddingValues(),
    viewModel: NearbyViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locationPermission = rememberLocationPermissionController { viewModel.onAction(Action.PermissionResult(it)) }
    // When Nearby opens and on every return to the app, e.g. from Settings.
    LifecycleResumeEffect(locationPermission) {
        viewModel.onAction(Action.PermissionChecked(locationPermission.status()))
        onPauseOrDispose {}
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is Event.OpenTrip -> onOpenTrip(event.tripId)
            Event.RequestPermission -> locationPermission.request()
            Event.OpenAppSettings -> locationPermission.openSettings()
        }
    }

    NearbyScreen(state = state, onAction = viewModel::onAction, barsPadding = barsPadding)

    // A place's images with their credits, opened from its cover.
    state.openImage?.let { place ->
        place.coverImageId?.let { imageId ->
            placeImageContent(place.id, imageId, { viewModel.onAction(Action.ReportImage(it)) }, { viewModel.onAction(Action.CloseImage) })
        }
    }
    state.report?.let { report ->
        ReportDialog(
            details = report.details,
            onReport = { reason -> viewModel.onAction(Action.SendReport(reason)) },
            onDismiss = { viewModel.onAction(Action.DismissReport) },
            isSending = report.isSending,
        )
    }
    AppBottomSheet(value = state.addPlace, onDismiss = { viewModel.onAction(Action.AddToTrip.Dismiss) }) { addPlace, close ->
        AddPlaceSheet(addPlace = addPlace, onAction = viewModel::onAction, onClose = close)
    }
}

@Composable
internal fun NearbyScreen(
    state: State,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
    barsPadding: PaddingValues = PaddingValues(),
) {
    val contentPadding = screenContentPadding(barsPadding)
    ShowMessage(message = state.message?.asString(), onShown = { onAction(Action.DismissMessage) }, isError = state.message !is UiText.Note)
    Box(modifier.fillMaxSize()) {
        when (state.status) {
            Status.NeedsPermission, Status.Blocked, Status.Unavailable -> LocationAsk(state.status, onAction, contentPadding)
            else -> LoadStateContent(
                isLoading = state.status == Status.Unknown || state.status == Status.Locating,
                errorMessage = state.loadError?.asString(),
                onRetry = { onAction(Action.Refresh) },
                contentPadding = contentPadding,
                loading = { NearbySkeleton(contentPadding) },
            ) {
                NearbyList(state, onAction, contentPadding)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearbyList(state: State, onAction: (Action) -> Unit, contentPadding: PaddingValues) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState) { onAction(Action.LoadMore) }
    PullToRefreshBox(isRefreshing = state.isLoading && state.places.isNotEmpty(), onRefresh = { onAction(Action.Refresh) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            item(key = "note") { LocationNote(Modifier.animateItem()) }
            if (state.places.isEmpty()) {
                item(key = "empty") { EmptyText(stringResource(Res.string.social_nearby_empty), Modifier.animateItem()) }
            }
            items(state.places, key = { it.id }) { place ->
                NearbyPlaceCard(
                    place = place,
                    onOpenImage = { onAction(Action.OpenImage(place)) },
                    onMarkHot = { onAction(Action.MarkHot(place)) },
                    onAdd = { onAction(Action.AddPlace(place)) },
                    modifier = Modifier.animateItem(),
                )
            }
            // Nearby lists OpenStreetMap places.
            item(key = "osm") { OsmAttribution(Modifier.padding(vertical = MaterialTheme.spacing.small).animateItem()) }
        }
    }
}

/** The list's own cards, drawn from placeholders as a [Skeleton], while the location and the places load. */
@Composable
private fun NearbySkeleton(contentPadding: PaddingValues) {
    Skeleton(Modifier.fillMaxSize()) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            LocationNote()
            placeholderNearbyPlaces.forEach { NearbyPlaceCard(place = it, onOpenImage = {}, onMarkHot = {}, onAdd = {}) }
        }
    }
}

/** "Your location is only used to find places and is never saved." above the places. */
@Composable
private fun LocationNote(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(NoteIconSize))
        Text(stringResource(Res.string.social_location_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Why Nearby needs the location and the way to allow it: the full-screen state with a location picture. */
@Composable
private fun LocationAsk(status: Status, onAction: (Action) -> Unit, contentPadding: PaddingValues) {
    val blocked = status == Status.Blocked
    ErrorScreen(
        onRetry = { onAction(Action.AllowLocation) },
        title = stringResource(Res.string.social_nearby),
        message = stringResource(if (blocked) Res.string.social_nearby_blocked else Res.string.social_nearby_ask),
        retryLabel = stringResource(if (blocked) Res.string.social_nearby_open_settings else Res.string.social_nearby_allow),
        contentPadding = contentPadding,
        illustration = {
            Box(
                Modifier.size(IllustrationSize).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            }
        },
    )
}

private val IllustrationSize = 168.dp
private val NoteIconSize = 14.dp

@Preview
@Composable
private fun NearbyScreenPreview(@PreviewParameter(NearbyPreviewStates::class) state: State) {
    TripinglyTheme { NearbyScreen(state = state, onAction = {}) }
}

@Preview
@Composable
private fun NearbyScreenDarkPreview() {
    TripinglyTheme(darkTheme = true) { NearbyScreen(state = NearbyPreviewStates().values.elementAt(3), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun NearbyScreenLandscapePreview() {
    TripinglyTheme { NearbyScreen(state = NearbyPreviewStates().values.elementAt(3), onAction = {}) }
}
