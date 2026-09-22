package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.presentation.component.ErrorBanner
import com.falcon.tripingly.core.presentation.theme.TripinglyTheme
import com.falcon.tripingly.core.presentation.theme.spacing
import com.falcon.tripingly.core.util.DateUtils
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.presentation.component.GoogleMapView
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Action
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.State
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.until
import org.jetbrains.compose.resources.stringResource
import tripingly.shared.generated.resources.Res
import tripingly.shared.generated.resources.*

@Composable
fun MapScreen(
    state: State,
    onAction: (Action) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        GoogleMapView(
            modifier = Modifier.fillMaxSize(),
            cameraTarget = state.cameraTarget,
            zoomLevel = state.zoomLevel,
            markers = state.markers,
            isMyLocationEnabled = state.isPermissionGranted,
            onCameraMove = { coords, zoom -> onAction(Action.OnCameraMove(coords, zoom)) },
            onMapClick = { coords -> onAction(Action.OnMapClick(coords)) },
            onMarkerClick = { marker -> onAction(Action.OnMarkerClick(marker)) }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
        ) {
            TripHeader(state.tripName)
            
            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.padding(MaterialTheme.spacing.medium)
            ) {
                state.errorMessage?.let { error ->
                    ErrorBanner(
                        errorMessage = error,
                        onDismiss = { onAction(Action.DismissError) }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .animateContentSize()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.End
        ) {
            LocationFab(
                state = state,
                onAction = onAction,
                modifier = Modifier.padding(MaterialTheme.spacing.medium)
            )

            DaySelectionTabs(
                state = state,
                onDaySelected = { onAction(Action.OnDaySelected(it)) }
            )

            AnimatedVisibility(
                visible = state.markers.isNotEmpty(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxWidth()
            ) {
                TripItineraryCard(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.spacing.medium)
                        .padding(bottom = MaterialTheme.spacing.medium)
                )
            }
        }
    }
}

@Composable
private fun TripHeader(title: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DaySelectionTabs(
    state: State,
    onDaySelected: (Int) -> Unit,
) {
    val totalDays = if (state.startDate != null && state.endDate != null) {
        (state.startDate.until(state.endDate, DateTimeUnit.DAY) + 1).toInt()
    } else 1

    SecondaryScrollableTabRow(
        selectedTabIndex = state.activeDayIndex,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        edgePadding = 16.dp,
        divider = {},
    ) {
        repeat(totalDays) { index ->
            Tab(
                selected = state.activeDayIndex == index,
                onClick = { onDaySelected(index) },
                text = {
                    val tabDate = state.startDate?.plus(index, DateTimeUnit.DAY)
                    val dateStr = tabDate?.let { DateUtils.formatAbbreviated(it) } ?: ""
                    Text(stringResource(Res.string.map_day_label, index + 1, dateStr))
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
    state: State,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            ItineraryTitle(state, onAction)

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(state.markers, key = { it.id }) { marker ->
                    ItineraryMarkerChip(
                        marker = marker,
                        state = state,
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
    state: State,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = marker.id == state.selectedMarker?.id
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
        trailingIcon = {
            IconButton(
                onClick = { onAction(Action.OnRemoveMarker(marker.id)) },
                modifier = Modifier.size(16.dp),
            ) {
                Text("✕", style = MaterialTheme.typography.labelSmall)
            }
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
    state: State,
    onAction: (Action) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.map_itinerary_title, state.markers.size),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = { onAction(Action.ClearAllMarkers) }) {
            Text(stringResource(Res.string.map_itinerary_clear_all), style = MaterialTheme.typography.labelSmall)
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

