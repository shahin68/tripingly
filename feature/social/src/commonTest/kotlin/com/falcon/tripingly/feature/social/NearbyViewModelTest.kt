package com.falcon.tripingly.feature.social

import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.social.ExploreFilter
import com.falcon.tripingly.core.model.social.ExplorePeriod
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.GeoPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.falcon.tripingly.core.data.places.FakePlaceRepository
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Action
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Event
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Status

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyViewModelTest : SocialTestBase() {

    private fun viewModel() = NearbyViewModel(social, FakePlaceRepository(), backend, backend, location)

    @Test
    fun nearby_asksFirst_thenSettingsWhenBlocked_andListsPlacesOnceAllowed() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.PermissionChecked(LocationPermission.NotAsked))
        assertEquals(Status.NeedsPermission, viewModel.uiState.value.status)
        viewModel.onAction(Action.AllowLocation)
        assertEquals(Event.RequestPermission, viewModel.events.first())

        viewModel.onAction(Action.PermissionResult(LocationPermission.Blocked))
        assertEquals(Status.Blocked, viewModel.uiState.value.status)
        viewModel.onAction(Action.AllowLocation)
        assertEquals(Event.OpenAppSettings, viewModel.events.first())

        viewModel.onAction(Action.PermissionChecked(LocationPermission.Granted))
        advanceUntilIdle()
        assertEquals(Status.Ready, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.places.isNotEmpty())
    }

    @Test
    fun aNearbyPlacesFlame_showsAtOnce_andARefusalPutsItBack() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(Action.PermissionChecked(LocationPermission.Granted))
        advanceUntilIdle()
        val place = viewModel.uiState.value.places.first()

        likeRefused = true
        viewModel.onAction(Action.MarkHot(place))
        val shown = viewModel.uiState.value.places.first()
        assertTrue(shown.hotByMe, "the flame lights at once")
        assertEquals(place.hotCount + 1, shown.hotCount)
        advanceUntilIdle()
        val back = viewModel.uiState.value.places.first()
        assertFalse(back.hotByMe, "a refusal puts the flame back")
        assertEquals(place.hotCount, back.hotCount)
    }

    @Test
    fun aNearbyPlacesImages_openFromItsCover_andACommonsImageIsReportedAsAPlaceImage() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(Action.PermissionChecked(LocationPermission.Granted))
        advanceUntilIdle()
        val place = viewModel.uiState.value.places.first()

        viewModel.onAction(Action.OpenImage(place))
        assertEquals(place, viewModel.uiState.value.openImage)

        val commons = commonsImage("commons-1")
        viewModel.onAction(Action.ReportImage(commons))
        viewModel.onAction(Action.SendReport(ReportReason.Other))
        viewModel.onAction(Action.ReportImage(commons.copy(id = "photo-1", credit = null)))
        viewModel.onAction(Action.SendReport(ReportReason.Other))
        advanceUntilIdle()
        assertEquals(listOf(ReportTarget.PlaceImage to "commons-1", ReportTarget.Photo to "photo-1"), reported)

        viewModel.onAction(Action.CloseImage)
        assertEquals(null, viewModel.uiState.value.openImage)
    }

    @Test
    fun addingAPlace_withoutTripsOfMine_says_so() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(Action.PermissionChecked(LocationPermission.Granted))
        advanceUntilIdle()

        viewModel.onAction(Action.AddPlace(viewModel.uiState.value.places.first()))
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.addPlace)
        assertTrue(viewModel.uiState.value.message != null)
    }

    @Test
    fun addingAPlace_pickTripThenDay_addsTheStop_andOpensTheTrip() = runTest {
        val vienna = publicTrip().trip.id
        val viewModel = viewModel()
        viewModel.onAction(Action.PermissionChecked(LocationPermission.Granted))
        advanceUntilIdle()
        val place = viewModel.uiState.value.places.first()

        viewModel.onAction(Action.AddPlace(place))
        advanceUntilIdle()
        val trip = viewModel.uiState.value.addPlace!!.trips.single()
        viewModel.onAction(Action.AddToTrip.PickTrip(trip))
        advanceUntilIdle()
        viewModel.onAction(Action.AddToTrip.PickDay(viewModel.uiState.value.addPlace!!.days.first()))
        advanceUntilIdle()

        assertEquals(Event.OpenTrip(vienna), viewModel.events.first())
        assertEquals(listOf(place.name), backend.observeTrip(vienna).first()!!.days.first().markers.map { it.name })
    }
}
