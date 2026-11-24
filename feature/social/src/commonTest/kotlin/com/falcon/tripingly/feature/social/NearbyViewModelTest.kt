package com.falcon.tripingly.feature.social

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.config.FakeAppConfigRepository
import com.falcon.tripingly.core.data.places.FakePlaceRepository
import com.falcon.tripingly.core.data.users.FakeUserRepository
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.social.PeoplePage
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Action
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Event
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyViewModelTest : SocialTestBase() {

    /** The fake's people; follows are refused while [followRefused] is set. */
    private class RefusingUsers(private val fake: FakeUserRepository = FakeUserRepository()) : UserRepository by fake {
        var followRefused = false
        var peopleRefused = false

        override suspend fun findPeople(query: String?, near: GeoPoint?, cursor: String?): AppResult<PeoplePage, DataError.Network> =
            if (peopleRefused) AppResult.Error(DataError.Network.NoInternet) else fake.findPeople(query, near, cursor)

        override suspend fun setFollowing(userId: String, following: Boolean): AppResult<Unit, DataError.Network> =
            if (followRefused) AppResult.Error(DataError.Network.NoInternet) else fake.setFollowing(userId, following)
    }

    private val people = RefusingUsers()

    private fun viewModel(config: AppConfig = AppConfig()) =
        NearbyViewModel(social, FakePlaceRepository(), people, location, FakeAppConfigRepository(config))

    private fun TestScope.located(): NearbyViewModel = viewModel().also {
        it.onAction(Action.PermissionChecked(LocationPermission.Granted))
        advanceUntilIdle()
    }

    @Test
    fun `the OpenStreetMap credit shows unless the server leaves OpenStreetMap places out`() {
        assertTrue(viewModel().uiState.value.isOsmCreditShown)
        assertFalse(viewModel(AppConfig(osmPinsEnabled = false)).uiState.value.isOsmCreditShown)
    }

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
    fun aFlameMarkedElsewhere_showsInNearby_withoutAReload() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(Action.PermissionChecked(LocationPermission.Granted))
        advanceUntilIdle()
        val place = viewModel.uiState.value.places.first()

        // As the trip map does for a stop at this place.
        social.setHot(place.id, hot = true)
        advanceUntilIdle()

        val shown = viewModel.uiState.value.places.first()
        assertTrue(shown.hotByMe)
        assertEquals(99, shown.hotCount)
    }

    @Test
    fun `no people show until a search finds some`() = runTest {
        val viewModel = located()
        assertTrue(viewModel.uiState.value.search.people.people.isEmpty())

        viewModel.onAction(Action.QueryChanged("an"))
        advanceUntilIdle()
        assertEquals(listOf("anna"), viewModel.uiState.value.search.people.people.map { it.user.username })
    }

    @Test
    fun `the search asks once typing pauses from two characters and finds people and spots`() = runTest {
        val viewModel = located()

        // One character is not a search: the grid stays.
        viewModel.onAction(Action.QueryChanged("L"))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.search.isSearching)
        assertFalse(viewModel.uiState.value.search.hasLoaded)

        viewModel.onAction(Action.QueryChanged("Lo"))
        advanceTimeBy(100)
        viewModel.onAction(Action.QueryChanged("Le"))
        advanceTimeBy(299)
        assertFalse(viewModel.uiState.value.search.hasLoaded, "nothing asked before the pause")
        advanceUntilIdle()

        val search = viewModel.uiState.value.search
        assertTrue(search.hasLoaded)
        // The latest query won: Lena, and no Louvre.
        assertEquals(listOf("lena"), search.people.people.map { it.user.username })
        assertTrue(search.spots.none { it.name == "Louvre" })

        viewModel.onAction(Action.QueryChanged("Louv"))
        advanceUntilIdle()
        assertEquals(listOf("Louvre"), viewModel.uiState.value.search.spots.map { it.name })
        // Located: each spot with its distance.
        assertNotNull(viewModel.uiState.value.search.spots.single().distanceMeters)

        // Cleared: the grid comes back at once.
        viewModel.onAction(Action.QueryChanged(""))
        assertFalse(viewModel.uiState.value.search.isSearching)
    }

    @Test
    fun `Follow switches at once and stays and a refusal puts it back`() = runTest {
        val viewModel = located()
        viewModel.onAction(Action.QueryChanged("ju"))
        advanceUntilIdle()
        val found = { viewModel.uiState.value.search.people.people }
        val julia = found().first { it.user.username == "julia_t" }
        assertFalse(julia.isFollowing)

        viewModel.onAction(Action.ToggleFollow(julia))
        assertTrue(found().first { it.user.id == julia.user.id }.isFollowing)
        advanceUntilIdle()
        val followed = found().first { it.user.id == julia.user.id }
        assertTrue(followed.isFollowing)

        people.followRefused = true
        viewModel.onAction(Action.ToggleFollow(followed))
        assertFalse(found().first { it.user.id == julia.user.id }.isFollowing)
        advanceUntilIdle()
        assertTrue(found().first { it.user.id == julia.user.id }.isFollowing)
        assertNotNull(viewModel.uiState.value.message)
    }

    @Test
    fun `someone I block leaves the faces and the count at once`() = runTest {
        val viewModel = located()
        viewModel.onAction(Action.QueryChanged("an"))
        advanceUntilIdle()
        val anna = viewModel.uiState.value.search.people.people.single()

        people.setBlocked(anna.user.id, blocked = true)
        advanceUntilIdle()

        val shown = viewModel.uiState.value.search.people
        assertTrue(shown.people.isEmpty())
        assertEquals(0, shown.total)
    }

    @Test
    fun `the people sheet opens from the faces and closes`() = runTest {
        val viewModel = located()
        viewModel.onAction(Action.OpenPeople)
        assertTrue(viewModel.uiState.value.isPeopleSheetOpen)
        viewModel.onAction(Action.ClosePeople)
        assertFalse(viewModel.uiState.value.isPeopleSheetOpen)
    }

    @Test
    fun `a failed search shows no people of the last query`() = runTest {
        val viewModel = located()
        viewModel.onAction(Action.QueryChanged("Le"))
        advanceUntilIdle()
        assertEquals(listOf("lena"), viewModel.uiState.value.search.people.people.map { it.user.username })

        people.peopleRefused = true
        viewModel.onAction(Action.QueryChanged("Louv"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.search.people.people.isEmpty())
        assertEquals(listOf("Louvre"), viewModel.uiState.value.search.spots.map { it.name })
    }
}
