package com.falcon.tripingly.feature.social

import com.falcon.tripingly.core.data.config.FakeAppConfigRepository
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.config.ConfigItem
import com.falcon.tripingly.core.model.social.ExploreFilter
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

    private val periods = listOf(
        ConfigItem("week", "This week", 60),
        ConfigItem("month", "This month", 60, isDefault = true),
        ConfigItem("year", "This year", 60),
        ConfigItem("all", "All time", 61),
    )
    private val appConfig = FakeAppConfigRepository(AppConfig(explorePeriods = periods))

    private fun viewModel() = ExploreViewModel(social, users, backend, location, appConfig)

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
    fun likingATrip_showsOnEveryScreen_withoutAReload() = runTest {
        explore = listOf(publicTrip())
        val here = viewModel()
        val elsewhere = viewModel()
        advanceUntilIdle()

        here.onAction(Action.LikeTrip(here.uiState.value.trips.single()))
        advanceUntilIdle()

        val trip = elsewhere.uiState.value.trips.single()
        assertTrue(trip.likedByMe)
        assertEquals(5, trip.likeCount)
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

        viewModel.onAction(Action.PeriodSelected("week"))
        viewModel.onAction(Action.PeriodSelected("week"))
        advanceUntilIdle()

        assertEquals(listOf(ExploreFilter("month"), ExploreFilter("week")), filters)
        assertEquals("week", viewModel.uiState.value.period)
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
        assertEquals(ExploreFilter("month", near = paris), filters.last(), "loads the trips near the user")

        viewModel.onAction(Action.ToggleNearby)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isNearbyFilter, "Nearby switches off")
        assertEquals(ExploreFilter("month"), filters.last(), "reloads without the location")
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
        viewModel.onAction(Action.SendReport("spam"))
        advanceUntilIdle()
        assertEquals(ReportTarget.Trip to trip.trip.id, viewModel.uiState.value.report?.let { it.target to it.id }, "open again for another try")
        assertTrue(viewModel.uiState.value.message != null)

        reportRefused = false
        viewModel.onAction(Action.SendReport("spam"))
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
        val viewModel = ExploreViewModel(failing, users, backend, location, appConfig)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loadError != null, "nothing to show: the error screen")
        assertEquals(null, viewModel.uiState.value.message, "no banner on top of it")

        refused = false
        viewModel.onAction(Action.Refresh)
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.loadError)
        assertEquals(1, viewModel.uiState.value.trips.size)
    }

    @Test
    fun aRetryThatFailsAgain_keepsTheErrorScreen_andSaysWhyInTheBanner() = runTest {
        val failing = object : com.falcon.tripingly.core.data.social.SocialRepository by social {
            override suspend fun explore(filter: ExploreFilter, cursor: String?) =
                com.falcon.tripingly.core.common.result.AppResult.Error(com.falcon.tripingly.core.common.error.DataError.Network.NoInternet)
        }
        val viewModel = ExploreViewModel(failing, users, backend, location, appConfig)
        advanceUntilIdle()

        viewModel.onAction(Action.Refresh)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loadError != null, "still nothing to show: the error screen")
        assertEquals(viewModel.uiState.value.loadError, viewModel.uiState.value.message, "and the reason in the banner")
    }

    @Test
    fun periodsComeFromTheServer_withItsDefaultPicked() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("week", "month", "year", "all"), viewModel.uiState.value.periods.map { it.key })
        assertEquals("This month", viewModel.uiState.value.pickedPeriod?.label)
    }

    @Test
    fun aNewLanguageRelabelsThePeriods_andKeepsThePickedOne() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(Action.PeriodSelected("year"))
        advanceUntilIdle()

        appConfig.config.value = AppConfig(explorePeriods = periods.map { it.copy(label = it.label.uppercase()) })
        advanceUntilIdle()

        assertEquals("THIS YEAR", viewModel.uiState.value.pickedPeriod?.label)
    }
}
