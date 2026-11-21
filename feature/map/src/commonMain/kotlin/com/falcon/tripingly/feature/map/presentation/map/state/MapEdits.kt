package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.trips.MarkerChange
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.TripErrorCodes
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.model.place.Brand
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_change_not_saved
import com.falcon.tripingly.feature.map.generated.resources.map_error_add_day
import com.falcon.tripingly.feature.map.generated.resources.map_error_delete_day
import com.falcon.tripingly.feature.map.generated.resources.map_error_stop_change
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
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
    private val placeRepository: PlaceRepository,
    private val myUserId: String?,
    private val onStopAdded: () -> Unit,
) {
    init {
        context.scope.launch {
            markerRepository.failures.collect { failure ->
                if (failure.tripId != context.tripId) return@collect
                stopNaming(failure.change.markerId)
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
        // A name the server gave ends the wait; so does the stop going (deleted here or by someone else).
        context.scope.launch {
            context.trip.collect { details ->
                val markers = details?.days?.flatMap { it.markers }?.associateBy { it.id } ?: return@collect
                context.model.value.namingStopIds.filter { markers[it]?.name?.isNotBlank() == true }.forEach(::stopNaming)
            }
        }
    }

    /** The stops being named, each with its wait: a stop the server can't name stops spinning after [NAMING_TIMEOUT]. */
    private val namingJobs = mutableMapOf<String, Job>()

    private fun startNaming(markerId: String) {
        namingJobs.remove(markerId)?.cancel()
        context.update { it.copy(namingStopIds = it.namingStopIds + markerId) }
        namingJobs[markerId] = context.scope.launch {
            delay(NAMING_TIMEOUT)
            stopNaming(markerId)
        }
    }

    private fun stopNaming(markerId: String) {
        namingJobs.remove(markerId)?.cancel()
        if (markerId in context.model.value.namingStopIds) context.update { it.copy(namingStopIds = it.namingStopIds - markerId) }
    }

    fun retryFailedChanges() {
        val failed = context.model.value.failedChanges
        context.update { it.copy(message = null, failedChanges = emptyList()) }
        // A nameless stop sent again waits for its name again.
        failed.filter { it.change is MarkerChange.Add && it.markerName.isBlank() }.forEach { startNaming(it.change.markerId) }
        context.scope.launch { failed.forEach { markerRepository.retry(it) } }
    }

    /** A tap on the map itself (pins take their own taps) adds a stop, unless it only closed a card. */
    fun onMapClick(coordinates: Coordinates) {
        if (selection.isClosingTouch) return
        // No name: the server names the stop after the place or address there.
        add(NewMarker("", GeoPoint(coordinates.latitude, coordinates.longitude)))
    }

    /** A long press drops a new spot there, made in the card over it (name, kind of place, photo). */
    fun onMapLongClick(coordinates: Coordinates) {
        if (context.canEdit() && context.activeDay() != null) selection.openNewSpot(coordinates)
    }

    fun pickSpotCategory(category: PlaceCategory) = selection.updateNewSpot { it.copy(category = category) }

    fun setSpotPhoto(photo: PickedImage?) = selection.updateNewSpot { it.copy(photo = photo) }

    private var brandsJob: Job? = null

    /**
     * Once typing pauses, the brands named like the spot (from [MIN_BRAND_QUERY] letters) are offered for "This is a
     * brand store"; a picked brand stays picked while it's still offered or the name still says it.
     */
    fun onSpotNameChanged(name: String) {
        brandsJob?.cancel()
        val query = name.trim()
        if (query.length < MIN_BRAND_QUERY) {
            selection.updateNewSpot { it.copy(brands = emptyList(), brand = null) }
            return
        }
        brandsJob = context.scope.launch {
            delay(BRAND_SEARCH_DEBOUNCE)
            val found = (placeRepository.brands(query) as? AppResult.Success)?.data ?: return@launch
            selection.updateNewSpot { spot ->
                // The picked one first, so it doesn't jump away while the name is still being typed.
                val picked = spot.brand?.takeIf { brand -> brand in found || query.contains(brand.name, ignoreCase = true) }
                spot.copy(brands = listOfNotNull(picked) + found.filter { it != picked }, brand = picked)
            }
        }
    }

    /** Ties the spot to [brand] (one of ours, never a new one), or unties it. */
    fun pickSpotBrand(brand: Brand?) = selection.updateNewSpot { it.copy(brand = brand) }

    /** Create: the place with its kind and the stop on the open day at once, its photo once the server has the stop. */
    fun createSpot() {
        val spot = context.model.value.selection as? Selection.NewSpot ?: return
        val name = spot.name.text.toString().trim()
        if (name.isEmpty()) return
        selection.closeNewSpot()
        brandsJob?.cancel()
        add(NewMarker(name, GeoPoint(spot.location.latitude, spot.location.longitude), category = spot.category, photo = spot.photo, brandId = spot.brand?.id))
    }

    /** A stop at the open place or address, named after it. */
    fun addPlace() {
        val place = selection.selectedPlace ?: return
        if (context.activeDay() == null) return
        selection.closePlace()
        add(place.toNewMarker())
    }

    /** My own stop goes at once; someone else's (only the owner may) only once the user agreed in a dialog. */
    fun removeMarker(markerId: String) {
        if (!context.canEdit()) return
        val marker = context.days().firstNotNullOfOrNull { day -> day.markers.firstOrNull { it.id == markerId } } ?: return
        if (marker.createdById != myUserId) {
            context.update { it.copy(dialog = Dialog.DeleteOthersStop(markerId)) }
            return
        }
        deleteMarker(markerId, confirm = false)
    }

    private fun deleteMarker(markerId: String, confirm: Boolean) {
        stopNaming(markerId)
        selection.closeStop(markerId)
        context.scope.launch { markerRepository.deleteMarker(markerId, confirm) }
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
            is Dialog.DeleteOthersStop -> deleteMarker(dialog.markerId, confirm = true)
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
        context.scope.launch {
            val added = markerRepository.addMarker(context.tripId, day.id, marker)
            // A tap sends no name: the server names the stop; the trip may already carry the name by now.
            if (marker.name.isBlank() && context.trip.value?.days?.flatMap { it.markers }?.firstOrNull { it.id == added.id }?.name?.isNotBlank() != true) {
                startNaming(added.id)
            }
        }
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

/** A stop for the place, or for the address from search, named after it. */
internal fun Selection.Place.toNewMarker(): NewMarker = address
    ?.let { NewMarker(it.name, it.location, osm = it.osm) }
    ?: NewMarker(place.name, place.location, placeId = place.id)

/** How long a nameless stop's title spins at most, when the server gives it no name. */
private val NAMING_TIMEOUT = 20.seconds

/** Typing has paused; see the search rule in 07-architecture. */
private val BRAND_SEARCH_DEBOUNCE = 300.milliseconds

/** Brands are looked for from two letters. */
private const val MIN_BRAND_QUERY = 2
