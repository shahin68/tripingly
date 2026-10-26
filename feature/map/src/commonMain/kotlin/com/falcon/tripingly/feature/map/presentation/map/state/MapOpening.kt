package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.trips.TripMapViewRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMapView
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_load_trip
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Loads the trip and opens it on the day and Route button it was left with ([mapViews]), framed like a tap on that
 * day's tab ([MapCamera.showOpenDay]). Saves them when they change, so they're there after the app is closed too.
 */
internal class MapOpening(
    private val context: MapContext,
    private val camera: MapCamera,
    private val tripRepository: TripRepository,
    private val mapViews: TripMapViewRepository,
    private val hasRoutes: () -> Boolean,
) {
    fun start() {
        load()
        rememberView()
    }

    private fun load() {
        context.scope.launch {
            val result = tripRepository.refreshTrip(context.tripId)
            context.update { it.copy(isLoadingTrip = false) }
            context.showMessage((result as? AppResult.Error)?.error?.toUiText(Res.string.map_error_load_trip))
            // Without the trip, or a trip without days, there's nothing to show first: the map opens on the default.
            if (result is AppResult.Error || (result as? AppResult.Success)?.data?.days?.isEmpty() == true) camera.showAsItIs()
        }
        context.scope.launch {
            val left = mapViews.get(context.tripId)
            val details = context.trip.first { it != null && it.days.isNotEmpty() } ?: return@launch
            if (left != null) restoreView(left, details)
            camera.showOpenDay()
        }
    }

    /** The open day (the first one if it's gone) and the Route button, as they were left. */
    private fun restoreView(left: TripMapView, details: TripDetails) {
        context.update {
            it.copy(
                activeDayIndex = details.days.indexOfFirst { day -> day.id == left.dayId }.coerceAtLeast(0),
                // Routes are Pro: one left on stays off once Pro is gone.
                route = it.route.copy(isDayRouteShown = left.isRouteShown && hasRoutes(), travelMode = left.travelMode),
            )
        }
    }

    /** Saves the view once it settles, not on every quick change. */
    private fun rememberView() {
        context.scope.launch {
            combine(context.trip, context.model, ::viewOf).distinctUntilChanged().collectLatest { view ->
                view ?: return@collectLatest
                delay(SAVE_VIEW_DELAY_MILLIS)
                mapViews.save(context.tripId, view)
            }
        }
    }

    /** Null until the map shows the trip. */
    private fun viewOf(details: TripDetails?, model: MapModel): TripMapView? {
        if (!model.camera.isReady) return null
        val day = details?.days?.getOrNull(model.activeDayIndex) ?: return null
        return TripMapView(
            dayId = day.id,
            isRouteShown = model.route.isDayRouteShown,
            travelMode = model.route.travelMode,
        )
    }

    private companion object {
        /** How long the view stays the same before it's saved. */
        const val SAVE_VIEW_DELAY_MILLIS = 500L
    }
}
