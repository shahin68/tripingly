package com.falcon.tripingly.feature.settings

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.AccountRepository
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.account.SocialSignIn
import com.falcon.tripingly.core.data.account.SocialSignInResult
import com.falcon.tripingly.core.model.account.DataExportStatus
import com.falcon.tripingly.core.model.account.IdentityProof
import com.falcon.tripingly.core.model.account.NotificationSettings
import com.falcon.tripingly.core.model.account.NotificationType
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.SignOutReason
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.AccountDialog
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.AccountDialogState
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.Action
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Onboarded, so the session is SignedIn. */
    private suspend fun signedIn() = FakeAccountBackend(today = { LocalDate(2026, 10, 19) }).also {
        it.signInForDevelopment("me", "Me")
        it.updateProfile(ProfileUpdate(username = "me", birthDate = LocalDate(1990, 1, 1)))
        it.accept((it.legalDocuments("en") as AppResult.Success).data)
    }

    /** The Google sheet answers [google]. */
    private class FakeSocialSignIn(var google: SocialSignInResult = SocialSignInResult.Google("fresh-token")) : SocialSignIn {
        override val appleAvailable = false
        override suspend fun google() = google
        override suspend fun apple() = SocialSignInResult.NotConfigured
    }

    private fun viewModel(
        account: FakeAccountBackend,
        repository: AccountRepository = account,
        socialSignIn: SocialSignIn = FakeSocialSignIn(),
    ) = SettingsViewModel(
        sessionRepository = account,
        accountRepository = repository,
        socialSignIn = socialSignIn,
        appVersion = "1.0",
        languageTag = { "en" },
    )

    @Test
    fun opening_showsTheAccountAndItsSettings() = runTest {
        val viewModel = viewModel(signedIn())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("me", state.username)
        assertFalse(state.hasPro)
        assertEquals(TripVisibility.PUBLIC, state.defaultVisibility)
        assertEquals(NotificationType.entries.toSet(), state.notifications.settings?.enabled)
        assertNull(state.export.export)
        assertTrue(state.export.hasLoaded)
        assertEquals(2, state.legalDocuments.size)
    }

    @Test
    fun aProAccount_showsProAsActive() = runTest {
        val account = FakeAccountBackend(today = { LocalDate(2026, 10, 20) }, entitlements = setOf("routes")).also {
            it.signInForDevelopment("me", "Me")
            it.updateProfile(ProfileUpdate(username = "me", birthDate = LocalDate(1990, 1, 1)))
            it.accept((it.legalDocuments("en") as AppResult.Success).data)
        }
        val viewModel = viewModel(account)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasPro)
    }

    @Test
    fun changingTheDefaultVisibility_updatesTheAccount() = runTest {
        val account = signedIn()
        val viewModel = viewModel(account)
        advanceUntilIdle()

        viewModel.onAction(Action.VisibilityChanged(TripVisibility.PRIVATE))
        assertEquals(TripVisibility.PRIVATE, viewModel.uiState.value.defaultVisibility)
        advanceUntilIdle()

        val session = account.session.value as SessionState.SignedIn
        assertEquals(TripVisibility.PRIVATE, session.account.defaultTripVisibility)
    }

    @Test
    fun aRefusedVisibilityChange_goesBack() = runTest {
        val account = signedIn()
        val refusing = object : AccountRepository by account {
            override suspend fun setDefaultTripVisibility(visibility: TripVisibility) =
                AppResult.Error(DataError.Network.NoInternet)
        }
        val viewModel = viewModel(account, refusing)
        advanceUntilIdle()

        viewModel.onAction(Action.VisibilityChanged(TripVisibility.PRIVATE))
        advanceUntilIdle()

        assertEquals(TripVisibility.PUBLIC, viewModel.uiState.value.defaultVisibility)
        assertNotNull(viewModel.uiState.value.message)
    }

    @Test
    fun tappingAgainWhileTheServerIsSlow_keepsTheLatestChoice() = runTest {
        val account = signedIn()
        val slow = object : AccountRepository by account {
            override suspend fun setDefaultTripVisibility(visibility: TripVisibility) =
                delay(1_000).let { account.setDefaultTripVisibility(visibility) }
        }
        val viewModel = viewModel(account, slow)
        advanceUntilIdle()
        val shown = mutableListOf<TripVisibility>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { shown += it.defaultVisibility }
        }

        viewModel.onAction(Action.VisibilityChanged(TripVisibility.PRIVATE))
        advanceTimeBy(500)
        viewModel.onAction(Action.VisibilityChanged(TripVisibility.PUBLIC))
        advanceUntilIdle()

        // Each tap moves the toggle once; the server's answers never move it again.
        assertEquals(listOf(TripVisibility.PUBLIC, TripVisibility.PRIVATE, TripVisibility.PUBLIC), shown.distinctUntilChanged())
        val session = account.session.value as SessionState.SignedIn
        assertEquals(TripVisibility.PUBLIC, session.account.defaultTripVisibility)
    }

    @Test
    fun turningANotificationOff_movesTheSwitchAtOnce() = runTest {
        val account = signedIn()
        val viewModel = viewModel(account)
        advanceUntilIdle()

        viewModel.onAction(Action.Notifications.Changed(NotificationType.LIKES, enabled = false))

        assertFalse(NotificationType.LIKES in viewModel.uiState.value.notifications.settings!!.enabled)
        advanceUntilIdle()
        val saved = (account.notificationSettings() as AppResult.Success).data
        assertEquals(NotificationSettings(NotificationType.entries.toSet() - NotificationType.LIKES), saved)
    }

    @Test
    fun aRefusedNotificationChange_movesTheSwitchBack() = runTest {
        val account = signedIn()
        val refusing = object : AccountRepository by account {
            override suspend fun setNotification(type: NotificationType, enabled: Boolean) =
                AppResult.Error(DataError.Network.NoInternet)
        }
        val viewModel = viewModel(account, refusing)
        advanceUntilIdle()

        viewModel.onAction(Action.Notifications.Changed(NotificationType.COMMENTS, enabled = false))
        advanceUntilIdle()

        assertTrue(NotificationType.COMMENTS in viewModel.uiState.value.notifications.settings!!.enabled)
        assertNotNull(viewModel.uiState.value.message)
    }

    @Test
    fun requestingAnExport_showsItsLink() = runTest {
        val viewModel = viewModel(signedIn())
        advanceUntilIdle()

        viewModel.onAction(Action.RequestExport)
        advanceUntilIdle()

        val export = viewModel.uiState.value.export.export!!
        assertEquals(DataExportStatus.READY, export.status)
        assertNotNull(export.downloadUrl)
    }

    @Test
    fun aSecondExportTheSameDay_showsTheServersMessage() = runTest {
        val account = signedIn()
        val limited = object : AccountRepository by account {
            override suspend fun requestDataExport() =
                AppResult.Error(DataError.Network.Api(429, "RATE_LIMITED", "Try again tomorrow."))
        }
        val viewModel = viewModel(account, limited)
        advanceUntilIdle()

        viewModel.onAction(Action.RequestExport)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.export.isRequesting)
        assertNotNull(viewModel.uiState.value.message)
    }

    @Test
    fun deletingTheAccount_signsOut() = runTest {
        val account = signedIn()
        val viewModel = viewModel(account)
        advanceUntilIdle()

        viewModel.onAction(Action.Account.Delete)
        assertEquals(AccountDialog.Delete, viewModel.uiState.value.account?.dialog)
        viewModel.onAction(Action.Account.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(SessionState.SignedOut(SignOutReason.ACCOUNT_DELETED), account.session.value)
    }

    @Test
    fun theDeleteDialog_closes_butNotWhileTheDeletionIsOnItsWay() = runTest {
        val account = signedIn()
        val slow = CompletableDeferred<Unit>()
        val repository = object : AccountRepository by account {
            override suspend fun deleteAccount(): AppResult<Unit, DataError.Network> {
                slow.await()
                return AppResult.Error(DataError.Network.NoInternet)
            }
        }
        val viewModel = viewModel(account, repository)
        advanceUntilIdle()

        viewModel.onAction(Action.Account.Delete)
        viewModel.onAction(Action.Account.Dismiss)
        assertNull(viewModel.uiState.value.account)

        viewModel.onAction(Action.Account.Delete)
        viewModel.onAction(Action.Account.ConfirmDelete)
        advanceUntilIdle()
        viewModel.onAction(Action.Account.Dismiss)
        assertEquals(AccountDialogState(AccountDialog.Delete, isDeleting = true), viewModel.uiState.value.account)

        slow.complete(Unit)
        advanceUntilIdle()
        assertEquals(AccountDialogState(AccountDialog.Delete), viewModel.uiState.value.account)
        assertNotNull(viewModel.uiState.value.message)
    }

    @Test
    fun deletingWithoutAFreshSignIn_asksForOne() = runTest {
        val account = signedIn()
        val needsSignIn = object : AccountRepository by account {
            override suspend fun deleteAccount() =
                AppResult.Error(DataError.Network.Api(403, "REAUTH_REQUIRED", "Sign in again."))
        }
        val viewModel = viewModel(account, needsSignIn)
        advanceUntilIdle()

        viewModel.onAction(Action.Account.Delete)
        viewModel.onAction(Action.Account.ConfirmDelete)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AccountDialogState(AccountDialog.Reauth), state.account)
        assertNull(state.message)
        assertTrue(account.session.value is SessionState.SignedIn)
    }

    private fun needingSignIn(account: FakeAccountBackend, confirm: suspend (IdentityProof) -> AppResult<Unit, DataError.Network>) =
        object : AccountRepository by account {
            var proof: IdentityProof? = null
            override suspend fun deleteAccount() =
                AppResult.Error(DataError.Network.Api(403, "REAUTH_REQUIRED", "Sign in again."))
            override suspend fun confirmAccountDeletion(proof: IdentityProof): AppResult<Unit, DataError.Network> {
                this.proof = proof
                return confirm(proof)
            }
        }

    @Test
    fun confirmingWithTheSameAccount_deletesIt() = runTest {
        val account = signedIn()
        val repository = needingSignIn(account) { account.confirmAccountDeletion(it) }
        val viewModel = viewModel(account, repository)
        advanceUntilIdle()

        viewModel.onAction(Action.Account.Delete)
        viewModel.onAction(Action.Account.ConfirmDelete)
        advanceUntilIdle()
        viewModel.onAction(Action.Account.ConfirmWithGoogle)
        advanceUntilIdle()

        assertEquals(IdentityProof.Google("fresh-token"), repository.proof)
        assertEquals(SessionState.SignedOut(SignOutReason.ACCOUNT_DELETED), account.session.value)
    }

    @Test
    fun confirmingWithAnotherAccount_keepsEverything() = runTest {
        val account = signedIn()
        val repository = needingSignIn(account) {
            AppResult.Error(DataError.Network.Api(403, "REAUTH_FAILED", "That sign-in didn't confirm this account."))
        }
        val viewModel = viewModel(account, repository)
        advanceUntilIdle()

        viewModel.onAction(Action.Account.Delete)
        viewModel.onAction(Action.Account.ConfirmDelete)
        advanceUntilIdle()
        viewModel.onAction(Action.Account.ConfirmWithGoogle)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.account)
        assertNotNull(state.message)
        assertTrue(account.session.value is SessionState.SignedIn)
    }

    @Test
    fun closingTheSignInSheet_keepsTheDialog() = runTest {
        val account = signedIn()
        val repository = needingSignIn(account) { AppResult.Success(Unit) }
        val viewModel = viewModel(account, repository, FakeSocialSignIn(SocialSignInResult.Cancelled))
        advanceUntilIdle()

        viewModel.onAction(Action.Account.Delete)
        viewModel.onAction(Action.Account.ConfirmDelete)
        advanceUntilIdle()
        viewModel.onAction(Action.Account.ConfirmWithGoogle)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AccountDialogState(AccountDialog.Reauth), state.account)
        assertNull(state.message)
        assertNull(repository.proof)
    }

    @Test
    fun signingOut_asksFirst() = runTest {
        val account = signedIn()
        val viewModel = viewModel(account)
        advanceUntilIdle()

        viewModel.onAction(Action.Account.SignOut)
        assertTrue(account.session.value is SessionState.SignedIn)
        viewModel.onAction(Action.Account.ConfirmSignOut)
        advanceUntilIdle()

        assertEquals(SessionState.SignedOut(null), account.session.value)
    }
}

/** The values in order, without repeats in a row. */
private fun <T> List<T>.distinctUntilChanged(): List<T> = filterIndexed { i, value -> i == 0 || this[i - 1] != value }
