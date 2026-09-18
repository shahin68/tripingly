package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.presentation.component.ErrorBanner
import com.falcon.tripingly.core.presentation.theme.spacing
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.presentation.component.GoogleMapView
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Action
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.State

@Composable
fun MapScreen(
    state: State,
    onAction: (Action) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            LocationFab(
                state = state,
                onAction = onAction
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = state.markers.isNotEmpty(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.medium)
            ) {
                TripItineraryCard(
                    state = state,
                    onAction = onAction
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            GoogleMapView(
                modifier = Modifier.fillMaxSize(),
                cameraTarget = state.cameraTarget,
                zoomLevel = state.zoomLevel,
                markers = state.markers,
                isMyLocationEnabled = state.isPermissionGranted,
                onMapClick = { coords -> onAction(Action.OnMapClick(coords)) },
                onMarkerClick = { marker -> onAction(Action.OnMarkerClick(marker)) }
            )


            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                state.errorMessage?.let { error ->
                    ErrorBanner(
                        errorMessage = error,
                        onDismiss = { onAction(Action.DismissError) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationFab(
    state: State,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = { onAction(Action.CenterOnUserLocation) },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    ) {
        if (state.isLoadingLocation) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = "📍",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun TripItineraryCard(
    state: State,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            ItineraryTitle(state, onAction)

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(state.markers, key = { it.id }) { marker ->
                    ItineraryMarkerChip(
                        marker = marker,
                        state = state,
                        onAction = onAction
                    )
                }
            }
        }
    }
}

@Composable
private fun ItineraryMarkerChip(
    marker: MapMarker,
    state: State,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = marker.id == state.selectedMarker?.id
    InputChip(
        selected = isSelected,
        onClick = { onAction(Action.OnMarkerClick(marker)) },
        label = {
            Text(
                text = marker.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = marker.orderNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        trailingIcon = {
            IconButton(
                onClick = { onAction(Action.OnRemoveMarker(marker.id)) },
                modifier = Modifier.size(16.dp)
            ) {
                Text("✕", style = MaterialTheme.typography.labelSmall)
            }
        },
        colors = InputChipDefaults.inputChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = modifier
    )
}

@Composable
private fun ItineraryTitle(
    state: State,
    onAction: (Action) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Trip Plan (${state.markers.size} stops)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        TextButton(onClick = { onAction(Action.ClearAllMarkers) }) {
            Text("Clear All", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Preview
@Composable
private fun Preview() {
    val mockMarkers = listOf(
        MapMarker(
            id = "1",
            position = Coordinates.Paris,
            title = "Eiffel Tower",
            orderNumber = 1
        ),
        MapMarker(
            id = "2",
            position = Coordinates.London,
            title = "Big Ben",
            orderNumber = 2
        ),
        MapMarker(
            id = "3",
            position = Coordinates.Rome,
            title = "Colosseum",
            orderNumber = 3
        )
    )
    val state = State(
        markers = mockMarkers,
        selectedMarker = mockMarkers[0]
    )
    MapScreen(
        state = state,
        onAction = {},
        snackbarHostState = remember { SnackbarHostState() }
    )
}
