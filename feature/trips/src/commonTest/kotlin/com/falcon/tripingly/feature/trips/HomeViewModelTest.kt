package com.falcon.tripingly.feature.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.places.FakePlaceRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.data.users.FakeUserRepository
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.OnboardingState
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.Destination
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMember
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_error_members
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_member_blocked
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_member_not_found
import com.falcon.tripingly.feature.trips.generated.resources.home_error_make_private
import com.falcon.tripingly.feature.trips.generated.resources.home_offline_cached
import com.falcon.tripingly.feature.trips.generated.resources.reschedule_error_days_not_empty
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Dialog
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val backend = FakeTripBackend()

    /** Counts what reaches the server. */
    private val places = object : PlaceRepository by FakePlaceRepository() {
        val queries = mutableListOf<String>()
        private val fake = FakePlaceRepository()
        override suspend fun searchDestinations(query: String): AppResult<List<PlaceSearchResult>, DataError.Network> {
            queries += query
            return fake.searchDestinations(query)
        }
    }

    /** Counts what reaches the server. */
    private val users = object : UserRepository by FakeUserRepository() {
        val queries = mutableListOf<String>()
        private val fake = FakeUserRepository()
        override suspend fun search(query: String): AppResult<List<UserSummary>, DataError.Network> {
            queries += query
            return fake.search(query)
        }
    }

    /** Signed in; [defaultVisibility] is the account's `defaultTripVisibility`. */
    private var defaultVisibility = TripVisibility.PUBLIC
    private val session = object : SessionRepository by FakeAccountBackend(today = { LocalDate(2026, 10, 11) }) {
        override val session: StateFlow<SessionState>
            get() = MutableStateFlow(
                SessionState.SignedIn(
                    Account(
                        id = "me",
                        username = "me",
                        displayName = "Me",
                        birthDate = null,
                        locale = "en",
                        defaultTripVisibility = defaultVisibility,
                        isAdmin = false,
                        onboarding = OnboardingState(completed = true, missingProfileFields = emptySet(), missingConsents = emptySet()),
                        entitlements = emptySet(),
                    ),
                ),
            )
    }

    private val anna = UserSummary("00000000-0000-4000-8000-000000000101", "anna", "Anna")
    private val jonas = UserSummary("00000000-0000-4000-8000-000000000102", "jonas.k", "Jonas")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** A view model whose state is collected, as the screen does. */
    private fun TestScope.viewModel(): HomeViewModel = HomeViewModel(backend, places, users, session).also { viewModel ->
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private suspend fun seedTrip(name: String, role: TripRole = TripRole.OWNER): TripDetails {
        val created = (backend.createTrip(NewTrip(name, LocalDate(2026, 6, 1), LocalDate(2026, 6, 3))) as AppResult.Success).data
        if (role == TripRole.OWNER) return created
        val shared = created.copy(
            trip = created.trip.copy(role = role, owner = UserSummary("u2", "anna", "Anna")),
            members = listOf(TripMember(UserSummary("u2", "anna", "Anna"), TripRole.OWNER)),
        )
        backend.seed(shared)
        return shared
    }

    @Test
    fun start_loadsMyTrips() = runTest {
        seedTrip("Paris")
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasLoaded)
        assertFalse(state.isRefreshing)
        assertEquals(listOf("Paris"), state.trips.map { it.name })
    }

    @Test
    fun refreshOffline_showsTheOfflineBanner_andKeepsTheTrips() = runTest {
        seedTrip("Paris")
        backend.nextError = DataError.Network.NoInternet
        val viewModel = viewModel()
        advanceUntilIdle()

        // A note, not an error: the banner shows it in neutral colors.
        val message = assertIs<UiText.Resource>(assertIs<UiText.Note>(viewModel.uiState.value.message).text)
        assertEquals(Res.string.home_offline_cached, message.resource)
        assertEquals(1, viewModel.uiState.value.trips.size)
    }

    @Test
    fun createTrip_closesTheDialog_whenTheServerAccepts() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.createTrip("Rome", LocalDate(2026, 7, 1), LocalDate(2026, 7, 2))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.dialog)
        assertFalse(state.isSaving)
        assertEquals(listOf("Rome"), state.trips.map { it.name })
        assertEquals(2, state.trips.single().dayCount)
    }

    @Test
    fun createTrip_refused_keepsTheDialogOpenWithTheServersMessage() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        backend.nextError = DataError.Network.Api(422, "LIMIT_REACHED", "You've reached the trip limit.")

        viewModel.createTrip("Rome", LocalDate(2026, 7, 1), LocalDate(2026, 7, 2))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Dialog.Create, state.dialog)
        assertEquals(UiText.Server("You've reached the trip limit."), state.dialogError)
        assertTrue(state.trips.isEmpty())
    }

    @Test
    fun reschedule_thatWouldDropDaysWithStops_explainsWhy() = runTest {
        val trip = seedTrip("Paris")
        backend.addMarker(trip.trip.id, trip.days.last().id, NewMarker("Louvre", GeoPoint(48.86, 2.33)))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnRescheduleTrip(trip.trip.id))
        viewModel.onAction(Action.OnConfirmRescheduleTrip(trip.trip.id, LocalDate(2026, 6, 1), LocalDate(2026, 6, 1)))
        advanceUntilIdle()

        val error = assertIs<UiText.Resource>(viewModel.uiState.value.dialogError)
        assertEquals(Res.string.reschedule_error_days_not_empty, error.resource)
        assertIs<Dialog.Reschedule>(viewModel.uiState.value.dialog)
    }

    @Test
    fun rename_updatesTheList() = runTest {
        val trip = seedTrip("Paris")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnRenameTrip(trip.trip.id))
        viewModel.onAction(Action.OnConfirmRenameTrip(trip.trip.id, "Paris in June"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals("Paris in June", viewModel.uiState.value.trips.single().name)
    }

    @Test
    fun delete_asksFirst_thenRemovesTheTrip() = runTest {
        val trip = seedTrip("Paris")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnDeleteTrip(trip.trip.id))
        advanceUntilIdle()
        assertIs<Dialog.ConfirmDelete>(viewModel.uiState.value.dialog)
        assertEquals(1, backend.observeMyTrips().first().size)

        viewModel.onAction(Action.OnConfirmDeleteTrip(trip.trip.id))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.trips.isEmpty())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun leave_removesASharedTrip() = runTest {
        val trip = seedTrip("Anna's trip", role = TripRole.EDITOR)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnLeaveTrip(trip.trip.id))
        viewModel.onAction(Action.OnConfirmLeaveTrip(trip.trip.id))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.trips.isEmpty())
    }

    @Test
    fun toggleVisibility_makesAPublicTripPrivate() = runTest {
        val trip = seedTrip("Paris")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnToggleVisibility(trip.trip.id))
        advanceUntilIdle()

        assertEquals(TripVisibility.PRIVATE, viewModel.uiState.value.trips.single().visibility)
    }

    @Test
    fun toggleVisibility_refused_showsTheMessage() = runTest {
        val trip = seedTrip("Paris")
        val viewModel = viewModel()
        advanceUntilIdle()
        backend.nextError = DataError.Network.NoInternet

        viewModel.onAction(Action.OnToggleVisibility(trip.trip.id))
        advanceUntilIdle()

        assertEquals(TripVisibility.PUBLIC, viewModel.uiState.value.trips.single().visibility)
        // What failed, then why.
        val message = assertIs<UiText.Joined>(viewModel.uiState.value.message)
        assertEquals(Res.string.home_error_make_private, assertIs<UiText.Resource>(message.first).resource)
        assertTrue(assertIs<UiText.Resource>(message.second).resource.key.contains("no_internet"))
    }

    @Test
    fun search_filtersByName() = runTest {
        seedTrip("Paris")
        seedTrip("Rome")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnSearchQueryChanged("ro"))
        advanceUntilIdle()

        assertEquals(listOf("Rome"), viewModel.uiState.value.trips.map { it.name })
        assertTrue(viewModel.uiState.value.hasTrips)
    }

    @Test
    fun tripClick_opensTheMap() = runTest {
        val trip = seedTrip("Paris")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnTripClick(trip.trip.id))

        assertEquals(Event.NavigateToMap(trip.trip.id), viewModel.events.first())
    }

    @Test
    fun destinationSearch_waitsForTypingToPause_andAsksOnceForTheLastText() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(Action.OnAddTripClick)

        listOf("P", "Pa", "Par", "Pari").forEach {
            viewModel.onAction(Action.OnDestinationQueryChanged(it))
            advanceTimeBy(100)
        }
        assertTrue(places.queries.isEmpty())

        advanceTimeBy(250)
        assertEquals(listOf("Pari"), places.queries)
        assertEquals(listOf("Paris"), viewModel.uiState.value.createTrip.destinationResults.map { it.name })
        assertFalse(viewModel.uiState.value.createTrip.isSearchingDestination)
    }

    @Test
    fun destinationSearch_startsFromTheFirstLetter() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(Action.OnAddTripClick)

        viewModel.onAction(Action.OnDestinationQueryChanged("B"))
        advanceUntilIdle()

        assertEquals(listOf("B"), places.queries)
        assertEquals(listOf("Budapest", "Berlin"), viewModel.uiState.value.createTrip.destinationResults.map { it.name })
    }

    @Test
    fun destinationSearch_clearedField_dropsResultsAtOnceWithoutAsking() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(Action.OnAddTripClick)
        viewModel.onAction(Action.OnDestinationQueryChanged("Vie"))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.createTrip.destinationResults.size)

        viewModel.onAction(Action.OnDestinationQueryChanged("Vien"))
        viewModel.onAction(Action.OnDestinationQueryChanged(""))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.createTrip.destinationResults.isEmpty())
        assertEquals(listOf("Vie"), places.queries)
    }

    @Test
    fun createTrip_withDestination_savesIt() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val paris = Destination("Paris", GeoPoint(48.8566, 2.3522))

        viewModel.onAction(Action.OnAddTripClick)
        viewModel.onAction(Action.OnCreateNameChanged("Spring"))
        viewModel.onAction(Action.OnCreateDatesChanged(LocalDate(2026, 7, 1), LocalDate(2026, 7, 2)))
        viewModel.onAction(Action.OnDestinationQueryChanged("Par"))
        advanceUntilIdle()
        viewModel.onAction(Action.OnDestinationSelected(viewModel.uiState.value.createTrip.destinationResults.single()))
        viewModel.onAction(Action.OnConfirmCreateTrip)
        advanceUntilIdle()

        val tripId = viewModel.uiState.value.trips.single().id
        assertEquals(paris, (backend.refreshTrip(tripId) as AppResult.Success).data.destination)
    }

    @Test
    fun createTrip_withoutDates_sendsNothing() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.createTrip("Someday", start = null, end = null)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.createTrip.canCreate)
        assertEquals(Dialog.Create, viewModel.uiState.value.dialog)
        assertTrue(backend.observeMyTrips().first().isEmpty())
    }

    @Test
    fun createTrip_withBlankName_sendsNothing() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.createTrip("  ", LocalDate(2026, 7, 1), LocalDate(2026, 7, 2))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.createTrip.canCreate)
        assertEquals(Dialog.Create, viewModel.uiState.value.dialog)
        assertTrue(backend.observeMyTrips().first().isEmpty())
    }

    @Test
    fun openingTheSheet_startsWithTheAccountsDefaultVisibility() = runTest {
        defaultVisibility = TripVisibility.PRIVATE
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnAddTripClick)
        advanceUntilIdle()

        assertEquals(TripVisibility.PRIVATE, viewModel.uiState.value.createTrip.visibility)
    }

    @Test
    fun createTrip_withVisibilityAndMembers_sendsThem() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnAddTripClick)
        advanceUntilIdle()
        viewModel.onAction(Action.OnCreateNameChanged("Rome"))
        viewModel.onAction(Action.OnCreateDatesChanged(LocalDate(2026, 7, 1), LocalDate(2026, 7, 2)))
        viewModel.onAction(Action.OnCreateVisibilityChanged(TripVisibility.PRIVATE))
        viewModel.onAction(Action.OnFriendToggled(anna))
        viewModel.onAction(Action.OnMemberQueryChanged("jon"))
        advanceUntilIdle()
        viewModel.onAction(Action.OnMemberAdded(viewModel.uiState.value.createTrip.memberResults.single()))
        viewModel.onAction(Action.OnConfirmCreateTrip)
        advanceUntilIdle()

        val trip = viewModel.uiState.value.trips.single()
        assertNull(viewModel.uiState.value.dialog)
        assertEquals(TripVisibility.PRIVATE, trip.visibility)
        val details = (backend.refreshTrip(trip.id) as AppResult.Success).data
        assertEquals(listOf("me", "anna", "jonas.k"), details.members.map { it.user.username })
        assertEquals(listOf(TripRole.OWNER, TripRole.EDITOR, TripRole.EDITOR), details.members.map { it.role })
    }

    @Test
    fun friends_fillTheRow_andATapTogglesThemAsMembers() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnAddTripClick)
        advanceUntilIdle()
        assertEquals(listOf("anna", "jonas.k", "lena"), viewModel.uiState.value.createTrip.friends.map { it.username })

        viewModel.onAction(Action.OnFriendToggled(anna))
        viewModel.onAction(Action.OnFriendToggled(jonas))
        advanceUntilIdle()
        assertEquals(listOf(anna, jonas), viewModel.uiState.value.createTrip.members)

        viewModel.onAction(Action.OnFriendToggled(anna))
        advanceUntilIdle()
        assertEquals(listOf(jonas), viewModel.uiState.value.createTrip.members)
    }

    @Test
    fun reopeningTheSheet_startsAFreshForm() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(Action.OnAddTripClick)
        viewModel.onAction(Action.OnCreateNameChanged("Rome"))
        viewModel.onAction(Action.OnCreateDatesChanged(LocalDate(2026, 7, 1), LocalDate(2026, 7, 2)))
        viewModel.onAction(Action.OnFriendToggled(anna))

        viewModel.onAction(Action.OnDismissDialog)
        viewModel.onAction(Action.OnAddTripClick)
        advanceUntilIdle()

        val form = viewModel.uiState.value.createTrip
        assertEquals("", form.name)
        assertTrue(form.members.isEmpty())
    }

    @Test
    fun createTrip_refusedMembers_showTheReasonOnTheirChips() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val unknown = UserSummary("u8", "unknown", "Unknown")
        val blocked = UserSummary("u9", "blocked", "Blocked")

        viewModel.onAction(Action.OnAddTripClick)
        viewModel.onAction(Action.OnCreateNameChanged("Rome"))
        viewModel.onAction(Action.OnCreateDatesChanged(LocalDate(2026, 7, 1), LocalDate(2026, 7, 2)))
        listOf(anna, unknown, blocked).forEach { viewModel.onAction(Action.OnMemberAdded(it)) }
        viewModel.onAction(Action.OnConfirmCreateTrip)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Dialog.Create, state.dialog)
        assertEquals(UiText.Resource(Res.string.create_trip_error_members), state.dialogError)
        assertEquals(
            mapOf<String, UiText>(
                "unknown" to UiText.Resource(Res.string.create_trip_member_not_found),
                "blocked" to UiText.Resource(Res.string.create_trip_member_blocked),
            ),
            state.createTrip.memberErrors.toMap(),
        )
        assertTrue(state.trips.isEmpty())

        viewModel.onAction(Action.OnMemberRemoved("unknown"))
        advanceUntilIdle()
        assertEquals(setOf("blocked"), viewModel.uiState.value.createTrip.memberErrors.keys)
        assertEquals(UiText.Resource(Res.string.create_trip_error_members), viewModel.uiState.value.dialogError)

        viewModel.onAction(Action.OnMemberRemoved("blocked"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.createTrip.memberErrors.isEmpty())
        assertNull(viewModel.uiState.value.dialogError)

        viewModel.onAction(Action.OnConfirmCreateTrip)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.dialog)
        assertEquals(listOf("Rome"), viewModel.uiState.value.trips.map { it.name })
    }

    @Test
    fun usernameSearch_waitsForTypingToPause_andAsksOnceForTheLastText() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(Action.OnAddTripClick)
        advanceUntilIdle()

        listOf("j", "jo", "jon").forEach {
            viewModel.onAction(Action.OnMemberQueryChanged(it))
            advanceTimeBy(100)
        }
        assertTrue(users.queries.isEmpty())

        advanceTimeBy(250)
        assertEquals(listOf("jon"), users.queries)
        assertEquals(listOf(jonas), viewModel.uiState.value.createTrip.memberResults)
        assertFalse(viewModel.uiState.value.createTrip.isSearchingMembers)
    }

    @Test
    fun usernameSearch_clearedField_dropsResultsAtOnceWithoutAsking() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(Action.OnAddTripClick)
        viewModel.onAction(Action.OnMemberQueryChanged("an"))
        advanceUntilIdle()
        assertEquals(listOf(anna), viewModel.uiState.value.createTrip.memberResults)

        viewModel.onAction(Action.OnMemberQueryChanged("ann"))
        viewModel.onAction(Action.OnMemberQueryChanged(""))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.createTrip.memberResults.isEmpty())
        assertEquals(listOf("an"), users.queries)
    }

    /** Opens the sheet, fills in the name and dates and taps Create trip. */
    private fun HomeViewModel.createTrip(name: String, start: LocalDate?, end: LocalDate?) {
        onAction(Action.OnAddTripClick)
        onAction(Action.OnCreateNameChanged(name))
        onAction(Action.OnCreateDatesChanged(start, end))
        onAction(Action.OnConfirmCreateTrip)
    }
}
