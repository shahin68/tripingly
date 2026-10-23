package com.falcon.tripingly.feature.map.presentation.screen

import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.TripDetails
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

    fun update(change: (MapModel) -> MapModel) = model.update(change)

    fun showMessage(message: UiText?) = model.update { it.copy(message = message) }
}

/** [index] kept within the trip's [dayCount] days. */
internal fun activeDayIndex(dayCount: Int, index: Int): Int = index.coerceIn(0, (dayCount - 1).coerceAtLeast(0))
