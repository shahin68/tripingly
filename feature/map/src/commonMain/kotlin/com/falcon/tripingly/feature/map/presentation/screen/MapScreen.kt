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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.ErrorBanner
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.presentation.component.GoogleMapView
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Action
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.State
import org.jetbrains.compose.resources.stringResource
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.*

@Composable
fun MapScreen(
    state: State,
    onAction: (Action) -> Unit,
) {
    // Stops are named by their place in the day, so the numbers follow deletes and reorders.
    val markers = state.markers.map { it.copy(title = stringResource(Res.string.map_stop_title_format, it.orderNumber)) }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        GoogleMapView(
            modifier = Modifier.fillMaxSize(),
            cameraTarget = state.cameraTarget,
            zoomLevel = state.zoomLevel,
            markers = markers,
            selectedMarkerId = state.selectedMarker?.id,
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
            TripHeader(state, onAction)

            AnimatedVisibility(
                visible = state.message != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.padding(MaterialTheme.spacing.medium)
            ) {
                state.message?.let { message ->
                    ErrorBanner(
                        errorMessage = message.asString(),
                        onDismiss = { onAction(Action.DismissError) },
                        actionLabel = if (state.canRetry) stringResource(Res.string.map_retry) else null,
                        onAction = if (state.canRetry) ({ onAction(Action.RetryFailedChanges) }) else null,
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
                    markers = markers,
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
private fun TripHeader(
    state: State,
    onAction: (Action) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.tripName,
                modifier = Modifier.weight(1f).padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (state.isSaving || state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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
                            if (state.days.size > 1) {
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

@Composable
private fun DaySelectionTabs(
    state: State,
    onDaySelected: (Int) -> Unit,
) {
    if (state.days.isEmpty()) return
    SecondaryScrollableTabRow(
        selectedTabIndex = state.activeDayIndex,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
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
    state: State,
    markers: List<MapMarker>,
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
                items(markers, key = { it.id }) { marker ->
                    ItineraryMarkerChip(
                        marker = marker,
                        isSelected = marker.id == state.selectedMarker?.id,
                        canEdit = state.canEdit,
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
    canEdit: Boolean,
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
        trailingIcon = if (canEdit) {
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
        if (state.canEdit) {
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

