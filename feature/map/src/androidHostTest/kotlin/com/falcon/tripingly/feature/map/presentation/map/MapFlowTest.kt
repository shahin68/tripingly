package com.falcon.tripingly.feature.map.presentation.map

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.asSuccess
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.config.FakeAppConfigRepository
import com.falcon.tripingly.core.data.photos.FakePhotoRepository
import com.falcon.tripingly.core.data.places.FakePlaceRepository
import com.falcon.tripingly.core.data.routing.FakeRouteRepository
import com.falcon.tripingly.core.data.social.FakeSocialRepository
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.LocationRepository
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.feature.map.presentation.FakeTripMapViewRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The trip map as a user drives it: real screen and view model, in-memory trips. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class MapFlowTest {

    private val backend = FakeTripBackend()
    private val trip: TripDetails = runBlocking {
        val created = (backend.createTrip(NewTrip("Vienna weekend", LocalDate(2026, 11, 6), LocalDate(2026, 11, 7))) as AppResult.Success).data
        listOf(
            "Stephansdom" to GeoPoint(48.2085, 16.3721),
            "Hofburg" to GeoPoint(48.2066, 16.3655),
            "Hotel Sacher" to GeoPoint(48.2038, 16.3696),
        ).forEach { (name, location) ->
            backend.addMarker(created.trip.id, created.days[0].id, NewMarker(name, location))
        }
        created
    }

    private fun viewModel() = MapViewModel(
        tripId = trip.trip.id,
        tripRepository = backend,
        markerRepository = backend,
        placeRepository = FakePlaceRepository(),
        routeRepository = FakeRouteRepository(),
        photoRepository = FakePhotoRepository(),
        socialRepository = FakeSocialRepository(),
        appConfigRepository = FakeAppConfigRepository(),
        mapViews = FakeTripMapViewRepository(),
        sessionRepository = FakeAccountBackend(today = { LocalDate(2026, 10, 16) }),
        getCurrentLocationUseCase = GetCurrentLocationUseCase(
            object : LocationRepository {
                override fun getLocationStream() = flowOf(Coordinates.Paris.asSuccess())
            },
        ),
    )

    private fun dayOneStops() = runBlocking {
        (backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.size
    }

    @Test
    fun `opening a trip shows the day's stops as chips`() = runComposeUiTest {
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, onOpenProfile = {}, onOpenPaywall = {}, membersContent = { _, _ -> }, viewModel = viewModel()) } }

        onNodeWithText("Vienna weekend").assertExists()
        onNodeWithText("3 stops").assertExists()
        // Each chip shows the stop's number next to its name.
        listOf("1", "2", "3").forEach { onNodeWithText(it).assertExists() }
        listOf("Stephansdom", "Hofburg", "Hotel Sacher").forEach { onNodeWithText(it).assertExists() }
    }

    @Test
    fun `the plan sheet pulls up and down from anywhere on it`() = runComposeUiTest {
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, onOpenProfile = {}, onOpenPaywall = {}, membersContent = { _, _ -> }, viewModel = viewModel()) } }
        // A trip with stops opens with the whole plan showing.
        onAllNodesWithContentDescription("Remove stop")[0].assertIsDisplayed()

        // Dragged by its title, not its handle.
        onNodeWithText("3 stops").performTouchInput { swipeDown() }
        waitForIdle()
        onAllNodesWithContentDescription("Remove stop")[0].assertIsNotDisplayed()

        onNodeWithText("3 stops").performTouchInput { swipeUp() }
        waitForIdle()
        onAllNodesWithContentDescription("Remove stop")[0].assertIsDisplayed()
    }

    @Test
    fun `adding a stop pulls the plan sheet all the way up`() = runComposeUiTest {
        val viewModel = viewModel()
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, onOpenProfile = {}, onOpenPaywall = {}, membersContent = { _, _ -> }, viewModel = viewModel) } }
        onNodeWithContentDescription("Show or hide the whole plan").performClick()
        waitForIdle()
        onAllNodesWithContentDescription("Remove stop")[0].assertIsNotDisplayed()

        viewModel.onAction(MapAction.MapView.Click(Coordinates(48.21, 16.37)))
        waitForIdle()
        onNodeWithText("4 stops").assertIsDisplayed()
        onAllNodesWithContentDescription("Remove stop")[0].assertIsDisplayed()

        // The first stop of an empty day: the sheet comes up whole, though it was pulled down before.
        onNodeWithContentDescription("Show or hide the whole plan").performClick()
        waitForIdle()
        viewModel.onAction(MapAction.Days.Select(1))
        waitForIdle()
        viewModel.onAction(MapAction.MapView.Click(Coordinates(48.21, 16.37)))
        waitForIdle()
        onNodeWithText("1 stop").assertIsDisplayed()
        onAllNodesWithContentDescription("Remove stop")[0].assertIsDisplayed()
    }

    @Test
    fun `a pulled-down plan comes back whole after a day without stops`() = runComposeUiTest {
        val viewModel = viewModel()
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, onOpenProfile = {}, onOpenPaywall = {}, membersContent = { _, _ -> }, viewModel = viewModel) } }
        onNodeWithContentDescription("Show or hide the whole plan").performClick()
        waitForIdle()
        onAllNodesWithContentDescription("Remove stop")[0].assertIsNotDisplayed()

        // The empty day has no sheet; back on a day with stops it comes up whole.
        viewModel.onAction(MapAction.Days.Select(1))
        waitForIdle()
        onNodeWithContentDescription("Show or hide the whole plan").assertDoesNotExist()
        viewModel.onAction(MapAction.Days.Select(0))
        waitForIdle()
        onAllNodesWithContentDescription("Remove stop")[0].assertIsDisplayed()
    }

    @Test
    fun `removing a chip deletes the stop and renumbers the rest`() = runComposeUiTest {
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, onOpenProfile = {}, onOpenPaywall = {}, membersContent = { _, _ -> }, viewModel = viewModel()) } }

        onAllNodesWithContentDescription("Remove stop")[1].performClick()

        onNodeWithText("2 stops").assertExists()
        onNodeWithText("Hofburg").assertDoesNotExist()
        onNodeWithText("3").assertDoesNotExist()
        assertEquals(2, dayOneStops())
    }

    @Test
    fun `clear all asks first and cancel keeps the stops`() = runComposeUiTest {
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, onOpenProfile = {}, onOpenPaywall = {}, membersContent = { _, _ -> }, viewModel = viewModel()) } }

        onNodeWithContentDescription("Day options").performClick()
        onNodeWithText("Clear day 1").performClick()
        onNodeWithText("Clear day 1?").assertExists()
        onNodeWithText("Cancel").performClick()

        onNodeWithText("Clear day 1?").assertDoesNotExist()
        onNodeWithText("3 stops").assertExists()
        assertEquals(3, dayOneStops())
    }

    @Test
    fun `clear all empties the day once confirmed`() = runComposeUiTest {
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, onOpenProfile = {}, onOpenPaywall = {}, membersContent = { _, _ -> }, viewModel = viewModel()) } }

        onNodeWithContentDescription("Day options").performClick()
        onNodeWithText("Clear day 1").performClick()
        onNodeWithText("Delete").performClick()

        waitUntil { dayOneStops() == 0 }
        onNodeWithText("Stephansdom").assertDoesNotExist()
    }

    @Test
    fun `a viewer can't change the stops`() = runComposeUiTest {
        val current = runBlocking { (backend.refreshTrip(trip.trip.id) as AppResult.Success).data }
        backend.seed(current.copy(trip = current.trip.copy(role = TripRole.VIEWER)))
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, onOpenProfile = {}, onOpenPaywall = {}, membersContent = { _, _ -> }, viewModel = viewModel()) } }

        onNodeWithText("Stephansdom").assertExists()
        onNodeWithContentDescription("Day options").assertDoesNotExist()
        onAllNodesWithContentDescription("Remove stop").assertCountEquals(0)
        assertEquals(3, dayOneStops())
    }

    @Test
    fun `the back button leaves the map`() = runComposeUiTest {
        var wentBack = false
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = { wentBack = true }, onOpenProfile = {}, onOpenPaywall = {}, membersContent = { _, _ -> }, viewModel = viewModel()) } }

        onNodeWithContentDescription("Back").performClick()

        waitForIdle()
        assertTrue(wentBack)
    }

    @Test
    fun `tapping the title opens the whole name and another touch closes it`() = runComposeUiTest {
        setContent { TripinglyTheme { MapRoute(trip.trip.id, onNavigateBack = {}, onOpenProfile = {}, onOpenPaywall = {}, membersContent = { _, _ -> }, viewModel = viewModel()) } }
        onAllNodesWithText("Vienna weekend").assertCountEquals(1)

        onNodeWithText("Vienna weekend").performClick()
        waitForIdle()
        onAllNodesWithText("Vienna weekend").assertCountEquals(2)

        onRoot().performTouchInput { click(center) }
        mainClock.advanceTimeBy(1_000)
        onAllNodesWithText("Vienna weekend").assertCountEquals(1)

        // A tap on the title opens it again, and a tap on it while open closes it.
        onNodeWithText("Vienna weekend").performClick()
        waitForIdle()
        onAllNodesWithText("Vienna weekend").assertCountEquals(2)
        onAllNodes(hasText("Vienna weekend") and hasClickAction()).onFirst().performClick()
        mainClock.advanceTimeBy(1_000)
        onAllNodesWithText("Vienna weekend").assertCountEquals(1)
    }
}

