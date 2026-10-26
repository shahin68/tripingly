package com.falcon.tripingly.feature.social.presentation.nearby

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.MessageBannerHost
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_add_place_pick_day
import com.falcon.tripingly.feature.social.generated.resources.social_add_place_pick_trip
import com.falcon.tripingly.feature.social.generated.resources.social_day_number
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Action
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.AddPlaceState
import com.falcon.tripingly.feature.social.presentation.component.AddPlacePreviewStates
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.foundation.background

/** Adds a Nearby place to a day: pick one of my trips, then the day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddPlaceSheet(addPlace: AddPlaceState, onAction: (Action) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onAction(Action.AddToTrip.Dismiss) }) {
        Box {
            Column(Modifier.padding(bottom = MaterialTheme.spacing.large)) {
                val trip = addPlace.trip
                Text(
                    text = if (trip == null) {
                        stringResource(Res.string.social_add_place_pick_trip, addPlace.place.name)
                    } else {
                        stringResource(Res.string.social_add_place_pick_day, trip.name)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
                )
                when {
                    trip == null -> LazyColumn {
                        items(addPlace.trips, key = { it.id }) { option ->
                            SheetRow(
                                modifier = Modifier.animateItem(),
                                title = option.name,
                                subtitle = DateUtils.formatRange(option.startDate, option.endDate),
                                onClick = { onAction(Action.AddToTrip.PickTrip(option)) },
                            )
                        }
                    }
                    addPlace.isLoadingDays -> Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> LazyColumn {
                        items(addPlace.days, key = { it.id }) { day ->
                            SheetRow(
                                modifier = Modifier.animateItem(),
                                title = stringResource(Res.string.social_day_number, day.position + 1),
                                subtitle = day.date?.let(DateUtils::formatAbbreviated),
                                onClick = { onAction(Action.AddToTrip.PickDay(day)) },
                            )
                        }
                    }
                }
            }
            MessageBannerHost(Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun SheetRow(title: String, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview
@Composable
private fun AddPlaceSheetPreview(@PreviewParameter(AddPlacePreviewStates::class) state: AddPlaceState) {
    TripinglyTheme { AddPlaceSheet(addPlace = state, onAction = {}) }
}

@Preview
@Composable
private fun SheetRowPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            SheetRow(title = "A week in Vienna", subtitle = "Jun 1 – Jun 7, 2026", onClick = {})
            SheetRow(title = "A very long trip name that goes on and on and doesn't fit on one line", subtitle = null, onClick = {})
        }
    }
}
