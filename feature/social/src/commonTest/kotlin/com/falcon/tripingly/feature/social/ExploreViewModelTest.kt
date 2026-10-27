package com.falcon.tripingly.feature.social

import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.social.ExploreFilter
import com.falcon.tripingly.core.model.social.ExplorePeriod
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.feature.social.presentation.explore.ExploreViewModel
import com.falcon.tripingly.feature.social.presentation.explore.ExploreViewModel.Action
import com.falcon.tripingly.feature.social.presentation.explore.ExploreViewModel.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest : SocialTestBase() {

    private fun viewModel() = ExploreViewModel(social, users, backend, location)

    @Test
    fun likingATrip_showsAtOnce_andKeepsIt() = runTest {
        explore = listOf(publicTrip())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.LikeTrip(viewModel.uiState.value.trips.single()))
        assertTrue(viewModel.uiState.value.trips.single().likedByMe)
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.trips.single().likeCount)
    }

    @Test
    fun likingATrip_refused_putsItBack() = runTest {
        explore = listOf(publicTrip())
        likeRefused = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.LikeTrip(viewModel.uiState.value.trips.single()))
        advanceUntilIdle()

        val trip = viewModel.uiState.value.trips.single()
        assertFalse(trip.likedByMe)
        assertEquals(4, trip.likeCount)
        assertTrue(viewModel.uiState.value.message != null)
    }

    @Test
    fun savingATrip_showsAtOnce_andPutsItInMyTrips() = runTest {
        explore = listOf(othersTrip())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.ToggleSaveTrip(viewModel.uiState.value.trips.single()))
        val trip = viewModel.uiState.value.trips.single()
        assertTrue(trip.trip.savedByMe)
        assertEquals(3, trip.saveCount)
        advanceUntilIdle()

        assertEquals(listOf("t-anna"), backend.observeMyTrips().first().map { it.id })
    }

    @Test
    fun savingMyOwnTrip_refused_putsItBack() = runTest {
        explore = listOf(publicTrip())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.ToggleSaveTrip(viewModel.uiState.value.trips.single()))
        advanceUntilIdle()

        val trip = viewModel.uiState.value.trips.single()
        assertFalse(trip.trip.savedByMe)
        assertEquals(0, trip.saveCount)
        assertTrue(viewModel.uiState.value.message != null)
    }

    @Test
    fun explore_opensOnThisMonth_andAPickedPeriodReloadsTheList() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.PeriodSelected(ExplorePeriod.Week))
        viewModel.onAction(Action.PeriodSelected(ExplorePeriod.Week))
        advanceUntilIdle()

        assertEquals(listOf(ExploreFilter(ExplorePeriod.Month), ExploreFilter(ExplorePeriod.Week)), filters)
        assertEquals(ExplorePeriod.Week, viewModel.uiState.value.period)
    }

    @Test
    fun exploreNearby_asksForTheLocationOnlyWhenSwitchedOn() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.ToggleNearby)
        advanceUntilIdle()
        assertEquals(Event.CheckLocation, viewModel.events.first())

        viewModel.onAction(Action.LocationChecked(LocationPermission.NotAsked))
        advanceUntilIdle()
        assertEquals(Event.RequestPermission, viewModel.events.first())

        viewModel.onAction(Action.LocationPermissionResult(LocationPermission.Granted))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isNearbyFilter, "Nearby is on once allowed")
        val paris = GeoPoint(Coordinates.Paris.latitude, Coordinates.Paris.longitude)
        assertEquals(ExploreFilter(ExplorePeriod.Month, near = paris), filters.last(), "loads the trips near the user")

        viewModel.onAction(Action.ToggleNearby)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isNearbyFilter, "Nearby switches off")
        assertEquals(ExploreFilter(ExplorePeriod.Month), filters.last(), "reloads without the location")
    }

    @Test
    fun exploreNearby_staysOffWhenTheLocationIsRefused() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.ToggleNearby)
        viewModel.onAction(Action.LocationChecked(LocationPermission.ShouldExplain))
        viewModel.onAction(Action.LocationPermissionResult(LocationPermission.ShouldExplain))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isNearbyFilter)

        viewModel.onAction(Action.LocationChecked(LocationPermission.Blocked))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isNearbyFilter)
        assertTrue(viewModel.uiState.value.message != null)
        assertEquals(1, filters.size)
    }

    @Test
    fun fastTaps_theLatestWins_andARefusalPutsBackTheLastConfirmedHeart() = runTest {
        explore = listOf(publicTrip())
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(Action.LikeTrip(viewModel.uiState.value.trips.single()))
        advanceUntilIdle()

        likeRefused = true
        repeat(3) { viewModel.onAction(Action.LikeTrip(viewModel.uiState.value.trips.single())) }
        assertFalse(viewModel.uiState.value.trips.single().likedByMe, "the third tap shows at once")
        advanceUntilIdle()

        val trip = viewModel.uiState.value.trips.single()
        assertTrue(trip.likedByMe, "back to the heart the server confirmed")
        assertEquals(5, trip.likeCount)
    }

    @Test
    fun explore_loadsMoreAtTheEnd_withoutRepeats() = runTest {
        val vienna = publicTrip()
        explore = listOf(vienna)
        exploreMore = listOf(vienna, othersTrip())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.LoadMore)
        viewModel.onAction(Action.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf("Vienna", "Budapest"), viewModel.uiState.value.trips.map { it.trip.name })
    }

    @Test
    fun reportingATrip_closesTheDialogOnceSent_andStaysOpenWhenRefused() = runTest {
        explore = listOf(othersTrip())
        val viewModel = viewModel()
        advanceUntilIdle()
        val trip = viewModel.uiState.value.trips.single()

        reportRefused = true
        viewModel.onAction(Action.ReportTrip(trip))
        viewModel.onAction(Action.SendReport(ReportReason.Spam))
        advanceUntilIdle()
        assertEquals(ReportTarget.Trip to trip.trip.id, viewModel.uiState.value.report?.let { it.target to it.id }, "open again for another try")
        assertTrue(viewModel.uiState.value.message != null)

        reportRefused = false
        viewModel.onAction(Action.SendReport(ReportReason.Spam))
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.report)
    }

    @Test
    fun blockingSomeone_dropsTheirTrips_atOnce() = runTest {
        explore = listOf(othersTrip(), publicTrip())
        val viewModel = viewModel()
        advanceUntilIdle()

        users.setBlocked("u-anna", blocked = true)
        advanceUntilIdle()

        assertEquals(listOf("Vienna"), viewModel.uiState.value.trips.map { it.trip.name })
    }

    @Test
    fun aFirstPageThatFails_showsTheErrorScreen_andARetryThatWorksShowsTheTrips() = runTest {
        var refused = true
        val failing = object : com.falcon.tripingly.core.data.social.SocialRepository by social {
            override suspend fun explore(filter: ExploreFilter, cursor: String?) =
                if (refused) com.falcon.tripingly.core.common.result.AppResult.Error(com.falcon.tripingly.core.common.error.DataError.Network.NoInternet) else social.explore(filter, cursor)
        }
        explore = listOf(publicTrip())
        val viewModel = ExploreViewModel(failing, users, backend, location)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loadError != null, "nothing to show: the error screen")
        assertEquals(null, viewModel.uiState.value.message, "no banner on top of it")

        refused = false
        viewModel.onAction(Action.Refresh)
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.loadError)
        assertEquals(1, viewModel.uiState.value.trips.size)
    }
}
