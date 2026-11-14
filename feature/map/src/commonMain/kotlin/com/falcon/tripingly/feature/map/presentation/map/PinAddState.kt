package com.falcon.tripingly.feature.map.presentation.map

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.ui.UiText
import kotlinx.collections.immutable.ImmutableList

/** What a pin card's green + does. */
@Immutable
sealed interface PinAdd {
    /** Puts the place on the open day of this trip (an owner or editor). */
    data class ToDay(val dayNumber: Int) : PinAdd

    /** Puts the stop or place on a day of one of my trips, picked in a sheet: on a trip I only view. */
    data object ToMyTrip : PinAdd
}

/** The sheet the + on a trip I only view opens: one of my [trips], then the picked [trip]'s [days] (null while they load). */
@Immutable
data class AddToTripState(
    /** The stop's or place's name. */
    val name: UiText,
    val trips: ImmutableList<Trip>,
    val trip: Trip?,
    val days: ImmutableList<TripDay>?,
)
