package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.falcon.tripingly.core.common.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.asSuccess
import com.falcon.tripingly.core.data.places.FakePlaceRepository
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.repository.LocationRepository
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The trip map as a user drives it: real screen and view model, in-memory trips. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class MapFlowTest {

    private class MainDispatchers : CoroutineDispatchers {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val io: CoroutineDispatcher = Dispatchers.Main
        override val default: CoroutineDispatcher = Dispatchers.Main
        override val unconfined: CoroutineDispatcher = Dispatchers.Main
    }

    private val backend = FakeTripBackend()
    private val trip: TripDetails = runBlocking {
        val created = (backend.createTrip(NewTrip("Vienna weekend", LocalDate(2026, 11, 6), LocalDate(2026, 11, 7))) as AppResult.Success).data
        listOf(GeoPoint(48.2085, 16.3721), GeoPoint(48.2066, 16.3655), GeoPoint(48.2038, 16.3696)).forEach {
            backend.addMarker(created.trip.id, created.days[0].id, NewMarker("Stop", it))
        }
        created
    }

    private fun viewModel() = MapViewModel(
        tripId = trip.trip.id,
        tripRepository = backend,
        markerRepository = backend,
        placeRepository = FakePlaceRepository(),
        getCurrentLocationUseCase = GetCurrentLocationUseCase(
            object : LocationRepository {
                override fun getLocationStream() = flowOf(Coordinates.Paris.asSuccess())
            },
        ),
        dispatchers = MainDispatchers(),
    )

    private fun dayOneStops() = runBlocking {
        (backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.size
    }

    @Test
    fun `opening a trip shows the day's stops as chips`() = runComposeUiTest {
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, viewModel = viewModel()) } }

        onNodeWithText("Vienna weekend").assertExists()
        onNodeWithText("Trip Plan (3 stops)").assertExists()
        listOf("Stop #1", "Stop #2", "Stop #3").forEach { onNodeWithText(it).assertExists() }
    }

    @Test
    fun `removing a chip deletes the stop and renumbers the rest`() = runComposeUiTest {
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, viewModel = viewModel()) } }

        onAllNodesWithContentDescription("Remove stop")[1].performClick()

        onNodeWithText("Trip Plan (2 stops)").assertExists()
        onNodeWithText("Stop #3").assertDoesNotExist()
        assertEquals(2, dayOneStops())
    }

    @Test
    fun `clear all asks first and cancel keeps the stops`() = runComposeUiTest {
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, viewModel = viewModel()) } }

        onNodeWithText("Clear All").performClick()
        onNodeWithText("Clear day 1?").assertExists()
        onNodeWithText("Cancel").performClick()

        onNodeWithText("Clear day 1?").assertDoesNotExist()
        onNodeWithText("Trip Plan (3 stops)").assertExists()
        assertEquals(3, dayOneStops())
    }

    @Test
    fun `clear all empties the day once confirmed`() = runComposeUiTest {
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, viewModel = viewModel()) } }

        onNodeWithText("Clear All").performClick()
        onNodeWithText("Delete").performClick()

        waitUntil { dayOneStops() == 0 }
        onNodeWithText("Stop #1").assertDoesNotExist()
    }

    @Test
    fun `a viewer can't change the stops`() = runComposeUiTest {
        val current = runBlocking { (backend.refreshTrip(trip.trip.id) as AppResult.Success).data }
        backend.seed(current.copy(trip = current.trip.copy(role = TripRole.VIEWER)))
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, viewModel = viewModel()) } }

        onNodeWithText("Stop #1").assertExists()
        onNodeWithText("Clear All").assertDoesNotExist()
        onAllNodesWithContentDescription("Remove stop").assertCountEquals(0)
        assertEquals(3, dayOneStops())
    }

    @Test
    fun `the back button leaves the map`() = runComposeUiTest {
        var wentBack = false
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = { wentBack = true }, viewModel = viewModel()) } }

        onNodeWithContentDescription("Back").performClick()

        waitForIdle()
        assertTrue(wentBack)
    }
}
