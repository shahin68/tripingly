package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import kotlin.time.Instant

/** My Trips while it loads: the timeline's own rows filled with placeholder trips, shown as a [Skeleton]. */
@Composable
internal fun TimelineSkeleton(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    Skeleton(modifier.fillMaxSize()) {
        Column(Modifier.padding(contentPadding)) {
            PlaceholderTrips.forEach { TimelineTripItem(it, PlaceholderToday, past = false, onAction = {}) }
        }
    }
}

private val PlaceholderToday = LocalDate(2026, 1, 1)

/** Names of typical lengths, so the bars look like a real list; some shared, like real trips. */
private val PlaceholderTrips = listOf(
    "Weekend in the mountains" to 0,
    "City break" to 1,
    "Road trip along the coast" to 0,
    "Lakes and old towns" to 2,
    "Family visit" to 0,
).mapIndexed { index, (name, editors) ->
    val start = PlaceholderToday.plus(DatePeriod(days = 3 + index * 12))
    Trip(
        id = "placeholder$index",
        name = name,
        startDate = start,
        endDate = start.plus(DatePeriod(days = 3)),
        visibility = TripVisibility.PUBLIC,
        role = TripRole.OWNER,
        owner = UserSummary("placeholder", "traveller", "Traveller"),
        dayCount = 4,
        markerCount = 6,
        editorCount = editors,
        createdAt = Instant.fromEpochSeconds(0),
        updatedAt = Instant.fromEpochSeconds(0),
    )
}

@Composable
private fun TimelineSkeletonPreviewContent() {
    TimelineSkeleton(PaddingValues(), Modifier.background(MaterialTheme.colorScheme.background))
}

@Preview
@Composable
private fun TimelineSkeletonPreview() {
    TripinglyTheme { TimelineSkeletonPreviewContent() }
}

@Preview
@Composable
private fun TimelineSkeletonDarkPreview() {
    TripinglyTheme(darkTheme = true) { TimelineSkeletonPreviewContent() }
}
