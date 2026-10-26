package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.TripErrorCodes
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_change_not_saved
import com.falcon.tripingly.feature.map.generated.resources.map_error_add_day
import com.falcon.tripingly.feature.map.generated.resources.map_error_delete_day
import com.falcon.tripingly.feature.map.generated.resources.map_error_remove_my_content
import com.falcon.tripingly.feature.map.generated.resources.map_error_stop_change
import com.falcon.tripingly.feature.map.generated.resources.map_my_content_removed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Owners and editors add and remove stops and days. Stop changes show at once and are saved in the background
 * (see [MarkerRepository]); one that fails is undone and explained, with Retry when the connection was the
 * problem. Day changes wait for the server.
 */
internal class MapEdits(
    private val context: MapContext,
    private val selection: MapSelection,
    private val tripRepository: TripRepository,
    private val markerRepository: MarkerRepository,
    private val onStopAdded: () -> Unit,
) {
    init {
        context.scope.launch {
            markerRepository.failures.collect { failure ->
                if (failure.tripId != context.tripId) return@collect
                context.update {
                    if (failure.needsConfirm) {
                        it.copy(dialog = Dialog.OthersContent.Change(failure.error.serverMessage(), failure))
                    } else if (failure.isConnectionProblem) {
                        it.copy(message = UiText.Resource(Res.string.map_change_not_saved), failedChanges = it.failedChanges + failure)
                    } else {
                        // Trying again won't change the server's mind.
                        it.copy(message = failure.error.toUiText(Res.string.map_error_stop_change), failedChanges = emptyList())
                    }
                }
            }
        }
    }

    fun retryFailedChanges() {
        val failed = context.model.value.failedChanges
        context.update { it.copy(message = null, failedChanges = emptyList()) }
        context.scope.launch { failed.forEach { markerRepository.retry(it) } }
    }

    /** A tap on the map itself (pins take their own taps) adds a stop, unless it only closed a card. */
    fun onMapClick(coordinates: Coordinates) {
        if (selection.isClosingTouch) return
        // No name: the server names the stop after the place or address there.
        add(NewMarker("", GeoPoint(coordinates.latitude, coordinates.longitude)))
    }

    /** A long press drops a pin the user names. */
    fun onMapLongClick(coordinates: Coordinates) {
        if (context.canEdit() && context.activeDay() != null) context.update { it.copy(dialog = Dialog.NamePin(coordinates)) }
    }

    fun addNamedPin(name: String) {
        val location = (context.model.value.dialog as? Dialog.NamePin)?.location ?: return
        context.update { it.copy(dialog = null) }
        if (name.isNotBlank()) add(NewMarker(name, GeoPoint(location.latitude, location.longitude)))
    }

    /** A stop at the open place or address, named after it. */
    fun addPlace() {
        val place = selection.selectedPlace ?: return
        if (context.activeDay() == null) return
        selection.closePlace()
        val address = place.address
        add(if (address != null) NewMarker(address.name, address.location, osm = address.osm) else NewMarker(place.place.name, place.place.location, placeId = place.place.id))
    }

    fun removeMarker(markerId: String) {
        if (!context.canEdit()) return
        selection.closeStop(markerId)
        context.scope.launch { markerRepository.deleteMarker(markerId) }
    }

    fun addDay() {
        if (!context.canEdit()) return
        save(Res.string.map_error_add_day) {
            tripRepository.addDay(context.tripId).also { result ->
                if (result is AppResult.Success) {
                    selection.closeStop()
                    context.update { it.copy(activeDayIndex = result.data.days.lastIndex) }
                }
            }
        }
    }

    /** The user agreed to the open dialog's change. */
    fun confirm() {
        val dialog = context.model.value.dialog
        val day = context.activeDay()
        context.update { it.copy(dialog = null) }
        if (day == null || !context.canEdit()) return
        when (dialog) {
            Dialog.ClearDay -> {
                selection.closeStop()
                context.scope.launch { markerRepository.clearDay(context.tripId, day.id) }
            }
            Dialog.DeleteDay -> deleteDay(day.id, confirm = false)
            Dialog.RemoveMyContent -> selection.selectedMarkerId?.let(::removeMyContent)
            else -> Unit
        }
    }

    /** The user agreed to delete other people's photos, comments and likes with the stop or day. */
    fun deleteAnyway() {
        val othersContent = context.model.value.dialog as? Dialog.OthersContent ?: return
        context.update { it.copy(dialog = null) }
        when (othersContent) {
            is Dialog.OthersContent.Change -> context.scope.launch { markerRepository.deleteAnyway(othersContent.failure) }
            is Dialog.OthersContent.Day -> deleteDay(othersContent.dayId, confirm = true)
        }
    }

    /** Shows at once; see [MarkerRepository]. */
    private fun add(marker: NewMarker) {
        val day = context.activeDay() ?: return
        if (!context.canEdit()) return
        context.scope.launch { markerRepository.addMarker(context.tripId, day.id, marker) }
        onStopAdded()
    }

    private fun deleteDay(dayId: String, confirm: Boolean) = save(Res.string.map_error_delete_day) {
        tripRepository.deleteDay(context.tripId, dayId, confirm).also { result ->
            when {
                result is AppResult.Success -> {
                    selection.closeStop()
                    context.update { it.copy(activeDayIndex = (it.activeDayIndex - 1).coerceAtLeast(0)) }
                }
                result is AppResult.Error && result.error.hasCode(TripErrorCodes.STOP_HAS_OTHERS_CONTENT) ->
                    context.update { it.copy(dialog = Dialog.OthersContent.Day(result.error.serverMessage(), dayId)) }
            }
        }
    }

    private fun removeMyContent(markerId: String) {
        context.scope.launch {
            val result = markerRepository.removeMyContent(context.tripId, markerId)
            context.showMessage(
                when (result) {
                    is AppResult.Success -> UiText.Note(UiText.Resource(Res.string.map_my_content_removed))
                    is AppResult.Error -> result.error.toUiText(Res.string.map_error_remove_my_content)
                },
            )
        }
    }

    /** Runs one day change at a time; a refusal shows [failed] and why. */
    private fun save(failed: StringResource, change: suspend () -> AppResult<*, DataError.Network>) {
        if (context.model.value.isSaving) return
        context.update { it.copy(isSaving = true) }
        context.scope.launch {
            val result = change()
            // Other people's content on the day asks first instead (see deleteDay).
            val error = (result as? AppResult.Error)?.error?.takeUnless { it.hasCode(TripErrorCodes.STOP_HAS_OTHERS_CONTENT) }
            context.update { it.copy(isSaving = false, message = error?.toUiText(failed) ?: it.message) }
        }
    }

    /** The server's localized explanation, for the warning before deleting other people's content. */
    private fun DataError.Network.serverMessage(): UiText = UiText.Server((this as? DataError.Network.Api)?.message.orEmpty())
}
