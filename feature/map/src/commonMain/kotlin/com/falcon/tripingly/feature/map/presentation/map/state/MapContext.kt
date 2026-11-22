package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.ui.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What [MapViewModel] and its helpers share: the trip, the screen's [model] and the scope their work runs in. */
internal class MapContext(
    val tripId: String,
    val scope: CoroutineScope,
    val trip: StateFlow<TripDetails?>,
    val model: MutableStateFlow<MapModel> = MutableStateFlow(MapModel()),
) {
    fun days(): List<TripDay> = trip.value?.days.orEmpty()

    /** The open day; the first one if it's gone. */
    fun activeDay(): TripDay? = days().getOrNull(activeDayIndex(days().size, model.value.activeDayIndex))

    fun canEdit(): Boolean = trip.value?.trip?.role?.canEdit == true

    /**
     * Someone else's trip I'm not on: the map is about their trip, so finding, filtering and routing places are off
     * (Shahin, 2026-11-22). Read live: once a request to join is accepted the trip reloads and every feature is back.
     */
    fun isVisitor(): Boolean = trip.value?.trip?.role == TripRole.VIEWER

    fun update(change: (MapModel) -> MapModel) = model.update(change)

    fun showMessage(message: UiText?) = model.update { it.copy(message = message) }
}

/** [index] kept within the trip's [dayCount] days. */
internal fun activeDayIndex(dayCount: Int, index: Int): Int = index.coerceIn(0, (dayCount - 1).coerceAtLeast(0))
