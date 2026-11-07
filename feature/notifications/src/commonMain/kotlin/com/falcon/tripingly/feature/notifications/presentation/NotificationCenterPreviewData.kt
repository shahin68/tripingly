package com.falcon.tripingly.feature.notifications.presentation

import com.falcon.tripingly.core.model.notification.NotificationEntry
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.notifications.generated.resources.Res
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_error_load
import com.falcon.tripingly.feature.notifications.presentation.NotificationCenterViewModel.State
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val now = Clock.System.now()

internal val previewCenter = State(
    entries = persistentListOf(
        NotificationEntry(
            id = "n1",
            actor = UserSummary("u1", "anna", "Anna Berg"),
            title = "Anna and 4 others liked your photo",
            body = "Albertina · Vienna",
            deepLink = "tripinly://markers/m1",
            isRead = false,
            at = now - 5.minutes,
        ),
        NotificationEntry(
            id = "n2",
            actor = UserSummary("u2", "jonas.k", "Jonas Krüger"),
            title = "Jonas replied on Naschmarkt",
            body = "@anna before ten is fine.",
            deepLink = "tripinly://markers/m2",
            isRead = false,
            at = now - 3.hours,
        ),
        NotificationEntry(
            id = "n3",
            actor = null,
            title = "DC Flats is getting hot",
            body = "25 people marked your spot hot. Nice find!",
            deepLink = "tripinly://markers/m3",
            isRead = true,
            at = now - 2.days,
        ),
    ),
    isLoading = false,
    hasLoaded = true,
)

internal val previewCenterError = State(isLoading = false, loadError = UiText.Resource(Res.string.notifications_error_load))

/** Typical entries: the rows' own skeleton while the first page loads. */
internal val PlaceholderEntries = listOf("Someone liked your photo", "Someone commented on a stop in your trip", "Someone followed you").mapIndexed { index, title ->
    NotificationEntry(
        id = "placeholder$index",
        actor = UserSummary("placeholder", "traveller", "Traveller"),
        title = title,
        body = "Vienna",
        deepLink = "",
        isRead = true,
        at = Instant.fromEpochSeconds(0),
    )
}
