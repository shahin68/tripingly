package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_add_to_trip_added
import com.falcon.tripingly.feature.map.generated.resources.map_add_to_trip_no_trips
import com.falcon.tripingly.feature.map.generated.resources.map_error_add_to_trip
import com.falcon.tripingly.feature.map.generated.resources.map_error_load_days
import com.falcon.tripingly.feature.map.presentation.component.MapMarker
import com.falcon.tripingly.feature.map.presentation.map.AddToTripState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The green + on a trip I only view: the stop or place goes to a day of one of my trips, picked in a sheet (my trips I
 * can edit, from the device; then the picked one's days, from the server). A stop is copied by the server, which keeps
 * where it came from; a place is added like a stop added on the map, at once. The map stays where it is; a note says
 * where it went.
 */
internal class MapAddToTrip(
    private val context: MapContext,
    private val selection: MapSelection,
    private val tripRepository: TripRepository,
    private val markerRepository: MarkerRepository,
    private val onAdded: () -> Unit,
) {
    /** My trips a place went to: a change the server refuses there is undone on the device and explained here. */
    private val addedTo = mutableSetOf<String>()

    init {
        context.scope.launch {
            markerRepository.failures.collect { failure ->
                if (failure.tripId in addedTo) context.showMessage(failure.error.toUiText(Res.string.map_error_add_to_trip))
            }
        }
    }

    fun open(fromDetails: Boolean) {
        val item = when (val selected = context.model.value.selection) {
            is Selection.Stop -> CopyItem.Stop(selected.markerId)
            is Selection.Place -> CopyItem.Place(selected.toNewMarker())
            null -> return
        }
        context.scope.launch {
            val trips = tripRepository.observeMyTrips().first().filter { it.role.canEdit && it.id != context.tripId }
            if (trips.isEmpty()) {
                context.showMessage(UiText.Resource(Res.string.map_add_to_trip_no_trips))
                return@launch
            }
            context.update { it.copy(sheet = Sheet.AddToTrip(item, trips, fromDetails = fromDetails)) }
        }
    }

    fun pickTrip(tripId: String) {
        val sheet = current() ?: return
        val trip = sheet.trips.firstOrNull { it.id == tripId } ?: return
        context.update { it.copy(sheet = sheet.copy(trip = trip, days = null)) }
        context.scope.launch {
            val result = tripRepository.refreshTrip(trip.id)
            context.update { model ->
                val shown = (model.sheet as? Sheet.AddToTrip)?.takeIf { it.trip?.id == trip.id } ?: return@update model
                when (result) {
                    is AppResult.Success -> model.copy(sheet = shown.copy(days = result.data.days))
                    // Back to the trips, so another can be picked or the same one tried again.
                    is AppResult.Error -> model.copy(sheet = shown.copy(trip = null), message = result.error.toUiText(Res.string.map_error_load_days))
                }
            }
        }
    }

    fun pickDay(dayId: String) {
        val sheet = current() ?: return
        val trip = sheet.trip ?: return
        val day = sheet.days?.firstOrNull { it.id == dayId } ?: return
        // Done: the details it was opened from close too.
        context.update { it.copy(sheet = null) }
        selection.closePlace()
        selection.closeStop()
        val added = UiText.Note(UiText.Resource(Res.string.map_add_to_trip_added, trip.name, day.position + 1))
        context.scope.launch {
            when (val item = sheet.item) {
                is CopyItem.Stop -> when (val result = markerRepository.copyMarker(item.markerId, day.id)) {
                    is AppResult.Success -> {
                        context.showMessage(added)
                        onAdded()
                    }
                    is AppResult.Error -> context.showMessage(result.error.toUiText(Res.string.map_error_add_to_trip))
                }
                is CopyItem.Place -> {
                    addedTo += trip.id
                    markerRepository.addMarker(trip.id, day.id, item.marker)
                    context.showMessage(added)
                    onAdded()
                }
            }
        }
    }

    private fun current() = context.model.value.sheet as? Sheet.AddToTrip
}

/** The sheet as shown: the stop's title (as on its pin) or the place's name. */
internal fun Sheet.AddToTrip.toState(stops: List<MapMarker>) = AddToTripState(
    name = when (item) {
        is CopyItem.Stop -> stops.firstOrNull { it.id == item.markerId }?.title ?: UiText.Server("")
        is CopyItem.Place -> UiText.Server(item.marker.name)
    },
    trips = trips.toImmutableList(),
    trip = trip,
    days = days?.toImmutableList(),
)
